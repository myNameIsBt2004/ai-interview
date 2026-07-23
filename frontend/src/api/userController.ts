import request from "@/libs/request";

export async function userLoginUsingPost(
  body: API.UserLoginRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseLoginUserVO_>("/api/user/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    data: body,
    ...(options || {}),
  });
}

export async function userRegisterUsingPost(
  body: API.UserRegisterRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseLong_>("/api/user/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    data: body,
    ...(options || {}),
  });
}

export async function getLoginUserUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseLoginUserVO_>("/api/user/get/login", {
    method: "GET",
    ...(options || {}),
  });
}

export async function userLogoutUsingPost(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean_>("/api/user/logout", {
    method: "POST",
    ...(options || {}),
  });
}
