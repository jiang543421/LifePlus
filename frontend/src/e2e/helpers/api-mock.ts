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