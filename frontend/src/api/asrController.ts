import request from "@/libs/request";

export async function getAsrConfigUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseAsrConfigVO_>("/api/asr/config", {
    method: "GET",
    ...(options || {}),
  });
}

export async function recognizeAsrUsingPost(
  file: Blob,
  filename = "speech.wav",
  options?: { [key: string]: any },
) {
  const formData = new FormData();
  formData.append("file", file, filename);
  return request<API.BaseResponseAsrTextVO_>("/api/asr/recognize", {
    method: "POST",
    data: formData,
    timeout: 120000,
    ...(options || {}),
  });
}
