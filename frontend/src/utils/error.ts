import { ElMessage } from 'element-plus';
import type { MessageOptions } from 'element-plus';
import { AuthErrorCode } from '@/types';

const AUTH_ERROR_MSG: Readonly<Record<number, string>> = {
  [AuthErrorCode.Validation]: '输入不合法，请检查密码长度 8-64 位、含字母与数字',
  [AuthErrorCode.BadCredentials]: '邮箱或密码错误',
  [AuthErrorCode.CrossUserDenied]: '无权访问该资源',
  [AuthErrorCode.NotFound]: '资源不存在',
  [AuthErrorCode.EmailRegistered]: '该邮箱已注册',
  [AuthErrorCode.RateLimit]: '请求过于频繁，请稍后再试',
  [AuthErrorCode.RefreshInvalid]: '会话已过期，请重新登录',
} as const;

const DEFAULT_MSG = '系统繁忙，请稍后再试';

export function authErrorMessage(code: number): string {
  return AUTH_ERROR_MSG[code] ?? DEFAULT_MSG;
}

export function showAuthError(code: number): void {
  ElMessage({ message: authErrorMessage(code), type: 'error' } as MessageOptions);
}