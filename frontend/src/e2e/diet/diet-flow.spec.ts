/**
 * Diet v1.2.2 E2E（CLAUDE.md §6.1 AC-8 + spec §07-diet-design）。
 *
 * <p>覆盖浏览器侧完整链路：
 * <ol>
 *   <li>入口：首页「饮食」卡 → /diets → 列表 + 营养汇总可见</li>
 *   <li>创建：点新增 → 填表单 → POST → 列表新增一行 + success toast</li>
 *   <li>编辑：列表点编辑 → dialog 预填 → 改 name → PATCH → 列表更新 + success toast</li>
 *   <li>跨用户 1003：访问 /diets/{别人的id} → 跳回 /diets + showAuthError toast</li>
 * </ol>
 *
 * <p>不依赖真实后端：`page.route()` 全量 mock `/api/v1/*`。
 * 真实后端契约由 {@code backend/.../DietServiceIT.java}（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupDietDefaults,
  mockDietCrossUser,
  type MockDiet,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
  expectAuthErrorToast,
} from '../helpers/test-fixtures';
import { selectElOption, fillElInputNumber } from '../helpers/el-control';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };

/** 取浏览器侧当天 ISO（YYYY-MM-DD）作为 occurredAt，避免 DietView 当日窗口过滤掉样例。 */
async function todayLocal(page: Page): Promise<string> {
  return await page.evaluate(() => {
    const fmt = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    return fmt.format(new Date());
  });
}

function diet(
  page: Page,
  overrides: Partial<MockDiet> & Pick<MockDiet, 'id' | 'mealType' | 'name'>,
): Promise<MockDiet> {
  return page.evaluate(
    async ({ overrides }) => {
      const fmt = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Shanghai',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      });
      const parts = fmt.formatToParts(new Date());
      const get = (t: string): string => parts.find((p) => p.type === t)?.value ?? '00';
      const now = `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}:${get('second')}+08:00`;
      return {
        userId: 1,
        kcal: 230,
        proteinG: 5,
        carbG: 50,
        fatG: 1,
        note: null,
        occurredAt: now,
        createdAt: now,
        updatedAt: now,
        ...overrides,
      };
    },
    { overrides },
  );
}

/** 走完登录表单 → 等守卫通过 → 留在登录后页面。 */
async function loginAs(page: Page, user: MockUser): Promise<void> {
  await page.goto('/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

// ---------------------------------------------------------------------------
// 入口：首页卡跳转 → /diets → 列表 + 营养汇总
// ---------------------------------------------------------------------------

test('首页「饮食」卡 → /diets → 列表与汇总可见', async ({ page }) => {
  const sample = await diet(page, {
    id: 5,
    mealType: 'LUNCH',
    name: '米饭',
    note: '午饭',
  });
  await setupAuthDefaults(page, { user: ALICE });
  await setupDietDefaults(page, { userId: ALICE.id, diets: [sample] });

  await loginAs(page, ALICE);
  await page.goto('/');

  // 首页饮食卡（v1.2.2 已激活为 module 卡）→ router-link 到 /diets
  await page.locator('[data-testid="home-card-diet"] a.module-card').click();
  await page.waitForURL(/\/diets$/);
  await expect(page).toHaveURL('http://localhost:5173/diets');

  // 列表项渲染（DietDayGroup 单行展示 time/name/kcal + edit/delete 按钮；note 仅详情页可见）
  await expect(page.locator('[data-testid="diet-item-5"]')).toBeVisible();
  await expect(page.locator('[data-testid="diet-item-5"]')).toContainText('米饭');
  await expect(page.locator('[data-testid="diet-item-5"]')).toContainText('230.00 kcal');

  // 营养汇总：当日 kcal 显示（230 / 2000 kcal）
  await expect(page.locator('[data-testid="diet-nutrition-total"]')).toContainText('230');
  await expect(page.locator('[data-testid="diet-nutrition-total"]')).toContainText('2000');
});

// ---------------------------------------------------------------------------
// 创建链路：点新增 → 填表单 → POST → 列表新增 + success toast
// ---------------------------------------------------------------------------

test('点新增 → 填 name/mealType/kcal → POST → 列表出现新行 + success toast', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupDietDefaults(page, { userId: ALICE.id, diets: [] });

  await loginAs(page, ALICE);
  await page.goto('/diets');
  await expect(page.locator('[data-testid="diet-view-empty"]')).toBeVisible();

  // 打开 dialog
  await page.locator('[data-testid="diet-create-btn"]').click();
  await expect(page.locator('[data-testid="diet-dialog"]')).toBeVisible();

  // name
  await page.locator('[data-testid="diet-dialog-name"] input').fill('豆浆');

  // mealType 用 selectElOption（EP 2.x el-select popper click 在 Chromium 下不稳 — 沿用
  // task-flow 同款 helper，直接 emit update:modelValue）。
  await selectElOption(page, '[data-testid="diet-dialog-meal-type"]', 'BREAKFAST');

  // kcal（el-input-number 通过 fillElInputNumber 走 emit 链路，避开 +/- 按钮偶发跳变）
  await fillElInputNumber(page, '[data-testid="diet-dialog-kcal"]', 80);

  // note
  await page.locator('[data-testid="diet-dialog-note"] textarea').fill('早餐');

  const createResp = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/diets') && r.request().method() === 'POST',
  );
  await page.locator('[data-testid="diet-dialog-submit"]').click();
  const resp = await createResp;
  expect(resp.status()).toBe(201);

  // mock state 写入
  expect(state.list).toHaveLength(1);
  expect(state.list[0]?.name).toBe('豆浆');
  expect(state.list[0]?.mealType).toBe('BREAKFAST');
  expect(state.list[0]?.kcal).toBe(80);
  expect(state.list[0]?.note).toBe('早餐');

  // success toast
  await expect(page.locator('.el-message--success')).toBeVisible();
});

// ---------------------------------------------------------------------------
// 编辑链路：列表点编辑 → dialog 预填 → 改 name → PATCH → 列表更新
// ---------------------------------------------------------------------------

test('列表点编辑 → 改 name → PATCH → 列表行更新 + success toast', async ({ page }) => {
  const sample = await diet(page, {
    id: 7,
    mealType: 'LUNCH',
    name: '米饭',
    note: null,
  });
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupDietDefaults(page, { userId: ALICE.id, diets: [sample] });

  await loginAs(page, ALICE);
  await page.goto('/diets');
  await expect(page.locator('[data-testid="diet-item-7"]')).toBeVisible();

  // 点编辑（DietDayGroup 行内）
  await page.locator('[data-testid="diet-edit-7"]').click();

  // dialog 预填：name 应该是「米饭」
  await expect(page.locator('[data-testid="diet-dialog"]')).toBeVisible();
  const nameInput = page.locator('[data-testid="diet-dialog-name"] input');
  await expect(nameInput).toHaveValue('米饭');

  // 改 name
  await nameInput.fill('面条');

  const patchResp = page.waitForResponse(
    (r) => /\/api\/v1\/diets\/7$/.test(r.url()) && r.request().method() === 'PATCH',
  );
  await page.locator('[data-testid="diet-dialog-submit"]').click();
  await patchResp;

  // mock state 已变
  expect(state.detail.get(7)?.name).toBe('面条');
  // 列表刷新后显示新名
  await expect(page.locator('[data-testid="diet-item-7"]')).toContainText('面条');
  // success toast
  await expect(page.locator('.el-message--success')).toBeVisible();
});

// ---------------------------------------------------------------------------
// 跨用户 1003：访问 /diets/{别人的id} → 跳回 /diets + showAuthError toast
// ---------------------------------------------------------------------------

test('访问 /diets/999（他人饮食）→ 1003 → 跳回 /diets + 错误 toast', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupDietDefaults(page, { userId: ALICE.id, diets: [] });
  // 跨用户覆盖：必须后注册 → LIFO 让 mockDietCrossUser 优先生效
  await mockDietCrossUser(page, 999);

  await loginAs(page, ALICE);
  await page.goto('/diets/999');

  // 跳回列表
  await page.waitForURL(/\/diets$/);
  await expect(page).toHaveURL('http://localhost:5173/diets');
  // showAuthError 弹 1003 文案
  await expectAuthErrorToast(page, 1003);
});