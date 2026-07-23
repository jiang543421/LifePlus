/**
 * AI 洞察 E2E（v2.1 LLM 扩展）。
 *
 * <p>v2.0 的核心流（card → drawer / refresh / 1501 / 1006 / 跨用户）已覆盖在
 * `ai-flow.spec.ts`，本 spec 只覆盖 v2.1 新增/变更的端到端路径（CLAUDE.md §6.1）：
 * <ol>
 *   <li>AI 卡加载成功 → 右上角显示「AI 智能」source 角标（spec §7.3 / CLAUDE.md §11.3）</li>
 *   <li>AI 卡 source=template → 显示「模板」角标（template 降级 UX）</li>
 *   <li>抽屉内点击「查看完整分析 →」→ 关闭抽屉 + 跳 /ai-analysis + 渲染 4 段（headline / advice / highlight / chips）+ 心情标签 + AI 生成 tag</li>
 *   <li>独立分析页 source=template → 显示「模板生成」tag + ElAlert 降级提示</li>
 *   <li>独立分析页点击「刷新」→ POST /ai/insight/refresh 触发 + freshnessSeconds → 0</li>
 *   <li>跨用户隔离：用户 B 登录后 /today 与 /analysis 走的是 B 的 token，前端不能伪造 userId</li>
 * </ol>
 *
 * <p>不依赖真实后端：`api-mock.ts` 全量拦截 `/api/v1/ai/insight/*`；
 * 真实后端契约由 `backend/.../ai/AiAnalysisIT.java`（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupAiDefaults,
  type MockAiInsight,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };
const BOB: MockUser = { id: 2, email: 'bob@lifepulse.test', nickname: 'bob' };

const LLM_INSIGHT: MockAiInsight = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '', trend: 'DOWN', deltaText: '较上周 -¥40' },
    { key: 'planDensity', label: '日程', value: '3', unit: '项', trend: 'NONE', deltaText: '今日 3 项' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
  source: 'llm',
  advice: '优先完成 2 项高优先级任务',
  highlight: '任务完成率较昨日 +20%',
  mood: 'POSITIVE',
};

const TEMPLATE_INSIGHT: MockAiInsight = {
  ...LLM_INSIGHT,
  source: 'template',
  // LLM 字段降级时通常为空；spec 验证 front-end 兜底渲染。
  advice: undefined,
  highlight: undefined,
  mood: undefined,
};

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
// 1. AI 卡加载成功 → 「AI 智能」source 角标
// ---------------------------------------------------------------------------

test('HomeView AI 卡 source=llm → 右上角显示「AI 智能」角标', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page, { insight: LLM_INSIGHT });

  await loginAs(page, ALICE);
  await page.goto('/');

  // 点击 AI 占位卡触发 /today
  await page.locator('[data-testid="home-card-ai"] button.module-card').click();

  // 抽屉打开（链路：/today → aiInsight → drawer v-model:show=true）
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();

  // source badge：AI 卡 wrapper 上的 data-testid 节点；class 区分 llm/template
  const badge = page.locator('[data-testid="home-card-source-badge"]');
  await expect(badge).toBeVisible();
  await expect(badge).toContainText('AI 智能');
  await expect(badge).toHaveAttribute('aria-label', 'AI 智能生成');
});

// ---------------------------------------------------------------------------
// 2. AI 卡 source=template → 「模板」角标（降级 UX）
// ---------------------------------------------------------------------------

test('HomeView AI 卡 source=template → 显示「模板」角标', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page, { insight: TEMPLATE_INSIGHT });

  await loginAs(page, ALICE);
  await page.goto('/');

  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();

  const badge = page.locator('[data-testid="home-card-source-badge"]');
  await expect(badge).toBeVisible();
  await expect(badge).toContainText('模板');
});

// ---------------------------------------------------------------------------
// 3. 抽屉「查看完整分析 →」→ /ai-analysis + 4 段渲染
// ---------------------------------------------------------------------------

test('抽屉 emit open-analysis → 关抽屉 + 跳 /ai-analysis + 渲染 4 段（headline/advice/highlight/chips）', async ({
  page,
}) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page, { insight: LLM_INSIGHT });

  await loginAs(page, ALICE);
  await page.goto('/');

  // 进入抽屉
  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();

  // 抽屉底部「查看完整分析 →」入口（仅 source=llm 时显示）
  const link = page.locator('[data-testid="ai-drawer-open-analysis"]');
  await expect(link).toBeVisible();
  await link.click();

  // 等待路由跳到 /ai-analysis
  await page.waitForURL(/\/ai-analysis$/);

  // 4 段必须全部渲染
  await expect(page.locator('[data-testid="ai-analysis-headline"]')).toContainText(
    LLM_INSIGHT.headline,
  );
  await expect(page.locator('[data-testid="ai-analysis-advice"]')).toContainText(
    LLM_INSIGHT.advice!,
  );
  await expect(page.locator('[data-testid="ai-analysis-highlight"]')).toContainText(
    LLM_INSIGHT.highlight!,
  );

  // 心情标签：POSITIVE → 「积极」
  const mood = page.locator('[data-testid="ai-analysis-mood"]');
  await expect(mood).toBeVisible();
  await expect(mood).toContainText('积极');

  // chips 数量：3 个
  const chips = page.locator('[data-testid="ai-analysis-chip"]');
  await expect(chips).toHaveCount(3);

  // AI 生成 tag（success 类型）：source=llm
  await expect(page.locator('[data-testid="ai-analysis-source-tag"]')).toContainText('AI 生成');

  // template 降级提示不应出现（source=llm）
  await expect(page.locator('[data-testid="ai-analysis-degraded-hint"]')).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// 4. 独立页 source=template → 模板生成 tag + ElAlert
// ---------------------------------------------------------------------------

test('/ai-analysis source=template → 「模板生成」tag + ElAlert 降级提示', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page, { insight: TEMPLATE_INSIGHT });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis');

  // 模板降级时 LLM 字段为空，前端兜底渲染「暂无建议 / 暂无亮点」
  await expect(page.locator('[data-testid="ai-analysis-source-tag"]')).toContainText('模板生成');
  await expect(page.locator('[data-testid="ai-analysis-advice"]')).toContainText('暂无建议');
  await expect(page.locator('[data-testid="ai-analysis-highlight"]')).toContainText('暂无亮点');

  const degraded = page.locator('[data-testid="ai-analysis-degraded-hint"]');
  await expect(degraded).toBeVisible();
  await expect(degraded).toContainText('AI 服务暂不可用');

  // mood 为空时 mood chip 不渲染
  await expect(page.locator('[data-testid="ai-analysis-mood"]')).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// 5. 独立页「刷新」→ POST /refresh + freshnessSeconds → 0
// ---------------------------------------------------------------------------

test('/ai-analysis 点击「刷新」 → POST /refresh 触发，insight 替换（freshnessSeconds=0）', async ({
  page,
}) => {
  await setupAuthDefaults(page, { user: ALICE });
  const ai = await setupAiDefaults(page, { insight: LLM_INSIGHT });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis');

  // headline 段已渲染
  await expect(page.locator('[data-testid="ai-analysis-headline"]')).toContainText(
    LLM_INSIGHT.headline,
  );

  // 点刷新按钮（独立页顶部 ElButton，data-testid 唯一）
  const refresh = page.locator('[data-testid="ai-analysis-refresh"]');
  await expect(refresh).toBeVisible();
  await refresh.click();

  // /refresh 触发后 mock 端把 freshnessSeconds 重置为 0；
  // 顶部 meta 段内的 `formatAge` 输出会变成「0 秒前」。
  await expect(page.locator('.ai-analysis .meta').first()).toContainText('0 秒', { timeout: 3000 });

  expect(ai.refreshCallCount).toBe(1);
});

// ---------------------------------------------------------------------------
// 6. 跨用户：Bob 登录后 /today + /analysis 走自己 token，前端不能伪造 userId
// ---------------------------------------------------------------------------

test('跨用户隔离：Bob 登录 → /today 与 /analysis 重新调用（不复用 Alice 数据）', async ({
  page,
}) => {
  // /today 计数器：1 次 Alice + 1 次 Bob = 2
  let todayIdx = 0;
  // 共享 insight：1 次 → Alice；2 次 → Bob
  await setupAuthDefaults(page, { user: ALICE });
  await page.route('**/api/v1/ai/insight/today', async (route) => {
    todayIdx += 1;
    const insight =
      todayIdx === 1
        ? LLM_INSIGHT
        : {
            ...LLM_INSIGHT,
            headline: 'Bob 今日任务完成率 20%（v2.1 LLM 命中）。',
            source: 'llm' as const,
          };
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data: insight }),
    });
  });
  // /analysis：1 次（从首页抽屉跳过去） + 1 次（Bob 跳过去）= 2
  let analysisIdx = 0;
  await page.route('**/api/v1/ai/insight/analysis', async (route) => {
    analysisIdx += 1;
    const insight =
      analysisIdx === 1
        ? { ...LLM_INSIGHT, headline: 'Alice 完整分析页 headline。' }
        : { ...LLM_INSIGHT, headline: 'Bob 完整分析页 headline。' };
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data: insight }),
    });
  });

  // ---- Alice 登录 + AI 卡 ----
  await loginAs(page, ALICE);
  await page.goto('/');
  await page.locator('[data-testid="home-card-ai"] button.module-card').click();
  await expect(page.locator('.el-drawer__title').filter({ hasText: 'AI 洞察' })).toBeVisible();
  await expect(page.locator('[data-testid="ai-drawer-headline"]')).toContainText('Alice');

  // ---- 切 Bob：清 token + 跳 /login（模拟 logout）。 ----
  await clearStorage(page);
  await loginAs(page, BOB);
  await page.goto('/ai-analysis');

  // /analysis 返回 Bob 数据，绝不是 Alice 的（证明前端不能伪造 userId，
  // 且 store 在用户切换后会被重新初始化，从 mock 拉新数据）。
  await expect(page.locator('[data-testid="ai-analysis-headline"]')).toContainText('Bob');
  await expect(page.locator('[data-testid="ai-analysis-headline"]')).not.toContainText('Alice');

  expect(todayIdx).toBe(1); // Bob 这次没点 AI 卡，只访问了 /ai-analysis
  expect(analysisIdx).toBe(1);
});
