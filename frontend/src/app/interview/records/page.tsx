"use client";

import React, { useEffect, useState } from "react";
import { Button, Empty, Spin, Tag } from "antd";
import Link from "next/link";
import { listMyMockInterviewByPageUsingPost } from "@/api/mockInterviewController";
import "./records.css";

type RecordItem = {
  id: number;
  interviewType: string;
  jobPosition: string;
  difficulty: string;
  companyName?: string;
  durationMinutes?: number;
  score?: number;
  status: number;
  startTime: string;
};

export default function InterviewRecordsPage() {
  const [loading, setLoading] = useState(true);
  const [records, setRecords] = useState<RecordItem[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const res = await listMyMockInterviewByPageUsingPost({
          current: 1,
          pageSize: 20,
          sortField: "createTime",
          sortOrder: "descend",
        });
        const list = (res.data?.records || []).map((r) => ({
          id: r.id!,
          interviewType: r.interviewType || "综合面试",
          jobPosition: r.jobPosition || "未知岗位",
          difficulty: r.difficulty || "--",
          companyName: r.companyName,
          durationMinutes: r.durationMinutes ?? r.duration,
          score: r.score,
          status: r.status ?? 0,
          startTime: r.startTime
            ? String(r.startTime).replace("T", " ").slice(0, 19)
            : r.createTime
              ? String(r.createTime).replace("T", " ").slice(0, 19)
              : "--",
        }));
        setRecords(list);
      } catch {
        setRecords([]);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return (
      <div className="page-wrap" style={{ textAlign: "center", padding: 80 }}>
        <Spin size="large" tip="加载面试记录..." />
      </div>
    );
  }

  return (
    <div className="page-wrap records-page">
      <h1>我的面试记录</h1>
      {records.length === 0 ? (
        <div className="card" style={{ padding: 40 }}>
          <Empty description="暂无面试记录">
            <Link href="/interview/setup">
              <Button type="primary" className="btn-brand">
                去开始面试
              </Button>
            </Link>
          </Empty>
        </div>
      ) : (
        <div className="records-list">
          {records.map((r) => (
            <div className="card record-card" key={r.id}>
              <Tag color="blue">{r.interviewType}</Tag>
              <h2>{r.jobPosition}</h2>
              <div className="meta">
                <div>
                  <span>面试分数</span>
                  <strong>{r.score ?? "--"}分</strong>
                </div>
                <div>
                  <span>难度等级</span>
                  <strong>{r.difficulty}</strong>
                </div>
                <div>
                  <span>公司名称</span>
                  <strong>{r.companyName || "--"}</strong>
                </div>
                <div>
                  <span>岗位名称</span>
                  <strong>{r.jobPosition}</strong>
                </div>
                <div>
                  <span>面试时长</span>
                  <strong>{r.durationMinutes ?? "--"}分钟</strong>
                </div>
                <div>
                  <span>开始时间</span>
                  <strong>{r.startTime}</strong>
                </div>
              </div>
              <div className="record-footer">
                <Tag color={r.status === 2 ? "success" : r.status === 1 ? "processing" : "default"}>
                  {r.status === 2 ? "已完成" : r.status === 1 ? "进行中" : "待开始"}
                </Tag>
                {r.status === 2 ? (
                  <Link href={`/interview/report/${r.id}`}>
                    <Button type="primary" className="btn-brand">
                      查看面试结果
                    </Button>
                  </Link>
                ) : (
                  <Link href={`/interview/room/${r.id}`}>
                    <Button type="primary" className="btn-brand">
                      继续面试
                    </Button>
                  </Link>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
