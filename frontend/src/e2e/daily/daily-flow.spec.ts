/**
 * 日报 v1.2.4 E2E（CLAUDE.md §6.1 + spec §08-daily-report-design §9.2）。
 *
 * <p>覆盖浏览器侧完整链路：
 * <ol>
 *   <li>入口：登录 → 首页「日报」卡 → /daily → 4 张指标卡 + TopBar 全部可见</li>
 *   <li>日期 query：直访 /daily?date=YYYY-MM-DD → daily API 被调且传入该日期</li>
 *   <li>周报切换：点「本周/上一周/下一周」按钮 → URL ?week=YYYY-Www 同步 + /daily/week 被调</li>
 *   <li>饮食冻结契约：diet.enabled=false → 占位卡显示「暂未启用」（v1.2.4 spec §3.1）</li>
 * </ol>
 *
 * <p>不依赖真实后端：`api-mock.ts` 全量 page.route() 拦截 `/api/v1/daily*`；
 * 真实后端契约由 {@code backend/.../daily/DailyReportIT.java}（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupDailyDefaults,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };

/** 走完登录表单 → 等守卫通过 → 留在登录后页面。 */
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
// 1. 入口：首页「日报」卡 → /daily → 4 张指标卡可见
// ---------------------------------------------------------------------------

test('登录 → 点击首页日报卡 → /daily → 4 张指标卡可见 + daily API 被调', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const daily = await setupDailyDefaults(page);

  await loginAs(page, ALICE);
  await page.goto('/');

  // 首页日报卡升级为 module（v1.2.4）：渲染为 <a class="module-card">
  const dailyLink = page.locator('[data-testid="home-card-daily"] a.module-card');
  await expect(dailyLink).toBeVisible();
  await expect(dailyLink).toHaveAttribute('href', '/daily');

  await dailyLink.click();
  await page.waitForURL((u) => u.pathname === '/daily');

  // 4 张指标卡全部可见
  await expect(page.locator('[data-testid="daily-task-card"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-plan-card"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-expense-card"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-diet-card"]')).toBeVisible();

  // task 摘要「3 / 5」（mock 默认 completedCount=3, totalCount=5）
  await expect(page.locator('[data-testid="daily-task-summary"]')).toContainText('3 / 5');

  // 三个周报切换按钮（默认 URL 无 ?week= 时仍渲染所有按钮）
  await expect(page.locator('[data-testid="daily-week-prev"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-week-current"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-week-next"]')).toBeVisible();

  // daily API 至少被调 1 次（onMounted + watch(currentDate) 在路由变化时可能双触发）；
  // 关注"是否真的发起 daily fetch"而非精确次数。
  expect(daily.dailyCallCount).toBeGreaterThanOrEqual(1);
  // week API 尚未触发（无 URL ?week= 且未点按钮）
  expect(daily.weekCallCount).toBe(0);
});

// ---------------------------------------------------------------------------
// 2. 日期 query：直访 /daily?date=YYYY-MM-DD → daily API 被调且传入该日期
// ---------------------------------------------------------------------------

test('直访 /daily?date=2026-07-23 → daily API 被调且 query.date 传入', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const daily = await setupDailyDefaults(page);

  await loginAs(page, ALICE);
  await page.goto('/daily?date=2026-07-23');
  await page.waitForURL(/\/daily/);

  await expect(page.locator('[data-testid="daily-task-card"]')).toBeVisible();

  // daily API 至少被调 1 次；query.date 应传入 2026-07-23
  expect(daily.dailyCallCount).toBeGreaterThanOrEqual(1);
  expect(daily.lastDailyDate).toBe('2026-07-23');
});

// ---------------------------------------------------------------------------
// 3. 周报切换：点「本周/上一周」按钮 → URL ?week=YYYY-Www + week API 被调
// ---------------------------------------------------------------------------

test('点「本周」按钮 → URL ?week=YYYY-Www + week API 被调 + 周报渲染', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const daily = await setupDailyDefaults(page);

  await loginAs(page, ALICE);
  await page.goto('/daily');
  await page.waitForURL(/\/daily/);

  await page.locator('[data-testid="daily-week-current"]').click();

  // URL 应包含 ?week=YYYY-Www（基于当前 Asia/Shanghai 周）
  await page.waitForURL(/\?week=\d{4}-W\d{2}/);

  // 周报区可见：isoWeek + weekStart + weekEnd
  await expect(page.locator('[data-testid="daily-week-label"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-week-label"]')).toContainText('2026-W30');
  await expect(page.locator('[data-testid="daily-week-label"]')).toContainText('2026-07-20');
  await expect(page.locator('[data-testid="daily-week-label"]')).toContainText('2026-07-26');

  // week API 被调 1 次
  expect(daily.weekCallCount).toBe(1);
});

test('点「上一周」按钮 → URL ?week 减 1 周（基于 baseWeek 解析）', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupDailyDefaults(page);

  await loginAs(page, ALICE);
  // 先用 ?week=2026-W30 作为基准，验证 prev 减到 W29
  await page.goto('/daily?week=2026-W30');
  await page.waitForURL(/\/daily/);

  await page.locator('[data-testid="daily-week-prev"]').click();
  await page.waitForURL(/\?week=2026-W29/);

  const url = new URL(page.url());
  expect(url.searchParams.get('week')).toBe('2026-W29');
});

// ---------------------------------------------------------------------------
// 4. 饮食冻结契约：diet.enabled=false → 占位卡显示「暂未启用」
// ---------------------------------------------------------------------------

test('diet.enabled=false → DietMetricsCard 渲染占位文案「暂未启用」', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupDailyDefaults(page);

  await loginAs(page, ALICE);
  await page.goto('/daily');
  await page.waitForURL(/\/daily/);

  // 占位卡可见
  await expect(page.locator('[data-testid="daily-diet-placeholder"]')).toBeVisible();
  await expect(page.locator('[data-testid="daily-diet-placeholder"]')).toContainText('暂未启用');
  // 占位副文案（reason）显示
  await expect(page.locator('[data-testid="daily-diet-placeholder"]')).toContainText('饮食模块暂未启用');
});