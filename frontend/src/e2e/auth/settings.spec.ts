/**
 * Settings v1.1 E2E（CLAUDE.md §6.1 HIGH-4 补完）。
 *
 * <p>覆盖 3 个动作在浏览器侧的完整链路：
 * <ol>
 *   <li>改昵称 → PATCH /api/v1/users/me → store & 表单回显新值</li>
 *   <li>改密码 → POST /api/v1/users/me/password → 跳 /login?reason=password-changed + 清 token</li>
 *   <li>注销账号 → DELETE /api/v1/users/me → 跳 /login?reason=account-deleted + 清 token</li>
 * </ol>
 *
 * <p>后端契约（鉴权 / 越权 / 错误码 / 幂等 / refresh 撤销）由
 * {@code backend/.../UserSettingsIT.java}（Testcontainers）覆盖；本页只验证前端动线 + 副作用。
 *
 * <p>不依赖真实后端：{@code page.route()} 全量 mock {@code /api/v1/*}。
 */
import { test, expect, type Page } from '@playwright/test';
import { type MockUser } from '../helpers/api-mock';
import {
  clearStorage,
  strongPassword,
} from '../helpers/test-fixtures';

const LP_KEYS = ['lp_access_token', 'lp_refresh_token', 'lp_user'] as const;

/**
 * 一站式 mock：login + me（GET/PATCH/DELETE）+ password + refresh。
 *
 * <p>{@code liveUser} 闭包可变：PATCH 后下一次 GET /users/me 反映新 nickname。
 */
async function setupSettingsAuthMocks(page: Page, initialUser: MockUser): Promise<void> {
  let liveUser = { ...initialUser };
  const tokens = {
    accessToken: 'access-' + Date.now(),
    refreshToken: 'refresh-' + Date.now(),
    expiresIn: 3600,
  };
  const envelopeOk = (data: unknown): string =>
    JSON.stringify({ code: 0, message: 'ok', data });

  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk(tokens),
    });
  });

  await page.route('**/api/v1/users/me', async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(liveUser),
      });
      return;
    }
    if (method === 'PATCH') {
      const body = JSON.parse(route.request().postData() ?? '{}') as { nickname: string | null };
      liveUser = { ...liveUser, nickname: body.nickname };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(liveUser),
      });
      return;
    }
    if (method === 'DELETE') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(null),
      });
      return;
    }
    await route.fallback();
  });

  await page.route('**/api/v1/users/me/password', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk(null),
    });
  });

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

/** 走完登录表单 → 等守卫通过 → 跳 /settings。 */
async function loginAndOpenSettings(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(strongPassword());
  await page.locator('button.submit-btn').click({ force: true });
  await page.waitForURL((u) => u.pathname === '/');
  await page.goto('/settings');
  await page.waitForURL(/\/settings$/);
  // 等 SettingsView 三个卡片都出来再继续交互
  await expect(page.locator('[data-testid="settings-profile-card"]')).toBeVisible();
}

async function assertTokensCleared(page: Page): Promise<void> {
  const remaining = await page.evaluate(
    (keys) => keys.map((k) => ({ k, v: localStorage.getItem(k) })),
    [...LP_KEYS],
  );
  for (const { k, v } of remaining) {
    expect(v, `localStorage[${k}] 应已被清空`).toBeNull();
  }
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

// ----------------------------------------------------------------------
// ① 改昵称
// ----------------------------------------------------------------------

test('改昵称 → PATCH /users/me → store + 表单回显', async ({ page }) => {
  await setupSettingsAuthMocks(page, {
    id: 1,
    email: 'settings-nick@lifepulse.test',
    nickname: 'old-nick',
  });
  await loginAndOpenSettings(page, 'settings-nick@lifepulse.test');

  const input = page.locator('[data-testid="settings-nickname-input"] input');
  await expect(input).toHaveValue('old-nick');

  const patchResp = page.waitForResponse(
    (resp) => resp.url().endsWith('/api/v1/users/me') && resp.request().method() === 'PATCH',
  );
  await input.fill('new-nick');
  await page.locator('[data-testid="settings-profile-submit"]').click({ force: true });

  const resp = await patchResp;
  expect(resp.status()).toBe(200);
  expect(JSON.parse(resp.request().postData() ?? '{}')).toEqual({ nickname: 'new-nick' });

  // 成功 toast + 表单回显（store.setUser 已由 auth.updateProfile 触发）
  await expect(page.locator('.el-message--success').filter({ hasText: '昵称已保存' })).toBeVisible();
  await expect(input).toHaveValue('new-nick');
});

// ----------------------------------------------------------------------
// ② 改密码
// ----------------------------------------------------------------------

test('改密码 → POST /users/me/password → 跳 /login?reason=password-changed 并清 token', async ({ page }) => {
  await setupSettingsAuthMocks(page, {
    id: 1,
    email: 'settings-pw@lifepulse.test',
    nickname: 'pw-test',
  });
  await loginAndOpenSettings(page, 'settings-pw@lifepulse.test');

  const pwResp = page.waitForResponse('**/api/v1/users/me/password');
  await page.locator('[data-testid="settings-old-password-input"] input').fill('OldPass123');
  await page.locator('[data-testid="settings-new-password-input"] input').fill('NewPass456');
  await page.locator('[data-testid="settings-password-submit"]').click({ force: true });

  const resp = await pwResp;
  expect(resp.status()).toBe(200);
  expect(JSON.parse(resp.request().postData() ?? '{}')).toEqual({
    oldPassword: 'OldPass123',
    newPassword: 'NewPass456',
  });

  await page.waitForURL(/\/login\?reason=password-changed/);
  await expect(page).toHaveURL('http://localhost:5173/login?reason=password-changed');
  await assertTokensCleared(page);
});

// ----------------------------------------------------------------------
// ③ 注销账号
// ----------------------------------------------------------------------

test('注销账号 → DELETE /users/me → 跳 /login?reason=account-deleted 并清 token', async ({ page }) => {
  await setupSettingsAuthMocks(page, {
    id: 1,
    email: 'settings-bye@lifepulse.test',
    nickname: 'bye',
  });
  await loginAndOpenSettings(page, 'settings-bye@lifepulse.test');

  const delResp = page.waitForResponse(
    (resp) => resp.url().endsWith('/api/v1/users/me') && resp.request().method() === 'DELETE',
  );
  await page.locator('[data-testid="settings-delete-account-btn"]').click({ force: true });

  // 第一次 ElMessageBox.prompt：填当前密码 + 点「下一步」（primary 按钮）
  const firstDialog = page.locator('.el-message-box').first();
  await firstDialog.locator('.el-message-box__input input').fill('MyPass123');
  await firstDialog.locator('.el-message-box__btns .el-button--primary').click();

  // 第二次 ElMessageBox.confirm：直接点「永久注销」（primary 按钮）
  const secondDialog = page.locator('.el-message-box').first();
  await secondDialog.locator('.el-message-box__btns .el-button--primary').click();

  const resp = await delResp;
  expect(resp.status()).toBe(200);
  expect(JSON.parse(resp.request().postData() ?? '{}')).toEqual({ password: 'MyPass123' });

  await page.waitForURL(/\/login\?reason=account-deleted/);
  await expect(page).toHaveURL('http://localhost:5173/login?reason=account-deleted');
  await assertTokensCleared(page);
});
