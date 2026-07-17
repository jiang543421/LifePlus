/**
 * 登录流 E2E（1.4-E）。
 *
 * <p>CLAUDE.md §6.1 强制：登录流必须有 E2E。
 */
import { test, expect } from '@playwright/test';
import {
  setupAuthDefaults,
  mockLoginFailure,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  gotoLogin,
  fillLoginForm,
  clickSubmit,
  expectAuthErrorToast,
  strongPassword,
  uniqueEmail,
} from '../helpers/test-fixtures';
import { AuthErrorCode } from '@/types';

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

test('登录成功 → 跳 / 并展示 greetingName', async ({ page }) => {
  const user: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };
  await setupAuthDefaults(page, { user });

  await page.goto('/');
  // 守卫会推到 /login
  await page.waitForURL(/\/login/);

  const loginResp = page.waitForResponse('**/api/v1/auth/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await loginResp;

  await expect(page).toHaveURL('http://localhost:5173/');
  await expect(page.locator('h1')).toContainText('alice');
});

test('登录失败 1002 → 展示「邮箱或密码错误」并保持 /login', async ({ page }) => {
  await mockLoginFailure(page, AuthErrorCode.BadCredentials);

  await gotoLogin(page);
  await fillLoginForm(page, uniqueEmail(), strongPassword());
  await clickSubmit(page);

  // 直接断言 toast——mock 响应极快，page.waitForResponse 可能错过；toast 自带重试
  await expectAuthErrorToast(page, AuthErrorCode.BadCredentials);
  await expect(page).toHaveURL(/\/login/);
});

test('带 ?redirect=/ 时登录成功后跳回 /', async ({ page }) => {
  await setupAuthDefaults(page, {
    user: { id: 2, email: 'bob@lifepulse.test', nickname: 'bob' },
  });

  // 先在 click 之前注册 listener，避免 mock 即时响应错过监听窗口
  const loginResp = page.waitForResponse('**/api/v1/auth/login');
  await gotoLogin(page, '/');
  await fillLoginForm(page, 'bob@lifepulse.test', strongPassword());
  await clickSubmit(page);
  await loginResp;

  // ?redirect=/ 在登录成功后被消费，跳 /
  await expect(page).toHaveURL('http://localhost:5173/');
});