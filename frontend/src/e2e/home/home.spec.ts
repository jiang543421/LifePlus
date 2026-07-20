/**
 * 首页仪表盘 E2E（Phase 4 P4-6）。
 *
 * <p>CLAUDE.md §6.1 强制：首页关键用户流必须有 E2E。
 * 覆盖：未登录守卫 / 登录后 6 卡网格 + 问候 / 模块卡跳转 / 占位卡 toast /
 * 设置入口 / 退出登录。
 *
 * <p>不依赖真实后端：`api-mock.ts` 全量 page.route() 拦截 `/api/v1/*`；
 * 真实后端契约由 `backend/src/test/.../*IT.java`（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupTaskDefaults,
  setupPlanDefaults,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };

const LP_KEYS = ['lp_access_token', 'lp_refresh_token', 'lp_user'] as const;

/** 走完登录表单 → 等守卫通过 → 留在登录后页面（/ 或 redirect 目标）。 */
async function loginAs(page: Page, user: MockUser): Promise<void> {
  await page.goto('/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

// ---------------------------------------------------------------------------
// 守卫
// ---------------------------------------------------------------------------

test('未登录访问 / → 守卫推到 /login?redirect=/', async ({ page }) => {
  await page.goto('/');
  await page.waitForURL(/\/login/);
  // Playwright toHaveURL 显示解码后的真实 URL，匹配 /login?redirect=/（与 task-flow.spec.ts:94 一致）
  await expect(page).toHaveURL(/\/login\?redirect=\/$/);
});

// ---------------------------------------------------------------------------
// 登录后首页骨架
// ---------------------------------------------------------------------------

test('登录后 /：展示 TopBar + 6 卡网格 + 问候语', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupTaskDefaults(page, { userId: ALICE.id, tasks: [] });
  await setupPlanDefaults(page, { userId: ALICE.id, plans: [] });

  await loginAs(page, ALICE);
  await page.goto('/');
  await expect(page).toHaveURL('http://localhost:5173/');

  // TopBar：品牌名「LifePulse」 + 副标「数字生活」
  await expect(page.locator('[data-testid="topbar-brand"]')).toBeVisible();
  await expect(page.locator('[data-testid="topbar-brand"]')).toContainText('LifePulse');
  await expect(page.locator('[data-testid="topbar-brand"]')).toContainText('数字生活');

  // 问候头：包含 nickname「alice」
  await expect(page.locator('.home-view__greeting-title')).toContainText('alice');

  // 6 张卡（key 与 HOME_CARDS 顺序一致）
  const grid = page.locator('[data-testid="home-view-grid"]');
  await expect(grid).toBeVisible();
  await expect(page.locator('[data-testid="home-card-task"]')).toBeVisible();
  await expect(page.locator('[data-testid="home-card-plan"]')).toBeVisible();
  await expect(page.locator('[data-testid="home-card-daily"]')).toBeVisible();
  await expect(page.locator('[data-testid="home-card-expense"]')).toBeVisible();
  await expect(page.locator('[data-testid="home-card-diet"]')).toBeVisible();
  await expect(page.locator('[data-testid="home-card-ai"]')).toBeVisible();
});

// ---------------------------------------------------------------------------
// 模块卡跳转
// ---------------------------------------------------------------------------

test('点击「任务」卡 → 跳转 /tasks', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupTaskDefaults(page, { userId: ALICE.id, tasks: [] });

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-task"] a.module-card').click();
  await page.waitForURL(/\/tasks$/);
  await expect(page).toHaveURL('http://localhost:5173/tasks');
});

test('点击「计划」卡 → 跳转 /plans', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupPlanDefaults(page, { userId: ALICE.id, plans: [] });

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-plan"] a.module-card').click();
  await page.waitForURL(/\/plans$/);
  await expect(page).toHaveURL('http://localhost:5173/plans');
});

// ---------------------------------------------------------------------------
// 占位卡 toast
// ---------------------------------------------------------------------------

test('点击「日报」占位卡 → 显示「即将上线」ElMessage', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-daily"] button.module-card').click();
  await expect(page.locator('.el-message').filter({ hasText: '即将上线' })).toBeVisible();
  await expect(page).toHaveURL('http://localhost:5173/');
});

test('点击「消费」模块卡 → 跳转 /expenses', async ({ page }) => {
  // v1.2.1 起消费卡从 placeholder 升级为 module（指向 /expenses）。
  await setupAuthDefaults(page, { user: ALICE });
  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-expense"] a.module-card').click();
  await page.waitForURL(/\/expenses$/);
  await expect(page).toHaveURL('http://localhost:5173/expenses');
});

test('点击「饮食」占位卡 → 显示「即将上线」ElMessage', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-diet"] button.module-card').click();
  await expect(page.locator('.el-message').filter({ hasText: '即将上线' })).toBeVisible();
});

test('点击「AI 分析」占位卡 → 显示「即将上线」ElMessage', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await expect(page.locator('.el-message').filter({ hasText: '即将上线' })).toBeVisible();
});

// ---------------------------------------------------------------------------
// 设置入口
// ---------------------------------------------------------------------------

test('点击顶栏设置图标 → 跳转 /settings', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await loginAs(page, ALICE);
  await page.goto('/');

  // 桌面态设置入口：顶栏右上角 router-link
  await page.locator('[data-testid="topbar-settings-link"]').first().click();
  await page.waitForURL(/\/settings$/);
  await expect(page).toHaveURL('http://localhost:5173/settings');
  // 落地到 SettingsView 空态
  await expect(page.locator('[data-testid="settings-empty"]')).toBeVisible();
});

// ---------------------------------------------------------------------------
// 退出登录
// ---------------------------------------------------------------------------

test('退出登录 → 跳转 /login 并清空本地 token', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await loginAs(page, ALICE);
  await page.goto('/');

  // 桌面态：点击头像触发 ElDropdown（Teleport 到 body），再点 dropdown 内的「退出登录」
  await page.locator('[data-testid="topbar-avatar-trigger"]').click();
  await page.locator('.el-dropdown-menu [data-testid="topbar-logout"]').click();

  await page.waitForURL(/\/login/);
  await expect(page).toHaveURL('http://localhost:5173/login');

  // 本地 storage 已清（auth.logout → clear()）
  const remaining = await page.evaluate((keys) => {
    return keys.map((k) => ({ k, v: localStorage.getItem(k) }));
  }, [...LP_KEYS]);
  for (const { k, v } of remaining) {
    expect(v, `localStorage[${k}] 应已被清空`).toBeNull();
  }
});