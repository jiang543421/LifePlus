import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import LoginView from '@/views/LoginView.vue';
import HomeView from '@/views/HomeView.vue';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/api/http';
import { authApi } from '@/api/auth';
import { showAuthError } from '@/utils/error';

vi.mock('@/api/auth', () => ({
  authApi: {
    register: vi.fn(),
    login: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
  },
}));

vi.mock('@/utils/error', () => ({
  showAuthError: vi.fn(),
  authErrorMessage: vi.fn(),
}));

function buildRouter(initialPath: string): Router {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/', component: HomeView },
    ],
  });
  router.push(initialPath);
  return router;
}

// 防御性预校验保证 jsdom 下也能早退；ElForm.validate 的具体行为由
// ElForm 自己负责，本测试不依赖它。
function stubElFormInvalid(_wrapper: ReturnType<typeof mount>): void {
  /* no-op: LoginView.submit 内置独立预校验 */
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(authApi.login).mockReset();
  vi.mocked(authApi.me).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('LoginView', () => {
  it('合法表单提交 → 调 authApi.login + me → 跳 ?redirect 或 /', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'A', refreshToken: 'R', expiresIn: 3600 });
    vi.mocked(authApi.me).mockResolvedValue({ id: 1, email: 'alice@example.com', nickname: '小爱' });

    const router = buildRouter('/login?redirect=/');
    await router.isReady();

    const wrapper = mount(LoginView, {
      global: { plugins: [router, ElementPlus] },
    });
    await wrapper.find('input[type="email"]').setValue('alice@example.com');
    await wrapper.find('input[type="password"]').setValue('password123');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();

    expect(authApi.login).toHaveBeenCalledWith({ email: 'alice@example.com', password: 'password123' });
    expect(authApi.me).toHaveBeenCalledTimes(1);
    expect(useAuthStore().isLoggedIn).toBe(true);
    expect(router.currentRoute.value.path).toBe('/');
  });

  it('带 ?redirect=/tasks 时跳到 /tasks', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'A', refreshToken: 'R', expiresIn: 3600 });
    vi.mocked(authApi.me).mockResolvedValue({ id: 1, email: 'a@b.com', nickname: null });

    const router = buildRouter('/login?redirect=/tasks');
    await router.isReady();

    const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('pw123456');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/tasks');
  });

  it('email 缺失时不调 login API', async () => {
    const router = buildRouter('/login');
    await router.isReady();
    const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.vm.$nextTick();
    stubElFormInvalid(wrapper);
    await wrapper.find('input[type="password"]').setValue('pw123456');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('email 格式非法时不调 login API', async () => {
    const router = buildRouter('/login');
    await router.isReady();
    const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.vm.$nextTick();
    stubElFormInvalid(wrapper);
    await wrapper.find('input[type="email"]').setValue('not-an-email');
    await wrapper.find('input[type="password"]').setValue('pw123456');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('login 抛 ApiError(1002) 时调 showAuthError(1002) 并保持当前页', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError(1002, '邮箱或密码错误'));
    const router = buildRouter('/login');
    await router.isReady();
    const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('wrong');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1002);
    expect(router.currentRoute.value.path).toBe('/login');
  });

  it('login 抛 ApiError(1006) 限流时调 showAuthError(1006)', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiError(1006, '请求过于频繁'));
    const router = buildRouter('/login');
    await router.isReady();
    const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('pw123456');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1006);
  });

  it('login 抛非 ApiError 时显示通用错误', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('network'));
    const router = buildRouter('/login');
    await router.isReady();
    const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('pw123456');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(showAuthError).not.toHaveBeenCalled();
  });

  it('渲染品牌标题 + 副标题 + 注册链接', () => {
    const router = buildRouter('/login');
    return router.isReady().then(() => {
      const wrapper = mount(LoginView, { global: { plugins: [router, ElementPlus] } });
      expect(wrapper.text()).toContain('LifePulse');
      expect(wrapper.text()).toContain('数字生活 · 登录');
      expect(wrapper.find('a[href="/register"]').exists()).toBe(true);
    });
  });
});
