import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { createPinia, setActivePinia } from 'pinia';
import http from '@/api/http';
import { useAiInsightStore } from './aiInsight';
import type { AiInsightResponse } from '@/types';

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { fullPath: '/' } },
  },
}));

let mock: MockAdapter;
let axiosMock: MockAdapter;

// 把字面量断言成 AiInsightResponse，让 sampleInsight 的 trend 被收窄到
// 'FLAT' | 'DOWN' | 'UP' | 'NONE' 联合（而不是宽泛的 string）。
const sampleInsight = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '¥', trend: 'FLAT', deltaText: '与上周持平' },
    { key: 'planDensity', label: '日程', value: '3', unit: '项', trend: 'NONE', deltaText: '今日 3 项' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
} as AiInsightResponse;

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

describe('useAiInsightStore.loadToday', () => {
  it('成功：写 insight + 清错误', async () => {
    mock.onGet('/ai/insight/today').reply(200, { code: 0, data: sampleInsight });

    const store = useAiInsightStore();
    const r = await store.loadToday();

    expect(r).toEqual(sampleInsight);
    expect(store.insight).toEqual(sampleInsight);
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
  });

  it('失败：保留旧 insight + 写 errorCode', async () => {
    const store = useAiInsightStore();
    store.insight = { ...sampleInsight, headline: 'stale' } as typeof sampleInsight;

    mock.onGet('/ai/insight/today').reply(200, { code: 1501, message: '降级' });

    const r = await store.loadToday();

    expect(r).toBeNull();
    expect(store.insight?.headline).toBe('stale'); // 保留
    expect(store.errorCode).toBe(1501);
    expect(store.error).toBe('降级');
  });
});

describe('useAiInsightStore.loadAnalysis (freshness-aware)', () => {
  it('缓存新鲜时直接复用，不再发请求', async () => {
    const store = useAiInsightStore();
    // freshnessSeconds=12 → 新鲜 (< 6h)
    store.insight = sampleInsight as typeof sampleInsight;

    const r = await store.loadAnalysis();

    expect(r).toEqual(sampleInsight);
    expect(mock.history.get.length).toBe(0); // 未发请求
  });

  it('缓存不存在时拉 /analysis', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: sampleInsight });

    const store = useAiInsightStore();
    const r = await store.loadAnalysis();

    expect(r).toEqual(sampleInsight);
    expect(mock.history.get).toHaveLength(1);
    expect(mock.history.get[0].url).toBe('/ai/insight/analysis');
  });

  it('缓存过期时（freshnessSeconds >= 6h）重新拉', async () => {
    const store = useAiInsightStore();
    // 模拟缓存条目"足够旧"
    store.insight = { ...sampleInsight, freshnessSeconds: 6 * 3600 } as typeof sampleInsight;

    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: sampleInsight });

    await store.loadAnalysis();

    expect(mock.history.get).toHaveLength(1);
  });

  it('失败时 errorCode=1501', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 1501, message: '降级' });

    const store = useAiInsightStore();
    const r = await store.loadAnalysis();

    expect(r).toBeNull();
    expect(store.errorCode).toBe(1501);
  });
});

describe('useAiInsightStore.refresh', () => {
  it('POST /refresh 成功后 insight 覆写', async () => {
    const updated = { ...sampleInsight, headline: 'after refresh' };
    mock.onPost('/ai/insight/refresh').reply(200, { code: 0, data: updated });

    const store = useAiInsightStore();
    const r = await store.refresh();

    expect(r?.headline).toBe('after refresh');
    expect(store.insight?.headline).toBe('after refresh');
  });

  it('失败保留旧 insight，写 errorCode=1006', async () => {
    const store = useAiInsightStore();
    store.insight = { ...sampleInsight, headline: 'stale' } as AiInsightResponse;

    mock.onPost('/ai/insight/refresh').reply(200, { code: 1006, message: '限流' });

    const r = await store.refresh();

    expect(r).toBeNull();
    expect(store.insight?.headline).toBe('stale');
    expect(store.errorCode).toBe(1006);
  });
});

describe('useAiInsightStore.clear', () => {
  it('清空 insight + error + errorCode', () => {
    const store = useAiInsightStore();
    store.insight = sampleInsight as AiInsightResponse;
    store.error = 'x';
    store.errorCode = 1501;

    store.clear();

    expect(store.insight).toBeNull();
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
  });
});