"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { Button, Input, Modal, message } from "antd";
import {
  AudioMutedOutlined,
  AudioOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  ExclamationCircleFilled,
  HistoryOutlined,
  HomeOutlined,
  PhoneOutlined,
  SoundOutlined,
  VideoCameraOutlined,
} from "@ant-design/icons";
import { useParams, useRouter } from "next/navigation";
import {
  getMockInterviewByIdUsingGet,
  handleMockInterviewEventUsingPost,
} from "@/api/mockInterviewController";
import { getAsrConfigUsingGet, recognizeAsrUsingPost } from "@/api/asrController";
import {
  InterviewRecord,
  INTERVIEWER_AVATAR,
  loadSetup,
  saveRecord,
} from "@/libs/interviewStore";
import { speakNatural, stopSpeaking, warmUpVoices } from "@/libs/speech";
import { WavRecorder } from "@/libs/wavRecorder";
import "./room.css";

type ChatMsg = {
  role: "user" | "assistant" | "system";
  content: string;
};

function parseMessages(raw?: string): ChatMsg[] {
  if (!raw) return [];
  try {
    const list = JSON.parse(raw) as Array<{ role?: string; message?: string }>;
    return list
      .filter((m) => m.role !== "system")
      .map((m) => ({
        role: m.role === "user" ? "user" : "assistant",
        content: m.message || "",
      }));
  } catch {
    return [];
  }
}

export default function InterviewRoomPage() {
  const params = useParams();
  const id = Number(params?.id);
  const router = useRouter();
  const setup = useMemo(() => loadSetup(), []);

  const [loading, setLoading] = useState(false);
  const [started, setStarted] = useState(false);
  const [ended, setEnded] = useState(false);
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState("");
  const [seconds, setSeconds] = useState(0);
  const [micOn, setMicOn] = useState(false);
  const [cameraOn, setCameraOn] = useState(true);
  const [speakerOn, setSpeakerOn] = useState(true);
  const [listening, setListening] = useState(false);
  const [recognizing, setRecognizing] = useState(false);
  const [endConfirmOpen, setEndConfirmOpen] = useState(false);
  const [doneOpen, setDoneOpen] = useState(false);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const wavRecorderRef = useRef<WavRecorder | null>(null);
  const asrEnabledRef = useRef<boolean | null>(null);
  const micOnRef = useRef(false);
  const recognizeSeqRef = useRef(0);
  const nextAppendRef = useRef(0);
  const pendingTextRef = useRef<Map<number, string>>(new Map());
  const inflightRef = useRef(0);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const startTimeRef = useRef<string>(new Date().toISOString().replace("T", " ").slice(0, 19));

  useEffect(() => {
    warmUpVoices();
  }, []);

  useEffect(() => {
    if (!id) {
      message.error("面试 ID 无效");
      router.replace("/interview/setup");
      return;
    }
    (async () => {
      try {
        const res = await getMockInterviewByIdUsingGet({ id });
        const data = res.data!;
        setMessages(parseMessages(data.messages));
        setStarted(data.status === 1);
        setEnded(data.status === 2);
      } catch {
        message.error("这场面试不存在或已删除，请重新开始");
        router.replace("/interview/setup");
      }
    })();
  }, [id, router]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    if (!started || ended) return;
    const t = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [started, ended]);

  useEffect(() => {
    if (cameraOn) startCamera();
    else stopCamera();
    return () => stopCamera();
  }, [cameraOn]);

  const startCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
    } catch {
      message.warning("打不开摄像头，看看浏览器权限开了没");
      setCameraOn(false);
    }
  };

  const stopCamera = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  };

  const speak = (text: string) => {
    if (!speakerOn) return;
    void speakNatural(text);
  };

  const skipVoice = () => {
    stopSpeaking();
  };

  const ensureAsrEnabled = async () => {
    if (asrEnabledRef.current != null) return asrEnabledRef.current;
    try {
      const res = await getAsrConfigUsingGet();
      asrEnabledRef.current = !!res.data?.enabled;
    } catch {
      asrEnabledRef.current = false;
    }
    return asrEnabledRef.current;
  };

  const flushPendingText = () => {
    const pending = pendingTextRef.current;
    while (pending.has(nextAppendRef.current)) {
      const text = pending.get(nextAppendRef.current) || "";
      pending.delete(nextAppendRef.current);
      nextAppendRef.current += 1;
      if (text) {
        setInput((prev) => (prev ? `${prev}${text}` : text));
      }
    }
  };

  /** 并行识别，按说话顺序追加；静音段失败静默忽略 */
  const enqueueRecognize = (blob: Blob) => {
    if (blob.size < 1200) return;
    const seq = recognizeSeqRef.current++;
    inflightRef.current += 1;
    setRecognizing(true);

    void (async () => {
      try {
        const res = await recognizeAsrUsingPost(blob, "speech.wav");
        const text = (res.data?.text || "").trim();
        pendingTextRef.current.set(seq, text);
        flushPendingText();
      } catch (e: any) {
        pendingTextRef.current.set(seq, "");
        flushPendingText();
        const msg = String(e?.message || "");
        // 静音/无有效语音：正常情况，不弹窗
        const isSilence =
          /no valid speech|normal silence|silence audio|no speech|无有效语音|静音/i.test(
            msg,
          );
        if (isSilence) return;
        if (micOnRef.current) {
          message.warning(msg || "有一段没听清，请继续说或改打字");
        } else {
          message.error(msg || "语音转文字失败");
        }
      } finally {
        inflightRef.current = Math.max(0, inflightRef.current - 1);
        if (inflightRef.current === 0) setRecognizing(false);
      }
    })();
  };

  /** 持续听写：开一次麦克风，边说边转文字；再点一次结束 */
  const toggleMic = async () => {
    if (micOn) {
      micOnRef.current = false;
      setListening(false);
      setMicOn(false);
      const recorder = wavRecorderRef.current;
      wavRecorderRef.current = null;
      if (!recorder) return;
      try {
        await recorder.stop();
        const start = Date.now();
        while (inflightRef.current > 0 && Date.now() - start < 8000) {
          await new Promise((r) => setTimeout(r, 100));
        }
        message.success("已结束听写，确认文字后点发送");
      } catch {
        /* ignore */
      }
      return;
    }

    skipVoice();
    try {
      const enabled = await ensureAsrEnabled();
      if (!enabled) {
        message.error(
          "当前无法语音转文字：请配置火山语音凭证并开通录音文件识别后重启后端",
        );
        return;
      }
      recognizeSeqRef.current = 0;
      nextAppendRef.current = 0;
      pendingTextRef.current.clear();
      inflightRef.current = 0;

      const recorder = new WavRecorder();
      await recorder.startContinuous({
        onSegment: (blob) => enqueueRecognize(blob),
        silenceMs: 700,
        minSpeechMs: 320,
        maxSegmentMs: 5000,
        silenceThreshold: 0.012,
      });
      wavRecorderRef.current = recorder;
      micOnRef.current = true;
      setMicOn(true);
      setListening(true);
      message.success("持续听写已开启：停顿片刻会自动出字；说完再点麦克风结束");
    } catch {
      message.error("无法打开麦克风，请检查浏览器权限");
      micOnRef.current = false;
      setMicOn(false);
      setListening(false);
    }
  };

  const callEvent = async (event: string, msg?: string) => {
    setLoading(true);
    try {
      const durationMinutes = Math.max(1, Math.round(seconds / 60) || 1);
      const res = await handleMockInterviewEventUsingPost({
        event,
        id,
        message: msg,
        durationMinutes:
          event === "end" || event === "chat" ? durationMinutes : undefined,
      });
      const userText = msg || (event === "start" ? "我准备好了，开始吧" : "面试结束");
      const aiText = res.data || "";
      setMessages((prev) => [
        ...prev,
        { role: "user", content: userText },
        { role: "assistant", content: aiText },
      ]);
      if (event === "start") setStarted(true);
      if (event === "end" || aiText.includes("【面试结束】")) {
        setEnded(true);
        finishAndSave(aiText, [
          ...messages,
          { role: "user", content: userText },
          { role: "assistant", content: aiText },
        ], durationMinutes);
      } else if (event !== "end") {
        speak(aiText);
      }
      return aiText;
    } catch (e: any) {
      message.error(e?.message || "请求失败");
      return null;
    } finally {
      setLoading(false);
    }
  };

  const finishAndSave = (
    summary: string,
    allMsgs: ChatMsg[],
    durationMinutes: number,
  ) => {
    skipVoice();
    micOnRef.current = false;
    const recorder = wavRecorderRef.current;
    wavRecorderRef.current = null;
    if (recorder?.isRecording()) {
      void recorder.stop();
    }
    setMicOn(false);
    setListening(false);
    const record: InterviewRecord = {
      id,
      interviewType: setup?.interviewType || "综合面试",
      jobPosition: setup?.jobPosition || "未知岗位",
      difficulty: setup?.difficulty || "中等",
      companyName: setup?.companyName,
      durationMinutes,
      status: "completed",
      startTime: startTimeRef.current,
      summary,
      messages: allMsgs,
    };
    saveRecord(record);
    setEndConfirmOpen(false);
    setDoneOpen(true);
  };

  const confirmEnd = () => {
    setEndConfirmOpen(true);
  };

  const handleConfirmEnd = async () => {
    await callEvent("end");
  };

  const send = async () => {
    if (!input.trim() || loading || ended || !started) return;
    const text = input.trim();
    setInput("");
    await callEvent("chat", text);
  };

  const mm = String(Math.floor(seconds / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");

  return (
    <div className="room-page">
      <div className="room-main card">
        <div className="room-main-head">
          <div>
            <strong>
              {setup?.focus || "综合面试"} - {setup?.jobPosition || "面试中"}
            </strong>
          </div>
          <div className="room-timer">{mm}:{ss}</div>
        </div>

        <div className="room-chat">
          {!started && !ended && (
            <div className="room-empty">
              <p>点击下方「开始面试」，AI 面试官将向你提问</p>
              <Button
                type="primary"
                className="btn-brand"
                loading={loading}
                onClick={() => callEvent("start")}
              >
                开始面试
              </Button>
            </div>
          )}
          {messages.map((m, idx) => (
            <div key={idx} className={`bubble-row ${m.role}`}>
              <div className={`bubble ${m.role}`}>
                <div className="bubble-content">{m.content}</div>
                {m.role === "assistant" && (
                  <Button size="small" type="link" onClick={skipVoice}>
                    跳过语音
                  </Button>
                )}
              </div>
            </div>
          ))}
          <div ref={chatEndRef} />
        </div>

        <div className="room-input">
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={!started || ended || loading}
            rows={2}
            placeholder={
              !started
                ? "请先开始面试"
                : micOn
                  ? recognizing
                    ? "听写中，文字会自动出现..."
                    : "持续听写中，停顿约0.7秒会自动出字"
                  : "可打字，或点麦克风开启持续听写"
            }
          />
          <Button type="primary" disabled={!started || ended} loading={loading} onClick={send}>
            发送
          </Button>
        </div>
      </div>

      <aside className="room-side">
        <div className="video-card card">
          <div className="video-label">AI 面试官</div>
          <div className="ai-avatar">
            <img
              className="ai-face"
              src={INTERVIEWER_AVATAR}
              alt={setup?.interviewer || "AI 面试官"}
            />
            <div className="ai-avatar-tip">{setup?.interviewer || "坤坤"}</div>
          </div>
        </div>
        <div className="video-card card">
          <div className="video-label">我</div>
          {cameraOn ? (
            <video ref={videoRef} autoPlay muted playsInline className="user-video" />
          ) : (
            <div className="camera-off">摄像头已关闭</div>
          )}
        </div>
        <div className="tips card">
          <h4>面试提示</h4>
          <ul>
            <li>面试官说完后，可语音作答或手动输入，然后点击发送</li>
            <li>可以说完后点击发送；也可随时结束面试生成报告</li>
            <li>点麦克风开启持续听写，边说边出字；说完再点一次结束</li>
            <li>识别异常时可直接打字发送</li>
          </ul>
        </div>
      </aside>

      <div className="control-bar card">
        <button
          className={`ctrl ${micOn ? "on" : "off"}`}
          onClick={() => void toggleMic()}
          disabled={!started || ended}
        >
          {micOn ? <AudioOutlined /> : <AudioMutedOutlined />}
          <span>
            {micOn ? (recognizing ? "听写中..." : "结束听写") : "开始听写"}
          </span>
        </button>
        <button
          className={`ctrl ${speakerOn ? "on" : "off"}`}
          onClick={() => {
            if (speakerOn) skipVoice();
            setSpeakerOn((v) => !v);
          }}
        >
          <SoundOutlined />
          <span>{speakerOn ? "关闭扬声器" : "开启扬声器"}</span>
        </button>
        <button className={`ctrl ${cameraOn ? "on" : "off"}`} onClick={() => setCameraOn((v) => !v)}>
          <VideoCameraOutlined />
          <span>{cameraOn ? "停止视频" : "开启视频"}</span>
        </button>
        <button className="ctrl end" onClick={confirmEnd} disabled={!started || ended}>
          <PhoneOutlined />
          <span>结束面试</span>
        </button>
      </div>

      <Modal
        open={endConfirmOpen}
        onCancel={() => setEndConfirmOpen(false)}
        footer={null}
        centered
        width={420}
        closable={false}
        className="room-end-modal"
        maskClosable={false}
      >
        <div className="end-confirm">
          <div className="end-confirm-head">
            <ExclamationCircleFilled className="end-confirm-icon" />
            <span className="end-confirm-title">结束面试</span>
          </div>
          <p className="end-confirm-desc">
            确定要结束面试吗？结束后将生成报告。
          </p>
          <div className="end-confirm-actions">
            <Button onClick={() => setEndConfirmOpen(false)}>取消</Button>
            <Button type="primary" loading={loading} onClick={handleConfirmEnd}>
              确定
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        open={doneOpen}
        footer={null}
        centered
        width={480}
        closable={false}
        className="room-done-modal"
        maskClosable={false}
      >
        <div className="done-modal">
          <CheckCircleFilled className="done-check" />
          <h2>面试已顺利完成！</h2>
          <p className="done-sub">您的专业表现正在生成详细评估报告中...</p>
          <p className="done-desc">
            报告将包含：综合点评、优缺点分析、改进建议、细致的问答表现分析等
          </p>
          <div className="done-eta">
            <ClockCircleOutlined />
            <span>预计3-5分钟内完成</span>
          </div>
          <div className="done-actions">
            <Button
              icon={<HomeOutlined />}
              onClick={() => {
                setDoneOpen(false);
                router.push("/");
              }}
            >
              返回首页
            </Button>
            <Button
              type="primary"
              icon={<HistoryOutlined />}
              onClick={() => {
                setDoneOpen(false);
                router.push("/interview/records");
              }}
            >
              查看记录
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
