/**
 * 浏览器朗读：尽量挑更自然的中文音色，并压低「播报感」
 */

let cachedVoice: SpeechSynthesisVoice | null | undefined;

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
  // 女声在中文场景里通常更柔和一些
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
  // 太碎的短句合并，避免像机器逐条报菜名
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

/**
 * 用更接近「人说话」的参数朗读：稍慢、略降调、分段停顿
 */
export function speakNatural(text: string, options?: SpeakOptions): void {
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
    // 默认 1.0 偏「播报员」；略慢 + 略低调更像聊天
    u.rate = 0.92;
    u.pitch = 0.95;
    u.volume = 1;
    u.onend = () => {
      index += 1;
      // 句间留一点空隙
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

export function stopSpeaking(): void {
  if (typeof window === "undefined" || !window.speechSynthesis) return;
  window.speechSynthesis.cancel();
}
