"use client";

import React, { useEffect, useState } from "react";
import { Button, Empty, Tag } from "antd";
import Link from "next/link";
import { InterviewRecord, loadRecords } from "@/libs/interviewStore";
import "./records.css";

export default function InterviewRecordsPage() {
  const [records, setRecords] = useState<InterviewRecord[]>([]);

  useEffect(() => {
    setRecords(loadRecords());
  }, []);

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
                  <strong>{r.durationMinutes}分钟</strong>
                </div>
                <div>
                  <span>开始时间</span>
                  <strong>{r.startTime}</strong>
                </div>
              </div>
              <div className="record-footer">
                <Tag color="success">已完成</Tag>
                <Link href={`/interview/report/${r.id}`}>
                  <Button type="primary" className="btn-brand">
                    查看面试结果
                  </Button>
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
