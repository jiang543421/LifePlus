import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import PlaceholderCard from '@/components/PlaceholderCard.vue';

function mountCard(props: { title: string; icon: string }) {
  return mount(PlaceholderCard, {
    props,
    global: { plugins: [ElementPlus] },
  });
}

describe('PlaceholderCard', () => {
  describe('渲染', () => {
    it('渲染 <button class="placeholder-card placeholder-card--placeholder"> 包裹 icon + title', () => {
      const w = mountCard({ title: '日报', icon: 'EditPen' });
      const btn = w.find('button.placeholder-card');
      expect(btn.exists()).toBe(true);
      expect(btn.classes()).toContain('placeholder-card--placeholder');
      expect(btn.text()).toContain('日报');
    });

    it('含 data-testid="placeholder-card-button" 便于集成测试 hook', () => {
      const w = mountCard({ title: '消费', icon: 'Wallet' });
      const btn = w.find('[data-testid="placeholder-card-button"]');
      expect(btn.exists()).toBe(true);
    });

    it('展示 title 文案', () => {
      const w = mountCard({ title: 'AI 分析', icon: 'DataAnalysis' });
      expect(w.find('.placeholder-card__title').text()).toBe('AI 分析');
    });

    it('不渲染任何 router-link（占位卡不应触发路由跳转）', () => {
      const w = mountCard({ title: '饮食', icon: 'KnifeFork' });
      expect(w.find('a').exists()).toBe(false);
    });
  });

  describe('点击行为', () => {
    it('点击 button emit placeholder-click 一次', async () => {
      const w = mountCard({ title: '日报', icon: 'EditPen' });
      await w.find('button.placeholder-card').trigger('click');
      expect(w.emitted('placeholder-click')).toBeTruthy();
      expect(w.emitted('placeholder-click')).toHaveLength(1);
    });

    it('多次点击触发多次 emit', async () => {
      const w = mountCard({ title: '消费', icon: 'Wallet' });
      const btn = w.find('button.placeholder-card');
      await btn.trigger('click');
      await btn.trigger('click');
      await btn.trigger('click');
      expect(w.emitted('placeholder-click')).toHaveLength(3);
    });
  });

  describe('icon 渲染', () => {
    it('icon 通过动态组件渲染（外壳 .placeholder-card__icon）', () => {
      const w = mountCard({ title: '日报', icon: 'EditPen' });
      const iconBox = w.find('.placeholder-card__icon');
      expect(iconBox.exists()).toBe(true);
      // Element Plus Icon 渲染为 svg
      expect(iconBox.find('svg').exists()).toBe(true);
    });

    it('未知 icon 名降级为 ElementPlus 占位 icon（不抛错）', () => {
      expect(() =>
        mountCard({ title: '???', icon: 'NoSuchIcon' }),
      ).not.toThrow();
    });
  });
});
