import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import TriStateEmpty from '@/components/TriStateEmpty.vue';

describe('TriStateEmpty', () => {
  it('默认 description + 默认 testId 渲染一个空态容器', () => {
    const w = mount(TriStateEmpty, {
      global: { plugins: [ElementPlus] },
    });
    const box = w.find('[data-testid="tristate-empty"]');
    expect(box.exists()).toBe(true);
    // 默认文案
    expect(box.text()).toContain('暂无数据');
  });

  it('description prop 透传：自定义 description 替换默认', () => {
    const w = mount(TriStateEmpty, {
      props: { description: '还没有任务，点右上角新建一个吧' },
      global: { plugins: [ElementPlus] },
    });
    expect(w.text()).toContain('还没有任务，点右上角新建一个吧');
    expect(w.text()).not.toContain('暂无数据');
  });

  it('testId prop 透传：自定义 testId 渲染到外层容器', () => {
    const w = mount(TriStateEmpty, {
      props: { testId: 'task-list-empty' },
      global: { plugins: [ElementPlus] },
    });
    expect(w.find('[data-testid="task-list-empty"]').exists()).toBe(true);
    // 默认 testId 不存在
    expect(w.find('[data-testid="tristate-empty"]').exists()).toBe(false);
  });

  it('testId + description 同时生效（同时命名空间隔离 + 文案）', () => {
    const w = mount(TriStateEmpty, {
      props: { testId: 'day-empty', description: '当天没有事件' },
      global: { plugins: [ElementPlus] },
    });
    const box = w.find('[data-testid="day-empty"]');
    expect(box.exists()).toBe(true);
    expect(box.text()).toContain('当天没有事件');
  });

  it('default slot 透传：可塞入自定义 action（按钮）', () => {
    const w = mount(TriStateEmpty, {
      props: { description: '暂无分析数据' },
      slots: {
        default: '<button data-testid="custom-action">立即刷新</button>',
      },
      global: { plugins: [ElementPlus] },
    });
    const action = w.find('[data-testid="custom-action"]');
    expect(action.exists()).toBe(true);
    expect(action.text()).toBe('立即刷新');
  });

  it('不带 slot 也能正常渲染（默认 slot 是可选的）', () => {
    expect(() =>
      mount(TriStateEmpty, {
        global: { plugins: [ElementPlus] },
      }),
    ).not.toThrow();
  });
});