import axios, { AxiosRequestConfig } from "axios";

const DEV_BASE_URL = "http://localhost:8101";

const myAxios = axios.create({
  baseURL: DEV_BASE_URL,
  timeout: 120000,
  withCredentials: true,
});

myAxios.interceptors.response.use(
  function (response) {
    const { data } = response;
    if (data.code === 40100) {
      if (
        !response.request.responseURL.includes("user/get/login") &&
        !window.location.pathname.includes("/user/login")
      ) {
        window.location.href = `/user/login?redirect=${encodeURIComponent(window.location.href)}`;
      }
    } else if (data.code !== 0) {
      throw new Error(data.message ?? "服务器错误");
    }
    return data;
  },
  function (error) {
    return Promise.reject(error);
  },
);

export default function request<T = any>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<T> {
  return myAxios.request<any, T>({
    url,
    ...config,
  });
}
