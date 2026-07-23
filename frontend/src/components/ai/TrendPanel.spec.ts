import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import MockAdapter from 'axios-mock-adapter';
import axios from 'axios';
import http from '@/api/http';
import TrendPanel from '@/components/ai/TrendPanel.vue';
import type { AiTrendResponse } from '@/types';

/** 测试用 router（memory history，避免污染 URL 状态）。 */
function buildTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div/>' } },
      { path: '/ai-analysis', name: 'ai-analysis', component: { template: '<div/>' } },
    ],
  });
}

const sampleTrend = (windowDays: number): AiTrendResponse => ({
  window: windowDays,
  from: `2026-07-${String(23 - windowDays + 1).padStart(2, '0')}`,
  to: '2026-07-23',
  metrics: ['task', 'plan', 'expense', 'diet'],
  series: {
    task: {
      key: 'task',
      label: '任务完成率',
      unit: '%',
      points: [
        { date: '2026-07-22', value: 0.5, label: '50%' },
        { date: '2026-07-23', value: 0.8, label: '80%' },
      ],
    },
    plan: {
      key: 'plan',
      label: '日程事件',
      unit: '项',
      points: [
        { date: '2026-07-22', value: 2, label: '2项' },
        { date: '2026-07-23', value: 3, label: '3项' },
      ],
    },
    expense: {
      key: 'expense',
      label: '消费金额',
      unit: '¥',
      points: [
        { date: '2026-07-22', value: 50, label: '¥50.00' },
        { date: '2026-07-23', value: 100, label: '¥100.00' },
      ],
    },
    diet: {
      key: 'diet',
      label: '饮食（永久占位）',
      unit: '',
      points: [],
    },
  },
  generatedAt: '2026-07-23T00:00:00Z',
});

let mock: MockAdapter;
let axiosMock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(http);
  axiosMock = new MockAdapter(axios);
  setActivePinia(createPinia());
});

afterEach(() => {
  mock.restore();
  axiosMock.restore();
});

describe('TrendPanel', () => {
  it('renders 4 sparkline charts after successful fetch', async () => {
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    const wrapper = mount(TrendPanel, {
      global: { plugins: [buildTestRouter()] },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="trend-panel-grid"]').exists()).toBe(true);
    // 用 class 选择器，避免命中 SparklineChart 内 TriStateEmpty 的 `${testId}-empty` 子节点
    const sparklines = wrapper.findAll('.sparkline-chart');
    expect(sparklines).toHaveLength(4);
    expect(sparklines[0].attributes('data-testid')).toBe('trend-panel-sparkline-task');
    expect(sparklines[1].attributes('data-testid')).toBe('trend-panel-sparkline-plan');
    expect(sparklines[2].attributes('data-testid')).toBe('trend-panel-sparkline-expense');
    expect(sparklines[3].attributes('data-testid')).toBe('trend-panel-sparkline-diet');
  });

  it('sends GET /ai/insight/trend on mount with default window=14', async () => {
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    mount(TrendPanel, { global: { plugins: [buildTestRouter()] } });
    await flushPromises();

    expect(mock.history.get).toHaveLength(1);
    expect(mock.history.get[0].url).toBe('/ai/insight/trend');
    expect(mock.history.get[0].params?.window).toBe(14);
  });

  it('shows loading skeleton before data arrives', async () => {
    mock.onGet('/ai/insight/trend').reply(() =>
      new Promise((resolve) =>
        setTimeout(() => resolve([200, { code: 0, data: sampleTrend(14) }]), 200),
      ),
    );

    const wrapper = mount(TrendPanel, {
      global: { plugins: [buildTestRouter()] },
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="trend-panel-loading"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="trend-panel-grid"]').exists()).toBe(false);
  });

  it('shows TriStateError + retry button when fetch fails', async () => {
    mock.onGet('/ai/insight/trend').reply(200, { code: 1501, message: '降级' });

    const wrapper = mount(TrendPanel, {
      global: { plugins: [buildTestRouter()] },
    });
    await flushPromises();

    expect(wrapper.find('[data-testid="trend-panel-error"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="trend-panel-error-retry"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="trend-panel-grid"]').exists()).toBe(false);
  });

  it('clicking retry re-fetches the trend', async () => {
    mock.onGet('/ai/insight/trend').reply(200, { code: 1501, message: '降级' });

    const wrapper = mount(TrendPanel, {
      global: { plugins: [buildTestRouter()] },
    });
    await flushPromises();
    expect(wrapper.find('[data-testid="trend-panel-error"]').exists()).toBe(true);

    mock.reset();
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });
    await wrapper.find('[data-testid="trend-panel-error-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="trend-panel-grid"]').exists()).toBe(true);
  });

  it('switches window on radio change and refetches with new window', async () => {
    mock.onGet('/ai/insight/trend').reply((config) => {
      const w = Number(config.params?.window ?? 14);
      return [200, { code: 0, data: sampleTrend(w) }];
    });

    const wrapper = mount(TrendPanel, {
      global: { plugins: [buildTestRouter()] },
    });
    await flushPromises();

    // ElRadioButton 内层 input[type=radio] 是 v-model 触发源
    const radios = wrapper.findAll('input[type="radio"]');
    expect(radios.length).toBeGreaterThanOrEqual(3);
    // 顺序与 WINDOW_OPTIONS 一致：7 / 14 / 30 → 第一个是 7
    await radios[0].setValue();
    await flushPromises();

    expect(mock.history.get.length).toBeGreaterThanOrEqual(2);
    const lastReq = mock.history.get[mock.history.get.length - 1];
    expect(lastReq.params?.window).toBe(7);
  });

  it('syncs URL ?window= via router.replace on switch', async () => {
    const router = buildTestRouter();
    await router.push('/ai-analysis');

    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(30) });

    const wrapper = mount(TrendPanel, {
      global: { plugins: [router] },
    });
    await flushPromises();

    // 切到 30 天（第 3 个 radio）
    const radios = wrapper.findAll('input[type="radio"]');
    await radios[2].setValue();
    await flushPromises();

    expect(router.currentRoute.value.query.window).toBe('30');
  });
});