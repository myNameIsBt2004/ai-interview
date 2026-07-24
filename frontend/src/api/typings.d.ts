declare namespace API {
  type BaseResponseLong_ = {
    code?: number;
    data?: number;
    message?: string;
  };

  type BaseResponseBoolean_ = {
    code?: number;
    data?: boolean;
    message?: string;
  };

  type BaseResponseString_ = {
    code?: number;
    data?: string;
    message?: string;
  };

  type BaseResponseMockInterview_ = {
    code?: number;
    data?: MockInterview;
    message?: string;
  };

  type BaseResponsePageMockInterview_ = {
    code?: number;
    data?: PageMockInterview_;
    message?: string;
  };

  type BaseResponseMockInterviewReportVO_ = {
    code?: number;
    data?: MockInterviewReportVO;
    message?: string;
  };

  type BaseResponseResumeParseVO_ = {
    code?: number;
    data?: ResumeParseVO;
    message?: string;
  };

  type BaseResponseLoginUserVO_ = {
    code?: number;
    data?: LoginUserVO;
    message?: string;
  };

  type LoginUserVO = {
    id?: number;
    userName?: string;
    userAvatar?: string;
    userProfile?: string;
    userRole?: string;
    createTime?: string;
  };

  type MockInterview = {
    createTime?: string;
    difficulty?: string;
    id?: number;
    isDelete?: number;
    jobPosition?: string;
    messages?: string;
    status?: number;
    updateTime?: string;
    userId?: number;
    workExperience?: string;
    interviewType?: string;
    salaryMin?: number;
    salaryMax?: number;
    jobDescription?: string;
    companyName?: string;
    personalDesc?: string;
    yearsOfExperience?: string;
    coreSkills?: string;
    projectExperience?: string;
    resumeName?: string;
    resumeText?: string;
    focus?: string;
    duration?: number;
    interviewer?: string;
    score?: number;
    durationMinutes?: number;
    reportJson?: string;
    startTime?: string;
  };

  type MockInterviewAddRequest = {
    difficulty?: string;
    jobPosition?: string;
    workExperience?: string;
    interviewType?: string;
    salaryMin?: number;
    salaryMax?: number;
    jobDescription?: string;
    companyName?: string;
    personalDesc?: string;
    yearsOfExperience?: string;
    coreSkills?: string;
    projectExperience?: string;
    resumeName?: string;
    resumeText?: string;
    focus?: string;
    duration?: number;
    interviewer?: string;
  };

  type MockInterviewEventRequest = {
    event?: string;
    id?: number;
    message?: string;
    durationMinutes?: number;
  };

  type MockInterviewQueryRequest = {
    current?: number;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    id?: number;
    status?: number;
    jobPosition?: string;
    difficulty?: string;
    workExperience?: string;
    userId?: number;
  };

  type PageMockInterview_ = {
    records?: MockInterview[];
    total?: number;
    size?: number;
    current?: number;
    pages?: number;
  };

  type ResumeParseVO = {
    resumeName?: string;
    resumeText?: string;
    personalDesc?: string;
    yearsOfExperience?: string;
    coreSkills?: string;
    projectExperience?: string;
  };

  type MockInterviewReportVO = {
    id?: number;
    interviewType?: string;
    jobPosition?: string;
    difficulty?: string;
    companyName?: string;
    interviewer?: string;
    score?: number;
    durationMinutes?: number;
    questionCount?: number;
    startTime?: string;
    status?: number;
    summary?: string;
    observations?: string[];
    suggestions?: string[];
    abilities?: Array<{ name?: string; score?: number; tip?: string }>;
    strengths?: string[];
    improvements?: string[];
    skillMatrix?: Array<{
      name?: string;
      score?: number;
      evaluation?: string;
      advice?: string;
      resources?: string[];
    }>;
    qaAnalysis?: Array<{
      question?: string;
      answer?: string;
      score?: number;
      analysis?: string;
      followUps?: string[];
      comment?: string;
      reference?: string;
      referenceAnswer?: string;
      relatedQuestions?: string[];
    }>;
    learningPlan?: string;
    learningFocus?: Array<{ name?: string; priority?: string }>;
    roadmap?: Array<{ stage?: string; duration?: string; items?: string[] }>;
    messages?: Array<{ role?: string; content?: string }>;
  };

  type getMockInterviewByIdUsingGETParams = {
    id?: number;
  };

  type UserLoginRequest = {
    userAccount?: string;
    userPassword?: string;
  };

  type UserRegisterRequest = {
    userAccount?: string;
    userPassword?: string;
    checkPassword?: string;
  };

  type TtsConfigVO = {
    provider?: string;
  };

  type TtsAudioVO = {
    audioBase64?: string;
    format?: string;
  };

  type TtsSynthesizeRequest = {
    text?: string;
  };

  type BaseResponseTtsConfigVO_ = {
    code?: number;
    data?: TtsConfigVO;
    message?: string;
  };

  type BaseResponseTtsAudioVO_ = {
    code?: number;
    data?: TtsAudioVO;
    message?: string;
  };

  type AsrConfigVO = {
    enabled?: boolean;
  };

  type AsrTextVO = {
    text?: string;
  };

  type BaseResponseAsrConfigVO_ = {
    code?: number;
    data?: AsrConfigVO;
    message?: string;
  };

  type BaseResponseAsrTextVO_ = {
    code?: number;
    data?: AsrTextVO;
    message?: string;
  };
}
