import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import PlanCalendarView from '@/views/PlanCalendarView.vue';
import PlanDetailView from '@/views/PlanDetailView.vue';
import { useAuthStore } from '@/stores/auth';
import { usePlanStore } from '@/stores/plan';
import { planApi } from '@/api/plan';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { MONTH_QUERY_SIZE } from '@/utils/calendar';
import type { PlanListItem } from '@/types';

vi.mock('@/api/plan', () => ({
  planApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/utils/error', () => ({
  showAuthError: vi.fn(),
  authErrorMessage: vi.fn(),
}));

function buildRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/plans', component: PlanCalendarView },
      { path: '/plans/:id(\\d+)', component: PlanDetailView },
    ],
  });
}

async function mountView() {
  const router = buildRouter();
  router.push('/plans');
  await router.isReady();
  const wrapper = mount(PlanCalendarView, {
    global: { plugins: [ElementPlus, router] },
  });
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  setActivePinia(createPinia());
  const auth = useAuthStore();
  auth.setTokens('access', 'refresh');
  auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });
  vi.mocked(planApi.list).mockReset();
  vi.mocked(planApi.create).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('PlanCalendarView / 加载与渲染', () => {
  it('onMounted 调 planApi.list 带当月范围 + page=1 + size=MONTH_QUERY_SIZE', async () => {
    vi.mocked(planApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: MONTH_QUERY_SIZE });

    await mountView();
    await flushPromises();

    expect(planApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: MONTH_QUERY_SIZE }),
    );
    const call = vi.mocked(planApi.list).mock.calls[0]?.[0];
    // from 为月初 00:00:00；to 为月末 23:59:59
    expect(call?.from).toMatch(/^\d{4}-\d{2}-01T00:00:00$/);
    expect(call?.to).toMatch(/^\d{4}-\d{2}-\d{2}T23:59:59$/);
  });

  it('fetchList 失败且 errorCode=1003 时调 showAuthError(1003)', async () => {
    vi.mocked(planApi.list).mockRejectedValue(new ApiError(1003, '无权操作'));
    await mountView();
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
  });

  it('事件行渲染并显示时间摘要', async () => {
    const items: PlanListItem[] = [
      { id: 1, title: '周会', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00', allDay: 0, location: '会议室', reminderMin: null },
      { id: 2, title: '出差', startTime: '2026-07-15T00:00:00', endTime: '2026-07-15T23:59:59', allDay: 1, location: null, reminderMin: null },
    ];
    vi.mocked(planApi.list).mockResolvedValue({ items, total: 2, page: 1, size: MONTH_QUERY_SIZE });
    const w = await mountView();
    await flushPromises();
    await w.find('[data-date="2026-07-15"]').trigger('click');
    await flushPromises();
    expect(w.findAll('[data-testid="event-row"]')).toHaveLength(2);
    expect(w.text()).toContain('周会');
    expect(w.text()).toContain('全天');
  });
});

describe('PlanCalendarView / 月份切换', () => {
  it('点击下月后调 planApi.list 带新月份范围', async () => {
    vi.mocked(planApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: MONTH_QUERY_SIZE });
    const w = await mountView();
    await flushPromises();
    vi.mocked(planApi.list).mockClear();

    await w.find('[data-testid="cal-next"]').trigger('click');
    await flushPromises();

    expect(planApi.list).toHaveBeenCalledTimes(1);
    const call = vi.mocked(planApi.list).mock.calls[0]?.[0];
    expect(call?.from).toMatch(/^\d{4}-\d{2}-01T00:00:00$/);
    expect(call?.to).toMatch(/^\d{4}-\d{2}-\d{2}T23:59:59$/);
  });
});

describe('PlanCalendarView / 选中日期与详情跳转', () => {
  it('点击日历某天更新 selectedDate 并按日期筛选事件', async () => {
    const items: PlanListItem[] = [
      { id: 1, title: '周会', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00', allDay: 0, location: null, reminderMin: null },
      { id: 2, title: '另一天', startTime: '2026-07-20T10:00:00', endTime: '2026-07-20T11:00:00', allDay: 0, location: null, reminderMin: null },
    ];
    vi.mocked(planApi.list).mockResolvedValue({ items, total: 2, page: 1, size: MONTH_QUERY_SIZE });
    const w = await mountView();
    await flushPromises();

    await w.find('[data-date="2026-07-20"]').trigger('click');
    await flushPromises();

    const rows = w.findAll('[data-testid="event-row"]');
    expect(rows).toHaveLength(1);
    expect(rows[0].text()).toContain('另一天');
  });

  it('点击事件行跳到 /plans/{id}', async () => {
    const items: PlanListItem[] = [
      { id: 7, title: '周会', startTime: '2026-07-15T10:00:00', endTime: '2026-07-15T11:00:00', allDay: 0, location: null, reminderMin: null },
    ];
    vi.mocked(planApi.list).mockResolvedValue({ items, total: 1, page: 1, size: MONTH_QUERY_SIZE });
    const w = await mountView();
    await flushPromises();
    await w.find('[data-date="2026-07-15"]').trigger('click');
    await flushPromises();

    await w.find('[data-testid="event-row"]').trigger('click');
    await flushPromises();

    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/plans/7');
  });
});

describe('PlanCalendarView / 新建事件', () => {
  it('点击「+ 新建事件」打开 dialog；填标题后提交调 planApi.create 并刷新当月', async () => {
    vi.mocked(planApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: MONTH_QUERY_SIZE });
    vi.mocked(planApi.create).mockResolvedValue({
      id: 99, userId: 1, title: '午餐', startTime: '2026-07-17T09:00:00', endTime: '2026-07-17T10:00:00',
      allDay: 0, location: null, note: null, reminderMin: null,
      createdAt: '2026-07-17T10:00:00+08:00', updatedAt: '2026-07-17T10:00:00+08:00',
    });

    const w = await mountView();
    await flushPromises();
    vi.mocked(planApi.list).mockClear();

    await w.find('[data-testid="new-plan"]').trigger('click');
    await flushPromises();
    expect(w.find('[data-testid="event-dialog"]').exists()).toBe(true);

    await w.find('[data-testid="event-title"] input').setValue('午餐');
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();

    expect(planApi.create).toHaveBeenCalledWith(expect.objectContaining({ title: '午餐' }));
    // 成功后 store 不再调 planApi.create；但视图层会再调一次 planApi.list 刷新当月
    expect(planApi.list).toHaveBeenCalled();
  });

  it('create 抛 ApiError(1001) 时调 showAuthError(1001) 直接从异常取业务码', async () => {
    vi.mocked(planApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: MONTH_QUERY_SIZE });
    vi.mocked(planApi.create).mockRejectedValue(new ApiError(1001, '参数错误'));

    const w = await mountView();
    await flushPromises();
    await w.find('[data-testid="new-plan"]').trigger('click');
    await flushPromises();
    await w.find('[data-testid="event-title"] input').setValue('午餐');
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();

    expect(showAuthError).toHaveBeenCalledWith(1001);
    expect(vi.mocked(planApi.create).mock.calls.length).toBeGreaterThan(0);
  });
});