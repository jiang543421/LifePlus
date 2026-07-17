/**
 * 任务流 E2E（Phase 2-G）。
 *
 * <p>CLAUDE.md §6.1 强制项：任务流必须有 E2E。
 * 覆盖：守卫 → 空态 → 新建 → 详情 → mark-done → 编辑 → 删除 → 筛选 → 跨用户 1003。
 *
 * <p>不依赖真实后端：`api-mock.ts` 全量 page.route() 拦截 `/api/v1/tasks*`；
 * 真实后端契约由 `backend/src/test/.../task/TaskFlowIT.java`（Testcontainers）覆盖。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  setupAuthDefaults,
  setupTaskDefaults,
  mockTaskCrossUser,
  type MockUser,
  type MockTask,
} from '../helpers/api-mock';
import {
  clearStorage,
  fillLoginForm,
  clickSubmit,
  strongPassword,
} from '../helpers/test-fixtures';
import { TaskStatusValue } from '@/types';

const ALICE: MockUser = { id: 1, email: 'alice@lifepulse.test', nickname: 'alice' };
const BOB: MockUser = { id: 2, email: 'bob@lifepulse.test', nickname: 'bob' };

const FIXED_NOW = '2026-07-16T10:00:00+08:00';

function task(overrides: Partial<MockTask> & Pick<MockTask, 'id' | 'title' | 'status'>): MockTask {
  return {
    userId: ALICE.id,
    planId: null,
    priority: 0,
    dueDate: null,
    tag: null,
    createdAt: FIXED_NOW,
    updatedAt: FIXED_NOW,
    ...overrides,
  };
}

/** 走完登录表单 → 等守卫通过 → 留在登录后页面。 */
async function loginAs(page: Page, user: MockUser): Promise<void> {
  await page.goto('/login');
  await fillLoginForm(page, user.email, strongPassword());
  await clickSubmit(page);
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
}

/**
 * 直接通过 pinia 调用 task store.setFilter + fetchList。
 *
 * <p>EP 2.x el-select 选项的 select 触发器绑定在 `pointerdown.prevent` 上；
 * Playwright 在 Chromium 下 `.click()` 派发的 pointerdown 序列偶发不触发 v-model
 * （hover 移动 aria-activedescendant 但 click 不 commit）。组件 → emit 链路已在
 * `TaskFilters.spec.ts` 单测覆盖；E2E 关注 filter 变更 → API → UI 的端到端。
 *
 * <p>调用前必须等 TaskListView 真正 mount（store 才会注册到 pinia._s）。
 */
async function setTaskFilter(page: Page, patch: Record<string, unknown>): Promise<void> {
  await page.evaluate((p) => {
    const root = document.querySelector('#app') as unknown as {
      __vue_app__?: {
        _context: {
          config: {
            globalProperties: {
              $pinia: {
                _s: Map<string, {
                  setFilter: (p: Record<string, unknown>) => void;
                  fetchList: () => Promise<unknown>;
                }>;
              };
            };
          };
        };
      };
    } | null;
    const pinia = root?.__vue_app__?._context.config.globalProperties.$pinia;
    const store = pinia?._s.get('task');
    store?.setFilter(p);
    void store?.fetchList();
  }, patch);
}

test.beforeEach(async ({ page }) => {
  await clearStorage(page);
});

test('未登录访问 /tasks → 守卫推到 /login?redirect=/tasks', async ({ page }) => {
  await page.goto('/tasks');
  await page.waitForURL(/\/login/);
  await expect(page).toHaveURL(/\/login\?redirect=\/tasks/);
});

test('登录后 /tasks 空态：展示引导文案', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  await setupTaskDefaults(page, { userId: ALICE.id, tasks: [] });

  await loginAs(page, ALICE);
  await page.goto('/tasks');

  await expect(page.locator('[data-testid="empty-state"]')).toBeVisible();
  await expect(page.locator('[data-testid="empty-state"]')).toContainText('还没有任务');
});

test('新建任务：dialog → 提交 → 列表出现该行', async ({ page }) => {
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupTaskDefaults(page, { userId: ALICE.id, tasks: [] });

  await loginAs(page, ALICE);
  await page.goto('/tasks');
  await expect(page.locator('[data-testid="empty-state"]')).toBeVisible();

  await page.locator('[data-testid="new-task"]').click();
  await expect(page.locator('[data-testid="new-task-dialog"]')).toBeVisible();

  await page.locator('[data-testid="new-title"] input').fill('买菜');

  const createResp = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/tasks') && r.request().method() === 'POST',
  );
  await page.locator('[data-testid="new-submit"]').click();
  const resp = await createResp;
  expect(resp.status()).toBe(201);

  // 列表刷新后空态消失，行渲染
  await expect(page.locator('[data-testid="task-rows"]')).toBeVisible();
  await expect(page.locator('.task-item')).toHaveCount(1);
  await expect(page.locator('.task-item').first()).toContainText('买菜');

  // mock state 也已写入
  expect(state.list).toHaveLength(1);
  expect(state.list[0]?.title).toBe('买菜');
});

test('详情页 mark-done：PATCH → 状态 tag 变「已完成」', async ({ page }) => {
  const t = task({ id: 1, title: '买菜', status: TaskStatusValue.TODO });
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupTaskDefaults(page, { userId: ALICE.id, tasks: [t] });

  await loginAs(page, ALICE);
  await page.goto('/tasks/1');
  await expect(page.locator('[data-testid="task-detail"]')).toBeVisible();
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('待办');

  const patchResp = page.waitForResponse(
    (r) => /\/api\/v1\/tasks\/1\/status$/.test(r.url()) && r.request().method() === 'PATCH',
  );
  await page.locator('[data-testid="mark-done"]').click();
  await patchResp;

  // mock state 已变 + UI 重新拉取后状态 tag 跟着变
  expect(state.detail.get(1)?.status).toBe(TaskStatusValue.DONE);
  await expect(page.locator('[data-testid="status-badge"]')).toContainText('已完成');
});

test('详情页编辑 → 保存：PUT → 标题更新 + 回列表看到新标题', async ({ page }) => {
  const t = task({ id: 7, title: '买菜', status: TaskStatusValue.TODO });
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupTaskDefaults(page, { userId: ALICE.id, tasks: [t] });

  await loginAs(page, ALICE);
  await page.goto('/tasks/7');
  await expect(page.locator('[data-testid="task-detail"]')).toBeVisible();

  await page.locator('[data-testid="edit-start"]').click();
  const titleInput = page.locator('[data-testid="edit-title"] input');
  await expect(titleInput).toBeVisible();
  await titleInput.fill('买菜+买水果');

  const putResp = page.waitForResponse(
    (r) => /\/api\/v1\/tasks\/7$/.test(r.url()) && r.request().method() === 'PUT',
  );
  await page.locator('[data-testid="edit-save"]').click();
  await putResp;

  expect(state.detail.get(7)?.title).toBe('买菜+买水果');
  // 详情页 reload 后展示新标题
  await expect(page.locator('[data-testid="task-detail"]')).toContainText('买菜+买水果');
});

test('详情页删除：ElMessageBox 确认 → DELETE → 跳回 /tasks 空态', async ({ page }) => {
  const t = task({ id: 9, title: '买菜', status: TaskStatusValue.TODO });
  await setupAuthDefaults(page, { user: ALICE });
  const state = await setupTaskDefaults(page, { userId: ALICE.id, tasks: [t] });

  await loginAs(page, ALICE);
  await page.goto('/tasks/9');
  await expect(page.locator('[data-testid="task-detail"]')).toBeVisible();

  await page.locator('[data-testid="delete-btn"]').click();
  // ElMessageBox 真实 modal：点 primary「删除」按钮确认
  await page.locator('.el-message-box .el-button--primary').click();

  await page.waitForURL(/\/tasks$/);
  // mock state 已删 + 列表回到空态
  expect(state.detail.has(9)).toBe(false);
  await expect(page.locator('[data-testid="empty-state"]')).toBeVisible();
});

test('列表筛选 status=TODO：filter 变更 → API 带 status=0 → 只剩 TODO 任务', async ({ page }) => {
  const t1 = task({ id: 1, title: '买菜', status: TaskStatusValue.TODO });
  const t2 = task({ id: 2, title: '看书', status: TaskStatusValue.DONE });
  await setupAuthDefaults(page, { user: ALICE });
  await setupTaskDefaults(page, { userId: ALICE.id, tasks: [t1, t2] });

  await loginAs(page, ALICE);
  await page.goto('/tasks');
  // 等 view mount → store 注册到 pinia._s
  await expect(page.locator('.task-item')).toHaveCount(2);

  // 走 pinia 直连 setFilter + fetchList（el-select 选项 click 在 EP 2.x + Playwright 下不触发 v-model）
  const filterResp = page.waitForResponse(
    (r) => /\/api\/v1\/tasks\?/.test(r.url()) && r.url().includes('status=0'),
  );
  await setTaskFilter(page, { status: 0 });
  await filterResp;

  // mock 已按 status=0 过滤返回 → UI 只剩 TODO
  await expect(page.locator('.task-item')).toHaveCount(1);
  await expect(page.locator('.task-item').first()).toContainText('买菜');
});

test('跨用户 1003：Bob 直接访问 Alice 的 /tasks/100 → 跳回 /tasks', async ({ page }) => {
  // Alice 原本有一份数据用于 sanity check
  const aliceTask = task({ id: 100, title: 'A 私有任务', status: TaskStatusValue.TODO });
  await setupAuthDefaults(page, { user: ALICE });
  await setupTaskDefaults(page, { userId: ALICE.id, tasks: [aliceTask] });
  await loginAs(page, ALICE);
  await page.goto('/tasks');
  await expect(page.locator('.task-item').first()).toContainText('A 私有任务');

  // 切换到 Bob：清空 storage、重设 auth + tasks（Bob 自己没任务）
  // setupTaskDefaults 注册的 broad handler 会让 GET /tasks/100 返回 1004，
  // mockTaskCrossUser 在之后注册 → LIFO 让 1003 覆盖
  await clearStorage(page);
  await setupAuthDefaults(page, { user: BOB });
  await setupTaskDefaults(page, { userId: BOB.id, tasks: [] });
  await mockTaskCrossUser(page, 100);

  await loginAs(page, BOB);
  // Bob 的列表为空
  await page.goto('/tasks');
  await expect(page.locator('[data-testid="empty-state"]')).toBeVisible();

  // 直接拿 Alice 的 task id 访问详情 → 1003 → 跳回 /tasks
  await page.goto('/tasks/100');
  await page.waitForURL(/\/tasks$/);
  await expect(page).toHaveURL(/\/tasks$/);
});