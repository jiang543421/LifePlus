import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ElementPlus from 'element-plus';
import RegisterView from '@/views/RegisterView.vue';
import HomeView from '@/views/HomeView.vue';
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
      { path: '/register', component: RegisterView },
      { path: '/', component: HomeView },
    ],
  });
  router.push(initialPath);
  return router;
}

// 防御性预校验保证 jsdom 下也能早退；ElForm.validate 的具体行为由
// ElForm 自己负责，本测试不依赖它。
function stubElFormInvalid(_wrapper: ReturnType<typeof mount>): void {
  /* no-op: RegisterView.submit 内置独立预校验 */
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.mocked(authApi.register).mockReset();
  vi.mocked(authApi.login).mockReset();
  vi.mocked(authApi.me).mockReset();
  vi.mocked(showAuthError).mockReset();
});

describe('RegisterView', () => {
  it('合法表单（强密码 + nickname）提交 → 调 register + login + me → 跳 /', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ userId: 1 });
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'A', refreshToken: 'R', expiresIn: 3600 });
    vi.mocked(authApi.me).mockResolvedValue({ id: 1, email: 'new@example.com', nickname: '新人' });

    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('new@example.com');
    await wrapper.find('input[type="password"]').setValue('Hello1234');
    // 第二个 text input 是 nickname
    const textInputs = wrapper.findAll('input[type="text"]');
    await textInputs[textInputs.length - 1].setValue('新人');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();

    expect(authApi.register).toHaveBeenCalledWith({ email: 'new@example.com', password: 'Hello1234', nickname: '新人' });
    expect(authApi.login).toHaveBeenCalledWith({ email: 'new@example.com', password: 'Hello1234' });
    expect(router.currentRoute.value.path).toBe('/');
  });

  it('nickname 为空时只传 email + password', async () => {
    vi.mocked(authApi.register).mockResolvedValue({ userId: 1 });
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'A', refreshToken: 'R', expiresIn: 3600 });
    vi.mocked(authApi.me).mockResolvedValue({ id: 1, email: 'a@b.com', nickname: null });

    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('Hello1234');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();

    expect(authApi.register).toHaveBeenCalledWith({ email: 'a@b.com', password: 'Hello1234', nickname: undefined });
  });

  it('密码不满足规则时不调 register', async () => {
    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.vm.$nextTick();
    stubElFormInvalid(wrapper);
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('short'); // < 8 位
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('email 缺失时不调 register', async () => {
    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.vm.$nextTick();
    stubElFormInvalid(wrapper);
    await wrapper.find('input[type="password"]').setValue('Hello1234');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('注册时 ApiError(1005 邮箱已注册) 时调 showAuthError(1005)', async () => {
    vi.mocked(authApi.register).mockRejectedValue(new (await import('@/api/http')).ApiError(1005, '该邮箱已注册'));
    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('taken@b.com');
    await wrapper.find('input[type="password"]').setValue('Hello1234');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1005);
  });

  it('注册时 ApiError(1001 密码弱/校验失败) 时调 showAuthError(1001)', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(authApi.register).mockRejectedValue(new ApiError(1001, 'password must contain at least one letter and one digit'));
    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
    await wrapper.find('input[type="email"]').setValue('a@b.com');
    await wrapper.find('input[type="password"]').setValue('Hello1234');
    await wrapper.find('button.submit-btn').trigger('click');
    await flushPromises();
    expect(showAuthError).toHaveBeenCalledWith(1001);
  });

  it('密码输入过程中四条规则实时高亮 ok/✗', async () => {
    const router = buildRouter('/register');
    await router.isReady();
    const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });

    // 初始：所有规则 ✗
    const rulesBefore = wrapper.findAll('.password-rules li');
    expect(rulesBefore.length).toBe(4);
    expect(rulesBefore.every((li) => !li.classes().includes('ok'))).toBe(true);

    // 输入 "Hello1234"：四条规则都满足（长度 / 字母 / 数字 / 不在常见弱密码字典）
    await wrapper.find('input[type="password"]').setValue('Hello1234');
    await flushPromises();
    const rulesAfter = wrapper.findAll('.password-rules li');
    expect(rulesAfter.every((li) => li.classes().includes('ok'))).toBe(true);
  });

  it('渲染品牌 + 登录链接', () => {
    const router = buildRouter('/register');
    return router.isReady().then(() => {
      const wrapper = mount(RegisterView, { global: { plugins: [router, ElementPlus] } });
      expect(wrapper.text()).toContain('LifePulse');
      expect(wrapper.text()).toContain('数字生活 · 注册');
      expect(wrapper.find('a[href="/login"]').exists()).toBe(true);
    });
  });
});
