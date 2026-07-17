import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import TaskListView from '@/views/TaskListView.vue';
import HomeView from '@/views/HomeView.vue';
import { useAuthStore } from '@/stores/auth';
import { useTaskStore } from '@/stores/task';
import { taskApi } from '@/api/task';
import { showAuthError } from '@/utils/error';
import type { TaskListItem } from '@/types';
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

function buildRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomeView },
      { path: '/tasks', component: TaskListView },
      { path: '/tasks/:id', component: { template: '<div>detail</div>' } },
    ],
  });
}

async function mountView() {
  const router = buildRouter();
  router.push('/tasks');
  await router.isReady();
  const wrapper = mount(TaskListView, {
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
  vi.mocked(taskApi.list).mockReset();
  vi.mocked(taskApi.create).mockReset();
  vi.mocked(taskApi.patchStatus).mockReset();
  vi.mocked(taskApi.delete).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('TaskListView', () => {
  it('onMounted 调 taskStore.fetchList', async () => {
    const items: TaskListItem[] = [
      { id: 1, title: '买菜', status: TaskStatusValue.TODO, priority: 2, dueDate: null, tag: null },
    ];
    vi.mocked(taskApi.list).mockResolvedValue({
      items, total: 1, page: 1, size: 20,
    });

    const w = await mountView();
    await flushPromises();

    expect(taskApi.list).toHaveBeenCalled();
    expect(w.find('[data-testid="task-rows"]').exists()).toBe(true);
    expect(w.text()).toContain('买菜');
  });

  it('列表空 + hasFilter=false 时展示引导文案', async () => {
    vi.mocked(taskApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const w = await mountView();
    await flushPromises();
    expect(w.find('[data-testid="empty-state"]').exists()).toBe(true);
    expect(w.text()).toContain('还没有任务');
  });

  it('fetchList 失败且 errorCode=1003 时调 showAuthError(1003)', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(taskApi.list).mockRejectedValue(new ApiError(1003, '无权操作'));
    await mountView();
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1003);
  });

  it('点击新建 → 打开 dialog；提交合法表单调 create', async () => {
    vi.mocked(taskApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(taskApi.create).mockResolvedValue({
      id: 1, userId: 1, planId: null, title: 't', status: 0, priority: 0,
      dueDate: null, tag: null,
      createdAt: '2026-07-16T10:00:00+08:00', updatedAt: '2026-07-16T10:00:00+08:00',
    });

    const w = await mountView();
    await w.find('[data-testid="new-task"]').trigger('click');
    await flushPromises();
    expect(w.find('[data-testid="new-task-dialog"]').exists()).toBe(true);

    const titleInput = w.find('[data-testid="new-title"] input');
    expect(titleInput.exists()).toBe(true);
    await titleInput.setValue('买牛奶');
    await w.find('[data-testid="new-submit"]').trigger('click');
    await flushPromises();

    expect(taskApi.create).toHaveBeenCalledWith(expect.objectContaining({ title: '买牛奶' }));
  });

  it('submit 成功后再次打开 dialog → title 为空（防陈旧值）', async () => {
    // CLAUDE.md §4.1 + Review C-5: dialog 关闭/成功后必须重置 state，
    // 否则用户再次打开会看到上次输入的脏值导致误提交
    vi.mocked(taskApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(taskApi.create).mockResolvedValue({
      id: 1, userId: 1, planId: null, title: 't', status: 0, priority: 0,
      dueDate: null, tag: null,
      createdAt: '2026-07-16T10:00:00+08:00', updatedAt: '2026-07-16T10:00:00+08:00',
    });

    const w = await mountView();
    // 第 1 次：填表 + 提交
    await w.find('[data-testid="new-task"]').trigger('click');
    await flushPromises();
    await w.find('[data-testid="new-title"] input').setValue('买牛奶');
    await w.find('[data-testid="new-submit"]').trigger('click');
    await flushPromises();

    // 第 2 次：再打开 dialog，title 应为空
    await w.find('[data-testid="new-task"]').trigger('click');
    await flushPromises();
    const titleInput2 = w.find('[data-testid="new-title"] input');
    expect((titleInput2.element as HTMLInputElement).value).toBe('');
  });

  it('openCreate 每次返回新对象（不 mutate 旧对象 — §4.1 不可变）', async () => {
    // 通过内部组件实例访问 newTask 引用，验证 openCreate 用整体替换
    // 而非逐字段 mutation（review C-5 的核心修复点）。
    // 注：Vue Test Utils 在 vm proxy 上会自动 unwrap ref，所以这里访问的就是对象本身。
    vi.mocked(taskApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const w = await mountView();
    const vm = w.vm as unknown as {
      newTask: { title: string };
      openCreate: () => void;
    };
    // 第 1 次打开：拿到原始引用
    vm.openCreate();
    await flushPromises();
    const first = vm.newTask;
    expect(first.title).toBe('');
    // 修改 dialog 内输入
    first.title = '脏值';
    // 第 2 次打开：应替换为新对象（first.title 仍是"脏值"，新对象 title=''）
    vm.openCreate();
    await flushPromises();
    expect(vm.newTask).not.toBe(first);
    expect(vm.newTask.title).toBe('');
    expect(first.title).toBe('脏值'); // 旧对象未被 mutate
  });

  it('store.setPage 更新 filter.page（视图层通过 onPageChange 调用 refresh）', async () => {
    vi.mocked(taskApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    await mountView();
    const store = useTaskStore();
    store.setPage(3);
    expect(store.filter.page).toBe(3);
  });

  it('onPageChange handler 触发 fetchList 带新 page', async () => {
    vi.mocked(taskApi.list).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const w = await mountView();
    // 直接调组件导出的 handler（el-pagination 在 jsdom 下渲染复杂，绕过事件走语义层）
    const vm = w.vm as unknown as { onPageChange: (p: number) => Promise<void> };
    await vm.onPageChange(3);
    expect(taskApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 3 }));
  });
});