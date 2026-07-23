import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { ref, type Ref } from 'vue';
import ElementPlus, { ElMessage } from 'element-plus';
import HomeView from '@/views/HomeView.vue';
import { useAuthStore } from '@/stores/auth';
import { HOME_CARDS } from '@/types/home';

// useBreakpoints 在 jsdom 下依赖 matchMedia；测试中替换为桌面（≥768px），
// 避免 Element Plus drawer / dropdown 在 jsdom 下渲染分支不可控。
vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core');
  return {
    ...actual,
    useBreakpoints: vi.fn(),
  };
});

// HomeView 占位卡点击调 ElMessage.warning；spy 拦截避免真实弹窗。
const warningSpy = vi.fn();
vi.spyOn(ElMessage, 'warning').mockImplementation(warningSpy as never);

import { useBreakpoints } from '@vueuse/core';

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/tasks', name: 'tasks', component: { template: '<div />' } },
      { path: '/plans', name: 'plans', component: { template: '<div />' } },
      { path: '/settings', name: 'settings', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' }, meta: { public: true } },
    ],
  });
}

function stubDesktop() {
  const desktop: Ref<boolean> = ref(true);
  (useBreakpoints as Mock).mockReturnValue({
    smaller: () => ref(false),
    greaterOrEqual: () => desktop,
  });
}

function mountHome() {
  stubDesktop();
  const router = makeRouter();
  const auth = useAuthStore();
  auth.setUser({ id: 1, email: 'alice@example.com', nickname: '小爱' });
  auth.setTokens('access', 'refresh');
  return mount(HomeView, {
    global: { plugins: [ElementPlus, router] },
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  // TopBar 的 Teleport drawer / dropdown 会把 DOM 写到 body
  document.body.innerHTML = '';
  warningSpy.mockClear();
});

describe('HomeView', () => {
  describe('顶栏', () => {
    it('包含 TopBar（data-testid="topbar-brand"）', () => {
      const w = mountHome();
      expect(w.find('[data-testid="topbar-brand"]').exists()).toBe(true);
    });
  });

  describe('问候头', () => {
    it('展示时段问候 + 用户名（greeting + greetingName）', () => {
      const w = mountHome();
      const text = w.text();
      // greeting() 返回 '早上好' / '中午好' / '下午好' / '晚上好' / '夜深了'
      expect(
        ['早上好', '中午好', '下午好', '晚上好', '夜深了'].some((g) => text.includes(g)),
      ).toBe(true);
      // greetingName 来自 nickname
      expect(text).toContain('小爱');
    });

    it('展示当前日期行（YYYY-MM-DD 星期X）', () => {
      const w = mountHome();
      const text = w.text();
      // 日期形如 2026-07-18 星期六（zh-cn locale）
      expect(text).toMatch(/\d{4}-\d{2}-\d{2}/);
      expect(text).toMatch(/[一二三四五六日]/);
    });
  });

  describe('卡片网格', () => {
    it('渲染 HOME_CARDS 全部 6 张卡', () => {
      const w = mountHome();
      expect(HOME_CARDS).toHaveLength(6);
      HOME_CARDS.forEach((card) => {
        expect(w.find(`[data-testid="home-card-${card.key}"]`).exists()).toBe(true);
      });
    });

    it('模块卡渲染为 router-link，href 指向 card.to', () => {
      const w = mountHome();
      const taskLink = w.find('[data-testid="home-card-task"] a.module-card');
      expect(taskLink.exists()).toBe(true);
      expect(taskLink.attributes('href')).toBe('/tasks');

      const planLink = w.find('[data-testid="home-card-plan"] a.module-card');
      expect(planLink.attributes('href')).toBe('/plans');

      // 消费 / 饮食 / 日报 卡均已激活为 module（expense v1.2.1、diet v1.2.2、daily v1.2.4）
      const expenseLink = w.find('[data-testid="home-card-expense"] a.module-card');
      expect(expenseLink.exists()).toBe(true);
      expect(expenseLink.attributes('href')).toBe('/expenses');

      const dietLink = w.find('[data-testid="home-card-diet"] a.module-card');
      expect(dietLink.exists()).toBe(true);
      expect(dietLink.attributes('href')).toBe('/diets');

      const dailyLink = w.find('[data-testid="home-card-daily"] a.module-card');
      expect(dailyLink.exists()).toBe(true);
      expect(dailyLink.attributes('href')).toBe('/daily');
    });

    it('占位卡渲染为 button（不渲染 router-link）', () => {
      const w = mountHome();
      // v1.2.4 后只剩 AI 分析占位；任务/计划/消费/饮食/日报 全部激活为 module
      const placeholderKeys = HOME_CARDS.filter((c) => c.kind === 'placeholder').map((c) => c.key);
      expect(placeholderKeys).toEqual(['ai']);
      placeholderKeys.forEach((key) => {
        const wrap = w.find(`[data-testid="home-card-${key}"]`);
        expect(wrap.exists()).toBe(true);
        // 占位卡内部是 button.module-card
        const btn = wrap.find('button.module-card');
        expect(btn.exists()).toBe(true);
        // 包内不渲染 router-link
        expect(wrap.find('a').exists()).toBe(false);
      });
    });
  });

  describe('占位卡点击', () => {
    // v1.2.4 后只剩 AI 卡占位；AI 卡点开走 openAiInsight() 抽屉入口，
    // 不再走「即将上线」toast。AI 卡交互由 v2.1 spec 覆盖（AiDrawer 测试套件）。
    it('点击 AI 占位卡打开 AiDrawer（v-model:show=true）', async () => {
      const w = mountHome();
      const aiCard = w.find('[data-testid="home-card-ai"] button.module-card');
      expect(aiCard.exists()).toBe(true);
      await aiCard.trigger('click');
      await flushPromises();
      // AiDrawer v-model:show=true 时 AiDrawer 渲染到 teleport target；
      // drawer 存在即证明占位卡 click 走 openAiInsight 路径
      // （不再触发 ElMessage.warning）。
      expect(warningSpy).not.toHaveBeenCalled();
    });
  });
});
