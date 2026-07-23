import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useDailyStore } from './daily';
import { ApiError } from '@/api/http';
import type {
  DailyReportPayload,
  WeeklyComparison,
  WeeklyReportPayload,
} from '@/types';

vi.mock('@/api/daily', () => ({
  dailyApi: {
    daily: vi.fn(),
    week: vi.fn(),
  },
}));

import { dailyApi } from '@/api/daily';

const mockDaily = (overrides: Partial<DailyReportPayload> = {}): DailyReportPayload => ({
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
  ...overrides,
});

const mockWeek = (overrides: Partial<WeeklyReportPayload> = {}): WeeklyReportPayload => ({
  isoWeek: '2026-W30',
  weekStart: '2026-07-20',
  weekEnd: '2026-07-26',
  comparison: {
    taskCompletion: { current: 0.65, previous: 0.5, delta: 0.15 },
    planEvents: { current: 8, previous: 5, delta: 3 },
    expenseAmount: { current: 1200, previous: 820, delta: 380 },
  } as WeeklyComparison,
  ...overrides,
});

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(dailyApi.daily).mockReset();
  vi.mocked(dailyApi.week).mockReset();
});

// ---------- state default ----------

describe('useDailyStore default state', () => {
  it('initial state is empty + not loading + no error', () => {
    const store = useDailyStore();

    expect(store.daily).toBeNull();
    expect(store.week).toBeNull();
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(store.filter).toEqual({ date: '' });
  });
});

// ---------- fetchDaily ----------

describe('useDailyStore.fetchDaily', () => {
  it('成功：写入 daily + 清错误 + 同步 filter.date', async () => {
    vi.mocked(dailyApi.daily).mockResolvedValue(mockDaily());

    const store = useDailyStore();
    const r = await store.fetchDaily('2026-07-23');

    expect(r).toEqual(mockDaily());
    expect(store.daily).toEqual(mockDaily());
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(store.filter.date).toBe('2026-07-23');
    expect(dailyApi.daily).toHaveBeenCalledWith('2026-07-23');
  });

  it('失败（1001 日期越界）：保留旧 daily + 写 errorCode + loading=false', async () => {
    const store = useDailyStore();
    store.daily = mockDaily({ date: '2026-07-22' });

    vi.mocked(dailyApi.daily).mockRejectedValue(
      new ApiError(1001, 'date out of range'),
    );

    const r = await store.fetchDaily('2026-06-01');

    expect(r).toBeNull();
    expect(store.daily?.date).toBe('2026-07-22'); // 保留旧值
    expect(store.errorCode).toBe(1001);
    expect(store.error).toBe('date out of range');
    expect(store.loading).toBe(false);
  });

  it('失败（非 ApiError）：errorCode=null + error 兜底文案', async () => {
    vi.mocked(dailyApi.daily).mockRejectedValue(new Error('boom'));

    const store = useDailyStore();
    const r = await store.fetchDaily();

    expect(r).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(store.error).toBe('fetch daily failed');
  });
});

// ---------- fetchWeek ----------

describe('useDailyStore.fetchWeek', () => {
  it('成功：写入 week + 清错误', async () => {
    vi.mocked(dailyApi.week).mockResolvedValue(mockWeek());

    const store = useDailyStore();
    const r = await store.fetchWeek('2026-07-23');

    expect(r).toEqual(mockWeek());
    expect(store.week).toEqual(mockWeek());
    expect(store.error).toBeNull();
    expect(store.errorCode).toBeNull();
    expect(dailyApi.week).toHaveBeenCalledWith('2026-07-23');
  });

  it('失败：errorCode 透传 + loading=false', async () => {
    vi.mocked(dailyApi.week).mockRejectedValue(
      new ApiError(1002, 'unauthorized'),
    );

    const store = useDailyStore();
    const r = await store.fetchWeek();

    expect(r).toBeNull();
    expect(store.errorCode).toBe(1002);
    expect(store.loading).toBe(false);
  });
});

// ---------- getters ----------

describe('useDailyStore getters', () => {
  it('taskCompletionRate：daily=null 时返回 0', () => {
    const store = useDailyStore();
    expect(store.taskCompletionRate).toBe(0);
  });

  it('taskCompletionRate：daily 存在时返回 completed/total（total=0 兜底 0）', () => {
    const store = useDailyStore();
    store.daily = mockDaily({
      task: {
        completedCount: 3,
        totalCount: 5,
        completionRate: 0.6,
        statusDistribution: {},
        priorityDistribution: {},
      },
    });

    expect(store.taskCompletionRate).toBeCloseTo(0.6);

    // total=0 边界
    store.daily = mockDaily({
      task: {
        completedCount: 0,
        totalCount: 0,
        completionRate: 0,
        statusDistribution: {},
        priorityDistribution: {},
      },
    });
    expect(store.taskCompletionRate).toBe(0);
  });

  it('dietEnabled：daily=null 时返回 false', () => {
    const store = useDailyStore();
    expect(store.dietEnabled).toBe(false);
  });

  it('dietEnabled：daily.diet.enabled 透传', () => {
    const store = useDailyStore();
    store.daily = mockDaily({
      diet: { enabled: true, value: null, reason: '' },
    });
    expect(store.dietEnabled).toBe(true);

    store.daily = mockDaily({
      diet: { enabled: false, value: null, reason: '暂未启用' },
    });
    expect(store.dietEnabled).toBe(false);
  });
});

// ---------- syncFromRoute ----------

describe('useDailyStore.syncFromRoute', () => {
  it('无 query 时：filter.date 置空', () => {
    const store = useDailyStore();
    store.filter = { date: '2026-07-22' };

    store.syncFromRoute({});

    expect(store.filter.date).toBe('');
  });

  it('有 ?date=YYYY-MM-DD 时：filter.date 同步（不自动 fetch，由视图 onMounted 拉）', () => {
    const store = useDailyStore();

    store.syncFromRoute({ date: '2026-07-23' });

    expect(store.filter.date).toBe('2026-07-23');
    // 不应自动触发 fetchDaily
    expect(dailyApi.daily).not.toHaveBeenCalled();
  });

  it('有 ?week=YYYY-Www 时：仅同步到 filter.date，week= 自身不写入 state（视图按需 fetchWeek）', () => {
    const store = useDailyStore();

    store.syncFromRoute({ week: '2026-W30' });

    // spec 约定：URL 上的 ?week=YYYY-Www 仅用于路由分享；store 不持有 isoWeek 字段，
    // 视图按 filter.date 计算目标周并 fetchWeek。
    expect(store.filter.date).toBe('');
    expect(dailyApi.week).not.toHaveBeenCalled();
  });
});

// ---------- resetFilter ----------

describe('useDailyStore.resetFilter', () => {
  it('清空 filter.date + 重新 fetchDaily（不带参数）', async () => {
    vi.mocked(dailyApi.daily).mockResolvedValue(mockDaily());

    const store = useDailyStore();
    store.filter = { date: '2026-07-22' };

    await store.resetFilter();

    expect(store.filter.date).toBe('');
    expect(dailyApi.daily).toHaveBeenCalledWith(undefined);
  });
});
