import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import type { ApiEnvelope, AuthResponse } from '@/types';
import { AuthErrorCode } from '@/types';
import { useAuthStore } from '@/stores/auth';
import router from '@/router';

const BASE_URL = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1';

/** 应用层错误：HTTP 4xx/5xx 或业务 code != 0 时抛出。 */
export class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
});

// ----- refresh queue（防并发雪崩）-----
let isRefreshing = false;
let pendingQueue: Array<(token: string) => void> = [];

async function refreshAccessToken(): Promise<string> {
  const auth = useAuthStore();
  if (!auth.refreshToken) throw new ApiError(AuthErrorCode.RefreshInvalid, 'no refresh token');
  const resp = await axios.post<ApiEnvelope<AuthResponse>>(
    `${BASE_URL}/auth/refresh`,
    { refreshToken: auth.refreshToken },
  );
  const body = resp.data;
  if (body.code !== 0 || !body.data) {
    throw new ApiError(body.code ?? AuthErrorCode.RefreshInvalid, body.message ?? 'refresh failed');
  }
  auth.setTokens(body.data.accessToken, body.data.refreshToken);
  return body.data.accessToken;
}

async function waitForRefresh(): Promise<string> {
  return new Promise((resolve) => pendingQueue.push(resolve));
}

async function handle401(): Promise<string | null> {
  if (isRefreshing) return waitForRefresh();
  isRefreshing = true;
  try {
    const token = await refreshAccessToken();
    pendingQueue.forEach((cb) => cb(token));
    pendingQueue.length = 0;
    return token;
  } catch (e) {
    pendingQueue.length = 0;
    const auth = useAuthStore();
    auth.clear();
    const currentPath = router.currentRoute.value.fullPath;
    if (currentPath !== '/login') {
      router.push({ name: 'login', query: { redirect: currentPath } });
    }
    return null;
  } finally {
    isRefreshing = false;
  }
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const auth = useAuthStore();
  if (auth.accessToken) {
    config.headers.set('Authorization', `Bearer ${auth.accessToken}`);
  }
  config.headers.set('X-Trace-Id', crypto.randomUUID());
  return config;
});

http.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiEnvelope<unknown> | undefined;
    if (!body || typeof body !== 'object' || !('code' in body)) return resp.data;
    if (body.code === 0) return body.data;
    throw new ApiError(body.code, body.message ?? 'business error');
  },
  async (err: AxiosError<ApiEnvelope<unknown>>) => {
    const code = err.response?.data?.code;
    const status = err.response?.status;
    // 只对已登录请求触发 refresh：未登录（refreshToken=null）时 /auth/login 返 1002
    // 是用户输入错误，不该走 refresh 路径；否则会把"邮箱或密码错误"误吐成"会话过期"。
    if (
      (code === AuthErrorCode.BadCredentials || status === 401) &&
      useAuthStore().refreshToken
    ) {
      const newToken = await handle401();
      if (newToken && err.config) {
        err.config.headers.set('Authorization', `Bearer ${newToken}`);
        return http.request(err.config);
      }
      throw new ApiError(AuthErrorCode.RefreshInvalid, 'session expired');
    }
    throw new ApiError(code ?? -1, err.message || 'network error');
  },
);

export default http;