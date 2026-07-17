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

  // C-6：补齐 priority 全部档位 + dueDate/tag 渲染 + 未知 status fallback + toggleStatus fallback

  it('priority=NONE → 不渲染 .priority div（v-if=false 分支）', () => {
    const w = mountItem(item({ priority: TaskPriorityValue.NONE }));
    expect(w.find('.priority').exists()).toBe(false);
  });

  it('priority=LOW/MEDIUM/HIGH 渲染 P低/P中/P高 + data-priority', () => {
    const low = mountItem(item({ priority: TaskPriorityValue.LOW }));
    expect(low.find('.priority').text()).toBe('P低');
    expect(low.find('.priority').attributes('data-priority')).toBe('1');

    const med = mountItem(item({ priority: TaskPriorityValue.MEDIUM }));
    expect(med.find('.priority').text()).toBe('P中');
    expect(med.find('.priority').attributes('data-priority')).toBe('2');

    const high = mountItem(item({ priority: TaskPriorityValue.HIGH }));
    expect(high.find('.priority').text()).toBe('P高');
    expect(high.find('.priority').attributes('data-priority')).toBe('3');
  });

  it('dueDate 非空 → 渲染 📅 {date}', () => {
    const w = mountItem(item({ dueDate: '2026-08-10' }));
    expect(w.find('.due').text()).toContain('📅 2026-08-10');
  });

  it('tag 非空 → 渲染 #{tag}', () => {
    const w = mountItem(item({ tag: 'work' }));
    expect(w.find('.tag').text()).toBe('#work');
  });

  it('未知 status → statusLabel=未知 + el-tag--info（default 分支）', () => {
    // 99 是 TaskStatusValue 之外的占位值，触发 statusType/statusLabel 的 default case
    const w = mountItem(item({ status: 99 as unknown as TaskListItem['status'] }));
    expect(w.text()).toContain('未知');
    expect(w.find('.el-tag--info').exists()).toBe(true);
  });

  it('toggleStatus：未知 status（除 TODO/DONE）→ emit change-status=TODO', async () => {
    // statusType/statusLabel 的 default 分支下，toggleStatus 也走 TODO fallback
    const w = mountItem(item({ status: 99 as unknown as TaskListItem['status'] }));
    expect(w.find('[data-testid="toggle-status"]').exists()).toBe(true);
    await w.find('[data-testid="toggle-status"]').trigger('click');
    expect(w.emitted('change-status')?.[0]).toEqual([TaskStatusValue.TODO]);
  });
});