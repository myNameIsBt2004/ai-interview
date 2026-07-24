"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  Button,
  Collapse,
  Form,
  Input,
  InputNumber,
  Modal,
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
import { parseResumeUsingPost } from "@/api/resumeController";
import {
  DIFFICULTY_OPTIONS,
  DURATION_OPTIONS,
  EXPERIENCE_OPTIONS,
  INTERVIEWER_OPTIONS,
  InterviewSetupData,
  JOB_OPTIONS,
  saveSetup,
} from "@/libs/interviewStore";
import {
  createResumeThumbnail,
  formatResumeDate,
} from "@/libs/resumeThumbnail";
import "./setup.css";

const { TextArea } = Input;

const defaultDesc = `1. 参与后端功能开发和联调
2. 写基础接口和单测
3. 按团队规范提交代码`;

type ResumePreview = {
  name: string;
  date: string;
  thumbUrl: string | null;
  fileUrl: string | null;
  mime: string;
};

export default function InterviewSetupPage() {
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [resumeName, setResumeName] = useState<string>();
  const [resumePreview, setResumePreview] = useState<ResumePreview | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [extraOpen, setExtraOpen] = useState<string[]>([]);
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

  useEffect(() => {
    return () => {
      if (resumePreview?.fileUrl) {
        URL.revokeObjectURL(resumePreview.fileUrl);
      }
    };
  }, [resumePreview?.fileUrl]);

  const readResumeFile = async (file: File) => {
    setResumeName(file.name);
    setParsing(true);
    const hide = message.loading("正在解析简历...", 0);

    // 先本地生成缩略图，解析可并行
    const fileUrl = URL.createObjectURL(file);
    const thumbPromise = createResumeThumbnail(file);

    try {
      const res = await parseResumeUsingPost(file);
      const thumbUrl = await thumbPromise.catch(() => null);
      const data = res.data;
      if (!data) {
        throw new Error("解析结果为空");
      }
      form.setFieldsValue({
        resumeName: data.resumeName || file.name,
        resumeText: data.resumeText,
        personalDesc: data.personalDesc || form.getFieldValue("personalDesc"),
        yearsOfExperience:
          data.yearsOfExperience || form.getFieldValue("yearsOfExperience"),
        coreSkills: data.coreSkills || form.getFieldValue("coreSkills"),
        projectExperience:
          data.projectExperience || form.getFieldValue("projectExperience"),
      });
      setResumePreview((prev) => {
        if (prev?.fileUrl) URL.revokeObjectURL(prev.fileUrl);
        return {
          name: data.resumeName || file.name,
          date: formatResumeDate(),
          thumbUrl,
          fileUrl,
          mime: file.type || "application/octet-stream",
        };
      });
      setExtraOpen(["extra"]);
      message.success("简历已解析，请核对项目经验是否完整");
    } catch (e: any) {
      form.setFieldsValue({ resumeName: file.name });
      const thumbUrl = await thumbPromise.catch(() => null);
      setResumePreview((prev) => {
        if (prev?.fileUrl) URL.revokeObjectURL(prev.fileUrl);
        return {
          name: file.name,
          date: formatResumeDate(),
          thumbUrl,
          fileUrl,
          mime: file.type || "application/octet-stream",
        };
      });
      message.error(e?.message || "简历解析失败，请手动填写");
    } finally {
      hide();
      setParsing(false);
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
        interviewType: values.interviewType || "综合面试",
        jobPosition: values.jobPosition,
        workExperience: values.workExperience,
        salaryMin: values.salaryMin,
        salaryMax: values.salaryMax,
        jobDescription: values.jobDescription,
        companyName: values.companyName,
        personalDesc: values.personalDesc,
        yearsOfExperience: values.yearsOfExperience,
        coreSkills: values.coreSkills,
        projectExperience: values.projectExperience,
        resumeName: values.resumeName,
        resumeText: values.resumeText,
        focus: values.focus || "综合面试",
        duration: values.duration,
        difficulty: values.difficulty,
        interviewer: values.interviewer,
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
                  <div>
                    <h3>选择简历</h3>
                    <div className="muted resume-sub">我的简历</div>
                  </div>
                  <Upload beforeUpload={readResumeFile} showUploadList={false} accept=".txt,.md,.pdf,.docx">
                    <Button icon={<UploadOutlined />} className="btn-brand" loading={parsing}>
                      从本地导入
                    </Button>
                  </Upload>
                </div>
                {resumePreview || resumeName ? (
                  <div className="resume-rail">
                    <div className="resume-tile">
                      <div
                        className="resume-tile-thumb"
                        onClick={() => resumePreview?.thumbUrl && setPreviewOpen(true)}
                      >
                        {resumePreview?.thumbUrl ? (
                          <img src={resumePreview.thumbUrl} alt="简历预览" />
                        ) : (
                          <div className="resume-thumb-fallback">预览生成中</div>
                        )}
                      </div>
                      <div className="resume-tile-meta">
                        <div className="resume-name" title={resumePreview?.name || resumeName}>
                          {(resumePreview?.name || resumeName || "").replace(/\.[^.]+$/, "")}
                        </div>
                        <div className="muted">{resumePreview?.date || formatResumeDate()}</div>
                        <button
                          type="button"
                          className="resume-view-btn"
                          onClick={() => setPreviewOpen(true)}
                          disabled={!resumePreview?.thumbUrl && !resumePreview?.fileUrl}
                        >
                          查看简历
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="resume-empty muted">还没上传简历的话，直接在下面写也行</div>
                )}
              </div>

              <Modal
                title={resumePreview?.name || "简历预览"}
                open={previewOpen}
                onCancel={() => setPreviewOpen(false)}
                footer={null}
                width={720}
                destroyOnClose
              >
                {resumePreview?.mime === "application/pdf" ||
                (resumePreview?.name || "").toLowerCase().endsWith(".pdf") ? (
                  resumePreview?.fileUrl ? (
                    <iframe
                      src={resumePreview.fileUrl}
                      title="resume-pdf"
                      className="resume-iframe"
                    />
                  ) : null
                ) : resumePreview?.thumbUrl ? (
                  <img
                    src={resumePreview.thumbUrl}
                    alt="简历"
                    className="resume-preview-full"
                  />
                ) : (
                  <div className="muted">暂无预览</div>
                )}
              </Modal>

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
                activeKey={extraOpen}
                onChange={(keys) =>
                  setExtraOpen(Array.isArray(keys) ? keys.map(String) : [String(keys)])
                }
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
                            rows={14}
                            placeholder={
                              "### 1. 项目名称\n**技术栈**: \n**项目详情**: \n\n### 2. 项目名称\n**技术栈**: \n**项目详情**: "
                            }
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
