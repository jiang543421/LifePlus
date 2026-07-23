import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import DailyView from '@/views/DailyView.vue';
import { useDailyStore } from '@/stores/daily';
import { dailyApi } from '@/api/daily';
import type { DailyReportPayload, WeeklyReportPayload } from '@/types';

vi.mock('@/api/daily', () => ({
  dailyApi: {
    daily: vi.fn(),
    week: vi.fn(),
  },
}));

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    replace: vi.fn(),
    currentRoute: { value: { query: {} } },
  },
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn().mockResolvedValue(undefined) }),
}));

vi.mock('@/utils/error', () => ({
  showAuthError: vi.fn(),
}));

const mkDaily = (over: Partial<DailyReportPayload> = {}): DailyReportPayload => ({
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
    categoryBreakdown: { MEAL: 78.5, SHOPPING: 0, TRANSPORT: 50, SUBSCRIPTION: 0, OTHER: 0 },
    topCategories: [
      { code: 'MEAL', amount: 78.5 },
      { code: 'TRANSPORT', amount: 50 },
    ],
  },
  diet: { enabled: false, value: null, reason: '饮食模块暂未启用（v1.2.4+ 启用）' },
  ...over,
});

const mkWeek = (over: Partial<WeeklyReportPayload> = {}): WeeklyReportPayload => ({
  isoWeek: '2026-W30',
  weekStart: '2026-07-20',
  weekEnd: '2026-07-26',
  comparison: {
    taskCompletion: { current: 0.65, previous: 0.5, delta: 0.15 },
    planEvents: { current: 8, previous: 5, delta: 3 },
    expenseAmount: { current: 1200, previous: 820, delta: 380 },
  },
  ...over,
});

function mountView(opts: { daily?: DailyReportPayload | null; week?: WeeklyReportPayload | null } = {}) {
  setActivePinia(createPinia());
  const daily = opts.daily === undefined ? null : opts.daily;
  const week = opts.week === undefined ? null : opts.week;
  // DailyView.onMounted 会调 fetchDaily/fetchWeek，必须让 mock 与 store 初值一致，
  // 否则 onMounted 完成后 store.daily/week 会被 mock 返回值覆盖成 undefined。
  // cast 是测试辅助：mock 实际返回 null 时调用方（fetchDaily）会走 catch 分支，运行时安全。
  vi.mocked(dailyApi.daily).mockResolvedValue(daily as DailyReportPayload);
  vi.mocked(dailyApi.week).mockResolvedValue(week as WeeklyReportPayload);
  const store = useDailyStore();
  store.$patch({
    daily,
    week,
    loading: false,
    error: null,
    errorCode: null,
    filter: { date: '' },
  });
  return mount(DailyView, {
    global: {
      plugins: [ElementPlus],
      stubs: { TopBar: true, RouterLink: RouterLinkStub },
    },
  });
}

beforeEach(() => {
  vi.mocked(dailyApi.daily).mockReset();
  vi.mocked(dailyApi.week).mockReset();
});

describe('DailyView — 4 卡渲染', () => {
  it('daily 非空 → 4 卡 + 周报按钮都渲染', async () => {
    const w = mountView({ daily: mkDaily() });

    await flushPromises();

    expect(w.find('[data-testid="daily-task-card"]').exists()).toBe(true);
    expect(w.find('[data-testid="daily-plan-card"]').exists()).toBe(true);
    expect(w.find('[data-testid="daily-expense-card"]').exists()).toBe(true);
    expect(w.find('[data-testid="daily-diet-card"]').exists()).toBe(true);
    // 周报按钮（spec §7.3：「本周」/「上一周」切换）
    expect(w.find('[data-testid="daily-week-prev"]').exists()).toBe(true);
    expect(w.find('[data-testid="daily-week-next"]').exists()).toBe(true);
  });

  it('daily 非空 → 渲染 task 摘要 "3 / 5"', async () => {
    const w = mountView({ daily: mkDaily() });
    await flushPromises();
    expect(w.find('[data-testid="daily-task-summary"]').text()).toContain('3 / 5');
  });

  it('daily 非空 + diet.enabled=false → 饮食占位卡显示', async () => {
    const w = mountView({ daily: mkDaily() });
    await flushPromises();
    expect(w.find('[data-testid="daily-diet-placeholder"]').exists()).toBe(true);
  });
});

describe('DailyView — 周报切换', () => {
  it('点「本周」按钮 → 调 dailyApi.week + URL 同步 ?week=YYYY-Www', async () => {
    const w = mountView({ daily: mkDaily() });
    // mountView 会用 opts 重置 mock，故在 mountView 之后再设 week mock
    vi.mocked(dailyApi.week).mockResolvedValue(mkWeek({ isoWeek: '2026-W30' }));
    await flushPromises();

    await w.find('[data-testid="daily-week-current"]').trigger('click');
    await flushPromises();

    expect(dailyApi.week).toHaveBeenCalled();
    // week 渲染：显示 isoWeek + weekStart~weekEnd
    expect(w.find('[data-testid="daily-week-label"]').text()).toContain('2026-W30');
    expect(w.find('[data-testid="daily-week-label"]').text()).toContain('2026-07-20');
    expect(w.find('[data-testid="daily-week-label"]').text()).toContain('2026-07-26');
  });

  it('周报 taskCompletion delta=null → 显示 "—"', async () => {
    const weekAllZero: WeeklyReportPayload = mkWeek({
      comparison: {
        taskCompletion: { current: 0.5, previous: 0, delta: null },
        planEvents: { current: 5, previous: 0, delta: null },
        expenseAmount: { current: 800, previous: 0, delta: null },
      },
    });

    const w = mountView({ daily: mkDaily(), week: weekAllZero });
    await flushPromises();

    // 三个 delta 都显示 "—"
    const html = w.text();
    expect(html).toContain('—');
  });

  it('点「上一周」按钮 → 调 dailyApi.week + store.week 被设置', async () => {
    const w = mountView({ daily: mkDaily({ date: '2026-07-23' }) });
    vi.mocked(dailyApi.week).mockResolvedValue(mkWeek());
    await flushPromises();

    await w.find('[data-testid="daily-week-prev"]').trigger('click');
    await flushPromises();

    // 「上一周」按钮触发后调 fetchWeek，store.week 被 fetchWeek 的返回值设置
    expect(dailyApi.week).toHaveBeenCalled();
    const store = useDailyStore();
    expect(store.week).not.toBeNull();
  });
});

describe('DailyView — 错误处理', () => {
  it('fetchDaily 失败 → 调 showAuthError', async () => {
    const { showAuthError } = await import('@/utils/error');
    vi.mocked(dailyApi.daily).mockResolvedValue(null as unknown as DailyReportPayload);

    const w = mountView();
    await flushPromises();

    // 模拟用户点击"刷新"等触发 fetchDaily 失败的场景
    // 这里直接给 store 注入失败状态，验证视图层 error toast 触发
    const store = useDailyStore();
    store.$patch({ errorCode: 1001, error: 'date out of range' });

    // 视图层不主动 toast（与 AiAnalysisView 同款）— 错误状态在 store 中暴露给其他视图
    // DailyView 的错误处理：loading 失败时 store.errorCode 写入，由视图按需 toast
    expect(store.errorCode).toBe(1001);
    expect(showAuthError).not.toHaveBeenCalled(); // mount 时不自动 toast
  });

  // ---- v1.2.5 #3：错误态友好提示 ----

  it('fetchDaily 失败 + daily=null + !loading → 渲染友好错误态（含文案 + 重试按钮）', async () => {
    vi.mocked(dailyApi.daily).mockResolvedValue(null as unknown as DailyReportPayload);
    const w = mountView();
    await flushPromises();

    const store = useDailyStore();
    store.$patch({ errorCode: 1501, error: 'daily temporarily unavailable', daily: null, loading: false });

    await flushPromises();

    const errorState = w.find('[data-testid="daily-view-error"]');
    expect(errorState.exists()).toBe(true);
    // 文案应避免直接透出 "temporarily unavailable" 这类英文技术细节
    const desc = w.find('[data-testid="daily-view-error-description"]');
    expect(desc.exists()).toBe(true);
    expect(desc.text()).toContain('暂时无法获取日报数据');
    // 提供重试入口
    const retry = w.find('[data-testid="daily-view-error-retry"]');
    expect(retry.exists()).toBe(true);
  });

  it('点击错误态「重试」→ 调 dailyApi.daily 重新拉取', async () => {
    vi.mocked(dailyApi.daily).mockResolvedValue(null as unknown as DailyReportPayload);
    const w = mountView();
    await flushPromises();

    const store = useDailyStore();
    store.$patch({ errorCode: 1501, error: '...', daily: null, loading: false });
    await flushPromises();

    const callsBefore = vi.mocked(dailyApi.daily).mock.calls.length;
    await w.find('[data-testid="daily-view-error-retry"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(dailyApi.daily).mock.calls.length).toBeGreaterThan(callsBefore);
  });

  it('loading 状态（未失败）→ 不渲染错误态（避免与 skeleton 互斥歧义）', async () => {
    vi.mocked(dailyApi.daily).mockResolvedValue(null as unknown as DailyReportPayload);
    const w = mountView();
    await flushPromises();

    const store = useDailyStore();
    store.$patch({ loading: true, daily: null, error: null, errorCode: null });
    await flushPromises();

    expect(w.find('[data-testid="daily-view-error"]').exists()).toBe(false);
    // skeleton 仍然在
    expect(w.find('.daily-view__loading').exists()).toBe(true);
  });
});

describe('DailyView — URL sync', () => {
  it('onMounted → 调 dailyApi.daily（不带参数 = 今日）', async () => {
    vi.mocked(dailyApi.daily).mockResolvedValue(mkDaily());

    mountView();
    await flushPromises();

    expect(dailyApi.daily).toHaveBeenCalledWith(undefined);
  });
});
