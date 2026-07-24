export type InterviewSetupData = {
  interviewType: string;
  jobPosition: string;
  workExperience: string;
  salaryMin?: number;
  salaryMax?: number;
  jobDescription: string;
  companyName?: string;
  resumeName?: string;
  resumeText?: string;
  personalDesc: string;
  yearsOfExperience?: string;
  coreSkills?: string;
  projectExperience?: string;
  focus: string;
  duration: number;
  difficulty: string;
  interviewer: string;
};

export const SETUP_STORAGE_KEY = "ai_interview_setup";
export const REPORT_STORAGE_PREFIX = "ai_interview_report_";
export const RECORDS_STORAGE_KEY = "ai_interview_records";

export type InterviewRecord = {
  id: number;
  interviewType: string;
  jobPosition: string;
  difficulty: string;
  companyName?: string;
  durationMinutes: number;
  score?: number;
  status: "completed" | "in_progress";
  startTime: string;
  summary?: string;
  messages?: Array<{ role: string; content: string }>;
};

export function saveSetup(data: InterviewSetupData) {
  sessionStorage.setItem(SETUP_STORAGE_KEY, JSON.stringify(data));
}

export function loadSetup(): InterviewSetupData | null {
  const raw = sessionStorage.getItem(SETUP_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as InterviewSetupData;
  } catch {
    return null;
  }
}

export function saveRecord(record: InterviewRecord) {
  const list = loadRecords();
  const idx = list.findIndex((r) => r.id === record.id);
  if (idx >= 0) list[idx] = record;
  else list.unshift(record);
  localStorage.setItem(RECORDS_STORAGE_KEY, JSON.stringify(list));
  localStorage.setItem(REPORT_STORAGE_PREFIX + record.id, JSON.stringify(record));
}

export function loadRecords(): InterviewRecord[] {
  const raw = localStorage.getItem(RECORDS_STORAGE_KEY);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as InterviewRecord[];
  } catch {
    return [];
  }
}

export function loadReport(id: string | number): InterviewRecord | null {
  const raw = localStorage.getItem(REPORT_STORAGE_PREFIX + id);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as InterviewRecord;
  } catch {
    return null;
  }
}

export const JOB_OPTIONS = [
  "Java后端开发",
  "前端开发工程师",
  "算法工程师",
  "测试开发工程师",
  "全栈工程师",
  "agent开发工程师",
];

export const EXPERIENCE_OPTIONS = ["校招应届", "1年以内", "1-3年", "3-5年", "5年以上"];

export const DIFFICULTY_OPTIONS = ["简单", "中等", "困难"];

export const DURATION_OPTIONS = [
  { label: "15 分钟", value: 15 },
  { label: "30 分钟", value: 30 },
  { label: "45 分钟", value: 45 },
  { label: "60 分钟", value: 60 },
];

export const INTERVIEWER_OPTIONS = ["坤坤", "偏技术追问", "偏沟通引导"];

/** 面试官头像（房间侧栏展示） */
export const INTERVIEWER_AVATAR = "/interviewers/avatar.jpeg";

