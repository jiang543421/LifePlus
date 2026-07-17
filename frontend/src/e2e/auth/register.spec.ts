/**
 * 注册流 E2E（1.4-E）。
 *
 * <p>客户端预校验（防御性 pre-validation）在 ElForm.validate 之前短路；
 * 所以"邮箱格式错误"、"密码不足"两类用例不发请求、不打到 server。
 */
import { test, expect } from '@playwright/test';
import {
  setupAuthDefaults,
  mockRegisterFailure,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  gotoRegister,
  fillRegisterForm,
  clickSubmit,
  expectAuthErrorToast,
  strongPassword,
  uniqueEmail,
} from '../helpers/test-fixtures';
import { AuthErrorCode } from '@/types';

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

test('注册成功 → 自动 login → 跳 /', async ({ page }) => {
  const user: MockUser = {
    id: 1,
    email: uniqueEmail(),
    nickname: '新用户',
  };
  await setupAuthDefaults(page, { user });

  // 提前注册 listener：等 register 之后连锁 /users/me 完成
  const meResp = page.waitForResponse('**/api/v1/users/me');
  await gotoRegister(page);
  await fillRegisterForm(page, user.email, strongPassword(), user.nickname ?? undefined);
  await clickSubmit(page);
  await meResp;

  await expect(page).toHaveURL('http://localhost:5173/');
  await expect(page.locator('h1')).toContainText('新用户');
});

test('email 已注册（mock 1005）→ 展示「该邮箱已注册」', async ({ page }) => {
  await mockRegisterFailure(page, AuthErrorCode.EmailRegistered, '该邮箱已注册');

  await gotoRegister(page);
  await fillRegisterForm(page, uniqueEmail(), strongPassword());
  await clickSubmit(page);

  // 直接断言 toast——mock 响应极快，waitForResponse 在 click 后注册会错过
  await expectAuthErrorToast(page, AuthErrorCode.EmailRegistered);
  await expect(page).toHaveURL(/\/register/);
});

test('密码不满足规则 → 客户端拦截、不发请求，展示密码规则错误', async ({ page }) => {
  // 如果 register API 被调用，测试立即失败（throw 让响应变成 server error）
  await page.route('**/api/v1/auth/register', async (route) => {
    throw new Error('register API 不应在密码不合规时被调用');
  });

  await gotoRegister(page);
  // 仅 6 位、纯字母 → 不满足"长度 8-64 位、含数字"
  await fillRegisterForm(page, uniqueEmail(), 'abcdef');
  await clickSubmit(page);

  // 等待一次 form 校验触发的 re-render
  await expect(page.locator('.el-form-item__error')).toContainText('密码需满足全部规则');
  // URL 仍在 /register
  await expect(page).toHaveURL(/\/register/);
});

test('email 格式错误 → 客户端拦截、不发请求，展示邮箱格式错误', async ({ page }) => {
  await page.route('**/api/v1/auth/register', async (route) => {
    throw new Error('register API 不应在邮箱格式错误时被调用');
  });

  await gotoRegister(page);
  await fillRegisterForm(page, 'not-a-valid-email', strongPassword());
  await clickSubmit(page);

  await expect(page.locator('.el-form-item__error')).toContainText('邮箱格式不正确');
  await expect(page).toHaveURL(/\/register/);
});