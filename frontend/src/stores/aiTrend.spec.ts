import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { createPinia, setActivePinia } from 'pinia';
import http from '@/api/http';
import { useAiTrendStore } from './aiTrend';
import type { AiTrendResponse } from '@/types';
import type { TrendWindowDays } from '@/api/ai-trend';

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { fullPath: '/' } },
  },
}));

let mock: MockAdapter;
let axiosMock: MockAdapter;

/** 构造指定窗口的 sample trend response。 */
const sampleTrend = (windowDays: TrendWindowDays): AiTrendResponse => ({
  window: windowDays,
  from: `2026-07-${String(23 - windowDays + 1).padStart(2, '0')}`,
  to: '2026-07-23',
  metrics: ['task', 'plan', 'expense', 'diet'],
  series: {
    task: {
      key: 'task',
      label: '任务完成率',
      unit: '%',
      points: [{ date: '2026-07-23', value: 0.8, label: '80%' }],
    },
    plan: {
      key: 'plan',
      label: '日程事件',
      unit: '项',
      points: [{ date: '2026-07-23', value: 3, label: '3项' }],
    },
    expense: {
      key: 'expense',
      label: '消费金额',
      unit: '¥',
      points: [{ date: '2026-07-23', value: 100, label: '¥100.00' }],
    },
    diet: { key: 'diet', label: '饮食', unit: '', points: [] },
  },
  generatedAt: '2026-07-23T08:00:00Z',
});

beforeEach(() => {
  mock = new MockAdapter(http);
  axiosMock = new MockAdapter(axios);
  setActivePinia(createPinia());
  localStorage.clear();
});

afterEach(() => {
  mock.restore();
  axiosMock.restore();
});

describe('useAiTrendStore.load', () => {
  it('成功 load(14)：写 trend[14] + 清 loading/error', async () => {
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    const store = useAiTrendStore();
    const r = await store.load(14);

    expect(r).not.toBeNull();
    expect(r?.window).toBe(14);
    expect(store.trend[14]).toEqual(r);
    expect(store.trend[7]).toBeNull(); // 其它槽位未触动
    expect(store.trend[30]).toBeNull();
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
  });

  it('失败（ApiError 1006 限流）：保留旧 trend + 写 errorCode', async () => {
    const store = useAiTrendStore();
    const stale = sampleTrend(14);
    store.trend[14] = stale;

    mock.onGet('/ai/insight/trend').reply(200, { code: 1006, message: 'AI 限流' });

    const r = await store.load(14);

    expect(r).toBeNull();
    expect(store.trend[14]).toEqual(stale); // 保留旧值
    expect(store.error).toBe('AI 限流');
    expect(store.errorCode).toBe(1006);
  });

  it('按窗口分桶：load(7) 不覆盖 trend[14]', async () => {
    mock.onGet('/ai/insight/trend').reply((config) => {
      const w = Number(config.params?.window ?? 14);
      return [200, { code: 0, data: sampleTrend(w as TrendWindowDays) }];
    });

    const store = useAiTrendStore();
    await store.load(14);
    const trend14 = store.trend[14];
    expect(trend14?.window).toBe(14);

    await store.load(7);

    expect(store.trend[14]).toEqual(trend14); // 不被覆盖
    expect(store.trend[7]?.window).toBe(7);
    expect(store.trend[30]).toBeNull();
  });

  it('失败（ApiError）后 retry 成功：覆盖 error + errorCode，重新写 trend', async () => {
    mock.onGet('/ai/insight/trend').replyOnce(200, { code: 1501, message: '降级' });

    const store = useAiTrendStore();
    await store.load(14);

    expect(store.errorCode).toBe(1501);
    expect(store.trend[14]).toBeNull();

    mock.reset();
    mock.onGet('/ai/insight/trend').reply(200, { code: 0, data: sampleTrend(14) });

    const r = await store.load(14);

    expect(r?.window).toBe(14);
    expect(store.trend[14]?.window).toBe(14);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
  });

  it('网络错：errorCode=-1（拦截器包装），error 写 message', async () => {
    mock.onGet('/ai/insight/trend').networkError();

    const store = useAiTrendStore();
    const r = await store.load(14);

    expect(r).toBeNull();
    expect(store.trend[14]).toBeNull();
    // http.ts 拦截器把网络错包装成 ApiError(-1, 'network error')
    expect(store.errorCode).toBe(-1);
    expect(store.error?.toLowerCase()).toContain('network');
  });

  it('loading 标志：拉数据期间为 true，完成后 false', async () => {
    let resolveHttp!: (v: [number, unknown]) => void;
    const replyPromise = new Promise<[number, unknown]>((resolve) => {
      resolveHttp = resolve;
    });
    mock.onGet('/ai/insight/trend').reply(() => replyPromise);

    const store = useAiTrendStore();
    const p = store.load(14);

    // 等待 mock adapter 同步调用 reply()，resolveHttp 被赋值
    await new Promise((r) => setTimeout(r, 0));
    expect(store.loading).toBe(true);

    resolveHttp([200, { code: 0, data: sampleTrend(14) }]);
    await p;

    expect(store.loading).toBe(false);
  });
});

describe('useAiTrendStore.clear', () => {
  it('清空所有槽位 + error / errorCode', async () => {
    mock.onGet('/ai/insight/trend').reply((config) => {
      const w = Number(config.params?.window ?? 14);
      return [200, { code: 0, data: sampleTrend(w as TrendWindowDays) }];
    });

    const store = useAiTrendStore();
    await store.load(7);
    await store.load(14);
    expect(store.trend[7]).not.toBeNull();
    expect(store.trend[14]).not.toBeNull();

    store.clear();

    expect(store.trend).toEqual({ 7: null, 14: null, 30: null });
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
  });
});