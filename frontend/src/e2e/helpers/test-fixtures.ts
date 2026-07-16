/**
 * E2E 浏览器级操作封装（1.4-E）。
 *
 * <p>CLAUDE.md §6.4：禁止 `page.waitForTimeout(N)`，统一用 `expect(...).toBeVisible()`
 * / `waitForResponse` / `waitForURL` 这类 awaitable API。
 */
import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';
import { authErrorMessage } from '@/utils/error';

const LP_KEYS = ['lp_access_token', 'lp_refresh_token', 'lp_user'] as const;

export async function clearStorage(page: Page): Promise<void> {
  await page.goto('/');
  await page.evaluate((keys) => {
    keys.forEach((k) => localStorage.removeItem(k));
  }, [...LP_KEYS]);
}

/** 跳到 /login；query 可选（自动 encode）。 */
export async function gotoLogin(page: Page, redirect?: string): Promise<void> {
  const url = redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login';
  await page.goto(url);
  await page.waitForURL(/\/login/);
}

export async function gotoRegister(page: Page): Promise<void> {
  await page.goto('/register');
  await page.waitForURL(/\/register/);
}

export async function gotoHome(page: Page): Promise<void> {
  await page.goto('/');
  // 已登录 → 留在 /；未登录 → 守卫推到 /login
  await page.waitForURL((u) => u.pathname === '/' || u.pathname === '/login');
}

export async function fillLoginForm(
  page: Page,
  email: string,
  password: string,
): Promise<void> {
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
}

export async function fillRegisterForm(
  page: Page,
  email: string,
  password: string,
  nickname?: string,
): Promise<void> {
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  if (nickname !== undefined) {
    // 唯一 text input（nickname），password input 是 type="password"
    await page.locator('input[type="text"]').fill(nickname);
  }
}

export async function clickSubmit(page: Page): Promise<void> {
  // Element Plus el-button 在 :loading=true 时会立刻变 disabled；Playwright
  // 默认 actionability（"receives events"）检查会和这个时序打架、卡 30s
  // 再超时。这里用 force:true 跳过 actionability，由 Playwright 直接派发 click。
  await page.locator('button.submit-btn').click({ force: true });
}

/** 等待 ElMessage toast 出现并断言文案包含错误码对应的中文。 */
export async function expectAuthErrorToast(page: Page, code: number): Promise<void> {
  const msg = authErrorMessage(code);
  await expect(page.locator('.el-message').filter({ hasText: msg })).toBeVisible();
}

/** 等待跳转（?redirect= 解码后路径）。 */
export async function waitForRedirectTarget(page: Page, redirect: string): Promise<void> {
  await page.waitForURL((u) => u.pathname === redirect || u.search.includes(`redirect=${redirect}`));
}

export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@lifepulse.test`;
}

export function uniqueNickname(): string {
  return `用户-${Math.random().toString(36).slice(2, 6)}`;
}

/** 满足后端密码策略（≥8 位、含字母 + 数字）。 */
export function strongPassword(): string {
  return `pw${Math.random().toString(36).slice(2, 8)}1`;
}