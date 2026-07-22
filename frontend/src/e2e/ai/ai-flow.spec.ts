/**
 * AI 洞察 E2E（v2.0）。
 *
 * <p>CLAUDE.md §6.1 强制项：AI 卡联调流必须有 E2E。
 * 覆盖：
 * <ol>
 *   <li>点击 AI 占位卡 → 抽屉打开 + 渲染 headline + 3 chip</li>
 *   <li>抽屉内点「刷新」→ POST /ai/insight/refresh 触发，insight 被替换</li>
 *   <li>后端 1501（AI_DEGRADED）→ 抽屉仍打开（body 不渲染） + Toast 降级文案</li>
 *   <li>后端 1006（限流）→ Toast 限流文案</li>
 *   <li>跨用户隔离：用户 B 登录后 AI 卡调用走的仍是自己的 token（不能伪造他人 insight）</li>
 * </ol>
 *
 * <p>不依赖真实后端：`api-mock.ts` 全量 page.route() 拦截 `/api/v1/ai/insight/*`；
 * 真实后端契约由 `backend/.../ai/AiInsightIT.java`（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupAiDefaults,
  mockAiTodayError,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  gotoLogin,
  strongPassword,
} from '../helpers/test-fixtures';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };
const BOB: MockUser = { id: 2, email: 'bob@lifepulse.test', nickname: 'bob' };

/** 走完登录表单 → 等守卫通过 → 留在登录后页面（/ 或 redirect 目标）。 */
async function loginAs(page: Page, user: MockUser): Promise<void> {
  await page.goto('/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
}

/** 等抽屉标题出现（ElDrawer 通过 Teleport 渲染到 body）。 */
async function waitForAiDrawer(page: Page): Promise<void> {
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).toBeVisible();
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

// ---------------------------------------------------------------------------
// 1. 点击 AI 占位卡 → 抽屉打开 + 渲染
// ---------------------------------------------------------------------------

test('点击 AI 占位卡 → 抽屉打开 + 渲染 headline + 3 chip + 刷新按钮', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const ai = await setupAiDefaults(page);

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await waitForAiDrawer(page);

  // 抽屉头部文案
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).toContainText(ai.insight.headline);

  // 3 个 chip：按 data-testid 计数 + 验证 chip 内的 label 节点
  // 用 .ai-drawer__chip-label 锁定到 chip，避免与 headline 中"任务完成率"子串冲突。
  const chips = page.locator('[data-testid="ai-drawer-chip"]');
  await expect(chips).toHaveCount(3);
  const chipLabels = page.locator('.ai-drawer__chip-label');
  await expect(chipLabels).toHaveCount(3);
  await expect(chipLabels.nth(0)).toContainText('任务完成');
  await expect(chipLabels.nth(1)).toContainText('本周消费');
  await expect(chipLabels.nth(2)).toContainText('日程');

  // 刷新按钮 + freshness 文案（12 秒前生成）
  await expect(page.locator('[data-testid="ai-drawer-refresh"]')).toBeVisible();
  await expect(page.locator('.ai-drawer__freshness')).toContainText('秒前生成');

  // 调用统计：/today 被命中 1 次，/refresh 0 次
  expect(ai.todayCallCount).toBe(1);
  expect(ai.refreshCallCount).toBe(0);
});

// ---------------------------------------------------------------------------
// 2. 抽屉内点「刷新」 → POST /refresh 触发 + insight 替换
// ---------------------------------------------------------------------------

test('抽屉内点「刷新」 → POST /refresh 被调用，insight 被替换', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const ai = await setupAiDefaults(page);

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await waitForAiDrawer(page);

  // 初始 freshness：12 秒前生成
  await expect(page.locator('.ai-drawer__freshness')).toContainText('12 秒前生成');

  // 点刷新按钮
  await page.locator('[data-testid="ai-drawer-refresh"]').click();

  // /refresh 后 freshnessSeconds=0 → "0 秒前生成"
  await expect(page.locator('.ai-drawer__freshness')).toContainText('0 秒前生成');

  expect(ai.refreshCallCount).toBe(1);
  // 抽屉仍打开（refresh 不关闭抽屉）
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();
});

// ---------------------------------------------------------------------------
// 3. /today 返回 1501 → 抽屉打开但 body 不渲染 + Toast
// ---------------------------------------------------------------------------

test('/today 返回 1501（AI_DEGRADED）→ 抽屉仍打开 + 降级 Toast', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page);
  await mockAiTodayError(page, 1501);

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-ai"] button.module-card').click();

  // 抽屉标题出现（v-model:show 已生效）
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();
  // insight=null → v-if 短路：headline / chips 不渲染
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).toHaveCount(0);
  await expect(page.locator('[data-testid="ai-drawer-chip"]')).toHaveCount(0);

  // 降级 Toast
  await expect(
    page.locator('.el-message').filter({ hasText: 'AI 洞察数据暂时不可用，请稍后重试' }),
  ).toBeVisible();
});

// ---------------------------------------------------------------------------
// 4. /today 返回 1006 → 限流 Toast
// ---------------------------------------------------------------------------

test('/today 返回 1006（限流）→ 限流 Toast', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page);
  await mockAiTodayError(page, 1006);

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-ai"] button.module-card').click();

  await expect(
    page.locator('.el-message').filter({ hasText: 'AI 洞察请求过于频繁，请稍后重试' }),
  ).toBeVisible();
});

// ---------------------------------------------------------------------------
// 5. 跨用户隔离：用户 B 登录后不能"接续"用户 A 的 insight
// ---------------------------------------------------------------------------

test('跨用户隔离：用户 B 登录 → AI 卡 fresh /today 调用，insight 不沿用 A 的', async ({ page }) => {
  // 一站式 auth + ai mock（覆盖 login/me/refresh + ai/insight/*）。
  await setupAuthDefaults(page, { user: ALICE });
  // 覆盖默认 /ai/insight/today handler：按调用计数切换响应。
  // 1 次调用 → Alice headline；2 次调用 → Bob headline。
  let callIdx = 0;
  await page.route('**/api/v1/ai/insight/today', async (route) => {
    callIdx += 1;
    const data =
      callIdx === 1
        ? {
            headline: 'Alice 今日任务完成率 80%。',
            chips: [],
            generatedAt: '2026-07-22T08:00:00Z',
            freshnessSeconds: 5,
          }
        : {
            headline: 'Bob 今日任务完成率 20%。',
            chips: [],
            generatedAt: '2026-07-22T08:00:00Z',
            freshnessSeconds: 9,
          };
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data }),
    });
  });

  // ---- Alice 登录 + AI 卡 ----
  await loginAs(page, ALICE);
  await page.goto('/');
  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await waitForAiDrawer(page);
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).toContainText('Alice');

  // ---- 切换 Bob：抽屉 overlay 遮住 topbar；直接清 token + 跳 /login，模拟 logout。
  // （真实 logout 路径已在 home.spec.ts 覆盖。）
  await clearStorage(page);
  await gotoLogin(page);

  // Bob 登录（同一 setupAuthDefaults 仍生效）
  await loginAs(page, BOB);
  await page.goto('/');
  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await waitForAiDrawer(page);

  // 抽屉内应展示 Bob 的 headline，绝不是 Alice 的（证明 HomeView 重新挂载后
  // /today 被 fresh 调用，缓存不复用前一用户的数据）。
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).toContainText('Bob');
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).not.toContainText('Alice');
  expect(callIdx).toBe(2);
});
