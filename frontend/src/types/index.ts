// 后端 DTO 对齐（spec §03-api-auth.md §5 + §3）。
// 前端所有 view / store / api 模块共享这一份类型源。

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  nickname?: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  email: string;
  nickname: string | null;
}

/** 更新昵称请求（PATCH /users/me body，Settings v1.1）。 */
export interface UpdateProfileRequest {
  /** 昵称；trim 后空字符串在服务端落为 null（允许清空）。 */
  nickname: string | null;
}

/** 修改密码请求（POST /users/me/password body，Settings v1.1）。 */
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

/** 注销账号请求（DELETE /users/me body，Settings v1.1）— 二次验证用。 */
export interface DeleteAccountRequest {
  password: string;
}

export interface ApiEnvelope<T> {
  code: number;
  message?: string;
  data?: T;
}

/** 密码强度规则（与后端 RegisterRequest @Size(min=8,max=64) + @Pattern 对齐）。 */
export const PASSWORD_RULES: ReadonlyArray<{ readonly key: string; readonly label: string; readonly test: (s: string) => boolean }> = [
  { key: 'length', label: '长度 8-64 位', test: (s) => s.length >= 8 && s.length <= 64 },
  { key: 'letter', label: '至少 1 个字母', test: (s) => /[A-Za-z]/.test(s) },
  { key: 'digit', label: '至少 1 个数字', test: (s) => /[0-9]/.test(s) },
] as const;

/** UI 原型约定的错误码（与后端 AuthConstants + GlobalExceptionHandler 对齐）。
 * 注意：后端没有专门的弱密码码；弱密码由 Jakarta @Pattern 触发 → 1001 Validation。 */
export const AuthErrorCode = {
  Validation: 1001,
  BadCredentials: 1002,
  CrossUserDenied: 1003,
  NotFound: 1004,
  EmailRegistered: 1005,
  RateLimit: 1006,
  RefreshInvalid: 1401,
} as const;
export type AuthErrorCodeValue = (typeof AuthErrorCode)[keyof typeof AuthErrorCode];

// ----------------------------------------------------------------------
// Task 模块类型（spec §04 §1/§2/§3 + 后端 TaskResponse / TaskListItem）
// ----------------------------------------------------------------------

/** 任务状态字面值（与后端 TaskConstants 对齐）。 */
export type TaskStatus = 0 | 1 | 2;          // TODO | DONE | CANCELLED
export const TaskStatusValue = {
  TODO: 0,
  DONE: 1,
  CANCELLED: 2,
} as const;

/** 任务优先级字面值（与后端 TaskConstants 对齐）。 */
export type TaskPriority = 0 | 1 | 2 | 3;     // NONE | LOW | MEDIUM | HIGH
export const TaskPriorityValue = {
  NONE: 0,
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
} as const;

/** 任务详情（POST /tasks / GET /tasks/{id} 响应）。 */
export interface TaskResponse {
  id: number;
  userId: number;
  planId: number | null;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  /** ISO-8601 date（YYYY-MM-DD），可能 null。 */
  dueDate: string | null;
  tag: string | null;
  /** ISO-8601 datetime。 */
  createdAt: string;
  updatedAt: string;
}

/** 任务列表项（GET /tasks / /tasks/by-plan/{id} 响应中的精简字段）。 */
export interface TaskListItem {
  id: number;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  tag: string | null;
}

/** 任务分页响应（与后端 PageResponse<T> 对齐）。 */
export interface TaskListResponse {
  items: TaskListItem[];
  total: number;
  page: number;
  size: number;
}

/** 创建请求（POST /tasks body）。 */
export interface TaskCreateRequest {
  title: string;
  priority?: TaskPriority;
  /** ISO-8601 date（YYYY-MM-DD）。 */
  dueDate?: string | null;
  tag?: string | null;
  planId?: number | null;
}

/** 更新请求（PUT /tasks/{id} body）— 所有字段可选，null-skip。 */
export interface TaskUpdateRequest {
  title?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  dueDate?: string | null;
  tag?: string | null;
  planId?: number | null;
}

/** 状态切换请求（PATCH /tasks/{id}/status body）。 */
export interface TaskStatusRequest {
  status: TaskStatus;
}

/** 列表过滤条件（与后端 TaskFilter 对齐，page/size 必有）。 */
export interface TaskFilter {
  status?: TaskStatus;
  priority?: TaskPriority;
  tag?: string;
  dueFrom?: string;       // ISO-8601 date
  dueTo?: string;         // ISO-8601 date
  page: number;
  size: number;
}

// ----------------------------------------------------------------------
// Plan 模块类型（spec §04 §5 + 后端 PlanResponse / PlanListItem）
// ----------------------------------------------------------------------

/** 计划全天标记字面值（与后端 PlanCreateRequest @Min(0)@Max(1) 对齐）。 */
export type PlanAllDay = 0 | 1;
export const PlanAllDayValue = {
  TIMED: 0,
  ALL_DAY: 1,
} as const;

/** 计划详情（POST /plans / GET /plans/{id} 响应）。 */
export interface PlanResponse {
  id: number;
  userId: number;
  title: string;
  /** ISO-8601 local datetime（无 offset，约定 TZ Asia/Shanghai 解释）。 */
  startTime: string;
  /** ISO-8601 local datetime。 */
  endTime: string;
  allDay: PlanAllDay;
  location: string | null;
  note: string | null;
  reminderMin: number | null;
  /** ISO-8601 datetime with offset（与 TaskResponse 对齐）。 */
  createdAt: string;
  updatedAt: string;
}

/** 计划列表项（GET /plans 响应中精简字段：无 note/userId/createdAt/updatedAt）。 */
export interface PlanListItem {
  id: number;
  title: string;
  startTime: string;
  endTime: string;
  allDay: PlanAllDay;
  location: string | null;
  reminderMin: number | null;
}

/** 计划分页响应（与后端 PageResponse<T> 对齐）。 */
export interface PlanListResponse {
  items: PlanListItem[];
  total: number;
  page: number;
  size: number;
}

/** 创建请求（POST /plans body）。 */
export interface PlanCreateRequest {
  title: string;
  startTime: string;
  endTime: string;
  allDay?: PlanAllDay;
  location?: string | null;
  note?: string | null;
  reminderMin?: number | null;
}

/** 更新请求（PUT /plans/{id} body）— 所有字段可选，null-skip。 */
export interface PlanUpdateRequest {
  title?: string;
  startTime?: string;
  endTime?: string;
  allDay?: PlanAllDay;
  location?: string | null;
  note?: string | null;
  reminderMin?: number | null;
}

/** 列表过滤条件（与后端 PlanFilter 对齐，page/size 必有）。 */
export interface PlanFilter {
  /** ISO-8601 local datetime 下界（含），null = 无下界。 */
  from?: string;
  /** ISO-8601 local datetime 上界（含），null = 无上界。 */
  to?: string;
  page: number;
  size: number;
}