import request from "@/libs/request";

export async function getTtsConfigUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseTtsConfigVO_>("/api/tts/config", {
    method: "GET",
    ...(options || {}),
  });
}

export async function synthesizeTtsUsingPost(
  body: API.TtsSynthesizeRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseTtsAudioVO_>("/api/tts/synthesize", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    data: body,
    ...(options || {}),
  });
}
