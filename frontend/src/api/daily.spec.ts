import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { createPinia, setActivePinia } from 'pinia';
import http from './http';
import { dailyApi } from './daily';
import type { DailyReportPayload, WeeklyReportPayload } from '@/types';

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { fullPath: '/' } },
  },
}));

let mock: MockAdapter;

const sampleDaily: DailyReportPayload = {
  date: '2026-07-23',
  task: {
    completedCount: 3,
    totalCount: 5,
    completionRate: 0.6,
    statusDistribution: { TODO: 2, DONE: 3, CANCELLED: 0 },
    priorityDistribution: { NONE: 1, LOW: 1, MEDIUM: 2, HIGH: 1 },
  },
  plan: {
    eventCount: 2,
    totalMinutes: 90,
    categoryDistribution: {},
    busiestHour: 10,
  },
  expense: {
    totalAmount: 128.5,
    count: 3,
    categoryBreakdown: {
      MEAL: 78.5,
      SHOPPING: 0,
      TRANSPORT: 50,
      SUBSCRIPTION: 0,
      OTHER: 0,
    },
    topCategories: [
      { code: 'MEAL', amount: 78.5 },
      { code: 'TRANSPORT', amount: 50 },
    ],
  },
  diet: {
    enabled: false,
    value: null,
    reason: '饮食模块暂未启用（v1.2.4+ 启用）',
  },
};

const sampleWeek: WeeklyReportPayload = {
  isoWeek: '2026-W30',
  weekStart: '2026-07-20',
  weekEnd: '2026-07-26',
  comparison: {
    taskCompletion: { current: 0.65, previous: 0.5, delta: 0.15 },
    planEvents: { current: 8, previous: 5, delta: 3 },
    expenseAmount: { current: 1200, previous: 820, delta: 380 },
  },
};

beforeEach(() => {
  mock = new MockAdapter(http);
  setActivePinia(createPinia());
  localStorage.clear();
});

afterEach(() => {
  mock.restore();
});

describe('dailyApi.daily', () => {
  it('GET /daily 不带 date 参数时 → 解包 envelope 返回 DailyReportPayload', async () => {
    mock.onGet('/daily').reply(200, { code: 0, data: sampleDaily });

    const result = await dailyApi.daily();

    expect(result).toEqual(sampleDaily);
    expect(result.date).toBe('2026-07-23');
    expect(result.task.completedCount).toBe(3);
    expect(result.diet.enabled).toBe(false);
  });

  it('GET /daily?date=YYYY-MM-DD 时透传 date 参数', async () => {
    let capturedParams: Record<string, unknown> | undefined;
    mock.onGet('/daily').reply((config) => {
      capturedParams = config.params as Record<string, unknown>;
      return [200, { code: 0, data: sampleDaily }];
    });

    await dailyApi.daily('2026-07-15');

    expect(capturedParams?.date).toBe('2026-07-15');
  });

  it('GET /daily 不带 date 时 query 不带 date 字段', async () => {
    let capturedParams: Record<string, unknown> | undefined;
    mock.onGet('/daily').reply((config) => {
      capturedParams = config.params as Record<string, unknown>;
      return [200, { code: 0, data: sampleDaily }];
    });

    await dailyApi.daily();

    expect(capturedParams?.date).toBeUndefined();
  });

  it('后端返 1001（日期超出 30 天窗口）抛 ApiError(1001)', async () => {
    mock.onGet('/daily').reply(200, {
      code: 1001,
      message: 'date out of range: 2026-06-01 earlier than 2026-06-23',
    });

    await expect(dailyApi.daily('2026-06-01')).rejects.toMatchObject({ code: 1001 });
  });

  it('后端返 1002（未登录）抛 ApiError(1002)', async () => {
    mock.onGet('/daily').reply(200, { code: 1002, message: 'unauthorized' });

    await expect(dailyApi.daily()).rejects.toMatchObject({ code: 1002 });
  });
});

describe('dailyApi.week', () => {
  it('GET /daily/week 不带 date 参数时 → 解包 envelope 返回 WeeklyReportPayload', async () => {
    mock.onGet('/daily/week').reply(200, { code: 0, data: sampleWeek });

    const result = await dailyApi.week();

    expect(result).toEqual(sampleWeek);
    expect(result.isoWeek).toBe('2026-W30');
    expect(result.comparison.taskCompletion.current).toBe(0.65);
  });

  it('GET /daily/week?date=YYYY-MM-DD 时透传 date 参数', async () => {
    let capturedParams: Record<string, unknown> | undefined;
    mock.onGet('/daily/week').reply((config) => {
      capturedParams = config.params as Record<string, unknown>;
      return [200, { code: 0, data: sampleWeek }];
    });

    await dailyApi.week('2026-07-23');

    expect(capturedParams?.date).toBe('2026-07-23');
  });

  it('previous=0 时 delta=null 透传（不丢失 null）', async () => {
    const weekPrevAllZero: WeeklyReportPayload = {
      ...sampleWeek,
      comparison: {
        taskCompletion: { current: 0.5, previous: 0, delta: null },
        planEvents: { current: 5, previous: 0, delta: null },
        expenseAmount: { current: 800, previous: 0, delta: null },
      },
    };
    mock.onGet('/daily/week').reply(200, { code: 0, data: weekPrevAllZero });

    const result = await dailyApi.week();

    expect(result.comparison.taskCompletion.delta).toBeNull();
    expect(result.comparison.planEvents.delta).toBeNull();
    expect(result.comparison.expenseAmount.delta).toBeNull();
  });

  it('后端返 1001 抛 ApiError(1001)', async () => {
    mock.onGet('/daily/week').reply(200, { code: 1001, message: 'date out of range' });

    await expect(dailyApi.week()).rejects.toMatchObject({ code: 1001 });
  });
});
