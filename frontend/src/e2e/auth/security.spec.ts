/**
 * 安全相关 E2E（1.4-E + R-004）。
 *
 * <p>CLAUDE.md §6.1 强制项：
 * - 登录限流 1006
 * - refresh 重放 1401
 * - 跨用户越权 1003
 * - CSP / CORS 安全头（R-004，见 `docs/issues/2026-07-18-csp-cors-prod-tightening.md`）
 *
 * <p>本组不依赖 backend；通过 page.route() 在浏览器层注入错误码，
 * 验证前端拦截器/守卫/清态链路正确。CORS 测试用 page.route() 模拟
 * 后端响应 CORS 头，浏览器 fetch 验证 allow-origin 解析链路。
 *
 * <p>nginx 端的 4 项 CSP / 安全头（Content-Security-Policy /
 * X-Content-Type-Options / Referrer-Policy / Strict-Transport-Security）
 * 不在 E2E 范围（dev 模式 `pnpm dev` 不走 nginx；prod 由 docker compose
 * nginx 提供），由 `frontend/src/__tests__/nginx-config.spec.ts` 静态
 * 解析 nginx.conf 兜底覆盖。
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

// ---------------------------------------------------------------------------
// R-004：CORS 跨域契约（防御性覆盖）
//
// 边界说明：dev E2E 不跑 nginx、不接后端。CORS 在浏览器侧的"allow / block"
// 判定由本章 2 个测试承担；后端响应正确性由 `backend/src/test/.../WebCorsIT`
// MockMvc 验证；nginx 4 项安全头（Content-Security-Policy 等）由
// `frontend/src/__tests__/nginx-config.spec.ts` 静态解析兜底。
// ---------------------------------------------------------------------------

test('R-004 CORS：白名单 origin → fetch 拿到 ACAO 并 resolve', async ({ page }) => {
  // page.route() 在浏览器网络层之前拦截 → 即便 localhost:65535 没有监听器
  // 也不会真连。page origin = http://localhost:5173。
  // Access-Control-Expose-Headers: * 让浏览器侧 JS 能读到 ACAO（默认 ACAO
  // 不在 safelist，跨域时 JS 读 r.headers.get('Access-Control-Allow-Origin')
  // 返回 null；Spring `CorsConfiguration.exposedHeaders` 配 '*' 即可生效）。
  await page.route('**/api/cors-probe', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Access-Control-Allow-Origin': 'http://localhost:5173',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Authorization, Content-Type',
        'Access-Control-Expose-Headers': '*',
        'Access-Control-Max-Age': '3600',
      },
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { ok: true } }),
    });
  });

  // 故意打 localhost:65535（同主机不同端口 = 跨源）模拟 cross-origin 请求。
  const result = await page.evaluate(async () => {
    try {
      const r = await fetch('http://localhost:65535/api/cors-probe', { mode: 'cors' });
      return {
        status: r.status,
        acao: r.headers.get('Access-Control-Allow-Origin'),
      };
    } catch (e) {
      return { error: String(e) };
    }
  });

  expect(result).not.toHaveProperty('error');
  expect(result.status).toBe(200);
  expect(result.acao).toBe('http://localhost:5173');
});

test('R-004 CORS：origin 不在白名单 → fetch 被浏览器拒绝', async ({ page }) => {
  // 后端 mock 只回 ACAO=evil.com，与 page origin http://localhost:5173 不匹配
  await page.route('**/api/cors-probe', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'Access-Control-Allow-Origin': 'http://evil.example.com',
      },
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { ok: true } }),
    });
  });

  const result = await page.evaluate(async () => {
    try {
      await fetch('http://localhost:65535/api/cors-probe', { mode: 'cors' });
      return { blocked: false };
    } catch (e) {
      return { blocked: true, reason: String(e) };
    }
  });

  expect(result.blocked, '浏览器 CORS 校验应当拒绝不在白名单的 ACAO').toBe(true);
});