import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import MockAdapter from 'axios-mock-adapter';
import ElementPlus from 'element-plus';
import http from '@/api/http';
import axios from 'axios';
import AiAnalysisView from '@/views/AiAnalysisView.vue';
import type { AiInsightResponse } from '@/types';
import type { AiTrendResponse } from '@/types';
import { useAuthStore } from '@/stores/auth';

// ---- mocks ----
const routerPush = vi.fn().mockResolvedValue(undefined);
vi.mock('@/router', () => ({
  default: {
    push: (...args: unknown[]) => routerPush(...args),
    currentRoute: { value: { fullPath: '/ai-analysis' } },
  },
}));
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router');
  return {
    ...actual,
    useRouter: () => ({
      push: (...args: unknown[]) => routerPush(...args),
      replace: vi.fn().mockResolvedValue(undefined),
      currentRoute: { value: { query: {}, fullPath: '/ai-analysis' } },
    }),
    useRoute: () => ({ query: {}, fullPath: '/ai-analysis' }),
  };
});

const sampleInsight: AiInsightResponse = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '', trend: 'DOWN', deltaText: '较上周 -¥40' },
    { key: 'planDensity', label: '日程', value: '3', unit: '项', trend: 'NONE', deltaText: '今日 3 项' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
  source: 'llm',
  advice: '保持节奏',
  highlight: '本周亮点',
  mood: 'POSITIVE',
};

const sampleTrend = (windowDays: number): AiTrendResponse => ({
  window: windowDays,
  from: '2026-07-10',
  to: '2026-07-23',
  metrics: ['task', 'plan', 'expense', 'diet'],
  series: {
    task: { key: 'task', label: '任务完成率', unit: '%', points: [{ date: '2026-07-23', value: 0.8, label: '80%' }] },
    plan: { key: 'plan', label: '日程事件', unit: '项', points: [{ date: '2026-07-23', value: 3, label: '3项' }] },
    expense: { key: 'expense', label: '消费金额', unit: '¥', points: [{ date: '2026-07-23', value: 100, label: '¥100' }] },
    diet: { key: 'diet', label: '饮食', unit: '', points: [] },
  },
  generatedAt: '2026-07-23T08:00:00Z',
});

let mock: MockAdapter;
let axiosMock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(http);
  axiosMock = new MockAdapter(axios);
  setActivePinia(createPinia());
  const auth = useAuthStore();
  auth.setUser({ id: 1, email: 'lp@ex.com', nickname: 'lp' });
  localStorage.clear();
});

afterEach(() => {
  mock.restore();
  axiosMock.restore();
});

function mountView() {
  return mount(AiAnalysisView, {
    global: { plugins: [ElementPlus, createPinia()] },
  });
}

describe('AiAnalysisView — 趋势段集成', () => {
  it('insight 加载完成后，TrendPanel 嵌入并自动拉数据', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: sampleInsight });
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    const w = mountView();
    await flushPromises();
    await flushPromises();

    // insight 4 段已渲染
    expect(w.find('[data-testid="ai-analysis-headline"]').exists()).toBe(true);
    // 趋势段存在
    const trendSection = w.find('[data-testid="ai-analysis-trend"]');
    expect(trendSection.exists()).toBe(true);
    expect(trendSection.find('h3').text()).toBe('趋势');
    // TrendPanel 内部 grid 已渲染（trend API 已 mock 200）
    expect(w.find('[data-testid="trend-panel"]').exists()).toBe(true);
    expect(w.find('[data-testid="trend-panel-grid"]').exists()).toBe(true);
  });

  it('insight=null 时不渲染趋势段（empty state 接管）', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 1501, message: '降级' });

    const w = mountView();
    await flushPromises();
    await flushPromises();

    // TriStateEmpty 接管
    expect(w.find('[data-testid="ai-analysis-empty"]').exists()).toBe(true);
    // 趋势段不渲染
    expect(w.find('[data-testid="ai-analysis-trend"]').exists()).toBe(false);
  });

  it('trend 段位置在 chips 段之后（DOM 顺序）', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: sampleInsight });
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    const w = mountView();
    await flushPromises();
    await flushPromises();

    const sections = w.findAll('section.section');
    const sectionIds = sections.map((s) => s.attributes('data-testid'));
    // chips 必须在 trend 之前
    const chipsIdx = sectionIds.indexOf('ai-analysis-chips');
    const trendIdx = sectionIds.indexOf('ai-analysis-trend');
    expect(chipsIdx).toBeGreaterThanOrEqual(0);
    expect(trendIdx).toBeGreaterThanOrEqual(0);
    expect(trendIdx).toBeGreaterThan(chipsIdx);
  });

  it('点击刷新按钮不影响趋势段（两个 store 独立）', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: sampleInsight });
    mock.onGet('/ai/insight/refresh').reply(200, { code: 0, data: sampleInsight });
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    const w = mountView();
    await flushPromises();
    await flushPromises();

    expect(w.find('[data-testid="trend-panel-grid"]').exists()).toBe(true);
    const trendCallsBefore = mock.history.get.filter((r) => r.url === '/ai/insight/trend').length;

    // 点刷新
    await w.find('[data-testid="ai-analysis-refresh"]').trigger('click');
    await flushPromises();
    await flushPromises();

    // insight 仍渲染（4 段还在）
    expect(w.find('[data-testid="ai-analysis-headline"]').exists()).toBe(true);
    // trend 段仍渲染（grid 存在，不退化为 skeleton）
    expect(w.find('[data-testid="trend-panel-grid"]').exists()).toBe(true);
    // refresh 不会重复拉 trend（两个端点独立）
    const trendCallsAfter = mock.history.get.filter((r) => r.url === '/ai/insight/trend').length;
    expect(trendCallsAfter).toBe(trendCallsBefore);
  });

  it('trend API 失败不影响 insight 渲染（独立错误隔离）', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: sampleInsight });
    mock.onGet('/ai/insight/trend').reply(200, { code: 1501, message: '降级' });

    const w = mountView();
    await flushPromises();
    await flushPromises();

    // insight 4 段正常渲染
    expect(w.find('[data-testid="ai-analysis-headline"]').exists()).toBe(true);
    expect(w.find('[data-testid="ai-analysis-advice"]').exists()).toBe(true);
    // 趋势段仍在 DOM，但内部 error 状态接管
    expect(w.find('[data-testid="ai-analysis-trend"]').exists()).toBe(true);
    expect(w.find('[data-testid="trend-panel"]').exists()).toBe(true);
    expect(w.find('[data-testid="trend-panel-error"]').exists()).toBe(true);
    expect(w.find('[data-testid="trend-panel-grid"]').exists()).toBe(false);
  });
});