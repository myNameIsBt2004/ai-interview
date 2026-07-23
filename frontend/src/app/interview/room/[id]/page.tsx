"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { Button, Input, Modal, Tag, message } from "antd";
import {
  AudioMutedOutlined,
  AudioOutlined,
  PhoneOutlined,
  SoundOutlined,
  VideoCameraOutlined,
} from "@ant-design/icons";
import { useParams, useRouter } from "next/navigation";
import {
  getMockInterviewByIdUsingGet,
  handleMockInterviewEventUsingPost,
} from "@/api/mockInterviewController";
import {
  InterviewRecord,
  loadSetup,
  saveRecord,
} from "@/libs/interviewStore";
import { speakNatural, stopSpeaking, warmUpVoices } from "@/libs/speech";
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

function estimateScore(text: string) {
  const len = text?.length || 0;
  return Math.min(95, Math.max(55, 60 + Math.floor(len / 80)));
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

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recognitionRef = useRef<any>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const startTimeRef = useRef<string>(new Date().toISOString().replace("T", " ").slice(0, 19));

  useEffect(() => {
    warmUpVoices();
  }, []);

  useEffect(() => {
    if (!id) return;
    (async () => {
      try {
        const res = await getMockInterviewByIdUsingGet({ id });
        const data = res.data!;
        setMessages(parseMessages(data.messages));
        setStarted(data.status === 1);
        setEnded(data.status === 2);
      } catch {
        message.error("加载面试失败");
      }
    })();
  }, [id]);

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
    speakNatural(text);
  };

  const skipVoice = () => {
    stopSpeaking();
  };

  const toggleMic = () => {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      message.warning("这个浏览器不支持语音识别，直接打字吧");
      return;
    }
    if (micOn) {
      recognitionRef.current?.stop();
      setMicOn(false);
      setListening(false);
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = "zh-CN";
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.onresult = (event: any) => {
      let finalText = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) finalText += event.results[i][0].transcript;
      }
      if (finalText) setInput((prev) => (prev ? `${prev}${finalText}` : finalText));
    };
    recognition.onerror = () => {
      setListening(false);
      message.warning("语音识别出了点问题，可以改成打字");
    };
    recognition.onend = () => {
      setListening(false);
      if (micOn) {
        try {
          recognition.start();
          setListening(true);
        } catch {
          /* ignore */
        }
      }
    };
    recognitionRef.current = recognition;
    recognition.start();
    setMicOn(true);
    setListening(true);
    message.success("麦克风开了，直接说就行");
  };

  const callEvent = async (event: string, msg?: string) => {
    setLoading(true);
    try {
      const res = await handleMockInterviewEventUsingPost({
        event,
        id,
        message: msg,
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
        ]);
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

  const finishAndSave = (summary: string, allMsgs: ChatMsg[]) => {
    skipVoice();
    recognitionRef.current?.stop();
    setMicOn(false);
    const record: InterviewRecord = {
      id,
      interviewType: setup?.interviewType || "综合面试",
      jobPosition: setup?.jobPosition || "未知岗位",
      difficulty: setup?.difficulty || "中等",
      companyName: setup?.companyName,
      durationMinutes: Math.max(1, Math.round(seconds / 60) || 1),
      score: estimateScore(summary),
      status: "completed",
      startTime: startTimeRef.current,
      summary,
      messages: allMsgs,
    };
    saveRecord(record);
    Modal.confirm({
      title: "面试已顺利完成！",
      icon: null,
      content: (
        <div>
          <p>您的专业表现正在生成详细评估报告中...</p>
          <p>报告将包含：综合点评、优缺点分析、改进建议、细致的问答表现分析等</p>
          <div
            style={{
              background: "#eaf3ff",
              padding: "10px 12px",
              borderRadius: 8,
              marginTop: 8,
            }}
          >
            预计数秒内可查看（本演示版即时生成）
          </div>
        </div>
      ),
      okText: "查看报告",
      cancelText: "返回首页",
      onOk: () => router.push(`/interview/report/${id}`),
      onCancel: () => router.push("/interview/setup"),
    });
  };

  const confirmEnd = () => {
    Modal.confirm({
      title: "结束面试",
      content: "确定要结束面试吗？结束后将生成评估报告。",
      okText: "确定",
      cancelText: "取消",
      onOk: async () => {
        await callEvent("end");
      },
    });
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
                  ? listening
                    ? "正在听你说..."
                    : "语音识别中，也可继续手动输入"
                  : "麦克风已关闭，可手动输入或在控制栏开启麦克风"
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
            <div className="ai-face">{setup?.interviewer || "AI"}</div>
            <Tag color="gold">仅展示形象，无需摄像头</Tag>
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
            <li>语音异常时可关闭麦克风，改用文字输入</li>
          </ul>
        </div>
      </aside>

      <div className="control-bar card">
        <button
          className={`ctrl ${micOn ? "on" : "off"}`}
          onClick={toggleMic}
          disabled={!started || ended}
        >
          {micOn ? <AudioOutlined /> : <AudioMutedOutlined />}
          <span>{micOn ? "静音" : "解除静音"}</span>
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
    </div>
  );
}
