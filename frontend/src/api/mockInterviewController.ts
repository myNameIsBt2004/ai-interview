import request from "@/libs/request";

export async function addMockInterviewUsingPost(
  body: API.MockInterviewAddRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseLong_>("/api/mockInterview/add", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    data: body,
    ...(options || {}),
  });
}

export async function getMockInterviewByIdUsingGet(
  params: API.getMockInterviewByIdUsingGETParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseMockInterview_>("/api/mockInterview/get", {
    method: "GET",
    params: { ...params },
    ...(options || {}),
  });
}

export async function handleMockInterviewEventUsingPost(
  body: API.MockInterviewEventRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseString_>("/api/mockInterview/handleEvent", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    data: body,
    ...(options || {}),
  });
}

export async function listMyMockInterviewByPageUsingPost(
  body: API.MockInterviewQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageMockInterview_>(
    "/api/mockInterview/my/list/page/vo",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      data: body,
      ...(options || {}),
    },
  );
}

export async function getMockInterviewReportUsingGet(
  params: API.getMockInterviewByIdUsingGETParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseMockInterviewReportVO_>(
    "/api/mockInterview/report/get",
    {
      method: "GET",
      params: { ...params },
      ...(options || {}),
    },
  );
}
