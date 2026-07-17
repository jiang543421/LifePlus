import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import PlanTaskList from '@/components/PlanTaskList.vue';
import type { TaskListItem } from '@/types';
import { TaskStatusValue, TaskPriorityValue } from '@/types';
import { ApiError } from '@/api/http';

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

import { taskApi } from '@/api/task';

function task(overrides: Partial<TaskListItem> = {}): TaskListItem {
  return {
    id: 1,
    title: '准备材料',
    status: TaskStatusValue.TODO,
    priority: TaskPriorityValue.MEDIUM,
    dueDate: null,
    tag: null,
    ...overrides,
  };
}

function mountList(planId = 7) {
  return mount(PlanTaskList, {
    props: { planId },
    global: { plugins: [ElementPlus] },
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(taskApi.byPlan).mockReset();
});

describe('PlanTaskList / 四态渲染', () => {
  it('初始 mount：触发 fetchByPlan(byPlanLoading=true) 显示 loading', async () => {
    // 永不 resolve → loading 持续
    vi.mocked(taskApi.byPlan).mockImplementation(() => new Promise(() => {}));
    const w = mountList(7);
    await flushPromises();
    expect(w.find('[data-testid="related-loading"]').exists()).toBe(true);
    expect(taskApi.byPlan).toHaveBeenCalledWith(7);
  });

  it('空列表：显示「该日程暂无关联任务」', async () => {
    vi.mocked(taskApi.byPlan).mockResolvedValue([]);
    const w = mountList(7);
    await flushPromises();
    expect(w.find('[data-testid="related-empty"]').exists()).toBe(true);
    expect(w.text()).toContain('暂无关联任务');
  });

  it('失败 ApiError：显示 byPlanError 文案', async () => {
    vi.mocked(taskApi.byPlan).mockRejectedValueOnce(new ApiError(1003, '无权操作该任务'));
    const w = mountList(7);
    await flushPromises();
    expect(w.find('[data-testid="related-error"]').exists()).toBe(true);
    expect(w.text()).toContain('无权操作该任务');
  });

  it('正常：渲染每条任务的 title、状态 badge、优先级、截止日、标签', async () => {
    const items: TaskListItem[] = [
      task({
        id: 1,
        title: '准备材料',
        priority: TaskPriorityValue.HIGH,
        dueDate: '2026-08-15',
        tag: 'work',
      }),
      task({
        id: 2,
        title: '预订会议室',
        status: TaskStatusValue.DONE,
        priority: TaskPriorityValue.LOW,
        dueDate: null,
        tag: null,
      }),
    ];
    vi.mocked(taskApi.byPlan).mockResolvedValue(items);
    const w = mountList(7);
    await flushPromises();

    const rows = w.findAll('[data-testid="related-task-row"]');
    expect(rows).toHaveLength(2);
    expect(rows[0]!.text()).toContain('准备材料');
    expect(rows[0]!.text()).toContain('高'); // priority HIGH → 高
    expect(rows[0]!.text()).toContain('2026-08-15');
    expect(rows[0]!.text()).toContain('#work');
  });
});

describe('PlanTaskList / 交互', () => {
  it('点击任务行 emit open 带 id', async () => {
    vi.mocked(taskApi.byPlan).mockResolvedValue([task({ id: 42 })]);
    const w = mountList(7);
    await flushPromises();
    await w.find('[data-testid="related-task-row"]').trigger('click');
    expect(w.emitted('open')?.[0]).toEqual([42]);
  });

  it('planId 变化时重新触发 fetchByPlan(新 planId)', async () => {
    vi.mocked(taskApi.byPlan).mockResolvedValue([]);
    const w = mountList(7);
    await flushPromises();
    expect(taskApi.byPlan).toHaveBeenCalledTimes(1);
    expect(taskApi.byPlan).toHaveBeenLastCalledWith(7);

    await w.setProps({ planId: 8 });
    await flushPromises();
    expect(taskApi.byPlan).toHaveBeenCalledTimes(2);
    expect(taskApi.byPlan).toHaveBeenLastCalledWith(8);
  });
});