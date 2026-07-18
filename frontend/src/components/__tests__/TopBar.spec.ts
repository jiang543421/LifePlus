import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { ref, type Ref } from 'vue';
import ElementPlus from 'element-plus';
import TopBar from '@/components/TopBar.vue';
import { useAuthStore } from '@/stores/auth';

// useBreakpoints 在 jsdom 下依赖 matchMedia；测试中替换为可注入的 ref，
// 避免引入 matchMedia polyfill 也避免跨用例状态泄漏。
vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core');
  return {
    ...actual,
    useBreakpoints: vi.fn(),
  };
});

// best-effort: TopBar.logout 路径会调 authApi.logout，测试环境无后端，stub 掉。
vi.mock('@/api/auth', async () => {
  const actual = await vi.importActual<typeof import('@/api/auth')>('@/api/auth');
  return {
    ...actual,
    authApi: {
      ...actual.authApi,
      logout: vi.fn().mockResolvedValue({ code: 0, message: 'ok', data: null }),
    },
  };
});

import { useBreakpoints } from '@vueuse/core';

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' }, meta: { public: true } },
      { path: '/settings', name: 'settings', component: { template: '<div />' } },
    ],
  });
}

/** 注入响应式断点：默认桌面；传 true 切移动。 */
function stubBreakpoint(isMobile: boolean) {
  const mobile: Ref<boolean> = ref(isMobile);
  (useBreakpoints as Mock).mockReturnValue({
    smaller: () => mobile,
    greaterOrEqual: () => ref(!isMobile),
  });
}

function mountTopBar(opts?: { isMobile?: boolean }) {
  stubBreakpoint(opts?.isMobile ?? false);
  const router = makeRouter();
  // pinia 通过 beforeEach 的 setActivePinia 激活，组件内 useAuthStore()
  // 与外部 seedUser / spy 共享同一实例。
  return mount(TopBar, {
    global: { plugins: [ElementPlus, router] },
  });
}

function seedUser(user: { id: number; email: string; nickname: string | null }) {
  const auth = useAuthStore();
  auth.setUser(user);
  auth.setTokens('access', 'refresh');
}

beforeEach(() => {
  setActivePinia(createPinia());
  // Teleport to body 的 drawer DOM 不会随 wrapper unmount 自动清理，手动清避免污染。
  document.body.innerHTML = '';
});

describe('TopBar', () => {
  describe('中央品牌区', () => {
    it('展示品牌名 "LifePulse" 与副标 "数字生活"', () => {
      const w = mountTopBar();
      expect(w.text()).toContain('LifePulse');
      expect(w.text()).toContain('数字生活');
    });
  });

  describe('右侧设置入口', () => {
    it('桌面态渲染 router-link 到 /settings，含 data-testid', () => {
      const w = mountTopBar({ isMobile: false });
      const link = w.find('a[data-testid="topbar-settings-link"]');
      expect(link.exists()).toBe(true);
      expect(link.attributes('href')).toBe('/settings');
    });

    it('移动态在 Drawer 内也提供设置入口（router-link /settings）', async () => {
      const w = mountTopBar({ isMobile: true });
      // 点击汉堡打开 Drawer
      await w.find('[data-testid="topbar-hamburger"]').trigger('click');
      await flushPromises();
      // drawer Teleport 到 body，找最新 drawer 内的设置链接
      const drawers = document.body.querySelectorAll('[data-testid="topbar-drawer"]');
      const drawer = drawers[drawers.length - 1];
      const link = drawer?.querySelector('a[data-testid="topbar-settings-link"]');
      expect(link).not.toBeNull();
      expect((link as HTMLAnchorElement).getAttribute('href')).toBe('/settings');
    });
  });

  describe('左侧头像 + 首字母', () => {
    it('有 nickname 时显示其首字符', () => {
      seedUser({ id: 1, email: 'alice@example.com', nickname: '张三' });
      const w = mountTopBar();
      const avatar = w.find('[data-testid="topbar-avatar"]');
      expect(avatar.exists()).toBe(true);
      expect(avatar.text()).toBe('张');
    });

    it('无 nickname 时回退到 email 首字符（大写）', () => {
      seedUser({ id: 1, email: 'bob@example.com', nickname: null });
      const w = mountTopBar();
      const avatar = w.find('[data-testid="topbar-avatar"]');
      expect(avatar.text()).toBe('B');
    });

    it('nickname 与 email 均缺失时显示 ?', () => {
      const auth = useAuthStore();
      auth.setUser({ id: 1, email: '', nickname: null });
      const w = mountTopBar();
      const avatar = w.find('[data-testid="topbar-avatar"]');
      expect(avatar.text()).toBe('?');
    });
  });

  describe('桌面态 ElDropdown 账号菜单', () => {
    it('点击头像展开 dropdown：含邮箱（掩码）+ 昵称 + 退出登录', async () => {
      seedUser({ id: 1, email: 'alice@example.com', nickname: '张三' });
      const w = mountTopBar({ isMobile: false });
      // ElDropdown 通过 teleport 渲染到 body，点击 trigger 后内容才出现
      await w.find('[data-testid="topbar-avatar-trigger"]').trigger('click');
      await flushPromises();
      const bodyText = document.body.textContent ?? '';
      expect(bodyText).toContain('al***@example.com');
      expect(bodyText).toContain('张三');
      expect(document.body.querySelector('[data-testid="topbar-logout"]')).not.toBeNull();
    });

    it('email 缺失 @ 时掩码为空字符串（不影响 dropdown 渲染）', async () => {
      seedUser({ id: 1, email: 'no-at-sign', nickname: 'X' });
      const w = mountTopBar({ isMobile: false });
      await w.find('[data-testid="topbar-avatar-trigger"]').trigger('click');
      await flushPromises();
      const bodyText = document.body.textContent ?? '';
      expect(bodyText).toContain('X');
      // 无 @ 时掩码输出 ''，但不影响昵称展示
      expect(bodyText).not.toContain('no');
    });

    it('点击「退出登录」调用 authStore.logout 并跳 /login', async () => {
      const auth = useAuthStore();
      auth.setUser({ id: 1, email: 'a@b.com', nickname: 'A' });
      auth.setTokens('access', 'refresh');
      const logoutSpy = vi.spyOn(auth, 'logout').mockResolvedValue(undefined);

      const w = mountTopBar({ isMobile: true });
      await w.find('[data-testid="topbar-hamburger"]').trigger('click');
      await flushPromises();
      const logoutBtn = document.body.querySelector(
        '[data-testid="topbar-logout"]',
      ) as HTMLButtonElement | null;
      expect(logoutBtn).not.toBeNull();
      await logoutBtn!.click();
      await flushPromises();
      expect(logoutSpy).toHaveBeenCalledTimes(1);
    });
  });

  describe('响应式断点', () => {
    it('桌面 (≥768px): 头像 dropdown 可见 + 汉堡按钮不存在', () => {
      seedUser({ id: 1, email: 'a@b.com', nickname: 'A' });
      const w = mountTopBar({ isMobile: false });
      // 桌面：头像 dropdown 触发器可见
      expect(w.find('[data-testid="topbar-avatar-trigger"]').exists()).toBe(true);
      // 汉堡按钮不应渲染
      expect(w.find('[data-testid="topbar-hamburger"]').exists()).toBe(false);
    });

    it('移动 (<768px): 汉堡按钮可见 + 头像 dropdown 触发器隐藏', () => {
      seedUser({ id: 1, email: 'a@b.com', nickname: 'A' });
      const w = mountTopBar({ isMobile: true });
      expect(w.find('[data-testid="topbar-hamburger"]').exists()).toBe(true);
      expect(w.find('[data-testid="topbar-avatar-trigger"]').exists()).toBe(false);
    });

    it('移动态点击汉堡打开 Drawer，展示账号信息 + 退出登录', async () => {
      seedUser({ id: 1, email: 'a@b.com', nickname: 'A' });
      const w = mountTopBar({ isMobile: true });
      await w.find('[data-testid="topbar-hamburger"]').trigger('click');
      await flushPromises();
      const drawer = document.body.querySelector('[data-testid="topbar-drawer"]');
      expect(drawer).not.toBeNull();
      expect(drawer!.textContent).toContain('A');
      expect(drawer!.querySelector('[data-testid="topbar-logout"]')).not.toBeNull();
    });
  });
});
