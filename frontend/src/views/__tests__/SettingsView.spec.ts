import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import { ref, type Ref } from 'vue';
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus';
import SettingsView from '@/views/SettingsView.vue';
import LoginView from '@/views/LoginView.vue';
import { useAuthStore } from '@/stores/auth';
import type { UserResponse } from '@/types';

vi.mock('@/api/auth', () => ({
  authApi: {
    register: vi.fn(),
    login: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    updateProfile: vi.fn(),
    changePassword: vi.fn(),
    deleteAccount: vi.fn(),
  },
}));

vi.mock('@/utils/error', () => ({
  showAuthError: vi.fn(),
  authErrorMessage: vi.fn(),
}));

// useBreakpoints：桌面态避免移动 drawer 分支干扰断言。
vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core');
  return {
    ...actual,
    useBreakpoints: vi.fn(),
  };
});

import { useBreakpoints } from '@vueuse/core';
import { authApi } from '@/api/auth';
import { showAuthError } from '@/utils/error';

const LS_ACCESS = 'lp_access_token';
const LS_REFRESH = 'lp_refresh_token';
const LS_USER = 'lp_user';

const mockUser: UserResponse = { id: 1, email: 'alice@example.com', nickname: '小爱' };

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/settings', name: 'settings', component: SettingsView },
      { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
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

async function mountView() {
  stubDesktop();
  localStorage.clear();
  const router = makeRouter();
  const auth = useAuthStore();
  auth.setUser(mockUser);
  auth.setTokens('access', 'refresh');
  await router.push('/settings');
  await router.isReady();
  const wrapper = mount(SettingsView, {
    global: { plugins: [ElementPlus, router] },
  });
  return { wrapper, router, auth };
}

/** el-form-item 在 jsdom 下挂载校验器时机不可靠，预校验是组件内置兜底；
 *  组件以 await validate()（Promise 形式）调用：
 *   - ok=true  → resolve（校验通过，继续业务）
 *   - ok=false → reject（被业务 try/catch 静默 return，与 ElForm 真实行为一致） */
function stubFormValidate(wrapper: ReturnType<typeof mount>, formRefName: string, ok = true) {
  const ref = (wrapper.vm.$refs as Record<string, unknown>)[formRefName] as { validate?: (...args: unknown[]) => unknown } | undefined;
  if (ref?.validate) {
    ref.validate = () => (ok ? Promise.resolve() : Promise.reject(new Error('validate-fail')));
  }
}

beforeEach(() => {
  setActivePinia(createPinia());
  localStorage.clear();
  vi.mocked(authApi.updateProfile).mockReset();
  vi.mocked(authApi.changePassword).mockReset();
  vi.mocked(authApi.deleteAccount).mockReset();
  vi.mocked(showAuthError).mockReset();
  // ElMessageBox.prompt / .confirm 是真实导出方法，spyOn 用 vi.fn 替换。
  vi.spyOn(ElMessageBox, 'prompt').mockImplementation(() => Promise.reject(new Error('cancel')) as never);
  vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => Promise.reject(new Error('cancel')) as never);
  document.body.innerHTML = '';
});

describe('SettingsView', () => {
  it('顶栏含 TopBar 与返回首页链接', async () => {
    const { wrapper } = await mountView();
    expect(wrapper.find('[data-testid="topbar-brand"]').exists()).toBe(true);
    const link = wrapper.find('a[data-testid="settings-back-home"]');
    expect(link.exists()).toBe(true);
    expect(link.attributes('href')).toBe('/');
  });

  it('渲染三段卡片：资料 / 安全 / 危险操作', async () => {
    const { wrapper } = await mountView();
    expect(wrapper.find('[data-testid="settings-profile-card"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-password-card"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="settings-danger-card"]').exists()).toBe(true);
  });

  it('资料卡片初值采用 store.user.nickname', async () => {
    const { wrapper } = await mountView();
    const input = wrapper.find('[data-testid="settings-nickname-input"] input');
    expect((input.element as HTMLInputElement).value).toBe('小爱');
  });
});

describe('SettingsView / 资料（改昵称）', () => {
  it('保存新昵称 → 调 auth.updateProfile + 提示成功', async () => {
    vi.mocked(authApi.updateProfile).mockResolvedValue({ id: 1, email: 'alice@example.com', nickname: '新昵称' });
    const { wrapper } = await mountView();
    stubFormValidate(wrapper, 'profileFormRef', true);

    const input = wrapper.find('[data-testid="settings-nickname-input"] input');
    await input.setValue('新昵称');
    await wrapper.find('[data-testid="settings-profile-submit"]').trigger('click');
    await flushPromises();

    expect(authApi.updateProfile).toHaveBeenCalledWith({ nickname: '新昵称' });
  });

  it('输入空白后保存 → 调 updateProfile({ nickname: null })', async () => {
    vi.mocked(authApi.updateProfile).mockResolvedValue({ id: 1, email: 'alice@example.com', nickname: null });
    const { wrapper } = await mountView();
    stubFormValidate(wrapper, 'profileFormRef', true);

    const input = wrapper.find('[data-testid="settings-nickname-input"] input');
    await input.setValue('   ');
    await wrapper.find('[data-testid="settings-profile-submit"]').trigger('click');
    await flushPromises();

    expect(authApi.updateProfile).toHaveBeenCalledWith({ nickname: null });
  });

  it('昵称未变更时不调 API', async () => {
    const { wrapper } = await mountView();
    stubFormValidate(wrapper, 'profileFormRef', true);
    // store 中已经是「小爱」，不动 input
    await wrapper.find('[data-testid="settings-profile-submit"]').trigger('click');
    await flushPromises();
    expect(authApi.updateProfile).not.toHaveBeenCalled();
  });

  it('保存失败 → 调 showAuthError(1001)', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(authApi.updateProfile).mockRejectedValue(new ApiError(1001, 'invalid'));
    const { wrapper } = await mountView();
    stubFormValidate(wrapper, 'profileFormRef', true);

    const input = wrapper.find('[data-testid="settings-nickname-input"] input');
    await input.setValue('新昵称');
    await wrapper.find('[data-testid="settings-profile-submit"]').trigger('click');
    await flushPromises();

    expect(showAuthError).toHaveBeenCalledWith(1001);
  });
});

describe('SettingsView / 安全（改密码）', () => {
  it('输入合法当前/新密码 → 调 auth.changePassword + 跳 /login?reason=password-changed', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue(undefined);
    const { wrapper, router } = await mountView();
    stubFormValidate(wrapper, 'pwFormRef', true);

    await wrapper.find('[data-testid="settings-old-password-input"] input').setValue('Old12345');
    await wrapper.find('[data-testid="settings-new-password-input"] input').setValue('New12345');
    await wrapper.find('[data-testid="settings-password-submit"]').trigger('click');
    await flushPromises();

    expect(authApi.changePassword).toHaveBeenCalledWith({ oldPassword: 'Old12345', newPassword: 'New12345' });
    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.reason).toBe('password-changed');
  });

  it('新密码不满足规则 → 不调 API', async () => {
    const { wrapper } = await mountView();
    stubFormValidate(wrapper, 'pwFormRef', false); // 模拟 ElForm 校验失败

    await wrapper.find('[data-testid="settings-old-password-input"] input').setValue('Old12345');
    await wrapper.find('[data-testid="settings-new-password-input"] input').setValue('short');
    await wrapper.find('[data-testid="settings-password-submit"]').trigger('click');
    await flushPromises();
    expect(authApi.changePassword).not.toHaveBeenCalled();
  });

  it('旧密码错（ApiError 1002）→ 调 showAuthError(1002)', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(authApi.changePassword).mockRejectedValue(new ApiError(1002, 'wrong old'));
    const { wrapper } = await mountView();
    stubFormValidate(wrapper, 'pwFormRef', true);

    await wrapper.find('[data-testid="settings-old-password-input"] input').setValue('Wrong1234');
    await wrapper.find('[data-testid="settings-new-password-input"] input').setValue('New12345');
    await wrapper.find('[data-testid="settings-password-submit"]').trigger('click');
    await flushPromises();

    expect(showAuthError).toHaveBeenCalledWith(1002);
  });

  it('PasswordRules 实时提示：根据输入更新 ok 状态', async () => {
    const { wrapper } = await mountView();
    const rulesBefore = wrapper.findAll('.password-rules li');
    expect(rulesBefore.length).toBe(3);
    expect(rulesBefore.every((li) => !li.classes().includes('ok'))).toBe(true);

    await wrapper.find('[data-testid="settings-new-password-input"] input').setValue('New12345');
    await flushPromises();
    const rulesAfter = wrapper.findAll('.password-rules li');
    expect(rulesAfter.every((li) => li.classes().includes('ok'))).toBe(true);
  });
});

describe('SettingsView / 危险操作（注销账号）', () => {
  it('prompt + confirm 都通过 → 调 auth.deleteAccount + 跳 /login?reason=account-deleted', async () => {
    vi.mocked(authApi.deleteAccount).mockResolvedValue(undefined);
    vi.mocked(ElMessageBox.prompt).mockResolvedValue({ value: 'MyPass123' } as never);
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never);

    const { wrapper, router } = await mountView();
    await wrapper.find('[data-testid="settings-delete-account-btn"]').trigger('click');
    await flushPromises();

    expect(authApi.deleteAccount).toHaveBeenCalledWith({ password: 'MyPass123' });
    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.reason).toBe('account-deleted');
  });

  it('用户在 prompt 取消 → 不调 API 不跳转', async () => {
    // beforeEach 已默认 mock 为 cancel
    const { wrapper, router } = await mountView();
    await wrapper.find('[data-testid="settings-delete-account-btn"]').trigger('click');
    await flushPromises();

    expect(authApi.deleteAccount).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('settings');
  });

  it('在 confirm 阶段取消 → 不调 API 不跳转', async () => {
    vi.mocked(ElMessageBox.prompt).mockResolvedValue({ value: 'MyPass123' } as never);
    vi.mocked(ElMessageBox.confirm).mockRejectedValue(new Error('cancel') as never);

    const { wrapper, router } = await mountView();
    await wrapper.find('[data-testid="settings-delete-account-btn"]').trigger('click');
    await flushPromises();

    expect(authApi.deleteAccount).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('settings');
  });

  it('deleteAccount 抛 ApiError(1002 密码错) → 调 showAuthError(1002)', async () => {
    const { ApiError } = await import('@/api/http');
    vi.mocked(authApi.deleteAccount).mockRejectedValue(new ApiError(1002, 'wrong password'));
    vi.mocked(ElMessageBox.prompt).mockResolvedValue({ value: 'Wrong1234' } as never);
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never);

    const { wrapper, router } = await mountView();
    await wrapper.find('[data-testid="settings-delete-account-btn"]').trigger('click');
    await flushPromises();

    expect(showAuthError).toHaveBeenCalledWith(1002);
    expect(router.currentRoute.value.name).toBe('settings');
  });
});