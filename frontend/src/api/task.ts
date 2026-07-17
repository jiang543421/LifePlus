import http from './http';
import type {
  TaskCreateRequest,
  TaskFilter,
  TaskListItem,
  TaskListResponse,
  TaskResponse,
  TaskStatusRequest,
  TaskUpdateRequest,
} from '@/types';

/**
 * Task 模块 API（spec §04 §3 + 后端 TaskController）。
 *
 * <p>axios 实例已配置 {@code baseURL='/api/v1'} + 鉴权拦截 + 信封拦截，
 * 路径只写 {@code /tasks...}；HTTP 4xx/5xx 与业务 code!=0 由拦截器抛
 * {@code ApiError(code, message)}，调用方只需关心成功路径。
 *
 * <p>{@link #list} 的 query 构造：null/undefined 字段不写入（避免空字符串污染）；
 * page/size 总是写入（与后端 1-based + 默认 1/20 配合）。
 */
export const taskApi = {
  /**
   * 过滤 + 分页查询（GET /tasks）。
   * @param filter 后端 TaskFilter 对齐；page/size 必有
   */
  list(filter: TaskFilter): Promise<TaskListResponse> {
    const params: Record<string, string | number> = {};
    if (filter.status !== undefined) params.status = filter.status;
    if (filter.priority !== undefined) params.priority = filter.priority;
    if (filter.tag) params.tag = filter.tag;
    if (filter.dueFrom) params.dueFrom = filter.dueFrom;
    if (filter.dueTo) params.dueTo = filter.dueTo;
    params.page = filter.page;
    params.size = filter.size;
    // 注意：axios.get(url, config) 的第二个参数是 AxiosRequestConfig，不是 query params 本身。
    // 必须包成 { params }，否则 status=0 之类字段被当作 config 选项丢弃，URL 永远不带 query。
    return http.get<unknown, TaskListResponse>('/tasks', { params });
  },

  /** 按 plan 聚合（GET /tasks/by-plan/{planId}）。 */
  byPlan(planId: number): Promise<TaskListItem[]> {
    return http.get<unknown, TaskListItem[]>(`/tasks/by-plan/${planId}`);
  },

  /** 详情（GET /tasks/{id}）。 */
  get(id: number): Promise<TaskResponse> {
    return http.get<unknown, TaskResponse>(`/tasks/${id}`);
  },

  /** 创建（POST /tasks，201 + TaskResponse）。 */
  create(req: TaskCreateRequest): Promise<TaskResponse> {
    return http.post<unknown, TaskResponse>('/tasks', req);
  },

  /** 局部更新（PUT /tasks/{id}）。 */
  update(id: number, req: TaskUpdateRequest): Promise<void> {
    return http.put<unknown, void>(`/tasks/${id}`, req);
  },

  /** 状态切换（PATCH /tasks/{id}/status）。 */
  patchStatus(id: number, req: TaskStatusRequest): Promise<void> {
    return http.patch<unknown, void>(`/tasks/${id}/status`, req);
  },

  /** 软删（DELETE /tasks/{id}）。 */
  delete(id: number): Promise<void> {
    return http.delete<unknown, void>(`/tasks/${id}`);
  },
};