/**
 * 日程流 E2E（Phase 3-G，2026-07 补丁：通过 fillDateTimePicker 稳定 EP date-picker）。
 *
 * <p>CLAUDE.md §6.1 强制项：日历流必须有 E2E。
 * 覆盖：守卫 → 空态 → 新建 → 编辑 → 删除 → 跨用户 1003 → 月份切换范围查询 → 关联任务。
 *
 * <p>不依赖真实后端：`api-mock.ts` 全量 page.route() 拦截 `/api/v1/plans*`；
 * 真实后端契约由 `backend/src/test/.../plan/PlanFlowIT.java`（Testcontainers）覆盖。
 *
 * <p>el-date-picker type=datetime 在 Chromium + Playwright 下 popper 浮层不稳
 * （见 CLAUDE.md MEMORY 与 `helpers/el-date-picker.ts` 头注），但 UI 行为已由
 * `EventDialog.spec.ts` 单测覆盖；E2E「新建事件」走真实 dialog 流程，仅日期输入
 * 这一步改用 `fillDateTimePicker`（直接 emit update:modelValue），保证链路稳定。
 *
 * <p>「编辑」用例的 dialog 链路（点 edit-start → 改 title → 提交）保留：
 * edit 模式不动 date-picker，端到端验证 PUT → 刷新 → 列表更新。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupPlanDefaults,
  setupTaskDefaults,
  mockPlanCrossUser,
  type MockUser,
  type MockPlan,
  type MockTask,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';
import { fillDateTimePicker } from '../helpers/el-date-picker';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };
const BOB: MockUser = { id: 2, email: 'bob@lifepulse.test', nickname: 'bob' };

const FIXED_NOW = '2026-07-16T10:00:00+08:00';

function plan(overrides: Partial<MockPlan> & Pick<MockPlan, 'id' | 'title' | 'startTime' | 'endTime'>): MockPlan {
  return {
    userId: ALICE.id,
    allDay: 0,
    location: null,
    note: null,
    reminderMin: null,
    createdAt: FIXED_NOW,
    updatedAt: FIXED_NOW,
    ...overrides,
  };
}

async function loginAs(page: Page, user: MockUser): Promise<void> {
  await page.goto('/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

test('未登录访问 /plans → 守卫推到 /login?redirect=/plans', async ({ page }) => {
  await page.goto('/plans');
  await page.waitForURL(/\/login/);
  await expect(page).toHaveURL(/\/login\?redirect=\/plans/);
});

test('登录后 /plans 当天无事件：day panel 展示「当天没有事件」', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupPlanDefaults(page, { userId: ALICE.id, plans: [] });

  await loginAs(page, ALICE);
  await page.goto('/plans');

  await expect(page.locator('[data-testid="day-empty"]')).toBeVisible();
  await expect(page.locator('[data-testid="day-empty"]')).toContainText('当天没有事件');
});

test('新建事件：点「+ 新建事件」→ 填时间/标题 → 提交 → 当日出现该事件', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupPlanDefaults(page, { userId: ALICE.id, plans: [] });

  await loginAs(page, ALICE);
  await page.goto('/plans');
  await expect(page.locator('[data-testid="day-empty"]')).toBeVisible();

  // 读 view 当前选中的日期，避免硬编码系统当天日期；E2E 必须与运行机器日期解耦。
  const dayTitle = (await page.locator('[data-testid="day-panel"] .day-title').textContent()) ?? '';
  const selectedDate = dayTitle.replace(' 的事件', '').trim();
  expect(selectedDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  const startTime = `${selectedDate}T12:00:00`;
  const endTime = `${selectedDate}T13:00:00`;

  // 真实 UI 路径：弹 dialog → 填日期 → 填标题 → 提交。
  await page.locator('[data-testid="new-plan"]').click();
  await expect(page.locator('[data-testid="event-dialog"]')).toBeVisible();

  // 日期输入走 fillDateTimePicker（绕开 popper，理由见 helpers/el-date-picker.ts）。
  await fillDateTimePicker(page, '[data-testid="event-start"]', startTime);
  await fillDateTimePicker(page, '[data-testid="event-end"]', endTime);
  await page.locator('[data-testid="event-title"] input').fill('午餐');

  const createResp = page.waitForResponse(
    (r) => /\/api\/v1\/plans$/.test(r.url()) && r.request().method() === 'POST',
  );
  await page.locator('[data-testid="event-submit"]').click();
  await createResp;

  // 列表刷新后该事件出现在 day panel
  await expect(page.locator('[data-testid="event-row"]')).toHaveCount(1);
  await expect(page.locator('[data-testid="event-row"]').first()).toContainText('午餐');

  // mock state 已写入
  expect(state.list).toHaveLength(1);
  expect(state.list[0]?.title).toBe('午餐');
});

test('详情页编辑 → 保存：PUT → 标题更新 + 回日历看到新标题', async ({ page }) => {
  const p = plan({ id: 7, title: '周会', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00' });
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupPlanDefaults(page, { userId: ALICE.id, plans: [p] });

  await loginAs(page, ALICE);
  await page.goto('/plans/7');
  await expect(page.locator('[data-testid="plan-detail"]')).toBeVisible();

  await page.locator('[data-testid="edit-start"]').click();
  await expect(page.locator('[data-testid="event-dialog"]')).toBeVisible();

  await page.locator('[data-testid="event-title"] input').fill('周会+复盘');

  const putResp = page.waitForResponse(
    (r) => /\/api\/v1\/plans\/7$/.test(r.url()) && r.request().method() === 'PUT',
  );
  await page.locator('[data-testid="event-submit"]').click();
  await putResp;

  expect(state.detail.get(7)?.title).toBe('周会+复盘');
  // 详情页 reload 后展示新标题
  await expect(page.locator('[data-testid="plan-detail"]')).toContainText('周会+复盘');
});

test('详情页删除：ElMessageBox 确认 → DELETE → 跳回 /plans 当天空态', async ({ page }) => {
  const p = plan({ id: 9, title: '周会', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00' });
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupPlanDefaults(page, { userId: ALICE.id, plans: [p] });

  await loginAs(page, ALICE);
  await page.goto('/plans/9');
  await expect(page.locator('[data-testid="plan-detail"]')).toBeVisible();

  await page.locator('[data-testid="delete-btn"]').click();
  // ElMessageBox 真实 modal：点 primary「删除」按钮确认
  await page.locator('.el-message-box .el-button--primary').click();

  await page.waitForURL(/\/plans$/);
  expect(state.detail.has(9)).toBe(false);
  // 选中日（默认 today）该事件已不在
  await expect(page.locator('[data-testid="day-empty"]')).toBeVisible();
});

test('月份切换：点击下月后 GET /plans 带新的 from/to 范围', async ({ page }) => {
  const p1 = plan({ id: 1, title: '七月事件', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00' });
  const p2 = plan({ id: 2, title: '八月事件', startTime: '2026-08-20T10:00:00', endTime: '2026-08-20T11:00:00' });
  await setupAuthDefaults(page, { user: ALICE });
  await setupPlanDefaults(page, { userId: ALICE.id, plans: [p1, p2] });

  await loginAs(page, ALICE);
  await page.goto('/plans');
  // 等初始 onMounted fetch 完成（from=2026-07-01）
  await page.waitForResponse(
    (r) => /\/api\/v1\/plans\?/.test(r.url()) && r.url().includes('from=2026-07-01'),
  );
  // 选中 7-15 → day panel 展示该日事件
  await page.locator('[data-date="2026-07-15"]').click();
  await expect(page.locator('[data-testid="event-row"]')).toContainText('七月事件');

  // 切到 8 月：等下月 fetch 命中八月 1 日
  const nextResp = page.waitForResponse(
    (r) => /\/api\/v1\/plans\?/.test(r.url()) && r.url().includes('from=2026-08-01'),
  );
  await page.locator('[data-testid="cal-next"]').click();
  await nextResp;
  // 月份标题变成 8 月
  await expect(page.locator('[data-testid="cal-title"]')).toContainText('2026 年 8 月');
  // 8-20 有事件点（mock 按 from/to 过滤：八月只命中 p2）
  await expect(page.locator('[data-date="2026-08-20"] [data-testid="day-dots"]')).toBeVisible();
  // 选中 8-20 → day panel 展示该日事件
  await page.locator('[data-date="2026-08-20"]').click();
  await expect(page.locator('[data-testid="event-row"]')).toContainText('八月事件');
});

test('跨用户 1003：Bob 直接访问 Alice 的 /plans/100 → 跳回 /plans', async ({ page }) => {
  const alicePlan = plan({ id: 100, title: 'A 私有事件', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00' });
  await setupAuthDefaults(page, { user: ALICE });
  await setupPlanDefaults(page, { userId: ALICE.id, plans: [alicePlan] });
  await loginAs(page, ALICE);
  await page.goto('/plans/100');
  await expect(page.locator('[data-testid="plan-detail"]')).toBeVisible();
  // 确认 Alice 自己能看见：sanity
  await expect(page.locator('[data-testid="plan-detail"]')).toContainText('A 私有事件');

  // 切到 Bob：清 storage、重设 auth + plans（Bob 自己的空列表）+ cross-user 1003
  await clearStorage(page);
  await setupAuthDefaults(page, { user: BOB });
  await setupPlanDefaults(page, { userId: BOB.id, plans: [] });
  await mockPlanCrossUser(page, 100);

  await loginAs(page, BOB);
  await page.goto('/plans/100');
  await page.waitForURL(/\/plans$/);
  await expect(page).toHaveURL(/\/plans$/);
});

test('详情页展示本日程下的关联任务（F-H03）：存在 2 条 task → 显示 2 行', async ({ page }) => {
  const p = plan({ id: 7, title: '周会', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00' });
  // 两个关联任务：id 100、101，planId=7
  const t1: MockTask = {
    id: 100,
    userId: ALICE.id,
    planId: 7,
    title: '准备材料',
    status: 0,
    priority: 2,
    dueDate: '2026-07-15',
    tag: 'work',
    createdAt: FIXED_NOW,
    updatedAt: FIXED_NOW,
  };
  const t2: MockTask = {
    ...t1,
    id: 101,
    title: '预订会议室',
    status: 1,
    priority: 1,
    dueDate: null,
    tag: null,
  };
  await setupAuthDefaults(page, { user: ALICE });
  await setupPlanDefaults(page, { userId: ALICE.id, plans: [p] });
  await setupTaskDefaults(page, { userId: ALICE.id, tasks: [t1, t2] });

  await loginAs(page, ALICE);
  // 同时等待 by-plan 响应与跳转（响应在 goto 期间就到，waitForResponse 必须在 goto 之前订阅）
  const [, byPlanResp] = await Promise.all([
    page.goto('/plans/7'),
    page.waitForResponse(
      (r) => /\/api\/v1\/tasks\/by-plan\/7/.test(r.url()) && r.request().method() === 'GET',
    ),
  ]);
  await expect(page.locator('[data-testid="plan-detail"]')).toBeVisible();

  // 响应状态码应 200，且返回 2 条任务
  expect(byPlanResp.status()).toBe(200);
  const rows = page.locator('[data-testid="related-task-row"]');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toContainText('准备材料');
  await expect(rows.nth(1)).toContainText('预订会议室');
});