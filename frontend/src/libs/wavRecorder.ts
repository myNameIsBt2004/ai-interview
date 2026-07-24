/**
 * 浏览器采集麦克风，导出 16kHz mono WAV。
 * 支持持续录音：按静音自动切句，边说边识别。
 */

export type ContinuousOptions = {
  /** 切出一段语音时回调 */
  onSegment: (blob: Blob) => void | Promise<void>;
  /** 判定为静音的 RMS 阈值，默认 0.015 */
  silenceThreshold?: number;
  /** 静音持续多久后切句（毫秒），默认 1200 */
  silenceMs?: number;
  /** 最短有效语音时长（毫秒），默认 500 */
  minSpeechMs?: number;
  /** 单段最长（毫秒），超时强制切句，默认 10000 */
  maxSegmentMs?: number;
};

export class WavRecorder {
  private stream: MediaStream | null = null;
  private ctx: AudioContext | null = null;
  private source: MediaStreamAudioSourceNode | null = null;
  private processor: ScriptProcessorNode | null = null;
  private chunks: Float32Array[] = [];
  private sampleRate = 16000;
  private recording = false;
  private continuous = false;
  private options: ContinuousOptions | null = null;

  private speechStarted = false;
  private silenceSamples = 0;
  private speechSamples = 0;
  private flushing = false;

  async start() {
    await this.openMic(false, null);
  }

  /** 持续录音：说完一句（静音）自动回调一段 WAV，无需反复点 */
  async startContinuous(options: ContinuousOptions) {
    await this.openMic(true, options);
  }

  private async openMic(continuous: boolean, options: ContinuousOptions | null) {
    if (this.recording) return;
    this.chunks = [];
    this.continuous = continuous;
    this.options = options;
    this.speechStarted = false;
    this.silenceSamples = 0;
    this.speechSamples = 0;
    this.flushing = false;

    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
      },
      video: false,
    });
    this.ctx = new AudioContext({ sampleRate: this.sampleRate });
    this.sampleRate = this.ctx.sampleRate;
    this.source = this.ctx.createMediaStreamSource(this.stream);
    this.processor = this.ctx.createScriptProcessor(4096, 1, 1);
    this.processor.onaudioprocess = (e) => {
      if (!this.recording) return;
      const input = e.inputBuffer.getChannelData(0);
      const copy = new Float32Array(input);
      this.chunks.push(copy);

      if (this.continuous && this.options) {
        this.handleVad(copy);
      }
    };
    this.source.connect(this.processor);
    // 接到静音节点，保证处理链路跑起来，又不会从扬声器听到自己
    const mute = this.ctx.createGain();
    mute.gain.value = 0;
    this.processor.connect(mute);
    mute.connect(this.ctx.destination);
    this.recording = true;
  }

  private handleVad(frame: Float32Array) {
    if (!this.options || this.flushing) return;
    const thr = this.options.silenceThreshold ?? 0.015;
    const silenceMs = this.options.silenceMs ?? 1200;
    const minSpeechMs = this.options.minSpeechMs ?? 500;
    const maxSegmentMs = this.options.maxSegmentMs ?? 10000;
    const rms = calcRms(frame);
    const frameMs = (frame.length / this.sampleRate) * 1000;

    if (rms >= thr) {
      this.speechStarted = true;
      this.silenceSamples = 0;
      this.speechSamples += frame.length;
    } else if (this.speechStarted) {
      this.silenceSamples += frame.length;
      this.speechSamples += frame.length;
    } else {
      // 开头静音丢掉，避免空段
      this.chunks = [];
      return;
    }

    const silenceDur = (this.silenceSamples / this.sampleRate) * 1000;
    const speechDur = (this.speechSamples / this.sampleRate) * 1000;

    if (
      (silenceDur >= silenceMs && speechDur >= minSpeechMs) ||
      speechDur >= maxSegmentMs
    ) {
      void this.flushSegment();
    }

    // 防止未引用告警
    void frameMs;
  }

  private async flushSegment() {
    if (this.flushing || !this.options) return;
    if (this.chunks.length === 0) return;
    this.flushing = true;
    try {
      const samples = mergeFloat32(this.chunks);
      this.chunks = [];
      this.speechStarted = false;
      this.silenceSamples = 0;
      this.speechSamples = 0;
      // 太短或能量过低（基本是静音），不送识别，避免报 no valid speech
      if (samples.length < this.sampleRate * 0.35) {
        return;
      }
      if (calcRms(samples) < (this.options.silenceThreshold ?? 0.015) * 0.85) {
        return;
      }
      const blob = encodeWav(samples, this.sampleRate);
      await this.options.onSegment(blob);
    } finally {
      this.flushing = false;
    }
  }

  async stop(): Promise<Blob> {
    // 持续模式：先冲掉最后一段
    if (this.continuous && this.options && this.chunks.length > 0) {
      await this.flushSegment();
    }

    this.recording = false;
    this.continuous = false;
    const leftover = this.chunks;
    this.chunks = [];
    this.options = null;

    try {
      this.processor?.disconnect();
      this.source?.disconnect();
      await this.ctx?.close();
    } catch {
      /* ignore */
    }
    this.stream?.getTracks().forEach((t) => t.stop());
    this.processor = null;
    this.source = null;
    this.ctx = null;
    this.stream = null;

    if (leftover.length === 0) {
      return encodeWav(new Float32Array(0), this.sampleRate);
    }
    return encodeWav(mergeFloat32(leftover), this.sampleRate);
  }

  isRecording() {
    return this.recording;
  }
}

function calcRms(frame: Float32Array) {
  let sum = 0;
  for (let i = 0; i < frame.length; i++) {
    const v = frame[i];
    sum += v * v;
  }
  return Math.sqrt(sum / Math.max(1, frame.length));
}

function mergeFloat32(chunks: Float32Array[]) {
  let len = 0;
  chunks.forEach((c) => {
    len += c.length;
  });
  const result = new Float32Array(len);
  let offset = 0;
  chunks.forEach((c) => {
    result.set(c, offset);
    offset += c.length;
  });
  return result;
}

function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);

  writeString(view, 0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  writeString(view, 8, "WAVE");
  writeString(view, 12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeString(view, 36, "data");
  view.setUint32(40, samples.length * 2, true);

  let offset = 44;
  for (let i = 0; i < samples.length; i++, offset += 2) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buffer], { type: "audio/wav" });
}

function writeString(view: DataView, offset: number, str: string) {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i));
  }
}
