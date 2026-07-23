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
  };

  type MockInterviewAddRequest = {
    difficulty?: string;
    jobPosition?: string;
    workExperience?: string;
  };

  type MockInterviewEventRequest = {
    event?: string;
    id?: number;
    message?: string;
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
}
