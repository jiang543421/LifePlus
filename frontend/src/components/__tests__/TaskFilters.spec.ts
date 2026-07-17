import { describe, it, expect } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import TaskFilters from '@/components/TaskFilters.vue';
import type { TaskFilter } from '@/types';

function mountFilters(filter: Partial<TaskFilter> = { page: 1, size: 20 }) {
  const full: TaskFilter = { page: 1, size: 20, ...filter };
  return mount(TaskFilters, { props: { filter: full }, global: { plugins: [ElementPlus] } });
}

describe('TaskFilters', () => {
  it('渲染全部 5 个过滤控件 + 重置按钮', () => {
    const w = mountFilters();
    expect(w.find('[data-testid="filter-status"]').exists()).toBe(true);
    expect(w.find('[data-testid="filter-priority"]').exists()).toBe(true);
    expect(w.find('[data-testid="filter-tag"]').exists()).toBe(true);
    expect(w.find('[data-testid="filter-due-from"]').exists()).toBe(true);
    expect(w.find('[data-testid="filter-due-to"]').exists()).toBe(true);
    expect(w.find('[data-testid="filter-reset"]').exists()).toBe(true);
  });

  it('外部 filter 变更时同步本地副本（status / tag 反映新值）', async () => {
    const w = mountFilters({ status: 0, tag: 'old' });
    await w.setProps({ filter: { page: 1, size: 20, status: 1, tag: 'new', dueFrom: '2026-08-01' } });
    await nextTick();
    const input = w.find('[data-testid="filter-tag"] input');
    expect(input.exists()).toBe(true);
    expect((input.element as HTMLInputElement).value).toBe('new');
  });

  it('点击重置 emit reset', async () => {
    const w = mountFilters({ status: 0, tag: 'work' });
    await w.find('[data-testid="filter-reset"]').trigger('click');
    expect(w.emitted('reset')).toBeTruthy();
    expect(w.emitted('reset')?.length).toBe(1);
  });

  it('输入标签 emit update:filter（仅含真实字段）', async () => {
    const w = mountFilters();
    const input = w.find('[data-testid="filter-tag"] input');
    expect(input.exists()).toBe(true);
    await input.setValue('work');
    expect(w.emitted('update:filter')?.[0]).toEqual([{ tag: 'work' }]);
  });

  it('未变化时不 emit update:filter', () => {
    const w = mountFilters();
    expect(w.emitted('update:filter')).toBeFalsy();
  });

  it('reset 后本地输入值清空', async () => {
    const w = mountFilters({ tag: 'work' });
    const input = w.find('[data-testid="filter-tag"] input');
    expect((input.element as HTMLInputElement).value).toBe('work');
    await w.find('[data-testid="filter-reset"]').trigger('click');
    await nextTick();
    expect((w.find('[data-testid="filter-tag"] input').element as HTMLInputElement).value).toBe('');
  });
});