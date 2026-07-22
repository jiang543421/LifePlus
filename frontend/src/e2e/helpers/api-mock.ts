/**
 * E2E API mock 集中点（1.4-E）。
 *
 * <p>策略：`page.route()` 拦截 `/api/v1/*`，完全替代真实后端。
 * 真实后端契约由 `backend/src/test/.../*IT.java` 覆盖。
 *
 * <p>mock 状态（计数器、是否首次调用）存在闭包内；每个 spec 在
 * `test.beforeEach` 重新调一次 setup，即默认覆盖一次路由。
 * 多次 page.route() 注册同一 URL 时，**后注册的 handler 优先生效**
 * （Playwright 行为），所以"先 setupAuthDefaults 再针对性 mockLoginFailure"
 * 这种叠加写法的预期是后者覆盖前者的 login handler——已验证。
 */
import type { Page } from '@playwright/test';
import type {
  CreateDietRequest,
  DietListResponse,
  DietResponse,
  DietSummary,
  MealType,
  PlanAllDay,
  PlanCreateRequest,
  PlanListItem,
  PlanListResponse,
  PlanResponse,
  PlanUpdateRequest,
  TaskCreateRequest,
  TaskListItem,
  TaskListResponse,
  TaskResponse,
  TaskStatus,
  TaskUpdateRequest,
  UpdateDietRequest,
} from '@/types';

interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface MockUser {
  id: number;
  email: string;
  nickname: string | null;
}

/** 单任务 mock 数据（POST/GET /tasks* 的完整结构）。 */
export interface MockTask {
  id: number;
  userId: number;
  planId: number | null;
  title: string;
  status: TaskStatus;
  priority: 0 | 1 | 2 | 3;
  dueDate: string | null;
  tag: string | null;
  createdAt: string;
  updatedAt: string;
}

/** setupTaskDefaults 返回的可变状态，用于测试断言 mock 是否真的更新了。 */
export interface TaskMockState {
  list: TaskListItem[];
  detail: Map<number, TaskResponse>;
}

const DEFAULT_TOKENS: Tokens = {
  accessToken: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  expiresIn: 3600,
};

const DEFAULT_USER: MockUser = {
  id: 1,
  email: 'mock@example.com',
  nickname: 'Mock 用户',
};

function envelopeOk<T>(data: T): string {
  return JSON.stringify({ code: 0, message: 'ok', data });
}

function envelopeErr(code: number, message: string): string {
  return JSON.stringify({ code, message });
}

function toListItem(t: MockTask): TaskListItem {
  return {
    id: t.id,
    title: t.title,
    status: t.status,
    priority: t.priority,
    dueDate: t.dueDate,
    tag: t.tag,
  };
}

function toResponse(t: MockTask): TaskResponse {
  return {
    id: t.id,
    userId: t.userId,
    planId: t.planId,
    title: t.title,
    status: t.status,
    priority: t.priority,
    dueDate: t.dueDate,
    tag: t.tag,
    createdAt: t.createdAt,
    updatedAt: t.updatedAt,
  };
}

/**
 * 一站式默认 mock：login/register/me/refresh 全成功。多数正常用例直接调它即可。
 */
export async function setupAuthDefaults(
  page: Page,
  opts?: { user?: MockUser; tokens?: Tokens },
): Promise<void> {
  const user = opts?.user ?? DEFAULT_USER;
  const tokens = opts?.tokens ?? DEFAULT_TOKENS;
  await mockRegisterSuccess(page, { user, tokens });
  await mockLoginSuccess(page, { user, tokens });
  await mockMeSuccess(page, user);
  await mockRefreshSuccess(page, tokens);
}

export async function mockLoginSuccess(
  page: Page,
  opts?: { user?: MockUser; tokens?: Tokens },
): Promise<void> {
  const tokens = opts?.tokens ?? DEFAULT_TOKENS;
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk(tokens),
    });
  });
}

export async function mockLoginFailure(
  page: Page,
  code: number,
  message?: string,
): Promise<void> {
  // 1006 → 429；1002 → 401；1001/1003 → 400/403；其它 → 400
  const status = code === 1006 ? 429 : code === 1002 ? 401 : code === 1003 ? 403 : 400;
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'error'),
    });
  });
}

/**
 * 登录限流：前 N 次返回 1002，第 N+1 次返回 1006。
 * 默认 N=5（CLAUDE.md §7.2 阈值）。
 */
export async function mockLoginRateLimit(
  page: Page,
  opts?: { triggerAfter?: number; message?: string },
): Promise<void> {
  const triggerAfter = opts?.triggerAfter ?? 5;
  let count = 0;
  await page.route('**/api/v1/auth/login', async (route) => {
    count += 1;
    if (count > triggerAfter) {
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: envelopeErr(1006, opts?.message ?? '请求过于频繁，请稍后再试'),
      });
    } else {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: envelopeErr(1002, '邮箱或密码错误'),
      });
    }
  });
}

export async function mockRegisterSuccess(
  page: Page,
  opts?: { user?: MockUser; tokens?: Tokens },
): Promise<void> {
  const tokens = opts?.tokens ?? DEFAULT_TOKENS;
  await page.route('**/api/v1/auth/register', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: envelopeOk({ userId: opts?.user?.id ?? 1 }),
    });
  });
  // register 成功后前端会接着 login + me，所以一并 mock
  await mockLoginSuccess(page, { user: opts?.user, tokens });
  await mockMeSuccess(page, opts?.user);
}

export async function mockRegisterFailure(
  page: Page,
  code: number,
  message?: string,
): Promise<void> {
  const status = code === 1005 ? 409 : code === 1001 ? 400 : 400;
  await page.route('**/api/v1/auth/register', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'error'),
    });
  });
}

export async function mockMeSuccess(page: Page, user: MockUser = DEFAULT_USER): Promise<void> {
  await page.route('**/api/v1/users/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk(user),
    });
  });
}

export async function mockMeFailure(page: Page, code: number, message?: string): Promise<void> {
  const status = code === 1002 ? 401 : code === 1003 ? 403 : code === 1004 ? 404 : 400;
  await page.route('**/api/v1/users/me', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'error'),
    });
  });
}

export async function mockRefreshSuccess(page: Page, tokens: Tokens = DEFAULT_TOKENS): Promise<void> {
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk({
        accessToken: tokens.accessToken + '-rotated',
        refreshToken: tokens.refreshToken + '-rotated',
        expiresIn: tokens.expiresIn,
      }),
    });
  });
}

/**
 * Refresh 重放：首次调用返回新 token；同 token 第二次调用返回 1401。
 * 用于验证前端自动清态 + 跳 /login 的链路。
 */
export async function mockRefreshReplay(page: Page): Promise<void> {
  const seen = new Set<string>();
  await page.route('**/api/v1/auth/refresh', async (route) => {
    const body = JSON.parse(route.request().postData() ?? '{}') as { refreshToken?: string };
    const rt = body.refreshToken ?? '';
    if (seen.has(rt)) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: envelopeErr(1401, 'refresh token 失效'),
      });
      return;
    }
    seen.add(rt);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: envelopeOk({
        accessToken: 'rotated-access-' + Date.now(),
        refreshToken: 'rotated-refresh-' + Date.now(),
        expiresIn: 3600,
      }),
    });
  });
}

/**
 * Refresh 返回给定错误码（1003 跨用户、1401 失效）。
 */
export async function mockRefreshInvalid(
  page: Page,
  code: number,
  message?: string,
): Promise<void> {
  const status = code === 1003 ? 403 : 401;
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message ?? 'refresh invalid'),
    });
  });
}

// ----------------------------------------------------------------------
// Task 模块 mock（Phase 2-G）
// ----------------------------------------------------------------------

/**
 * 一站式 Task mock：拦截 `/api/v1/tasks**` 全部分支。
 *
 * <p>GET /tasks 支持 query 过滤（status / priority / tag / dueFrom / dueTo / page / size），
 * 用于断言列表筛选 E2E。POST 创建会自增 id 并写入 state.list。
 *
 * <p>state 闭包内可变：PATCH/PUT/DELETE 立即更新 state 便于测试断言。
 *
 * <p>专项覆盖（如 mockTaskCrossUser）注册在更晚 → LIFO 优先生效。
 */
export async function setupTaskDefaults(
  page: Page,
  opts?: { userId?: number; tasks?: MockTask[] },
): Promise<TaskMockState> {
  const userId = opts?.userId ?? 1;
  const initial = opts?.tasks ?? [];
  const state: TaskMockState = {
    list: initial.map(toListItem),
    detail: new Map(initial.map((t) => [t.id, toResponse(t)])),
  };
  let nextId = initial.reduce((m, t) => Math.max(m, t.id), 0) + 1;

  await page.route('**/api/v1/tasks**', async (route) => {
    const req = route.request();
    const method = req.method();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api\/v1/, '');

    // GET /tasks（带 query 过滤）
    if (method === 'GET' && path === '/tasks') {
      const statusQ = url.searchParams.get('status');
      const priorityQ = url.searchParams.get('priority');
      const tagQ = url.searchParams.get('tag');
      let items = state.list;
      if (statusQ !== null) items = items.filter((t) => String(t.status) === statusQ);
      if (priorityQ !== null) items = items.filter((t) => String(t.priority) === priorityQ);
      if (tagQ) items = items.filter((t) => t.tag === tagQ);
      const page = Number(url.searchParams.get('page') ?? '1');
      const size = Number(url.searchParams.get('size') ?? '20');
      const body: TaskListResponse = { items, total: items.length, page, size };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(body),
      });
      return;
    }

    // GET /tasks/{id}
    const detailMatch = path.match(/^\/tasks\/(\d+)$/);
    if (method === 'GET' && detailMatch) {
      const id = Number(detailMatch[1]);
      const t = state.detail.get(id);
      if (!t) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: envelopeErr(1004, 'task not found'),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(t),
      });
      return;
    }

    // GET /tasks/by-plan/{planId}（F-H03：PlanDetailView 嵌入关联任务）
    const byPlanMatch = path.match(/^\/tasks\/by-plan\/(\d+)$/);
    if (method === 'GET' && byPlanMatch) {
      const planId = Number(byPlanMatch[1]);
      // state.list 是 TaskListItem（无 planId 字段），必须从 detail 反查
      const items = Array.from(state.detail.values())
        .filter((t) => t.planId === planId)
        .map(toListItem);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(items),
      });
      return;
    }

    // PATCH /tasks/{id}/status
    const statusMatch = path.match(/^\/tasks\/(\d+)\/status$/);
    if (method === 'PATCH' && statusMatch) {
      const id = Number(statusMatch[1]);
      const body = JSON.parse(req.postData() ?? '{}') as { status: TaskStatus };
      const detail = state.detail.get(id);
      const item = state.list.find((t) => t.id === id);
      if (detail && item) {
        detail.status = body.status;
        item.status = body.status;
        detail.updatedAt = new Date().toISOString();
      }
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // PUT /tasks/{id}（null-skip 语义由前端保证）
    if (method === 'PUT' && detailMatch) {
      const id = Number(detailMatch[1]);
      const body = JSON.parse(req.postData() ?? '{}') as TaskUpdateRequest;
      const detail = state.detail.get(id);
      const item = state.list.find((t) => t.id === id);
      if (detail && item) {
        if (body.title !== undefined) { detail.title = body.title; item.title = body.title; }
        if (body.status !== undefined) { detail.status = body.status; item.status = body.status; }
        if (body.priority !== undefined) { detail.priority = body.priority; item.priority = body.priority; }
        if (body.dueDate !== undefined) { detail.dueDate = body.dueDate; item.dueDate = body.dueDate; }
        if (body.tag !== undefined) { detail.tag = body.tag; item.tag = body.tag; }
        detail.updatedAt = new Date().toISOString();
      }
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // DELETE /tasks/{id}
    if (method === 'DELETE' && detailMatch) {
      const id = Number(detailMatch[1]);
      state.detail.delete(id);
      state.list = state.list.filter((t) => t.id !== id);
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // POST /tasks
    if (method === 'POST' && path === '/tasks') {
      const body = JSON.parse(req.postData() ?? '{}') as TaskCreateRequest;
      const now = new Date().toISOString();
      const id = nextId++;
      const created: TaskResponse = {
        id,
        userId,
        planId: body.planId ?? null,
        title: body.title,
        status: 0,
        priority: body.priority ?? 0,
        dueDate: body.dueDate ?? null,
        tag: body.tag ?? null,
        createdAt: now,
        updatedAt: now,
      };
      state.detail.set(id, created);
      state.list = [...state.list, toListItem(created)];
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: envelopeOk(created),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: envelopeErr(1004, 'not found'),
    });
  });

  return state;
}

/**
 * 跨用户越权：GET /tasks/{id} 强制返回 1003。
 *
 * <p>注册时机：必须在 setupTaskDefaults 之后调用，LIFO 让更具体的 URL 模式先生效。
 */
export async function mockTaskCrossUser(page: Page, taskId: number): Promise<void> {
  await page.route(`**/api/v1/tasks/${taskId}`, async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: envelopeErr(1003, 'cross user denied'),
    });
  });
}

// ----------------------------------------------------------------------
// Plan 模块 mock（Phase 3-G）
// ----------------------------------------------------------------------

/** 单计划 mock 数据（POST/GET /plans* 的完整结构）。 */
export interface MockPlan {
  id: number;
  userId: number;
  title: string;
  startTime: string;
  endTime: string;
  allDay: PlanAllDay;
  location: string | null;
  note: string | null;
  reminderMin: number | null;
  createdAt: string;
  updatedAt: string;
}

/** setupPlanDefaults 返回的可变状态，用于测试断言 mock 是否真的更新了。 */
export interface PlanMockState {
  list: PlanListItem[];
  detail: Map<number, PlanResponse>;
}

function toPlanListItem(p: MockPlan): PlanListItem {
  return {
    id: p.id,
    title: p.title,
    startTime: p.startTime,
    endTime: p.endTime,
    allDay: p.allDay,
    location: p.location,
    reminderMin: p.reminderMin,
  };
}

function toPlanResponse(p: MockPlan): PlanResponse {
  return {
    id: p.id,
    userId: p.userId,
    title: p.title,
    startTime: p.startTime,
    endTime: p.endTime,
    allDay: p.allDay,
    location: p.location,
    note: p.note,
    reminderMin: p.reminderMin,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  };
}

/**
 * 一站式 Plan mock：拦截 `/api/v1/plans**` 全部分支。
 *
 * <p>GET /plans 支持 from/to 范围查询（ISO-8601 local datetime）；
 * 跨日事件用同一 from/to 仍能命中（按 startTime ∈ [from, to] 过滤，模拟
 * 后端按 startTime 索引扫描）。POST 创建会自增 id 并写入 state.list。
 *
 * <p>state 闭包内可变：PUT/DELETE 立即更新 state 便于测试断言。
 *
 * <p>专项覆盖（如 mockPlanCrossUser）注册在更晚 → LIFO 优先生效。
 */
export async function setupPlanDefaults(
  page: Page,
  opts?: { userId?: number; plans?: MockPlan[] },
): Promise<PlanMockState> {
  const userId = opts?.userId ?? 1;
  const initial = opts?.plans ?? [];
  const state: PlanMockState = {
    list: initial.map(toPlanListItem),
    detail: new Map(initial.map((p) => [p.id, toPlanResponse(p)])),
  };
  let nextId = initial.reduce((m, p) => Math.max(m, p.id), 0) + 1;

  await page.route('**/api/v1/plans**', async (route) => {
    const req = route.request();
    const method = req.method();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api\/v1/, '');

    // GET /plans（带 from/to + page/size）
    if (method === 'GET' && path === '/plans') {
      const fromQ = url.searchParams.get('from');
      const toQ = url.searchParams.get('to');
      let items = state.list;
      if (fromQ) items = items.filter((p) => p.startTime >= fromQ);
      if (toQ) items = items.filter((p) => p.startTime <= toQ);
      const page = Number(url.searchParams.get('page') ?? '1');
      const size = Number(url.searchParams.get('size') ?? '20');
      const body: PlanListResponse = { items, total: items.length, page, size };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(body),
      });
      return;
    }

    // GET /plans/{id}
    const detailMatch = path.match(/^\/plans\/(\d+)$/);
    if (method === 'GET' && detailMatch) {
      const id = Number(detailMatch[1]);
      const p = state.detail.get(id);
      if (!p) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: envelopeErr(1004, 'plan not found'),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(p),
      });
      return;
    }

    // PUT /plans/{id}（null-skip 语义由前端保证）
    if (method === 'PUT' && detailMatch) {
      const id = Number(detailMatch[1]);
      const body = JSON.parse(req.postData() ?? '{}') as PlanUpdateRequest;
      const detail = state.detail.get(id);
      const item = state.list.find((p) => p.id === id);
      if (detail && item) {
        if (body.title !== undefined) { detail.title = body.title; item.title = body.title; }
        if (body.startTime !== undefined) { detail.startTime = body.startTime; item.startTime = body.startTime; }
        if (body.endTime !== undefined) { detail.endTime = body.endTime; item.endTime = body.endTime; }
        if (body.allDay !== undefined) { detail.allDay = body.allDay; item.allDay = body.allDay; }
        if (body.location !== undefined) { detail.location = body.location; item.location = body.location; }
        if (body.note !== undefined) { detail.note = body.note; }
        if (body.reminderMin !== undefined) { detail.reminderMin = body.reminderMin; item.reminderMin = body.reminderMin; }
        detail.updatedAt = new Date().toISOString();
      }
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // DELETE /plans/{id}
    if (method === 'DELETE' && detailMatch) {
      const id = Number(detailMatch[1]);
      state.detail.delete(id);
      state.list = state.list.filter((p) => p.id !== id);
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // POST /plans
    if (method === 'POST' && path === '/plans') {
      const body = JSON.parse(req.postData() ?? '{}') as PlanCreateRequest;
      const now = new Date().toISOString();
      const id = nextId++;
      const created: PlanResponse = {
        id,
        userId,
        title: body.title,
        startTime: body.startTime,
        endTime: body.endTime,
        allDay: body.allDay ?? 0,
        location: body.location ?? null,
        note: body.note ?? null,
        reminderMin: body.reminderMin ?? null,
        createdAt: now,
        updatedAt: now,
      };
      state.detail.set(id, created);
      state.list = [...state.list, toPlanListItem(created)];
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: envelopeOk(created),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: envelopeErr(1004, 'not found'),
    });
  });

  return state;
}

/**
 * 跨用户越权：GET /plans/{id} 强制返回 1003。
 *
 * <p>注册时机：必须在 setupPlanDefaults 之后调用，LIFO 让更具体的 URL 模式先生效。
 */
export async function mockPlanCrossUser(page: Page, planId: number): Promise<void> {
  await page.route(`**/api/v1/plans/${planId}`, async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: envelopeErr(1003, 'cross user denied'),
    });
  });
}

// ----------------------------------------------------------------------
// Expense 模块 mock（v1.2.1）
// ----------------------------------------------------------------------

/** 单消费 mock 数据（POST/GET /expenses* 的完整结构）。 */
export interface MockExpense {
  id: number;
  userId: number;
  amount: number;
  category: 'MEAL' | 'SHOPPING' | 'TRANSPORT' | 'SUBSCRIPTION' | 'OTHER';
  note: string | null;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
}

/** setupExpenseDefaults 返回的可变状态，用于测试断言 mock 是否真的更新了。 */
export interface ExpenseMockState {
  list: MockExpense[];
  detail: Map<number, MockExpense>;
}

function emptyAmountByCategory(): Record<MockExpense['category'], number> {
  return { MEAL: 0, SHOPPING: 0, TRANSPORT: 0, SUBSCRIPTION: 0, OTHER: 0 };
}

/**
 * 一站式 Expense mock：拦截 `/api/v1/expenses**` 全部分支。
 *
 * <p>GET /expenses 支持 category/from/to/page/size 过滤；
 * GET /expenses/summary 累计 amountByCategory 与 totalAmount；
 * POST 自增 id；PATCH / DELETE 立即更新 state。
 *
 * <p>真实后端契约由 {@code backend/.../ExpenseServiceIT.java}（Testcontainers）覆盖。
 */
export async function setupExpenseDefaults(
  page: Page,
  opts?: { userId?: number; expenses?: MockExpense[] },
): Promise<ExpenseMockState> {
  const userId = opts?.userId ?? 1;
  const initial = opts?.expenses ?? [];
  const state: ExpenseMockState = {
    list: [...initial],
    detail: new Map(initial.map((e) => [e.id, e])),
  };
  let nextId = initial.reduce((m, e) => Math.max(m, e.id), 0) + 1;

  await page.route('**/api/v1/expenses**', async (route) => {
    const req = route.request();
    const method = req.method();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api\/v1/, '');

    // GET /expenses（带 category/from/to/page/size 过滤）
    if (method === 'GET' && path === '/expenses') {
      const categoryQ = url.searchParams.get('category');
      const fromQ = url.searchParams.get('from');
      const toQ = url.searchParams.get('to');
      let items = state.list;
      if (categoryQ) items = items.filter((e) => e.category === categoryQ);
      if (fromQ) items = items.filter((e) => e.occurredAt >= fromQ);
      if (toQ) items = items.filter((e) => e.occurredAt <= toQ);
      const page = Number(url.searchParams.get('page') ?? '1');
      const size = Number(url.searchParams.get('size') ?? '20');
      // List items are精简字段（与 ExpenseListItem 对齐）
      const listItems = items.map((e) => ({
        id: e.id,
        amount: e.amount,
        category: e.category,
        note: e.note,
        occurredAt: e.occurredAt,
      }));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk({ items: listItems, total: items.length, page, size }),
      });
      return;
    }

    // GET /expenses/categories
    if (method === 'GET' && path === '/expenses/categories') {
      const cats = [
        { code: 'MEAL', name: '餐饮' },
        { code: 'SHOPPING', name: '购物' },
        { code: 'TRANSPORT', name: '交通' },
        { code: 'SUBSCRIPTION', name: '订阅' },
        { code: 'OTHER', name: '其他' },
      ];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(cats),
      });
      return;
    }

    // GET /expenses/summary?year=&month=
    if (method === 'GET' && path === '/expenses/summary') {
      const year = Number(url.searchParams.get('year') ?? '0');
      const month = Number(url.searchParams.get('month') ?? '0');
      const monthStr = `${year}-${String(month).padStart(2, '0')}-01`;
      const inMonth = state.list.filter((e) => e.occurredAt.startsWith(monthStr.slice(0, 7)));
      const buckets = emptyAmountByCategory();
      for (const e of inMonth) buckets[e.category] += e.amount;
      const totalAmount = Object.values(buckets).reduce((s, v) => s + v, 0);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk({
          startMonth: monthStr,
          endMonth: monthStr,
          amountByCategory: buckets,
          totalAmount,
        }),
      });
      return;
    }

    // GET /expenses/{id}
    const detailMatch = path.match(/^\/expenses\/(\d+)$/);
    if (method === 'GET' && detailMatch) {
      const id = Number(detailMatch[1]);
      const e = state.detail.get(id);
      if (!e) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: envelopeErr(1004, 'expense not found'),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(e),
      });
      return;
    }

    // PATCH /expenses/{id}
    if (method === 'PATCH' && detailMatch) {
      const id = Number(detailMatch[1]);
      const body = JSON.parse(req.postData() ?? '{}') as Partial<MockExpense>;
      const detail = state.detail.get(id);
      if (detail) {
        if (body.amount !== undefined) detail.amount = body.amount;
        if (body.category !== undefined) detail.category = body.category;
        if (body.note !== undefined) detail.note = body.note;
        if (body.occurredAt !== undefined) detail.occurredAt = body.occurredAt;
        detail.updatedAt = new Date().toISOString();
      }
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // DELETE /expenses/{id}
    if (method === 'DELETE' && detailMatch) {
      const id = Number(detailMatch[1]);
      state.detail.delete(id);
      state.list = state.list.filter((e) => e.id !== id);
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // POST /expenses
    if (method === 'POST' && path === '/expenses') {
      const body = JSON.parse(req.postData() ?? '{}') as Partial<MockExpense>;
      const now = new Date().toISOString();
      const id = nextId++;
      const created: MockExpense = {
        id,
        userId,
        amount: body.amount ?? 0,
        category: (body.category as MockExpense['category']) ?? 'MEAL',
        note: body.note ?? null,
        occurredAt: body.occurredAt ?? now,
        createdAt: now,
        updatedAt: now,
      };
      state.detail.set(id, created);
      state.list = [...state.list, created];
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: envelopeOk(created),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: envelopeErr(1004, 'not found'),
    });
  });

  return state;
}

/**
 * 跨用户越权：GET /expenses/{id} 强制返回 1003。
 */
export async function mockExpenseCrossUser(page: Page, expenseId: number): Promise<void> {
  await page.route(`**/api/v1/expenses/${expenseId}`, async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: envelopeErr(1003, 'cross user denied'),
    });
  });
}

// ----------------------------------------------------------------------
// Diet 模块 mock（v1.2.2）
// ----------------------------------------------------------------------

/** 单饮食 mock 数据（POST/GET /diets* 的完整结构）。 */
export interface MockDiet {
  id: number;
  userId: number;
  mealType: MealType;
  name: string;
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  note: string | null;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
}

/** setupDietDefaults 返回的可变状态，用于测试断言 mock 是否真的更新了。 */
export interface DietMockState {
  list: MockDiet[];
  detail: Map<number, MockDiet>;
}

/**
 * 一站式 Diet mock：拦截 `/api/v1/diets**` 全部分支。
 *
 * <p>GET /diets 支持 mealType/from/to/page/size 过滤；
 * GET /diets/summary?date=YYYY-MM-DD 累计当日 4 项营养；
 * GET /diets/frequent?from=&to=&limit= 返回空数组（默认）；
 * POST 自增 id；PATCH / DELETE 立即更新 state。
 *
 * <p>真实后端契约由 {@code backend/.../DietServiceIT.java}（Testcontainers）覆盖。
 */
export async function setupDietDefaults(
  page: Page,
  opts?: { userId?: number; diets?: MockDiet[] },
): Promise<DietMockState> {
  const userId = opts?.userId ?? 1;
  const initial = opts?.diets ?? [];
  const state: DietMockState = {
    list: [...initial],
    detail: new Map(initial.map((d) => [d.id, d])),
  };
  let nextId = initial.reduce((m, d) => Math.max(m, d.id), 0) + 1;

  await page.route('**/api/v1/diets**', async (route) => {
    const req = route.request();
    const method = req.method();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api\/v1/, '');

    // GET /diets（带 mealType/from/to/page/size 过滤）
    if (method === 'GET' && path === '/diets') {
      const mealTypeQ = url.searchParams.get('mealType');
      const fromQ = url.searchParams.get('from');
      const toQ = url.searchParams.get('to');
      let items = state.list;
      if (mealTypeQ) items = items.filter((d) => d.mealType === mealTypeQ);
      if (fromQ) items = items.filter((d) => d.occurredAt >= fromQ);
      if (toQ) items = items.filter((d) => d.occurredAt <= toQ);
      const page = Number(url.searchParams.get('page') ?? '1');
      const size = Number(url.searchParams.get('size') ?? '20');
      const body: DietListResponse = { items, total: items.length, page, size };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(body),
      });
      return;
    }

    // GET /diets/summary?date=YYYY-MM-DD
    if (method === 'GET' && path === '/diets/summary') {
      const dateQ = url.searchParams.get('date') ?? '';
      const dayPrefix = dateQ.slice(0, 10);
      const inDay = state.list.filter((d) => d.occurredAt.startsWith(dayPrefix));
      const sum = inDay.reduce(
        (acc, d) => ({
          kcal: acc.kcal + d.kcal,
          proteinG: acc.proteinG + d.proteinG,
          carbG: acc.carbG + d.carbG,
          fatG: acc.fatG + d.fatG,
        }),
        { kcal: 0, proteinG: 0, carbG: 0, fatG: 0 },
      );
      const summary: DietSummary = {
        ...sum,
        kcalDeltaYesterday: null,
        kcalDeltaLastWeek: null,
      };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(summary),
      });
      return;
    }

    // GET /diets/frequent
    if (method === 'GET' && path === '/diets/frequent') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk([]),
      });
      return;
    }

    // GET /diets/{id}
    const detailMatch = path.match(/^\/diets\/(\d+)$/);
    if (method === 'GET' && detailMatch) {
      const id = Number(detailMatch[1]);
      const d = state.detail.get(id);
      if (!d) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: envelopeErr(1004, 'diet not found'),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(d),
      });
      return;
    }

    // PATCH /diets/{id}
    if (method === 'PATCH' && detailMatch) {
      const id = Number(detailMatch[1]);
      const body = JSON.parse(req.postData() ?? '{}') as UpdateDietRequest;
      const detail = state.detail.get(id);
      if (detail) {
        if (body.mealType !== undefined) detail.mealType = body.mealType;
        if (body.name !== undefined) detail.name = body.name;
        if (body.kcal !== undefined) detail.kcal = body.kcal;
        if (body.proteinG !== undefined) detail.proteinG = body.proteinG;
        if (body.carbG !== undefined) detail.carbG = body.carbG;
        if (body.fatG !== undefined) detail.fatG = body.fatG;
        if (body.note !== undefined) detail.note = body.note;
        if (body.occurredAt !== undefined) detail.occurredAt = body.occurredAt;
        detail.updatedAt = new Date().toISOString();
      }
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // DELETE /diets/{id}
    if (method === 'DELETE' && detailMatch) {
      const id = Number(detailMatch[1]);
      state.detail.delete(id);
      state.list = state.list.filter((d) => d.id !== id);
      await route.fulfill({ status: 204, contentType: 'application/json', body: '' });
      return;
    }

    // POST /diets
    if (method === 'POST' && path === '/diets') {
      const body = JSON.parse(req.postData() ?? '{}') as CreateDietRequest;
      const now = new Date().toISOString();
      const id = nextId++;
      const created: MockDiet = {
        id,
        userId,
        mealType: body.mealType,
        name: body.name,
        kcal: body.kcal,
        proteinG: body.proteinG,
        carbG: body.carbG,
        fatG: body.fatG,
        note: body.note ?? null,
        occurredAt: body.occurredAt,
        createdAt: now,
        updatedAt: now,
      };
      state.detail.set(id, created);
      state.list = [...state.list, created];
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: envelopeOk(created),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: envelopeErr(1004, 'not found'),
    });
  });

  return state;
}

/**
 * 跨用户越权：GET /diets/{id} 强制返回 1003。
 *
 * <p>注册时机：必须在 setupDietDefaults 之后调用，LIFO 让更具体的 URL 模式先生效。
 */
export async function mockDietCrossUser(page: Page, dietId: number): Promise<void> {
  await page.route(`**/api/v1/diets/${dietId}`, async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: envelopeErr(1003, 'cross user denied'),
    });
  });
}

// ----------------------------------------------------------------------
// AI 模块 mock（v2.0）
// ----------------------------------------------------------------------

/** 单条 AI 洞察 mock 数据（与后端 AiInsightResponse 对齐）。 */
export interface MockAiInsight {
  headline: string;
  chips: Array<{
    key: string;
    label: string;
    value: string;
    unit: string;
    trend: 'UP' | 'DOWN' | 'FLAT' | 'NONE';
    deltaText: string;
  }>;
  generatedAt: string;
  freshnessSeconds: number;
}

const DEFAULT_AI_INSIGHT: MockAiInsight = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '', trend: 'DOWN', deltaText: '较上周 -¥40' },
    { key: 'planDensity', label: '日程', value: '3', unit: '项', trend: 'NONE', deltaText: '今日 3 项' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
};

/** setupAiDefaults 返回的可变状态。 */
export interface AiMockState {
  insight: MockAiInsight;
  todayCallCount: number;
  refreshCallCount: number;
}

/**
 * 一站式 AI mock：拦截 `/api/v1/ai/insight/today` 与 `/api/v1/ai/insight/refresh`。
 *
 * <p>`/today` 默认返回 {@link DEFAULT_AI_INSIGHT}；`/refresh` 默认重算
 * freshnessSeconds=0 + 推进 generatedAt 用于断言"刷新后值被替换"。
 * 闭包内状态便于测试断言调用次数与是否真的触发。
 *
 * <p>真实后端契约由 {@code backend/.../ai/AiInsightIT.java}（Testcontainers）覆盖。
 */
export async function setupAiDefaults(
  page: Page,
  opts?: { insight?: MockAiInsight },
): Promise<AiMockState> {
  const state: AiMockState = {
    insight: opts?.insight ?? DEFAULT_AI_INSIGHT,
    todayCallCount: 0,
    refreshCallCount: 0,
  };

  await page.route('**/api/v1/ai/insight/**', async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname.replace(/^\/api\/v1/, '');

    if (req.method() === 'GET' && path === '/ai/insight/today') {
      state.todayCallCount += 1;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(state.insight),
      });
      return;
    }

    if (req.method() === 'POST' && path === '/ai/insight/refresh') {
      state.refreshCallCount += 1;
      const refreshed: MockAiInsight = {
        ...state.insight,
        freshnessSeconds: 0,
        generatedAt: new Date().toISOString(),
      };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: envelopeOk(refreshed),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: envelopeErr(1004, 'ai route not found'),
    });
  });

  return state;
}

/**
 * 强制 /today 返回指定错误码（1501 AI_DEGRADED / 1006 限流）。
 * 用于覆盖默认 setupAiDefaults（注册时机：必须在 setupAiDefaults 之后调用，
 * LIFO 让更具体的 URL 模式先生效）。
 */
export async function mockAiTodayError(page: Page, code: number): Promise<void> {
  const status = code === 1006 ? 429 : 400;
  const message =
    code === 1501
      ? 'AI 洞察数据暂时不可用，请稍后重试'
      : code === 1006
        ? 'AI 洞察请求过于频繁，请稍后重试'
        : 'error';
  await page.route('**/api/v1/ai/insight/today', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message),
    });
  });
}

/**
 * 强制 /refresh 返回指定错误码。
 */
export async function mockAiRefreshError(page: Page, code: number): Promise<void> {
  const status = code === 1006 ? 429 : 400;
  const message =
    code === 1501
      ? 'AI 洞察数据暂时不可用，请稍后重试'
      : code === 1006
        ? 'AI 洞察请求过于频繁，请稍后重试'
        : 'error';
  await page.route('**/api/v1/ai/insight/refresh', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: envelopeErr(code, message),
    });
  });
}