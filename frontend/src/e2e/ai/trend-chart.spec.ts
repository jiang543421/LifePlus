/**
 * AI 趋势图 E2E（v2.2 trend 补 patch）。
 *
 * <p>v2.2 的 4 槽 sparkline（task / plan / expense / diet）+ ElRadioButton 窗口切换 +
 * URL ?window= 同步在 AiAnalysisView 内嵌的 {@code TrendPanel} 上验证（CLAUDE.md §6.1）：
 * <ol>
 *   <li>/ai-analysis 加载完成 → 默认 window=14 拉 /trend，渲染 4 个 sparkline，diet 永远 empty</li>
 *   <li>切 7 天 → URL 同步 ?window=7 + 重新 GET /trend?window=7</li>
 *   <li>直接访问 /ai-analysis?window=30 → 路由 query 解析命中 30（解析非法值回退 14 由单测覆盖）</li>
 *   <li>diet 槽位永远空 points → 渲染 TriStateEmpty「暂无数据」</li>
 *   <li>/trend 返回 503（1501） → TriStateError 渲染 + 点 retry 重新调用</li>
 * </ol>
 *
 * <p>不依赖真实后端：`api-mock.ts` 拦截 `/api/v1/auth/*`；`/trend` 在本 spec 内
 * 用 `page.route()` 直接注册（setupAiDefaults 兜底会回 404）。
 * 真实后端契约由 `backend/.../ai/AiTrendControllerIT.java`（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupAiDefaults,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';
import type { AiTrendResponse, MetricSeriesDto } from '@/types';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };

/** 构造一个 N 点的 metric series；base 日期向前递减。 */
function makeSeries(
  key: string,
  label: string,
  unit: string,
  count: number,
  valueAt: (i: number) => number,
  labelAt: (v: number) => string,
): MetricSeriesDto {
  const today = new Date('2026-07-23T00:00:00Z');
  const points = Array.from({ length: count }, (_, i) => {
    const d = new Date(today);
    d.setUTCDate(today.getUTCDate() - (count - 1 - i));
    const v = valueAt(i);
    return {
      date: d.toISOString().slice(0, 10),
      value: v,
      label: labelAt(v),
    };
  });
  return { key, label, unit, points };
}

/** 默认 window=14 响应：14 个点 × 4 槽，diet 永远 0 长度。 */
function trendResponse(window: number): AiTrendResponse {
  const count = window;
  const today = new Date('2026-07-23T00:00:00Z');
  const from = new Date(today);
  from.setUTCDate(today.getUTCDate() - (count - 1));
  return {
    window,
    from: from.toISOString().slice(0, 10),
    to: today.toISOString().slice(0, 10),
    metrics: ['taskCompletion', 'planDensity', 'weeklyExpense', 'dietIntake'],
    series: {
      task: makeSeries('task', '任务完成率', '%', count, (i) => 0.5 + i * 0.03, (v) => `${Math.round(v * 100)}%`),
      plan: makeSeries('plan', '日程事件', '项', count, (i) => 1 + (i % 4), (v) => `${v} 项`),
      expense: makeSeries('expense', '消费金额', '¥', count, (i) => 50 + i * 12, (v) => `¥${v.toFixed(2)}`),
      // diet 永久占位（spec / CLAUDE.md §1 NOT-DO）
      diet: { key: 'diet', label: '饮食', unit: '', points: [] },
    },
    generatedAt: '2026-07-23T08:00:00Z',
  };
}

async function loginAs(page: Page, user: MockUser): Promise<void> {
  await page.goto('/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
}

async function mockTrendSuccess(
  page: Page,
  opts: { window: number; body?: AiTrendResponse },
): Promise<{ calls: { window: number | null }[] }> {
  const state = { calls: [] as { window: number | null }[] };
  await page.route('**/api/v1/ai/insight/trend**', async (route) => {
    const url = new URL(route.request().url());
    state.calls.push({ window: Number(url.searchParams.get('window')) });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data: opts.body ?? trendResponse(opts.window) }),
    });
  });
  return state;
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

// ---------------------------------------------------------------------------
// 1. 默认 window=14 → 4 sparkline 渲染
// ---------------------------------------------------------------------------

test('AiAnalysisView 趋势段：默认 window=14 → /trend?window=14 + 4 sparkline', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page); // 兜底 /today + /analysis，/trend 由 mockTrendSuccess 接管
  const trend = await mockTrendSuccess(page, { window: 14 });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis');

  // 趋势段已渲染
  const trendSection = page.locator('[data-testid="ai-analysis-trend"]');
  await expect(trendSection).toBeVisible();

  // 默认 window=14 → 4 sparkline
  await expect(page.locator('[data-testid="trend-panel-window-switch"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-window-14"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-sparkline-task"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-sparkline-plan"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-sparkline-expense"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-sparkline-diet"]')).toBeVisible();

  // 14 个点对应的 polyline points 字符串：x1,y1 x2,y2 ...（14 对）
  const polyline = page.locator('[data-testid="trend-panel-sparkline-task"] polyline');
  const pointsAttr = await polyline.getAttribute('points');
  expect(pointsAttr).not.toBeNull();
  expect(pointsAttr!.trim().split(/\s+/)).toHaveLength(14);

  // /trend 被调用 1 次且 window=14
  expect(trend.calls).toEqual([{ window: 14 }]);

  // 14 天窗口的 URL 未带 ?window=（组件 parseWindow 默认值，watch 不触发 router.replace）
  expect(new URL(page.url()).searchParams.get('window')).toBeNull();
});

// ---------------------------------------------------------------------------
// 2. 切 7 天 → URL 同步 + 重新 GET
// ---------------------------------------------------------------------------

test('TrendPanel 切 7 天 → URL ?window=7 + 重新 GET /trend?window=7', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page);
  const trend = await mockTrendSuccess(page, { window: 7 });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis');

  // 等初次加载完成（默认 14）
  await expect(page.locator('[data-testid="trend-panel-window-switch"]')).toBeVisible();

  // 点 7 天 radio button
  await page.locator('[data-testid="trend-panel-window-7"]').click();

  // URL 同步 ?window=7
  await page.waitForURL(/window=7/);
  expect(new URL(page.url()).searchParams.get('window')).toBe('7');

  // 7 个点
  await expect(page.locator('[data-testid="trend-panel-sparkline-task"] polyline')).toHaveAttribute(
    'points',
    /^\d+(\.\d+)?,\d+(\.\d+)?( \d+(\.\d+)?,\d+(\.\d+)?){6}$/,
  );
  const pointsAttr = await page
    .locator('[data-testid="trend-panel-sparkline-task"] polyline')
    .getAttribute('points');
  expect(pointsAttr!.trim().split(/\s+/)).toHaveLength(7);

  // 调用历史：默认 14 + 切 7 = 2 次（按 window 计数）
  expect(trend.calls).toEqual([{ window: 14 }, { window: 7 }]);
});

// ---------------------------------------------------------------------------
// 3. URL 直接 ?window=30 → 路由 query 解析命中 30
// ---------------------------------------------------------------------------

test('直接访问 /ai-analysis?window=30 → 默认拉 /trend?window=30', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page);
  const trend = await mockTrendSuccess(page, { window: 30 });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis?window=30');

  // 30 个点
  await expect(page.locator('[data-testid="trend-panel-window-30"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-sparkline-task"] polyline')).toBeVisible();
  const pointsAttr = await page
    .locator('[data-testid="trend-panel-sparkline-task"] polyline')
    .getAttribute('points');
  expect(pointsAttr!.trim().split(/\s+/)).toHaveLength(30);

  expect(trend.calls).toEqual([{ window: 30 }]);
});

// ---------------------------------------------------------------------------
// 4. diet 永久占位 → TriStateEmpty
// ---------------------------------------------------------------------------

test('diet 槽位空 points → 渲染 TriStateEmpty「暂无数据」', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page);
  await mockTrendSuccess(page, { window: 14 });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis');

  // diet sparkline 整体存在，但 chart 部分走 empty
  const diet = page.locator('[data-testid="trend-panel-sparkline-diet"]');
  await expect(diet).toBeVisible();
  // diet 没有 points → SparklineChart 渲染 TriStateEmpty，polyline 不渲染
  await expect(diet.locator('polyline')).toHaveCount(0);
  await expect(diet.locator('[data-testid="trend-panel-sparkline-diet-empty"]')).toBeVisible();

  // task / plan / expense 都有 polyline
  await expect(page.locator('[data-testid="trend-panel-sparkline-task"] polyline')).toHaveCount(1);
  await expect(page.locator('[data-testid="trend-panel-sparkline-plan"] polyline')).toHaveCount(1);
  await expect(page.locator('[data-testid="trend-panel-sparkline-expense"] polyline')).toHaveCount(1);
});

// ---------------------------------------------------------------------------
// 5. /trend 503 → TriStateError + retry 重新调用
// ---------------------------------------------------------------------------

test('AI 趋势 /trend 返回 1501 → TriStateError 渲染 + retry 重新调用', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupAiDefaults(page);
  let callCount = 0;
  await page.route('**/api/v1/ai/insight/trend**', async (route) => {
    callCount += 1;
    if (callCount === 1) {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 1501, message: 'AI 趋势不可用' }),
      });
      return;
    }
    // 第 2 次：成功
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data: trendResponse(14) }),
    });
  });

  await loginAs(page, ALICE);
  await page.goto('/ai-analysis');

  // 第 1 次失败 → TriStateError
  await expect(page.locator('[data-testid="trend-panel-error"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-error-retry"]')).toBeVisible();
  // grid 此时不渲染
  await expect(page.locator('[data-testid="trend-panel-grid"]')).toHaveCount(0);

  // retry
  await page.locator('[data-testid="trend-panel-error-retry"]').click();

  // 第 2 次成功 → grid 渲染
  await expect(page.locator('[data-testid="trend-panel-grid"]')).toBeVisible();
  await expect(page.locator('[data-testid="trend-panel-sparkline-task"]')).toBeVisible();
  // error 消失
  await expect(page.locator('[data-testid="trend-panel-error"]')).toHaveCount(0);

  expect(callCount).toBe(2);
});