import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import TaskItem from '@/components/TaskItem.vue';
import type { TaskListItem } from '@/types';
import { TaskStatusValue, TaskPriorityValue } from '@/types';

function item(overrides: Partial<TaskListItem> = {}): TaskListItem {
  return {
    id: 1,
    title: '买菜',
    status: TaskStatusValue.TODO,
    priority: TaskPriorityValue.MEDIUM,
    dueDate: null,
    tag: null,
    ...overrides,
  };
}

function mountItem(task: TaskListItem) {
  return mount(TaskItem, { props: { task }, global: { plugins: [ElementPlus] } });
}

describe('TaskItem', () => {
  it('渲染 title + 状态 badge', () => {
    const w = mountItem(item());
    expect(w.text()).toContain('买菜');
    expect(w.find('[data-testid="status-badge"], .status-tag').exists()).toBe(true);
  });

  it('不同 status 对应不同 el-tag type（warning / success / info）', () => {
    const todo = mountItem(item({ status: TaskStatusValue.TODO }));
    expect(todo.find('.el-tag--warning').exists()).toBe(true);

    const done = mountItem(item({ status: TaskStatusValue.DONE }));
    expect(done.find('.el-tag--success').exists()).toBe(true);

    const cancelled = mountItem(item({ status: TaskStatusValue.CANCELLED }));
    expect(cancelled.find('.el-tag--info').exists()).toBe(true);
  });

  it('点击完成/重做 emit change-status（TODO ↔ DONE）', async () => {
    const w = mountItem(item({ status: TaskStatusValue.TODO }));
    await w.find('[data-testid="toggle-status"]').trigger('click');
    expect(w.emitted('change-status')?.[0]).toEqual([TaskStatusValue.DONE]);

    const w2 = mountItem(item({ status: TaskStatusValue.DONE }));
    await w2.find('[data-testid="toggle-status"]').trigger('click');
    expect(w2.emitted('change-status')?.[0]).toEqual([TaskStatusValue.TODO]);
  });

  it('CANCELLED 状态不渲染切换按钮', () => {
    const w = mountItem(item({ status: TaskStatusValue.CANCELLED }));
    expect(w.find('[data-testid="toggle-status"]').exists()).toBe(false);
  });

  it('点击编辑/删除 emit edit/remove 带 id', async () => {
    const w = mountItem(item({ id: 42 }));
    await w.find('[data-testid="edit"]').trigger('click');
    await w.find('[data-testid="remove"]').trigger('click');
    expect(w.emitted('edit')?.[0]).toEqual([42]);
    expect(w.emitted('remove')?.[0]).toEqual([42]);
  });
});