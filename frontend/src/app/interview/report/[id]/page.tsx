"use client";

import React, { useEffect, useMemo, useState } from "react";
import { Button, Collapse, Empty, Progress } from "antd";
import Link from "next/link";
import { useParams } from "next/navigation";
import { InterviewRecord, loadReport } from "@/libs/interviewStore";
import "./report.css";

function buildAbility(score: number) {
  const base = Math.round(score / 10);
  return [
    { name: "表达能力", score: Math.min(10, base + 1), tip: "表达较清晰，术语使用较规范" },
    { name: "逻辑能力", score: Math.min(10, base), tip: "回答有一定结构，可继续强化分层表述" },
    { name: "技术深度", score: Math.max(4, base - 1), tip: "建议补充原理与量化指标" },
    { name: "项目展现力", score: Math.min(10, base), tip: "项目描述完整度中等，可突出个人贡献" },
    { name: "岗位契合度", score: Math.min(10, base + 1), tip: "与目标岗位方向基本匹配" },
  ];
}

export default function InterviewReportPage() {
  const params = useParams();
  const id = String(params?.id || "");
  const [record, setRecord] = useState<InterviewRecord | null>(null);

  useEffect(() => {
    setRecord(loadReport(id));
  }, [id]);

  const abilities = useMemo(
    () => buildAbility(record?.score || 60),
    [record?.score],
  );

  if (!record) {
    return (
      <div className="page-wrap">
        <Empty description="未找到报告，请先完成一场面试">
          <Link href="/interview/setup">
            <Button type="primary" className="btn-brand">
              开始面试
            </Button>
          </Link>
        </Empty>
      </div>
    );
  }

  const qa = (record.messages || []).reduce<
    Array<{ q: string; a: string }>
  >((acc, cur, idx, arr) => {
    if (cur.role === "assistant") {
      const next = arr[idx + 1];
      acc.push({ q: cur.content, a: next?.role === "user" ? next.content : "" });
    }
    return acc;
  }, []);

  return (
    <div className="page-wrap report-page">
      <div className="card report-hero">
        <div>
          <h1>{record.jobPosition}</h1>
          <div className="hero-meta">
            <div>
              <span>面试类型</span>
              {record.interviewType}
            </div>
            <div>
              <span>岗位名称</span>
              {record.jobPosition}
            </div>
            <div>
              <span>面试时长</span>
              {record.durationMinutes}分钟
            </div>
            <div>
              <span>题目数量</span>
              {Math.max(1, qa.length)}题
            </div>
            <div>
              <span>面试时间</span>
              {record.startTime}
            </div>
          </div>
        </div>
        <div className="score-ring">
          <Progress
            type="circle"
            percent={record.score || 0}
            format={(p) => (
              <div className="score-text">
                <div className="num">{p}</div>
                <div className="label">综合得分</div>
              </div>
            )}
            strokeColor="#f5c518"
            size={120}
          />
        </div>
      </div>

      <div className="card section">
        <h2>面试官评价</h2>
        <p className="summary">{record.summary || "暂无总结"}</p>
      </div>

      <div className="two-col">
        <div className="card observe">
          <h3>关键观察</h3>
          <ul>
            <li>具备与目标岗位相关的技术表述能力</li>
            <li>回答中能体现项目/实习经历线索</li>
            <li>建议补充更多量化结果与边界说明</li>
          </ul>
        </div>
        <div className="card advice">
          <h3>核心建议</h3>
          <ul>
            <li>尽量完成整场面试，避免中途退出影响深度评估</li>
            <li>自我介绍补充学校/时间等基础信息</li>
            <li>用 STAR 结构组织项目经历（情境-任务-行动-结果）</li>
          </ul>
        </div>
      </div>

      <div className="card section">
        <h2>综合能力分析</h2>
        <div className="ability-list">
          {abilities.map((a) => (
            <div key={a.name} className="ability-item">
              <div className="ability-head">
                <strong>
                  {a.name}：{a.score}/10
                </strong>
                <Progress percent={a.score * 10} showInfo={false} strokeColor="#f5c518" />
              </div>
              <div className="muted">{a.tip}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="card section">
        <h2>优点</h2>
        <div className="box green">
          <ul>
            <li>技术栈表达相对完整，方向与岗位匹配</li>
            <li>能围绕项目进行连续性说明</li>
            <li>对追问具备一定承接能力</li>
          </ul>
        </div>
        <h2>待改进</h2>
        <div className="box yellow">
          <ul>
            <li>基础信息（时间线、角色边界）可更完整</li>
            <li>技术原理与生产指标可以再深入</li>
            <li>收尾时可主动总结亮点与成长诉求</li>
          </ul>
        </div>
      </div>

      <div className="card section">
        <h2>面试题目解析记录</h2>
        <Collapse
          items={qa.map((item, i) => ({
            key: String(i),
            label: (
              <div className="qa-label">
                <span className="q-tag">第 {i + 1} 题</span>
                <span className="q-preview">{item.q.slice(0, 42)}...</span>
              </div>
            ),
            children: (
              <div className="qa-body">
                <h4>面试官提问</h4>
                <p>{item.q}</p>
                <h4>您的回答</h4>
                <div className="answer-box">{item.a || "（本题暂无作答记录）"}</div>
              </div>
            ),
          }))}
        />
      </div>

      <div className="report-actions">
        <Link href="/interview/records">
          <Button>返回记录</Button>
        </Link>
        <Link href="/interview/setup">
          <Button type="primary" className="btn-brand">
            再面一次
          </Button>
        </Link>
      </div>
    </div>
  );
}
