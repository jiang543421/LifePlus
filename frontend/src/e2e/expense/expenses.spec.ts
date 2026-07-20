/**
 * Expense v1.2.1 E2E（CLAUDE.md §6.1 AC-8）。
 *
 * <p>覆盖浏览器侧完整链路：
 * <ol>
 *   <li>list → summary 渲染（首页卡 → /expenses 跳转 + 汇总金额可见）</li>
 *   <li>delete 二次确认 → 调 /expenses/{id} DELETE → 列表移除 → success toast</li>
 *   <li>未登录守卫：直接访问 /expenses → 重定向 /login</li>
 * </ol>
 *
 * <p>不依赖真实后端：{@code page.route()} 全量 mock {@code /api/v1/*}。
 * 真实后端契约由 {@code backend/.../ExpenseServiceIT.java}（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupExpenseDefaults,
  type MockExpense,
  type MockUser,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };

const SAMPLE_EXPENSE: MockExpense = {
  id: 5,
  userId: 1,
  amount: 35.5,
  category: 'MEAL',
  note: '午饭',
  occurredAt: '2026-07-15T12:00:00+08:00',
  createdAt: '2026-07-15T12:00:01+08:00',
  updatedAt: '2026-07-15T12:00:01+08:00',
};

/** 走完登录表单 → 等守卫通过 → 跳目标页。 */
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
// 入口：首页卡跳转 → 列表 + 汇总
// ---------------------------------------------------------------------------

test('首页「消费」卡 → /expenses → 列表与汇总可见', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupExpenseDefaults(page, { userId: ALICE.id, expenses: [SAMPLE_EXPENSE] });

  await loginAs(page, ALICE);
  await page.goto('/');

  // 首页消费卡 → router-link 到 /expenses
  await page.locator('[data-testid="home-card-expense"] a.module-card').click();
  await page.waitForURL(/\/expenses$/);
  await expect(page).toHaveURL('http://localhost:5173/expenses');

  // 汇总卡：总额显示「¥ 35.50」
  await expect(page.locator('[data-testid="summary-total"]')).toContainText('¥ 35.50');

  // 列表：单条渲染 + 金额 + 备注「午饭」
  await expect(page.locator('[data-testid="expense-item-5"]')).toBeVisible();
  await expect(page.locator('[data-testid="expense-item-amount"]').first()).toContainText('¥ 35.50');
  await expect(page.locator('[data-testid="expense-item-note"]').first()).toContainText('午饭');
});

// ---------------------------------------------------------------------------
// 删除链路：二次确认 + DELETE + success toast
// ---------------------------------------------------------------------------

test('点击「删除」→ 二次确认 → DELETE → 列表清空 + success toast', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupExpenseDefaults(page, {
    userId: ALICE.id,
    expenses: [SAMPLE_EXPENSE],
  });

  await loginAs(page, ALICE);
  await page.goto('/expenses');

  await page.locator('[data-testid="expense-item-delete"]').first().click();
  // ElMessageBox 真实 modal：点 primary「删除」按钮确认（与 task-flow.spec.ts:195 同款）
  await page.locator('.el-message-box .el-button--primary').click();

  // 列表行被移除
  await expect(page.locator('[data-testid="expense-item-5"]')).toHaveCount(0);
  // mock state 同步清空
  expect(state.list).toHaveLength(0);
  // success toast
  await expect(page.locator('.el-message--success')).toBeVisible();
});

// ---------------------------------------------------------------------------
// 守卫：未登录访问 /expenses → /login
// ---------------------------------------------------------------------------

test('未登录直接访问 /expenses → 守卫推到 /login', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await page.goto('/expenses');
  await page.waitForURL(/\/login/);
  await expect(page).toHaveURL(/\/login\?redirect=\/expenses$/);
});
