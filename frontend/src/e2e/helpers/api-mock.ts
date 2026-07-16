/**
 * E2E API mock 集中点（1.4-E）。
 *
 * <p>策略：`page.route()` 拦截 `/api/v1/*`，完全替代真实后端。
 * 真实后端契约由 `backend/src/test/.../*IT.java` 覆盖。
 *
 * <p>mock 状态（计数器、是否首次调用）存在闭包内；每个 spec 在
 * `test.beforeEach` 重新调一次 setup，即默认覆盖一次路由。
 * 多次 page.route() 注册同一 URL 时，**后注册的 handler 优先生效**
 * （Playwright 行为），所以"先 setupAuthDefaults 再针对性 mockLoginFailure"
 * 这种叠加写法的预期是后者覆盖前者的 login handler——已验证。
 */
import type { Page } from '@playwright/test';

interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface MockUser {
  id: number;
  email: string;
  nickname: string | null;
}

const DEFAULT_TOKENS: Tokens = {
  accessToken: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  expiresIn: 3600,
};

const DEFAULT_USER: MockUser = {
  id: 1,
  email: 'mock@example.com',
  nickname: 'Mock 用户',
};

function envelopeOk<T>(data: T): string {
  return JSON.stringify({ code: 0, message: 'ok', data });
}

function envelopeErr(code: number, message: string): string {
  return JSON.stringify({ code, message });
}

/**
 * 一站式默认 mock：login/register/me/refresh 全成功。多数正常用例直接调它即可。
 */
export async function setupAuthDefaults(
  page: Page,
  opts?: { user?: MockUser; tokens?: Tokens },
): Promise<void> {
  const user = opts?.user ?? DEFAULT_USER;
  const tokens = opts?.tokens ?? DEFAULT_TOKENS;
  await mockRegisterSuccess(page, { user, tokens });
  await mockLoginSuccess(page, { user, tokens });
  await mockMeSuccess(page, user);
  await mockRefreshSuccess(page, tokens);
}

export async function mockLoginSuccess(
  page: Page,
  opts?: { user?: MockUser; tokens?: Tokens },
): Promise<void> {
  const tokens = opts?.tokens ?? DEFAULT_TOKENS;
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk(tokens),
    });
  });
}

export async function mockLoginFailure(
  page: Page,
  code: number,
  message?: string,
): Promise<void> {
  // 1006 → 429；1002 → 401；1001/1003 → 400/403；其它 → 400
  const status = code === 1006 ? 429 : code === 1002 ? 401 : code === 1003 ? 403 : 400;
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'error'),
    });
  });
}

/**
 * 登录限流：前 N 次返回 1002，第 N+1 次返回 1006。
 * 默认 N=5（CLAUDE.md §7.2 阈值）。
 */
export async function mockLoginRateLimit(
  page: Page,
  opts?: { triggerAfter?: number; message?: string },
): Promise<void> {
  const triggerAfter = opts?.triggerAfter ?? 5;
  let count = 0;
  await page.route('**/api/v1/auth/login', async (route) => {
    count += 1;
    if (count > triggerAfter) {
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: envelopeErr(1006, opts?.message ?? '请求过于频繁，请稍后再试'),
      });
    } else {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: envelopeErr(1002, '邮箱或密码错误'),
      });
    }
  });
}

export async function mockRegisterSuccess(
  page: Page,
  opts?: { user?: MockUser; tokens?: Tokens },
): Promise<void> {
  const tokens = opts?.tokens ?? DEFAULT_TOKENS;
  await page.route('**/api/v1/auth/register', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: envelopeOk({ userId: opts?.user?.id ?? 1 }),
    });
  });
  // register 成功后前端会接着 login + me，所以一并 mock
  await mockLoginSuccess(page, { user: opts?.user, tokens });
  await mockMeSuccess(page, opts?.user);
}

export async function mockRegisterFailure(
  page: Page,
  code: number,
  message?: string,
): Promise<void> {
  const status = code === 1005 ? 409 : code === 1001 ? 400 : 400;
  await page.route('**/api/v1/auth/register', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'error'),
    });
  });
}

export async function mockMeSuccess(page: Page, user: MockUser = DEFAULT_USER): Promise<void> {
  await page.route('**/api/v1/users/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk(user),
    });
  });
}

export async function mockMeFailure(page: Page, code: number, message?: string): Promise<void> {
  const status = code === 1002 ? 401 : code === 1003 ? 403 : code === 1004 ? 404 : 400;
  await page.route('**/api/v1/users/me', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'error'),
    });
  });
}

export async function mockRefreshSuccess(page: Page, tokens: Tokens = DEFAULT_TOKENS): Promise<void> {
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk({
        accessToken: tokens.accessToken + '-rotated',
        refreshToken: tokens.refreshToken + '-rotated',
        expiresIn: tokens.expiresIn,
      }),
    });
  });
}

/**
 * Refresh 重放：首次调用返回新 token；同 token 第二次调用返回 1401。
 * 用于验证前端自动清态 + 跳 /login 的链路。
 */
export async function mockRefreshReplay(page: Page): Promise<void> {
  const seen = new Set<string>();
  await page.route('**/api/v1/auth/refresh', async (route) => {
    const body = JSON.parse(route.request().postData() ?? '{}') as { refreshToken?: string };
    const rt = body.refreshToken ?? '';
    if (seen.has(rt)) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: envelopeErr(1401, 'refresh token 失效'),
      });
      return;
    }
    seen.add(rt);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk({
        accessToken: 'rotated-access-' + Date.now(),
        refreshToken: 'rotated-refresh-' + Date.now(),
        expiresIn: 3600,
      }),
    });
  });
}

/**
 * Refresh 返回给定错误码（1003 跨用户、1401 失效）。
 */
export async function mockRefreshInvalid(
  page: Page,
  code: number,
  message?: string,
): Promise<void> {
  const status = code === 1003 ? 403 : 401;
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'refresh invalid'),
    });
  });
}