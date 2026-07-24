import request from "@/libs/request";

/** 上传简历并解析 */
export async function parseResumeUsingPost(
  file: File,
  options?: { [key: string]: any },
) {
  const formData = new FormData();
  formData.append("file", file);
  return request<API.BaseResponseResumeParseVO_>("/api/resume/parse", {
    method: "POST",
    data: formData,
    // 让浏览器自动带 multipart boundary
    ...(options || {}),
  });
}
