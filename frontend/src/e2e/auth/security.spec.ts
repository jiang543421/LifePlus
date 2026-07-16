/**
 * 安全相关 E2E（1.4-E）。
 *
 * <p>CLAUDE.md §6.1 强制项：
 * - 登录限流 1006
 * - refresh 重放 1401
 * - 跨用户越权 1003
 *
 * <p>本组不依赖 backend；通过 page.route() 在浏览器层注入错误码，
 * 验证前端拦截器/守卫/清态链路正确。
 */
import { test, expect } from '@playwright/test';
import {
  mockRefreshInvalid,
  mockLoginRateLimit,
  mockRegisterSuccess,
  mockLoginSuccess,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  gotoLogin,
  gotoRegister,
  gotoHome,
  fillLoginForm,
  fillRegisterForm,
  clickSubmit,
  expectAuthErrorToast,
  strongPassword,
  uniqueEmail,
} from '../helpers/test-fixtures';
import { AuthErrorCode } from '@/types';

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

test('登录限流 1006：连续 6 次错误登录 → 第 6 次返回 1006', async ({ page }) => {
  // 前 5 次返回 1002，第 6 次返回 1006（CLAUDE.md §7.2 阈值）
  await mockLoginRateLimit(page, { triggerAfter: 5 });
  await gotoLogin(page);
  await fillLoginForm(page, uniqueEmail(), strongPassword());

  // 用 captureRequest 计数，不依赖每次 waitForResponse（避免 6 次 click 之间
  // 状态翻转导致 Playwright actionability 误判）。
  let requestCount = 0;
  page.on('request', (req) => {
    if (req.url().endsWith('/api/v1/auth/login')) requestCount += 1;
  });

  // 顺序触发 6 次
  for (let i = 0; i < 6; i++) {
    await clickSubmit(page);
  }
  // 等收到第 6 次响应后断言
  await expect
    .poll(() => requestCount, { timeout: 10_000 })
    .toBe(6);

  await expectAuthErrorToast(page, AuthErrorCode.RateLimit);
  await expect(page).toHaveURL(/\/login/);
});

test('refresh 重放 1401 → 自动跳 /login?redirect=& 清空登录态', async ({ page }) => {
  // 注册成功后链路：POST /register → POST /login → GET /users/me
  // 第 3 步 /users/me 强制 401 → 触发 refresh → refresh 返回 1401 → 清态
  await mockRegisterSuccess(page);
  await mockLoginSuccess(page);
  await mockRefreshInvalid(page, AuthErrorCode.RefreshInvalid, 'refresh token 失效');
  await page.route('**/api/v1/users/me', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: AuthErrorCode.BadCredentials, message: 'invalid' }),
    });
  });

  await gotoRegister(page);
  await fillRegisterForm(page, uniqueEmail(), strongPassword());
  await clickSubmit(page);

  // handle401 内部：clear() + router.push({ name: 'login', query: { redirect: '/register' } })
  // vue-router createWebHistory 不对 / 做 encode，URL 即 `?redirect=/register`
  await page.waitForURL(/\/login\?redirect=\/register/);
  // lp_* 全被 clear() 抹掉
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('lp_access_token')))
    .toBeNull();
});

test('跨用户 1003 refresh → 自动跳 /login?redirect=& 清空登录态', async ({ page }) => {
  // 链路：register → login → me 401 → handle401 → refresh 1003 → 清态 + push /login
  // /users/me 必须先返回 401 才能进 refresh 路径；refresh 返回 1003 触发 handle401 catch
  await mockRegisterSuccess(page);
  await mockLoginSuccess(page);
  await mockRefreshInvalid(page, AuthErrorCode.CrossUserDenied, 'cross user');
  await page.route('**/api/v1/users/me', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: AuthErrorCode.BadCredentials, message: 'invalid' }),
    });
  });

  await gotoRegister(page);
  await fillRegisterForm(page, uniqueEmail(), strongPassword());
  await clickSubmit(page);

  await page.waitForURL(/\/login\?redirect=\/register/);
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('lp_refresh_token')))
    .toBeNull();
});

test('未登录访问 / → 守卫推到 /login?redirect=/', async ({ page }) => {
  // 不设任何 mock，也不预设登录态 → clearStorage 在 beforeEach 已跑
  await gotoHome(page);
  // 守卫推到 /login?redirect=/
  await expect(page).toHaveURL(/\/login\?redirect=\//);
  // 直接断言输入框存在（视图正常 mount）
  await expect(page.locator('input[type="email"]')).toBeVisible();
});