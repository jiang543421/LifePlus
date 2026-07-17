import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import TaskDetailView from '@/views/TaskDetailView.vue';
import TaskListView from '@/views/TaskListView.vue';
import { useAuthStore } from '@/stores/auth';
import { taskApi } from '@/api/task';
import { showAuthError } from '@/utils/error';
import type { TaskResponse } from '@/types';
import { TaskStatusValue } from '@/types';

vi.mock('@/api/task', () => ({
  taskApi: {
    list: vi.fn(),
    byPlan: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    patchStatus: vi.fn(),
    delete: vi.fn(),
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
      { path: '/tasks', component: TaskListView },
      { path: '/tasks/:id', component: TaskDetailView },
    ],
  });
}

async function mountAt(id: string) {
  const router = buildRouter();
  router.push(`/tasks/${id}`);
  await router.isReady();
  const wrapper = mount(TaskDetailView, {
    global: { plugins: [ElementPlus, router] },
  });
  await flushPromises();
  return wrapper;
}

const sampleTask: TaskResponse = {
  id: 7,
  userId: 1,
  planId: null,
  title: '买菜',
  status: TaskStatusValue.TODO,
  priority: 2,
  dueDate: null,
  tag: null,
  createdAt: '2026-07-16T10:00:00+08:00',
  updatedAt: '2026-07-16T10:00:00+08:00',
};

beforeEach(() => {
  setActivePinia(createPinia());
  const auth = useAuthStore();
  auth.setTokens('access', 'refresh');
  auth.setUser({ id: 1, email: 'a@example.com', nickname: 'A' });
  vi.mocked(taskApi.list).mockReset();
  vi.mocked(taskApi.get).mockReset();
  vi.mocked(taskApi.update).mockReset();
  vi.mocked(taskApi.patchStatus).mockReset();
  vi.mocked(taskApi.delete).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('TaskDetailView', () => {
  it('onMounted 拉取 task 并展示标题', async () => {
    vi.mocked(taskApi.get).mockResolvedValue(sampleTask);
    const w = await mountAt('7');
    await flushPromises();
    expect(taskApi.get).toHaveBeenCalledWith(7);
    expect(w.find('[data-testid="task-detail"]').exists()).toBe(true);
    expect(w.text()).toContain('买菜');
  });

  it('get 抛 1003 → showAuthError(1003) 并跳回 /tasks', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(taskApi.get).mockRejectedValue(new ApiError(1003, '无权操作'));
    const w = await mountAt('7');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
    // router 应已 replace /tasks
    expect((w.vm as unknown as { $route: { path: string } }).$route.path).toBe('/tasks');
  });

  it('点击标记完成调 patchStatus', async () => {
    vi.mocked(taskApi.get).mockResolvedValue(sampleTask);
    vi.mocked(taskApi.patchStatus).mockResolvedValue(undefined);
    const w = await mountAt('7');
    await flushPromises();
    await w.find('[data-testid="mark-done"]').trigger('click');
    await flushPromises();
    expect(taskApi.patchStatus).toHaveBeenCalledWith(7, { status: TaskStatusValue.DONE });
  });

  it('点击删除调 taskApi.delete 并跳回 /tasks', async () => {
    vi.mocked(taskApi.get).mockResolvedValue(sampleTask);
    vi.mocked(taskApi.delete).mockResolvedValue(undefined);
    // Element Plus ElMessageBox 默认 confirm → OK
    const w = await mountAt('7');
    await flushPromises();
    await w.find('[data-testid="delete-btn"]').trigger('click');
    await flushPromises();
    // ElMessageBox 在 jsdom 下会立即 resolve confirm；delete 应被调用
    expect(taskApi.delete).toHaveBeenCalledWith(7);
  });

  it('点击编辑 → 保存调 update', async () => {
    vi.mocked(taskApi.get).mockResolvedValue(sampleTask);
    vi.mocked(taskApi.update).mockResolvedValue(undefined);
    const w = await mountAt('7');
    await flushPromises();

    await w.find('[data-testid="edit-start"]').trigger('click');
    await flushPromises();
    expect(w.find('[data-testid="edit-save"]').exists()).toBe(true);

    const titleInput = w.find('[data-testid="edit-title"] input');
    expect(titleInput.exists()).toBe(true);
    await titleInput.setValue('买菜+买水果');
    await w.find('[data-testid="edit-save"]').trigger('click');
    await flushPromises();

    expect(taskApi.update).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ title: '买菜+买水果' }),
    );
  });
});