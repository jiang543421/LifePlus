import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import TriStateError from '@/components/TriStateError.vue';

describe('TriStateError', () => {
  it('默认 description + 默认 testId 渲染一个错误态容器（含 ⚠️ + 重试按钮）', () => {
    const w = mount(TriStateError, {
      global: { plugins: [ElementPlus] },
    });
    const box = w.find('[data-testid="tristate-error"]');
    expect(box.exists()).toBe(true);
    // 默认文案（来自 v1.2.5 #3 DailyView 错误态）
    expect(box.text()).toContain('暂时无法获取数据，请稍后重试');
    // ⚠️ icon 渲染
    expect(box.text()).toContain('⚠️');
    // 默认重试按钮
    const retry = box.find('[data-testid="tristate-error-retry"]');
    expect(retry.exists()).toBe(true);
    expect(retry.text()).toContain('重试');
  });

  it('description prop 透传：自定义 description 替换默认', () => {
    const w = mount(TriStateError, {
      props: { description: '任务加载失败' },
      global: { plugins: [ElementPlus] },
    });
    expect(w.text()).toContain('任务加载失败');
    expect(w.text()).not.toContain('暂时无法获取数据');
  });

  it('testId prop 透传：自定义 testId 应用到容器 + retry 按钮', () => {
    const w = mount(TriStateError, {
      props: { testId: 'task-list-error' },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="task-list-error"]').exists()).toBe(true);
    expect(w.find('[data-testid="task-list-error-retry"]').exists()).toBe(true);
    // 默认 testId 不存在
    expect(w.find('[data-testid="tristate-error"]').exists()).toBe(false);
    expect(w.find('[data-testid="tristate-error-retry"]').exists()).toBe(false);
  });

  it('retry 按钮点击触发 emit("retry")，且只触发一次', async () => {
    const w = mount(TriStateError, {
      global: { plugins: [ElementPlus] },
    });
    const retry = w.find('[data-testid="tristate-error-retry"]');
    await retry.trigger('click');
    expect(w.emitted('retry')).toBeTruthy();
    expect(w.emitted('retry')).toHaveLength(1);
  });

  it('retryLabel prop 透传：自定义按钮文案（默认「重试」）', () => {
    const w = mount(TriStateError, {
      props: { retryLabel: '重新加载' },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="tristate-error-retry"]').text()).toBe('重新加载');
  });

  it('testId + description + retryLabel 三 prop 同时生效', () => {
    const w = mount(TriStateError, {
      props: {
        testId: 'daily-view-error',
        description: '暂时无法获取日报数据，请稍后重试',
        retryLabel: '重试',
      },
      global: { plugins: [ElementPlus] },
    });
    const box = w.find('[data-testid="daily-view-error"]');
    expect(box.exists()).toBe(true);
    expect(box.text()).toContain('暂时无法获取日报数据');
    expect(box.find('[data-testid="daily-view-error-retry"]').exists()).toBe(true);
  });
});