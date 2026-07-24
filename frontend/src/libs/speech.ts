/**
 * 语音朗读：按后端开关选择浏览器 speechSynthesis 或火山 TTS 音频播放
 */

import {
  getTtsConfigUsingGet,
  synthesizeTtsUsingPost,
} from "@/api/ttsController";

let cachedVoice: SpeechSynthesisVoice | null | undefined;
let cachedProvider: "browser" | "volc" | null = null;
let currentAudio: HTMLAudioElement | null = null;
let objectUrl: string | null = null;

const PREFERRED_VOICE_PATTERNS = [
  /xiaoxiao/i,
  /xiaoyi/i,
  /yunxi/i,
  /yunyang/i,
  /yunxia/i,
  /huihui/i,
  /yaoyao/i,
  /kangkang/i,
  /google.*中文/i,
  /google.*普通话/i,
  /microsoft.*(xiaoxiao|yunxi|huihui)/i,
  /ting-ting/i,
  /meijia/i,
];

const AVOID_VOICE_PATTERNS = [/espeak/i, /festival/i, /robot/i, /compact/i];

function scoreVoice(v: SpeechSynthesisVoice): number {
  const name = `${v.name} ${v.lang}`;
  if (AVOID_VOICE_PATTERNS.some((re) => re.test(name))) return -100;
  if (!/zh|cmn|chinese|中文|普通话/i.test(name) && !v.lang.toLowerCase().startsWith("zh")) {
    return -50;
  }
  let score = 10;
  if (v.localService) score += 5;
  if (/neural|online|natural|自然/i.test(name)) score += 20;
  PREFERRED_VOICE_PATTERNS.forEach((re, i) => {
    if (re.test(name)) score += 40 - i;
  });
  if (/female|女|xiao|hui|yao|ting|mei/i.test(name)) score += 3;
  return score;
}

export function pickChineseVoice(): SpeechSynthesisVoice | null {
  if (typeof window === "undefined" || !window.speechSynthesis) return null;
  if (cachedVoice !== undefined) return cachedVoice;

  const voices = window.speechSynthesis.getVoices();
  if (!voices.length) {
    cachedVoice = null;
    return null;
  }

  const ranked = [...voices].sort((a, b) => scoreVoice(b) - scoreVoice(a));
  cachedVoice = ranked[0] && scoreVoice(ranked[0]) > 0 ? ranked[0] : null;
  return cachedVoice;
}

/** Chrome 里 voices 经常异步加载 */
export function warmUpVoices(): void {
  if (typeof window === "undefined" || !window.speechSynthesis) return;
  const synth = window.speechSynthesis;
  const refresh = () => {
    cachedVoice = undefined;
    pickChineseVoice();
  };
  refresh();
  synth.addEventListener("voiceschanged", refresh);
  // 预拉取后端 TTS 策略
  void resolveProvider();
}

function cleanForSpeech(text: string): string {
  return text
    .replace(/【面试结束】/g, "面试结束。")
    .replace(/[#>*_`~\-\[\](){}]/g, " ")
    .replace(/\s{2,}/g, " ")
    .trim();
}

function splitSentences(text: string): string[] {
  const parts = text
    .split(/(?<=[。！？；!?；\n])/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (!parts.length) return [text];
  const merged: string[] = [];
  let buf = "";
  for (const p of parts) {
    if ((buf + p).length < 28) {
      buf += p;
    } else {
      if (buf) merged.push(buf);
      buf = p;
    }
  }
  if (buf) merged.push(buf);
  return merged;
}

export type SpeakOptions = {
  onEnd?: () => void;
};

async function resolveProvider(): Promise<"browser" | "volc"> {
  if (cachedProvider) return cachedProvider;
  try {
    const res = await getTtsConfigUsingGet();
    cachedProvider = res.data?.provider === "volc" ? "volc" : "browser";
  } catch {
    cachedProvider = "browser";
  }
  return cachedProvider;
}

function stopVolcAudio() {
  if (currentAudio) {
    currentAudio.pause();
    currentAudio.onended = null;
    currentAudio.onerror = null;
    currentAudio = null;
  }
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  }
}

function base64ToBlob(base64: string, format: string): Blob {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  const mime =
    format === "wav"
      ? "audio/wav"
      : format === "pcm"
        ? "audio/pcm"
        : "audio/mpeg";
  return new Blob([bytes], { type: mime });
}

async function speakWithVolc(text: string, options?: SpeakOptions): Promise<void> {
  stopVolcAudio();
  const cleaned = cleanForSpeech(text).slice(0, 300);
  if (!cleaned) {
    options?.onEnd?.();
    return;
  }
  const res = await synthesizeTtsUsingPost({ text: cleaned });
  const audioBase64 = res.data?.audioBase64;
  const format = res.data?.format || "mp3";
  if (!audioBase64) {
    throw new Error("TTS 未返回音频");
  }
  const blob = base64ToBlob(audioBase64, format);
  objectUrl = URL.createObjectURL(blob);
  const audio = new Audio(objectUrl);
  currentAudio = audio;
  await new Promise<void>((resolve, reject) => {
    audio.onended = () => {
      stopVolcAudio();
      options?.onEnd?.();
      resolve();
    };
    audio.onerror = () => {
      stopVolcAudio();
      reject(new Error("音频播放失败"));
    };
    void audio.play().catch(reject);
  });
}

function speakWithBrowser(text: string, options?: SpeakOptions): void {
  if (typeof window === "undefined" || !window.speechSynthesis) {
    options?.onEnd?.();
    return;
  }
  const synth = window.speechSynthesis;
  synth.cancel();

  const cleaned = cleanForSpeech(text).slice(0, 800);
  if (!cleaned) {
    options?.onEnd?.();
    return;
  }

  const voice = pickChineseVoice();
  const chunks = splitSentences(cleaned);
  let index = 0;

  const speakNext = () => {
    if (index >= chunks.length) {
      options?.onEnd?.();
      return;
    }
    const u = new SpeechSynthesisUtterance(chunks[index]);
    u.lang = voice?.lang || "zh-CN";
    if (voice) u.voice = voice;
    u.rate = 0.92;
    u.pitch = 0.95;
    u.volume = 1;
    u.onend = () => {
      index += 1;
      window.setTimeout(speakNext, 180);
    };
    u.onerror = () => {
      index += 1;
      speakNext();
    };
    synth.speak(u);
  };

  speakNext();
}

/**
 * 按后端 ai.tts.provider 选择朗读方式：
 * - volc：调用后端火山音色合成
 * - browser：浏览器 speechSynthesis
 * 火山失败时自动回退到浏览器
 */
export async function speakNatural(text: string, options?: SpeakOptions): Promise<void> {
  stopSpeaking();
  const provider = await resolveProvider();
  if (provider === "volc") {
    try {
      await speakWithVolc(text, options);
      return;
    } catch (e) {
      console.warn("火山 TTS 失败，回退浏览器朗读", e);
    }
  }
  speakWithBrowser(text, options);
}

export function stopSpeaking(): void {
  stopVolcAudio();
  if (typeof window === "undefined" || !window.speechSynthesis) return;
  window.speechSynthesis.cancel();
}

/** 测试或切换配置后可清空缓存 */
export function resetTtsProviderCache(): void {
  cachedProvider = null;
}
