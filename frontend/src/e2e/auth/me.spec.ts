/**
 * /users/me 校验 E2E（1.4-E）。
 *
 * <p>覆盖三种场景：登录后立即拉、greetingName fallback、刷新页面后从
 * localStorage 恢复。注意 HomeView 自己不再主动 fetch /users/me，全靠
 * auth.login() 在 login/register 链路里拉一次并写入 store。
 */
import { test, expect } from '@playwright/test';
import { setupAuthDefaults, type MockUser } from '../helpers/api-mock';
import {
  clearStorage,
  gotoHome,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

test('登录后立即调 /users/me → HomeView 展示 nickname', async ({ page }) => {
  const user: MockUser = { id: 1, email: 'carol@lifepulse.test', nickname: 'carol' };
  await setupAuthDefaults(page, { user });

  await page.goto('/');
  await page.waitForURL(/\/login/);
  const meResp = page.waitForResponse('**/api/v1/users/me');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  // 等 /users/me 响应完成 → store.setUser 写入 → HomeView 重渲染
  await meResp;

  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator('h1')).toContainText('carol');
});

test('无 nickname → 展示 email 前缀', async ({ page }) => {
  const user: MockUser = {
    id: 2,
    email: 'dave@lifepulse.test',
    nickname: null,
  };
  await setupAuthDefaults(page, { user });

  await page.goto('/');
  await page.waitForURL(/\/login/);
  const meResp = page.waitForResponse('**/api/v1/users/me');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await meResp;

  await expect(page.locator('h1')).toContainText('dave');
});

test('刷新页面后从 localStorage 恢复并展示', async ({ page }) => {
  const user: MockUser = { id: 3, email: 'eve@lifepulse.test', nickname: 'eve' };
  await setupAuthDefaults(page, { user });

  // 第一次登录
  await page.goto('/');
  await page.waitForURL(/\/login/);
  const meResp = page.waitForResponse('**/api/v1/users/me');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await meResp;
  await expect(page.locator('h1')).toContainText('eve');

  // 刷新页面 → store 从 localStorage 重建，无 /users/me 也会展示
  // 注意：刷新后 HomeView 不主动调 /users/me（避免冷启干扰）
  await page.reload();

  // 重渲染后仍能看到 nickname（哪怕本次 reload 没去拿 /users/me）
  await expect(page.locator('h1')).toContainText('eve');
});