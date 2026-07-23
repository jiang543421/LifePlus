import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { createPinia, setActivePinia } from 'pinia';
import http, { ApiError } from './http';
import { aiApi } from './ai';

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { fullPath: '/' } },
  },
}));

let mock: MockAdapter;
let axiosMock: MockAdapter;

const sampleInsight = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '¥', trend: 'FLAT', deltaText: '与上周持平' },
    { key: 'planDensity', label: '日程', value: '3', unit: '项', trend: 'NONE', deltaText: '今日 3 项' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
};

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

describe('aiApi.today', () => {
  it('GET /ai/insight/today 并解包 envelope 返回 AiInsightResponse', async () => {
    mock.onGet('/ai/insight/today').reply(200, { code: 0, data: sampleInsight });

    const result = await aiApi.today();

    expect(result).toEqual(sampleInsight);
    expect(result.headline).toBe('今日任务完成率 80%；本周消费 ¥420。');
    expect(result.chips).toHaveLength(3);
    expect(result.freshnessSeconds).toBe(12);
  });

  it('后端返 1501（AI_DEGRADED）抛 ApiError(1501)', async () => {
    mock.onGet('/ai/insight/today').reply(200, {
      code: 1501,
      message: 'AI 洞察数据暂时不可用，请稍后重试',
    });

    await expect(aiApi.today()).rejects.toBeInstanceOf(ApiError);
    await expect(aiApi.today()).rejects.toMatchObject({
      name: 'ApiError',
      code: 1501,
      message: 'AI 洞察数据暂时不可用，请稍后重试',
    });
  });

  it('后端返 1006（限流）抛 ApiError(1006)', async () => {
    mock.onGet('/ai/insight/today').reply(200, {
      code: 1006,
      message: 'AI 洞察请求过于频繁，请稍后重试',
    });

    await expect(aiApi.today()).rejects.toMatchObject({ code: 1006 });
  });

  it('网络错误抛 ApiError(-1)', async () => {
    mock.onGet('/ai/insight/today').networkError();

    await expect(aiApi.today()).rejects.toBeInstanceOf(ApiError);
    await expect(aiApi.today()).rejects.toMatchObject({ code: -1 });
  });
});

describe('aiApi.refresh', () => {
  it('POST /ai/insight/refresh 返回解包后的 AiInsightResponse', async () => {
    mock.onPost('/ai/insight/refresh').reply(200, { code: 0, data: sampleInsight });

    const result = await aiApi.refresh();

    expect(result).toEqual(sampleInsight);
  });

  it('后端返 1501 抛 ApiError(1501)', async () => {
    mock.onPost('/ai/insight/refresh').reply(200, { code: 1501, message: '降级' });

    await expect(aiApi.refresh()).rejects.toMatchObject({ code: 1501 });
  });

  it('后端返 1006 抛 ApiError(1006)', async () => {
    mock.onPost('/ai/insight/refresh').reply(200, { code: 1006, message: '限流' });

    await expect(aiApi.refresh()).rejects.toMatchObject({ code: 1006 });
  });
});

describe('aiApi.analysis', () => {
  it('GET /ai/insight/analysis 返回解包后的 AiInsightResponse（含 v2.1 字段）', async () => {
    const v21Insight = {
      ...sampleInsight,
      source: 'llm',
      advice: '继续保持节奏',
      highlight: '昨天完成了 5 个任务',
      mood: 'POSITIVE',
      llmMeta: { promptTokens: 120, responseTokens: 80, latencyMs: 850 },
    };
    mock.onGet('/ai/insight/analysis').reply(200, { code: 0, data: v21Insight });

    const result = await aiApi.analysis();

    expect(result).toEqual(v21Insight);
    expect(result.source).toBe('llm');
    expect(result.advice).toBe('继续保持节奏');
    expect(result.mood).toBe('POSITIVE');
    expect(result.llmMeta?.promptTokens).toBe(120);
  });

  it('后端返 1501 抛 ApiError(1501)', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 1501, message: '降级' });

    await expect(aiApi.analysis()).rejects.toMatchObject({ code: 1501 });
  });

  it('后端返 1006 抛 ApiError(1006)', async () => {
    mock.onGet('/ai/insight/analysis').reply(200, { code: 1006, message: '限流' });

    await expect(aiApi.analysis()).rejects.toMatchObject({ code: 1006 });
  });
});
