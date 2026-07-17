import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import PlanDetailView from '@/views/PlanDetailView.vue';
import PlanCalendarView from '@/views/PlanCalendarView.vue';
import { useAuthStore } from '@/stores/auth';
import { planApi } from '@/api/plan';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import type { PlanResponse } from '@/types';
import { PlanAllDayValue } from '@/types';

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

// ElMessageBox.confirm 在 jsdom 下渲染 modal 用户无法交互，直接 stub 为"确认"
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus');
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
    },
  };
});

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

async function mountAt(id: string) {
  const router = buildRouter();
  router.push(`/plans/${id}`);
  await router.isReady();
  const wrapper = mount(PlanDetailView, {
    global: { plugins: [ElementPlus, router] },
  });
  await flushPromises();
  return wrapper;
}

const timedPlan: PlanResponse = {
  id: 7,
  userId: 1,
  title: '周会',
  startTime: '2026-07-17T10:00:00',
  endTime: '2026-07-17T11:00:00',
  allDay: 0,
  location: '会议室 A',
  note: '带纪要',
  reminderMin: 15,
  createdAt: '2026-07-17T10:00:00+08:00',
  updatedAt: '2026-07-17T10:00:00+08:00',
};

const allDayPlan: PlanResponse = {
  ...timedPlan,
  id: 8,
  title: '出差',
  startTime: '2026-07-20T00:00:00',
  endTime: '2026-07-21T23:59:59',
  allDay: PlanAllDayValue.ALL_DAY,
  location: null,
  note: null,
  reminderMin: null,
};

beforeEach(() => {
  setActivePinia(createPinia());
  const auth = useAuthStore();
  auth.setTokens('access', 'refresh');
  auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });
  vi.mocked(planApi.get).mockReset();
  vi.mocked(planApi.update).mockReset();
  vi.mocked(planApi.delete).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('PlanDetailView / 加载与展示', () => {
  it('onMounted 拉取并展示标题与字段', async () => {
    vi.mocked(planApi.get).mockResolvedValue(timedPlan);
    const w = await mountAt('7');
    await flushPromises();
    expect(planApi.get).toHaveBeenCalledWith(7);
    expect(w.find('[data-testid="plan-detail"]').exists()).toBe(true);
    expect(w.text()).toContain('周会');
    expect(w.find('[data-testid="location-text"]').text()).toContain('会议室 A');
    expect(w.find('[data-testid="note-text"]').text()).toContain('带纪要');
    expect(w.find('[data-testid="reminder-text"]').text()).toContain('提前 15 分钟');
  });

  it('全天事件展示 allday-badge 与日期范围', async () => {
    vi.mocked(planApi.get).mockResolvedValue(allDayPlan);
    const w = await mountAt('8');
    await flushPromises();
    expect(w.find('[data-testid="allday-badge"]').exists()).toBe(true);
    expect(w.find('[data-testid="reminder-text"]').text()).toBe('不提醒');
  });

  it('get 抛 1003 → showAuthError(1003) 并跳回 /plans', async () => {
    vi.mocked(planApi.get).mockRejectedValue(new ApiError(1003, '无权操作'));
    const w = await mountAt('7');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/plans');
  });

  it('get 抛 1004 → showAuthError(1004) 并跳回 /plans', async () => {
    vi.mocked(planApi.get).mockRejectedValue(new ApiError(1004, '事件不存在'));
    const w = await mountAt('7');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1004);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/plans');
  });

  it('get 抛其他 ApiError 时只 toast，不跳回', async () => {
    vi.mocked(planApi.get).mockRejectedValue(new ApiError(1500, '网络异常'));
    const w = await mountAt('7');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1500);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/plans/7');
  });
});

describe('PlanDetailView / 编辑', () => {
  it('编辑 → 改标题 → 保存调 planApi.update 并刷新', async () => {
    vi.mocked(planApi.get).mockResolvedValue(timedPlan);
    vi.mocked(planApi.update).mockResolvedValue(undefined);
    const w = await mountAt('7');
    await flushPromises();

    await w.find('[data-testid="edit-start"]').trigger('click');
    await flushPromises();
    expect(w.find('[data-testid="event-dialog"]').exists()).toBe(true);

    await w.find('[data-testid="event-title"] input').setValue('周会+复盘');
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();

    expect(planApi.update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ title: '周会+复盘' }),
    );
    // 成功后重新拉取详情
    expect(planApi.get).toHaveBeenCalledTimes(2);
  });

  it('update 抛 ApiError(1001) 时调 showAuthError(1001)，dialog 不关闭', async () => {
    vi.mocked(planApi.get).mockResolvedValue(timedPlan);
    vi.mocked(planApi.update).mockRejectedValue(new ApiError(1001, '参数错误'));
    const w = await mountAt('7');
    await flushPromises();

    await w.find('[data-testid="edit-start"]').trigger('click');
    await flushPromises();
    await w.find('[data-testid="event-submit"]').trigger('click');
    await flushPromises();

    expect(showAuthError).toHaveBeenCalledWith(1001);
    expect(w.find('[data-testid="event-dialog"]').exists()).toBe(true);
  });
});

describe('PlanDetailView / 删除', () => {
  it('确认删除 → 调 planApi.delete 并跳回 /plans', async () => {
    vi.mocked(planApi.get).mockResolvedValue(timedPlan);
    vi.mocked(planApi.delete).mockResolvedValue(undefined);
    const w = await mountAt('7');
    await flushPromises();

    await w.find('[data-testid="delete-btn"]').trigger('click');
    await flushPromises();

    expect(planApi.delete).toHaveBeenCalledWith(7);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/plans');
  });

  it('用户取消删除（ElMessageBox 抛 reject）→ 不调 delete', async () => {
    const { ElMessageBox } = await import('element-plus');
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce(new Error('cancel'));
    vi.mocked(planApi.get).mockResolvedValue(timedPlan);
    const w = await mountAt('7');
    await flushPromises();

    await w.find('[data-testid="delete-btn"]').trigger('click');
    await flushPromises();

    expect(planApi.delete).not.toHaveBeenCalled();
  });
});