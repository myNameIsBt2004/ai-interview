"use client";

import React, { useEffect, useRef, useState } from "react";
import { Button, Collapse, Empty, Progress, Spin, message } from "antd";
import {
  CheckCircleFilled,
  ClockCircleOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileTextOutlined,
  LinkOutlined,
  ReadOutlined,
  ThunderboltOutlined,
  WarningFilled,
} from "@ant-design/icons";
import Link from "next/link";
import { useParams } from "next/navigation";
import { getMockInterviewReportUsingGet } from "@/api/mockInterviewController";
import AbilityRadar from "@/components/AbilityRadar";
import { downloadReportPdf } from "@/libs/downloadReport";
import "./report.css";

export default function InterviewReportPage() {
  const params = useParams();
  const id = Number(params?.id || 0);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);
  const [report, setReport] = useState<API.MockInterviewReportVO | null>(null);
  const [error, setError] = useState<string>();
  const reportRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      return;
    }
    (async () => {
      try {
        const res = await getMockInterviewReportUsingGet({ id });
        setReport(res.data || null);
      } catch (e: any) {
        setError(e?.message || "加载报告失败");
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  const handleDownload = async () => {
    if (!reportRef.current || !report) return;
    setDownloading(true);
    const hide = message.loading("正在生成 PDF...", 0);
    try {
      await downloadReportPdf(
        reportRef.current,
        `${report.jobPosition || "面试"}-评估报告-${report.id || id}`,
      );
      message.success("报告已下载");
    } catch (e: any) {
      message.error(e?.message || "下载失败，请重试");
    } finally {
      hide();
      setDownloading(false);
    }
  };

  if (loading) {
    return (
      <div className="page-wrap" style={{ textAlign: "center", padding: 80 }}>
        <Spin size="large" tip="加载评估报告..." />
      </div>
    );
  }

  if (!report) {
    return (
      <div className="page-wrap">
        <Empty description={error || "未找到报告，请先完成一场面试"}>
          <Link href="/interview/setup">
            <Button type="primary" className="btn-brand">
              开始面试
            </Button>
          </Link>
        </Empty>
      </div>
    );
  }

  const abilities = report.abilities || [];
  const observations = report.observations || [];
  const suggestions = report.suggestions || [];
  const strengths = report.strengths || [];
  const improvements = report.improvements || [];
  const skillMatrix = report.skillMatrix || [];
  const learningFocus = report.learningFocus || [];
  const roadmap = report.roadmap || [];
  const qa = report.qaAnalysis?.length
    ? report.qaAnalysis
    : (report.messages || []).reduce<
        Array<{
          question?: string;
          answer?: string;
          score?: number;
          analysis?: string;
          followUps?: string[];
          comment?: string;
          reference?: string;
          referenceAnswer?: string;
          relatedQuestions?: string[];
        }>
      >((acc, cur, idx, arr) => {
        if (cur.role === "assistant") {
          const next = arr[idx + 1];
          acc.push({
            question: cur.content,
            answer: next?.role === "user" ? next.content : "",
          });
        }
        return acc;
      }, []);

  const startTimeText = report.startTime
    ? String(report.startTime).replace("T", " ").slice(0, 19)
    : "--";

  const barColor = (score?: number) =>
    (score || 0) >= 7 ? "#52c41a" : (score || 0) >= 5 ? "#f5c518" : "#ff7875";

  return (
    <div className="page-wrap report-page">
      <div className="report-toolbar no-print">
        <div className="muted">面试评估报告</div>
        <Button
          type="primary"
          className="btn-brand"
          icon={<DownloadOutlined />}
          loading={downloading}
          onClick={handleDownload}
        >
          下载报告
        </Button>
      </div>

      <div className="report-export" ref={reportRef}>
        <div className="card report-hero">
          <div className="hero-main">
            <h1>{report.jobPosition}</h1>
            <div className="hero-meta">
              <div>
                <span>面试类型</span>
                {report.interviewType || "综合面试"}
              </div>
              <div>
                <span>岗位名称</span>
                {report.jobPosition}
              </div>
              <div>
                <span>面试时长</span>
                {report.durationMinutes ?? "--"}分钟
              </div>
              <div>
                <span>题目数量</span>
                {report.questionCount ?? Math.max(1, qa.length)}题
              </div>
              <div>
                <span>面试时间</span>
                {startTimeText}
              </div>
              <div>
                <span>面试难度</span>
                {report.difficulty || "--"}
              </div>
            </div>
          </div>
          <div className="score-ring">
            <Progress
              type="circle"
              percent={report.score || 0}
              format={(p) => (
                <div className="score-text">
                  <div className="num">{p}</div>
                  <div className="label">综合得分</div>
                </div>
              )}
              strokeColor="#f5c518"
              size={128}
            />
          </div>
        </div>

        <div className="card section">
          <h2>
            <FileTextOutlined className="sec-icon" />
            面试官评价
          </h2>
          <p className="summary">{report.summary || "暂无总结"}</p>
        </div>

        <div className="two-col">
          <div className="card observe">
            <h3>
              <EyeOutlined /> 关键观察
            </h3>
            <ul>
              {(observations.length ? observations : ["暂无关键观察"]).map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
          <div className="card advice">
            <h3>
              <CheckCircleFilled /> 核心建议
            </h3>
            <ul>
              {(suggestions.length ? suggestions : ["暂无建议"]).map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
        </div>

        {abilities.length > 0 && (
          <div className="card section">
            <h2>
              <ThunderboltOutlined className="sec-icon" />
              综合能力分析
            </h2>
            <div className="ability-grid">
              <div className="ability-radar-wrap">
                <AbilityRadar abilities={abilities} />
              </div>
              <div className="ability-list">
                {abilities.map((a) => (
                  <div key={a.name} className="ability-item">
                    <div className="ability-title">
                      <strong>{a.name}</strong>
                      <span className="ability-score" style={{ color: barColor(a.score) }}>
                        {a.score}/10
                      </span>
                    </div>
                    <div className="muted">{a.tip}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        <div className="two-col">
          <div className="card pros-card">
            <h3>
              <CheckCircleFilled /> 优点
            </h3>
            <ul>
              {(strengths.length ? strengths : ["暂无"]).map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
          <div className="card cons-card">
            <h3>
              <WarningFilled /> 待改进
            </h3>
            <ul>
              {(improvements.length ? improvements : ["暂无"]).map((t) => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
        </div>

        {skillMatrix.length > 0 && (
          <div className="card section">
            <h2>
              <ReadOutlined className="sec-icon" />
              技术技能矩阵
            </h2>
            <div className="skill-grid">
              {skillMatrix.map((s) => (
                <div key={s.name} className="skill-card">
                  <div className="skill-head">
                    <strong>{s.name}</strong>
                    <span style={{ color: barColor(s.score) }}>{s.score} / 10</span>
                  </div>
                  <Progress
                    percent={(s.score || 0) * 10}
                    showInfo={false}
                    strokeColor={barColor(s.score)}
                    size="small"
                  />
                  <div className="skill-label">评价</div>
                  <p className="skill-text">{s.evaluation || "暂无评价"}</p>
                  {s.advice ? (
                    <div className="skill-advice">
                      <div className="skill-label">建议</div>
                      <p>{s.advice}</p>
                    </div>
                  ) : null}
                  {(s.resources || []).length > 0 && (
                    <div className="skill-links">
                      {(s.resources || []).map((r) => (
                        <span key={r}>
                          <LinkOutlined /> {r}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {(learningFocus.length > 0 || roadmap.length > 0 || report.learningPlan) && (
          <div className="card section">
            <h2>
              <ReadOutlined className="sec-icon" />
              学习规划与建议
            </h2>
            {report.learningPlan ? <p className="summary">{report.learningPlan}</p> : null}
            {learningFocus.length > 0 && (
              <>
                <h3 className="sub-title">学习重点推荐</h3>
                <div className="focus-tags">
                  {learningFocus.map((f) => (
                    <span
                      key={f.name}
                      className={`focus-tag ${(f.priority || "").includes("高") ? "high" : "mid"}`}
                    >
                      {f.name}
                      {f.priority ? `（${f.priority}）` : ""}
                    </span>
                  ))}
                </div>
              </>
            )}
            {roadmap.length > 0 && (
              <>
                <h3 className="sub-title">提升路线图</h3>
                <div className="roadmap">
                  {roadmap.map((r, idx) => (
                    <div key={`${r.stage}-${idx}`} className="roadmap-item">
                      <div className="roadmap-dot">
                        {idx === 0 ? (
                          <CheckCircleFilled />
                        ) : idx === 1 ? (
                          <ClockCircleOutlined />
                        ) : (
                          <FileTextOutlined />
                        )}
                      </div>
                      <div className="roadmap-body">
                        <div className="roadmap-title">
                          {r.stage}
                          {r.duration ? <span>（{r.duration}）</span> : null}
                        </div>
                        <ul>
                          {(r.items || []).map((it) => (
                            <li key={it}>{it}</li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        <div className="card section">
          <div className="qa-section-head">
            <h2>
              <FileTextOutlined className="sec-icon" />
              面试题目解析记录
            </h2>
            <span className="muted">共 {qa.length} 题</span>
          </div>
          <Collapse
            defaultActiveKey={qa.length ? ["0"] : []}
            items={qa.map((item, i) => ({
              key: String(i),
              label: (
                <div className="qa-label">
                  <span className="q-tag">第 {i + 1} 题</span>
                  <span className="q-preview">
                    {(item.question || "").slice(0, 48)}
                    {(item.question || "").length > 48 ? "..." : ""}
                  </span>
                  {item.score != null ? (
                    <span className="q-score" style={{ color: barColor(item.score) }}>
                      {item.score} / 10 分
                    </span>
                  ) : null}
                </div>
              ),
              children: (
                <div className="qa-body">
                  <h4>面试官提问</h4>
                  <p>{item.question}</p>

                  {(item.followUps || []).length > 0 && (
                    <>
                      <h4>追问</h4>
                      <ol className="follow-list">
                        {(item.followUps || []).map((f) => (
                          <li key={f}>{f}</li>
                        ))}
                      </ol>
                    </>
                  )}

                  <h4>您的回答</h4>
                  <div className="answer-box">{item.answer || "（本题暂无作答记录）"}</div>

                  {item.comment ? (
                    <div className="comment-box">
                      <h4>面试官点评</h4>
                      <p>{item.comment}</p>
                    </div>
                  ) : item.analysis ? (
                    <div className="comment-box">
                      <h4>面试官点评</h4>
                      <p>{item.analysis}</p>
                    </div>
                  ) : null}

                  {item.reference ? (
                    <div className="ref-box">
                      <h4>参考思路</h4>
                      <p>{item.reference}</p>
                    </div>
                  ) : null}

                  {item.referenceAnswer ? (
                    <div className="ref-answer">
                      <h4>参考回答</h4>
                      <p>{item.referenceAnswer}</p>
                    </div>
                  ) : null}

                  {(item.relatedQuestions || []).length > 0 && (
                    <div className="related-links">
                      {(item.relatedQuestions || []).map((q) => (
                        <span key={q}>
                          <LinkOutlined /> {q}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ),
            }))}
          />
        </div>
      </div>

      <div className="report-actions no-print">
        <Link href="/interview/records">
          <Button>返回记录</Button>
        </Link>
        <Button
          type="primary"
          className="btn-brand"
          icon={<DownloadOutlined />}
          loading={downloading}
          onClick={handleDownload}
        >
          下载报告
        </Button>
        <Link href="/interview/setup">
          <Button type="primary" className="btn-brand">
            再面一次
          </Button>
        </Link>
      </div>
    </div>
  );
}
