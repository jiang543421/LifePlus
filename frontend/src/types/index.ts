// 后端 DTO 对齐（spec §03-api-auth.md §5 + §3）。
// 前端所有 view / store / api 模块共享这一份类型源。

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  nickname?: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  email: string;
  nickname: string | null;
}

export interface ApiEnvelope<T> {
  code: number;
  message?: string;
  data?: T;
}

/** 密码强度规则（与后端 RegisterRequest @Size(min=8,max=64) + @Pattern 对齐）。 */
export const PASSWORD_RULES: ReadonlyArray<{ readonly key: string; readonly label: string; readonly test: (s: string) => boolean }> = [
  { key: 'length', label: '长度 8-64 位', test: (s) => s.length >= 8 && s.length <= 64 },
  { key: 'letter', label: '至少 1 个字母', test: (s) => /[A-Za-z]/.test(s) },
  { key: 'digit', label: '至少 1 个数字', test: (s) => /[0-9]/.test(s) },
] as const;

/** UI 原型约定的错误码（与后端 AuthConstants + GlobalExceptionHandler 对齐）。
 * 注意：后端没有专门的弱密码码；弱密码由 Jakarta @Pattern 触发 → 1001 Validation。 */
export const AuthErrorCode = {
  Validation: 1001,
  BadCredentials: 1002,
  CrossUserDenied: 1003,
  NotFound: 1004,
  EmailRegistered: 1005,
  RateLimit: 1006,
  RefreshInvalid: 1401,
} as const;
export type AuthErrorCodeValue = (typeof AuthErrorCode)[keyof typeof AuthErrorCode];