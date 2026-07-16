import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useAuthStore } from './auth';
import type { AuthResponse, UserResponse } from '@/types';

vi.mock('@/api/auth', () => ({
  authApi: {
    register: vi.fn(),
    login: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
  },
}));

// 必须放在 vi.mock 之后；mock 内部被 hoist 提升，但静态 import 解析顺序固定。
import { authApi } from '@/api/auth';

const LS_ACCESS = 'lp_access_token';
const LS_REFRESH = 'lp_refresh_token';
const LS_USER = 'lp_user';

const mockUser: UserResponse = { id: 1, email: 'alice@example.com', nickname: '小爱' };
const mockTokens: AuthResponse = { accessToken: 'A', refreshToken: 'R', expiresIn: 3600 };

function seedLocalStorage(access: string | null, refresh: string | null, user: UserResponse | null): void {
  if (access !== null) localStorage.setItem(LS_ACCESS, JSON.stringify(access));
  if (refresh !== null) localStorage.setItem(LS_REFRESH, JSON.stringify(refresh));
  if (user !== null) localStorage.setItem(LS_USER, JSON.stringify(user));
}

beforeEach(() => {
  localStorage.clear();
  setActivePinia(createPinia());
  vi.mocked(authApi.register).mockReset();
  vi.mocked(authApi.login).mockReset();
  vi.mocked(authApi.refresh).mockReset();
  vi.mocked(authApi.logout).mockReset();
  vi.mocked(authApi.me).mockReset();
});

describe('useAuthStore / state hydration', () => {
  it('全部 localStorage 为空时初始为 null', () => {
    const auth = useAuthStore();
    expect(auth.accessToken).toBeNull();
    expect(auth.refreshToken).toBeNull();
    expect(auth.user).toBeNull();
  });

  it('从 localStorage 完整恢复 access / refresh / user', () => {
    seedLocalStorage('A', 'R', mockUser);
    const auth = useAuthStore();
    expect(auth.accessToken).toBe('A');
    expect(auth.refreshToken).toBe('R');
    expect(auth.user).toEqual(mockUser);
  });

  it('localStorage 含非法 JSON 时静默降级为 null', () => {
    localStorage.setItem(LS_USER, '{not json');
    const auth = useAuthStore();
    expect(auth.user).toBeNull();
  });
});

describe('useAuthStore / getters', () => {
  it('isLoggedIn 需要 accessToken + user 同时存在', () => {
    const auth = useAuthStore();
    expect(auth.isLoggedIn).toBe(false);
    auth.setTokens('A', 'R');
    expect(auth.isLoggedIn).toBe(false);
    auth.setUser(mockUser);
    expect(auth.isLoggedIn).toBe(true);
  });

  it('greetingName 优先级：nickname > email 前缀 > 默认', () => {
    const auth = useAuthStore();
    expect(auth.greetingName).toBe('用户');
    auth.setUser({ id: 1, email: 'bob@example.com', nickname: null });
    expect(auth.greetingName).toBe('bob');
    auth.setUser({ id: 1, email: 'bob@example.com', nickname: '鲍勃' });
    expect(auth.greetingName).toBe('鲍勃');
  });
});

describe('useAuthStore / setTokens & setUser & clear', () => {
  it('setTokens 同时写入 access / refresh 到 localStorage', () => {
    const auth = useAuthStore();
    auth.setTokens('A2', 'R2');
    expect(auth.accessToken).toBe('A2');
    expect(auth.refreshToken).toBe('R2');
    expect(JSON.parse(localStorage.getItem(LS_ACCESS) ?? 'null')).toBe('A2');
    expect(JSON.parse(localStorage.getItem(LS_REFRESH) ?? 'null')).toBe('R2');
  });

  it('setUser 写入 user 到 localStorage', () => {
    const auth = useAuthStore();
    auth.setUser(mockUser);
    expect(auth.user).toEqual(mockUser);
    expect(JSON.parse(localStorage.getItem(LS_USER) ?? 'null')).toEqual(mockUser);
  });

  it('clear 清空 state + localStorage 三键', () => {
    seedLocalStorage('A', 'R', mockUser);
    const auth = useAuthStore();
    auth.clear();
    expect(auth.accessToken).toBeNull();
    expect(auth.refreshToken).toBeNull();
    expect(auth.user).toBeNull();
    expect(localStorage.getItem(LS_ACCESS)).toBeNull();
    expect(localStorage.getItem(LS_REFRESH)).toBeNull();
    expect(localStorage.getItem(LS_USER)).toBeNull();
  });
});

describe('useAuthStore / login', () => {
  it('login 调 api.login → setTokens → 调 api.me → setUser', async () => {
    vi.mocked(authApi.login).mockResolvedValue(mockTokens);
    vi.mocked(authApi.me).mockResolvedValue(mockUser);

    const auth = useAuthStore();
    await auth.login('alice@example.com', 'pw');

    expect(authApi.login).toHaveBeenCalledWith({ email: 'alice@example.com', password: 'pw' });
    expect(authApi.me).toHaveBeenCalledTimes(1);
    expect(auth.accessToken).toBe('A');
    expect(auth.refreshToken).toBe('R');
    expect(auth.user).toEqual(mockUser);
  });

  it('login 抛错时不写入任何 state', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('bad creds'));
    const auth = useAuthStore();
    await expect(auth.login('x', 'y')).rejects.toThrow('bad creds');
    expect(auth.accessToken).toBeNull();
    expect(auth.user).toBeNull();
    expect(localStorage.getItem(LS_ACCESS)).toBeNull();
  });
});

describe('useAuthStore / register', () => {
  it('register 先调 api.register 再调 login（含 nickname 透传）', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ userId: 1 });
    vi.mocked(authApi.login).mockResolvedValue(mockTokens);
    vi.mocked(authApi.me).mockResolvedValue(mockUser);

    const auth = useAuthStore();
    await auth.register('alice@example.com', 'pw', '小爱');

    expect(authApi.register).toHaveBeenCalledWith({ email: 'alice@example.com', password: 'pw', nickname: '小爱' });
    expect(authApi.login).toHaveBeenCalledWith({ email: 'alice@example.com', password: 'pw' });
    expect(auth.isLoggedIn).toBe(true);
  });
});

describe('useAuthStore / logout', () => {
  it('有 refreshToken 时先调 api.logout 再 clear', async () => {
    vi.mocked(authApi.logout).mockResolvedValue(undefined);
    const auth = useAuthStore();
    auth.setTokens('A', 'R');
    auth.setUser(mockUser);

    await auth.logout();

    expect(authApi.logout).toHaveBeenCalledWith({ refreshToken: 'R' });
    expect(auth.accessToken).toBeNull();
    expect(auth.user).toBeNull();
  });

  it('api.logout 失败时仍然 clear 本地态（best-effort）', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network'));
    const auth = useAuthStore();
    auth.setTokens('A', 'R');
    auth.setUser(mockUser);

    await auth.logout();

    expect(auth.accessToken).toBeNull();
    expect(auth.user).toBeNull();
    expect(localStorage.getItem(LS_ACCESS)).toBeNull();
  });

  it('无 refreshToken 时跳过 api.logout 直接 clear', async () => {
    const auth = useAuthStore();
    await auth.logout();
    expect(authApi.logout).not.toHaveBeenCalled();
    expect(auth.accessToken).toBeNull();
  });
});
