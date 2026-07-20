import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import DietDetailView from '@/views/DietDetailView.vue';
import DietView from '@/views/DietView.vue';
import { useAuthStore } from '@/stores/auth';
import { dietApi } from '@/api/diet';
import { showAuthError } from '@/utils/error';
import type { DietResponse } from '@/types';

vi.mock('@/api/diet', () => ({
  dietApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    summary: vi.fn(),
    frequent: vi.fn(),
  },
}));

vi.mock('@/utils/error', () => ({
  showAuthError: vi.fn(),
  authErrorMessage: vi.fn(),
}));

// ElMessageBox.confirm 在 jsdom 下会渲染 modal，用户无法交互 → 直接 stub 为"确认"
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
      { path: '/diets', component: DietView },
      { path: '/diets/:id', component: DietDetailView },
    ],
  });
}

async function mountAt(id: string) {
  const router = buildRouter();
  router.push(`/diets/${id}`);
  await router.isReady();
  const wrapper = mount(DietDetailView, {
    global: { plugins: [ElementPlus, router] },
  });
  await flushPromises();
  return wrapper;
}

const sample: DietResponse = {
  id: 7,
  userId: 1,
  mealType: 'LUNCH',
  name: '米饭',
  kcal: 230,
  proteinG: 5,
  carbG: 50,
  fatG: 1,
  note: '午饭',
  occurredAt: '2026-07-15T12:00:00+08:00',
  createdAt: '2026-07-15T12:00:00+08:00',
  updatedAt: '2026-07-15T12:00:00+08:00',
};

beforeEach(() => {
  setActivePinia(createPinia());
  const auth = useAuthStore();
  auth.setTokens('access', 'refresh');
  auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });
  vi.mocked(dietApi.list).mockReset();
  vi.mocked(dietApi.get).mockReset();
  vi.mocked(dietApi.create).mockReset();
  vi.mocked(dietApi.update).mockReset();
  vi.mocked(dietApi.delete).mockReset();
  vi.mocked(dietApi.summary).mockReset();
  vi.mocked(dietApi.frequent).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('DietDetailView / load', () => {
  it('onMounted 拉取 diet 并展示全部字段', async () => {
    vi.mocked(dietApi.get).mockResolvedValue(sample);
    const w = await mountAt('7');
    await flushPromises();
    expect(dietApi.get).toHaveBeenCalledWith(7);
    expect(w.find('[data-testid="diet-detail"]').exists()).toBe(true);
    expect(w.find('[data-testid="diet-detail-name"]').text()).toBe('米饭');
    expect(w.find('[data-testid="diet-detail-meal-type"]').text()).toBe('午餐');
    expect(w.find('[data-testid="diet-detail-kcal"]').text()).toBe('230.00 kcal');
    expect(w.find('[data-testid="diet-detail-protein-g"]').text()).toBe('5.00 g');
    expect(w.find('[data-testid="diet-detail-carb-g"]').text()).toBe('50.00 g');
    expect(w.find('[data-testid="diet-detail-fat-g"]').text()).toBe('1.00 g');
    expect(w.find('[data-testid="diet-detail-note"]').text()).toBe('午饭');
    expect(w.find('[data-testid="diet-detail-occurred-at"]').text()).toBe('2026-07-15 12:00');
  });

  it('get 抛 1003 → showAuthError(1003) 并跳回 /diets', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(dietApi.get).mockRejectedValue(new ApiError(1003, '无权操作该饮食'));
    const w = await mountAt('7');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/diets');
  });

  it('note=null → 渲染 "—"', async () => {
    vi.mocked(dietApi.get).mockResolvedValue({ ...sample, note: null });
    const w = await mountAt('7');
    await flushPromises();
    expect(w.find('[data-testid="diet-detail-note"]').text()).toBe('—');
  });
});

describe('DietDetailView / actions', () => {
  it('点击「进入编辑」→ 写 store + 跳 /diets', async () => {
    vi.mocked(dietApi.get).mockResolvedValue(sample);
    const w = await mountAt('7');
    await flushPromises();
    await w.find('[data-testid="diet-detail-edit-btn"]').trigger('click');
    await flushPromises();
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/diets');
  });

  it('点击「删除」→ 调 dietApi.delete 并跳回 /diets', async () => {
    vi.mocked(dietApi.get).mockResolvedValue(sample);
    vi.mocked(dietApi.delete).mockResolvedValue(undefined);
    const w = await mountAt('7');
    await flushPromises();
    await w.find('[data-testid="diet-detail-delete-btn"]').trigger('click');
    await flushPromises();
    expect(dietApi.delete).toHaveBeenCalledWith(7);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/diets');
  });

  it('删除失败 1003 → showAuthError，不跳转', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(dietApi.get).mockResolvedValue(sample);
    vi.mocked(dietApi.delete).mockRejectedValue(new ApiError(1003, '无权操作'));
    const w = await mountAt('7');
    await flushPromises();
    await w.find('[data-testid="diet-detail-delete-btn"]').trigger('click');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/diets/7');
  });

  it('点击「返回列表」→ 跳 /diets', async () => {
    vi.mocked(dietApi.get).mockResolvedValue(sample);
    const w = await mountAt('7');
    await flushPromises();
    // ElPageHeader 的 back 触发 → goBack → router.replace('/diets')
    await w.find('[data-testid="diet-detail"]').find('button').trigger('click');
    await flushPromises();
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/diets');
  });
});

describe('DietDetailView / 不同餐别 label', () => {
  it.each([
    ['BREAKFAST', '早餐'],
    ['LUNCH', '午餐'],
    ['DINNER', '晚餐'],
    ['SNACK', '加餐'],
  ] as const)('mealType=%s 渲染 %s', async (mealType, expectedLabel) => {
    vi.mocked(dietApi.get).mockResolvedValue({ ...sample, mealType });
    const w = await mountAt('7');
    await flushPromises();
    expect(w.find('[data-testid="diet-detail-meal-type"]').text()).toBe(expectedLabel);
  });
});