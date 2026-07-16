import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { authErrorMessage, showAuthError } from '@/utils/error';
import { AuthErrorCode } from '@/types';

vi.mock('element-plus', () => ({
  ElMessage: vi.fn(),
}));

// 把每个分支路径都覆盖：known code、unknown code、边界（code=0）。
describe('authErrorMessage', () => {
  it('返回各已知错误码对应的中文文案', () => {
    expect(authErrorMessage(AuthErrorCode.Validation)).toContain('输入不合法');
    expect(authErrorMessage(AuthErrorCode.BadCredentials)).toBe('邮箱或密码错误');
    expect(authErrorMessage(AuthErrorCode.CrossUserDenied)).toBe('无权访问该资源');
    expect(authErrorMessage(AuthErrorCode.NotFound)).toBe('资源不存在');
    expect(authErrorMessage(AuthErrorCode.EmailRegistered)).toBe('该邮箱已注册');
    expect(authErrorMessage(AuthErrorCode.RateLimit)).toContain('请求过于频繁');
    expect(authErrorMessage(AuthErrorCode.RefreshInvalid)).toBe('会话已过期，请重新登录');
  });

  it('未知错误码返回默认文案', () => {
    expect(authErrorMessage(9999)).toBe('系统繁忙，请稍后再试');
    expect(authErrorMessage(0)).toBe('系统繁忙，请稍后再试');
  });
});

describe('showAuthError', () => {
  beforeEach(async () => {
    const { ElMessage } = await import('element-plus');
    vi.mocked(ElMessage).mockClear();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('通过 ElMessage 渲染对应错误文案', async () => {
    const { ElMessage } = await import('element-plus');
    showAuthError(AuthErrorCode.BadCredentials);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({ message: '邮箱或密码错误', type: 'error' }),
    );
  });

  it('未知错误码走默认文案', async () => {
    const { ElMessage } = await import('element-plus');
    showAuthError(12345);
    expect(ElMessage).toHaveBeenCalledWith(
      expect.objectContaining({ message: '系统繁忙，请稍后再试', type: 'error' }),
    );
  });
});