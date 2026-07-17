import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { createPinia, setActivePinia } from 'pinia';
import http, { ApiError } from './http';
import { useAuthStore } from '@/stores/auth';
import { AuthErrorCode } from '@/types';

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { fullPath: '/some-page' } },
  },
}));

const mockUser = { id: 1, email: 'alice@example.com', nickname: '小爱' };

let mock: MockAdapter;
let axiosMock: MockAdapter;
let routerPush: ReturnType<typeof vi.fn>;

beforeEach(async () => {
  // http.ts 既用 axios.create() 出的 http 实例，也用 bare axios.post(...) 调 refresh。
  // 两者 adapter 在 create 时各自复制 axios.defaults.adapter，所以需要分别 patch。
  mock = new MockAdapter(http);
  axiosMock = new MockAdapter(axios);
  setActivePinia(createPinia());
  localStorage.clear();
  const { default: router } = await import('@/router');
  routerPush = vi.mocked(router.push);
  routerPush.mockReset();
  router.currentRoute.value.fullPath = '/some-page';
});

afterEach(() => {
  mock.restore();
  axiosMock.restore();
});

describe('http / request interceptor', () => {
  it('有 accessToken 时附加 Bearer Authorization 头', async () => {
    let capturedAuth: string | undefined;
    mock.onGet('/users/me').reply((config) => {
      capturedAuth = config.headers?.Authorization as string | undefined;
      return [200, { code: 0, data: mockUser }];
    });

    const auth = useAuthStore();
    auth.setTokens('TKN', 'REF');
    auth.setUser(mockUser);

    await http.get('/users/me');
    expect(capturedAuth).toBe('Bearer TKN');
  });

  it('无 accessToken 时不附加 Authorization 头', async () => {
    let capturedAuth: string | undefined;
    mock.onGet('/public').reply((config) => {
      capturedAuth = config.headers?.Authorization as string | undefined;
      return [200, { code: 0, data: null }];
    });
    await http.get('/public');
    expect(capturedAuth).toBeUndefined();
  });

  it('每次请求附加 X-Trace-Id（UUID 格式）', async () => {
    let traceId: string | undefined;
    mock.onGet('/x').reply((config) => {
      traceId = config.headers?.['X-Trace-Id'] as string | undefined;
      return [200, { code: 0, data: null }];
    });
    await http.get('/x');
    expect(traceId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });
});

describe('http / response interceptor (envelope)', () => {
  it('code=0 时解包返回 body.data', async () => {
    mock.onGet('/x').reply(200, { code: 0, data: { hello: 'world' } });
    const result = await http.get<unknown, { hello: string }>('/x');
    expect(result).toEqual({ hello: 'world' });
  });

  it('code!=0 时抛 ApiError(code, message)', async () => {
    mock.onGet('/x').reply(200, { code: AuthErrorCode.BadCredentials, message: '邮箱或密码错误' });
    await expect(http.get('/x')).rejects.toMatchObject({
      name: 'ApiError',
      code: AuthErrorCode.BadCredentials,
      message: '邮箱或密码错误',
    });
  });

  it('body 不含 code 字段（非 envelope）时直接返回 body.data', async () => {
    mock.onGet('/raw').reply(200, { plain: 'value' });
    const result = await http.get<unknown, { plain: string }>('/raw');
    expect(result).toEqual({ plain: 'value' });
  });
});

describe('http / 401 + refresh 重放', () => {
  it('401 触发 refresh 一次，成功后用新 token 重放原请求', async () => {
    mock.onGet('/users/me').replyOnce(401, { code: AuthErrorCode.BadCredentials, message: 'expired' });
    mock.onGet('/users/me').reply(200, { code: 0, data: mockUser });
    axiosMock.onPost('/api/v1/auth/refresh').reply(200, {
      code: 0,
      data: { accessToken: 'NEW', refreshToken: 'NEW_R', expiresIn: 3600 },
    });

    const auth = useAuthStore();
    auth.setTokens('OLD', 'OLD_R');
    auth.setUser(mockUser);

    const result = await http.get<unknown, typeof mockUser>('/users/me');
    expect(result).toEqual(mockUser);
    expect(auth.accessToken).toBe('NEW');
    expect(auth.refreshToken).toBe('NEW_R');
  });

  it('refresh 失败时清空本地态并跳 /login?redirect=<currentPath>', async () => {
    mock.onGet('/users/me').reply(401, { code: AuthErrorCode.BadCredentials, message: 'expired' });
    axiosMock.onPost('/api/v1/auth/refresh').reply(200, { code: AuthErrorCode.RefreshInvalid, message: 'replay' });

    const auth = useAuthStore();
    auth.setTokens('OLD', 'OLD_R');
    auth.setUser(mockUser);

    await expect(http.get('/users/me')).rejects.toBeInstanceOf(ApiError);
    expect(auth.accessToken).toBeNull();
    expect(auth.user).toBeNull();
    expect(routerPush).toHaveBeenCalledWith({
      name: 'login',
      query: { redirect: '/some-page' },
    });
  });

  it('refresh 失败但当前已在 /login 时不重复跳转', async () => {
    const { default: router } = await import('@/router');
    (router.currentRoute.value as { fullPath: string }).fullPath = '/login';

    mock.onGet('/x').reply(401);
    axiosMock.onPost('/api/v1/auth/refresh').reply(500);

    const auth = useAuthStore();
    auth.setTokens('OLD', 'OLD_R');

    await expect(http.get('/x')).rejects.toBeInstanceOf(ApiError);
    expect(routerPush).not.toHaveBeenCalled();
  });

  it('非 401/1002 的错误立即抛出，不触发 refresh', async () => {
    mock.onGet('/x').reply(500, { code: -1, message: 'server error' });
    let refreshCalls = 0;
    axiosMock.onPost('/api/v1/auth/refresh').reply(() => {
      refreshCalls += 1;
      return [200, { code: 0, data: { accessToken: 'NEW', refreshToken: 'NEW_R', expiresIn: 3600 } }];
    });

    const auth = useAuthStore();
    auth.setTokens('OLD', 'OLD_R');

    await expect(http.get('/x')).rejects.toMatchObject({ code: -1 });
    expect(auth.accessToken).toBe('OLD');
    expect(refreshCalls).toBe(0);
  });
});

describe('http / refresh 单飞', () => {
  it('并发多个 401 只触发一次 /auth/refresh', async () => {
    // 三个并发请求都先 401
    mock.onGet('/a').replyOnce(401);
    mock.onGet('/b').replyOnce(401);
    mock.onGet('/c').replyOnce(401);
    // refresh 端点：用变量计数
    let refreshCalls = 0;
    axiosMock.onPost('/api/v1/auth/refresh').reply(() => {
      refreshCalls += 1;
      return [200, { code: 0, data: { accessToken: 'NEW', refreshToken: 'NEW_R', expiresIn: 3600 } }];
    });
    // 重放请求：成功
    mock.onGet('/a').reply(200, { code: 0, data: { ok: 'a' } });
    mock.onGet('/b').reply(200, { code: 0, data: { ok: 'b' } });
    mock.onGet('/c').reply(200, { code: 0, data: { ok: 'c' } });

    const auth = useAuthStore();
    auth.setTokens('OLD', 'OLD_R');

    const [ra, rb, rc] = await Promise.all([
      http.get<unknown, { ok: string }>('/a'),
      http.get<unknown, { ok: string }>('/b'),
      http.get<unknown, { ok: string }>('/c'),
    ]);

    expect(ra).toEqual({ ok: 'a' });
    expect(rb).toEqual({ ok: 'b' });
    expect(rc).toEqual({ ok: 'c' });
    expect(refreshCalls).toBe(1);
  });
});
