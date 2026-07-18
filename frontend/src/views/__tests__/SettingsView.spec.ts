import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { ref, type Ref } from 'vue';
import ElementPlus from 'element-plus';
import SettingsView from '@/views/SettingsView.vue';
import { useAuthStore } from '@/stores/auth';

// useBreakpoints：桌面态避免移动 drawer 分支干扰断言。
vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core');
  return {
    ...actual,
    useBreakpoints: vi.fn(),
  };
});

import { useBreakpoints } from '@vueuse/core';

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/settings', name: 'settings', component: SettingsView },
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

function mountView() {
  stubDesktop();
  const router = makeRouter();
  const auth = useAuthStore();
  auth.setUser({ id: 1, email: 'alice@example.com', nickname: '小爱' });
  auth.setTokens('access', 'refresh');
  return mount(SettingsView, {
    global: { plugins: [ElementPlus, router] },
  });
}

beforeEach(() => {
  setActivePinia(createPinia());
  document.body.innerHTML = '';
});

describe('SettingsView', () => {
  describe('顶栏', () => {
    it('含 TopBar（与 HomeView 一致）', () => {
      const w = mountView();
      expect(w.find('[data-testid="topbar-brand"]').exists()).toBe(true);
    });
  });

  describe('空态', () => {
    it('展示「设置」主标题', () => {
      const w = mountView();
      const main = w.find('[data-testid="settings-empty"]');
      expect(main.exists()).toBe(true);
      expect(main.text()).toContain('设置');
    });

    it('展示「敬请期待」或「即将推出」占位文案', () => {
      const w = mountView();
      const main = w.find('[data-testid="settings-empty"]');
      const text = main.text();
      // 占位文案二选一即可（具体由实现决定）
      expect(text.includes('敬请期待') || text.includes('即将推出')).toBe(true);
    });
  });

  describe('返回入口', () => {
    it('含返回首页 router-link（data-testid="settings-back-home"）', () => {
      const w = mountView();
      const link = w.find('a[data-testid="settings-back-home"]');
      expect(link.exists()).toBe(true);
      expect(link.attributes('href')).toBe('/');
    });
  });
});
