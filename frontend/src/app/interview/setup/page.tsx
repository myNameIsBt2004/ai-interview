"use client";

import React, { useMemo, useState } from "react";
import {
  Button,
  Collapse,
  Form,
  Input,
  InputNumber,
  Select,
  Steps,
  Upload,
  message,
} from "antd";
import {
  ArrowLeftOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { useRouter } from "next/navigation";
import { addMockInterviewUsingPost } from "@/api/mockInterviewController";
import {
  DIFFICULTY_OPTIONS,
  DURATION_OPTIONS,
  EXPERIENCE_OPTIONS,
  INTERVIEWER_OPTIONS,
  InterviewSetupData,
  JOB_OPTIONS,
  saveSetup,
} from "@/libs/interviewStore";
import "./setup.css";

const { TextArea } = Input;

const defaultDesc = `1. 参与后端功能开发和联调
2. 写基础接口和单测
3. 按团队规范提交代码`;


export default function InterviewSetupPage() {
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [resumeName, setResumeName] = useState<string>();
  const [form] = Form.useForm<InterviewSetupData>();

  const jobDescription = Form.useWatch("jobDescription", form) || "";
  const personalDesc = Form.useWatch("personalDesc", form) || "";
  const coreSkills = Form.useWatch("coreSkills", form) || "";

  const initialValues = useMemo(
    () => ({
      interviewType: "综合面试",
      jobPosition: "Java后端开发",
      workExperience: "校招应届",
      salaryMin: 8,
      salaryMax: 13,
      jobDescription: defaultDesc,
      personalDesc: "",
      yearsOfExperience: "",
      coreSkills: "",
      projectExperience: "",
      focus: "综合面试",
      duration: 30,
      difficulty: "中等",
      interviewer: "坤坤",
    }),
    [],
  );

  const readResumeFile = async (file: File) => {
    setResumeName(file.name);
    if (file.type.startsWith("text/") || /\.(txt|md|csv)$/i.test(file.name)) {
      const text = await file.text();
      const current = form.getFieldValue("personalDesc") || "";
      form.setFieldsValue({
        personalDesc: current ? `${current}\n\n【简历摘录】\n${text.slice(0, 1800)}` : text.slice(0, 1800),
        resumeName: file.name,
        resumeText: text.slice(0, 5000),
      });
      message.success("简历内容已填入，缺的地方自己补一下就行");
    } else {
      form.setFieldsValue({ resumeName: file.name });
      message.info("文件已上传。PDF/Word 暂不能自动识别，请在下面自己填");
    }
    return false;
  };

  const goNext = async () => {
    try {
      if (step === 1) {
        await form.validateFields(["jobPosition", "workExperience", "jobDescription"]);
        setStep(2);
        return;
      }
      if (step === 2) {
        setStep(3);
        return;
      }
      if (step === 3) {
        await form.validateFields(["difficulty", "duration", "interviewer"]);
        await startInterview();
      }
    } catch {
      /* validation */
    }
  };

  const startInterview = async () => {
    const values = form.getFieldsValue(true) as InterviewSetupData;
    values.resumeName = resumeName || values.resumeName;
    values.interviewType = "综合面试";
    values.focus = values.focus || "综合面试";
    saveSetup(values);

    setLoading(true);
    const hide = message.loading("正在进入面试...", 0);
    try {
      const res = await addMockInterviewUsingPost({
        jobPosition: values.jobPosition,
        workExperience: values.workExperience,
        difficulty: values.difficulty,
      });
      hide();
      message.success("好了，开始吧");
      router.push(`/interview/room/${res.data}`);
    } catch (e: any) {
      hide();
      message.error(e?.message || "创建失败，先登录一下");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="setup-page page-wrap">
      <div className="setup-hero">
        <h1>综合面试</h1>
        <p>按步骤填好信息，就可以开始模拟面试</p>
      </div>

      <div className="card setup-card">
        <Steps
          current={step}
          className="setup-steps"
          items={[
            { title: "面试类型" },
            { title: "目标岗位" },
            { title: "个人信息" },
            { title: "面试设置" },
          ]}
        />

        <Form form={form} layout="vertical" initialValues={initialValues} className="setup-form">
          {step === 1 && (
            <>
              <Form.Item label="岗位名称" name="jobPosition" rules={[{ required: true }]}>
                <Select options={JOB_OPTIONS.map((v) => ({ label: v, value: v }))} />
              </Form.Item>
              <Form.Item label="工作年限" name="workExperience" rules={[{ required: true }]}>
                <Select options={EXPERIENCE_OPTIONS.map((v) => ({ label: v, value: v }))} />
              </Form.Item>
              <Form.Item label="薪资范围">
                <div className="salary-row">
                  <Form.Item name="salaryMin" noStyle>
                    <InputNumber min={1} max={200} />
                  </Form.Item>
                  <span>—</span>
                  <Form.Item name="salaryMax" noStyle>
                    <InputNumber min={1} max={200} />
                  </Form.Item>
                  <span className="muted">K/月</span>
                </div>
              </Form.Item>
              <Form.Item
                label="岗位描述"
                name="jobDescription"
                rules={[{ required: true, message: "请填写岗位描述" }]}
                extra={`${jobDescription.length} / 500`}
              >
                <TextArea rows={6} maxLength={500} showCount={false} />
              </Form.Item>
              <Collapse
                ghost
                items={[
                  {
                    key: "company",
                    label: "公司信息（可选）",
                    children: (
                      <Form.Item label="公司名称" name="companyName">
                        <Input placeholder="比如：某某科技" />
                      </Form.Item>
                    ),
                  },
                ]}
              />
            </>
          )}

          {step === 2 && (
            <>
              <div className="resume-block">
                <div className="resume-head">
                  <h3>选择简历</h3>
                  <Upload beforeUpload={readResumeFile} showUploadList={false} accept=".txt,.md,.pdf,.doc,.docx">
                    <Button icon={<UploadOutlined />} className="btn-brand">
                      从本地导入
                    </Button>
                  </Upload>
                </div>
                {resumeName ? (
                  <div className="resume-card">
                    <div className="resume-thumb">PDF/TXT</div>
                    <div>
                      <div className="resume-name">{resumeName}</div>
                      <div className="muted">已导入，下面还能继续改</div>
                    </div>
                  </div>
                ) : (
                  <div className="resume-empty muted">还没上传简历的话，直接在下面写也行</div>
                )}
              </div>

              <Form.Item
                label="个人描述"
                name="personalDesc"
                extra={`${personalDesc.length} / 2000`}
              >
                <TextArea
                  rows={5}
                  maxLength={2000}
                  placeholder="比如：做过两年 Java，主要写 Spring Boot，也接触过 Redis、MySQL..."
                />
              </Form.Item>

              <Collapse
                ghost
                items={[
                  {
                    key: "extra",
                    label: "补充信息（可选）",
                    children: (
                      <>
                        <Form.Item label="个人工作年限" name="yearsOfExperience">
                          <Input placeholder="例如：5年" />
                        </Form.Item>
                        <Form.Item
                          label="个人核心技能"
                          name="coreSkills"
                          extra={`${(coreSkills || "").length} / 500`}
                        >
                          <TextArea
                            rows={3}
                            maxLength={500}
                            placeholder="例如: Java, Spring Boot, MySQL, Redis, Docker"
                          />
                        </Form.Item>
                        <Form.Item label="个人项目经验" name="projectExperience">
                          <TextArea
                            rows={8}
                            placeholder={"### 1. 项目名称\n**时间**: \n**项目详情**: "}
                          />
                        </Form.Item>
                      </>
                    ),
                  },
                ]}
              />
            </>
          )}

          {step === 3 && (
            <>
              <Form.Item label="面试重点" name="focus">
                <Select disabled options={[{ label: "综合面试", value: "综合面试" }]} />
              </Form.Item>
              <Form.Item label="面试时长" name="duration" rules={[{ required: true }]}>
                <Select options={DURATION_OPTIONS} />
              </Form.Item>
              <Form.Item label="面试难度" name="difficulty" rules={[{ required: true }]}>
                <Select options={DIFFICULTY_OPTIONS.map((v) => ({ label: v, value: v }))} />
              </Form.Item>
              <Form.Item label="面试官选择" name="interviewer" rules={[{ required: true }]}>
                <Select options={INTERVIEWER_OPTIONS.map((v) => ({ label: v, value: v }))} />
              </Form.Item>
            </>
          )}
        </Form>

        <div className="setup-footer">
          <Button
            icon={<ArrowLeftOutlined />}
            disabled={step === 1}
            onClick={() => setStep((s) => Math.max(1, s - 1))}
          >
            上一步
          </Button>
          <Button
            type="primary"
            className="btn-brand"
            loading={loading}
            onClick={goNext}
          >
            {step === 3 ? "开始面试" : "下一步 →"}
          </Button>
        </div>
      </div>
    </div>
  );
}
