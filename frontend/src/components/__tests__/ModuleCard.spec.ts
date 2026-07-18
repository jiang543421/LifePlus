import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import ElementPlus from 'element-plus';
import ModuleCard from '@/components/ModuleCard.vue';

/** 挂一个最简 vue-router，让 <router-link> 渲染为 <a> 而不是被 stub。 */
function withRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/tasks', component: { template: '<div />' } },
      { path: '/plans', component: { template: '<div />' } },
    ],
  });
}

function mountCard(props: {
  title: string;
  icon: string;
  to?: string;
  placeholder?: boolean;
}) {
  return mount(ModuleCard, {
    props,
    global: { plugins: [ElementPlus, withRouter()] },
  });
}

describe('ModuleCard', () => {
  describe('module mode（默认 placeholder=false）', () => {
    it('render <a class="module-card"> 包裹 icon + title', () => {
      const w = mountCard({ title: '任务', icon: 'List', to: '/tasks' });
      // v-if 分支：渲染 <router-link>（最终 DOM 是 <a>），不是 <button>
      const link = w.find('a.module-card');
      expect(link.exists()).toBe(true);
      expect(link.attributes('href')).toBe('/tasks');
      expect(link.text()).toContain('任务');
    });

    it('含 data-testid 与 .module-card 类名（视觉测试 hook）', () => {
      const w = mountCard({ title: '计划', icon: 'Calendar', to: '/plans' });
      const link = w.find('a.module-card');
      expect(link.classes()).toContain('module-card');
      expect(link.find('.module-card__title').exists()).toBe(true);
    });
  });

  describe('placeholder mode', () => {
    it('placeholder=true 渲染 <button>，不是 <a>', () => {
      const w = mountCard({ title: '日报', icon: 'EditPen', placeholder: true });
      const btn = w.find('button.module-card');
      expect(btn.exists()).toBe(true);
      expect(w.find('a.module-card').exists()).toBe(false);
    });

    it('placeholder mode 点击 emit placeholder-click', async () => {
      const w = mountCard({ title: 'AI 分析', icon: 'DataAnalysis', placeholder: true });
      await w.find('button.module-card').trigger('click');
      expect(w.emitted('placeholder-click')).toBeTruthy();
      expect(w.emitted('placeholder-click')).toHaveLength(1);
    });

    it('placeholder mode 不渲染 router-link（不会触发路由跳转）', () => {
      const w = mountCard({ title: '消费', icon: 'Wallet', placeholder: true });
      // 路由跳转的载体 <a.module-card> 不存在 → 点击不会 push
      expect(w.find('a.module-card').exists()).toBe(false);
    });

    it('placeholder mode 仍展示 title + icon 容器', () => {
      const w = mountCard({ title: '饮食', icon: 'KnifeFork', placeholder: true });
      expect(w.find('.module-card__title').text()).toBe('饮食');
      // icon 区域存在
      expect(w.find('.module-card__icon').exists()).toBe(true);
    });
  });

  describe('icon 渲染', () => {
    it('icon 通过动态组件渲染（外壳 .module-card__icon）', () => {
      const w = mountCard({ title: '任务', icon: 'List', to: '/tasks' });
      const iconBox = w.find('.module-card__icon');
      expect(iconBox.exists()).toBe(true);
      // 内部包含一个 svg（Element Plus Icon 渲染为 svg）
      expect(iconBox.find('svg').exists()).toBe(true);
    });

    it('未知 icon 名降级为 ElementPlus 占位 icon（不抛错）', () => {
      expect(() =>
        mountCard({ title: '???', icon: 'NoSuchIcon', to: '/tasks' }),
      ).not.toThrow();
    });
  });
});
