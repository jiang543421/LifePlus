import http from './http';
import type {
  PlanCreateRequest,
  PlanFilter,
  PlanListResponse,
  PlanResponse,
  PlanUpdateRequest,
} from '@/types';

/**
 * Plan 模块 API（spec §04 §5 + 后端 PlanController）。
 *
 * <p>axios 实例已配置 {@code baseURL='/api/v1'} + 鉴权拦截 + 信封拦截，
 * 路径只写 {@code /plans...}；HTTP 4xx/5xx 与业务 code!=0 由拦截器抛
 * {@code ApiError(code, message)}，调用方只需关心成功路径。
 *
 * <p>{@link #list} 的 query 构造：null/undefined 字段不写入（避免空字符串污染）；
 * page/size 总是写入（与后端 1-based + 默认 1/20 配合）。
 */
export const planApi = {
  /**
   * 范围过滤 + 分页查询（GET /plans）。
   * @param filter 后端 PlanFilter 对齐；page/size 必有，from/to 可选
   */
  list(filter: PlanFilter): Promise<PlanListResponse> {
    const params: Record<string, string | number> = {};
    if (filter.from) params.from = filter.from;
    if (filter.to) params.to = filter.to;
    params.page = filter.page;
    params.size = filter.size;
    return http.get<unknown, PlanListResponse>('/plans', { params });
  },

  /** 详情（GET /plans/{id}）。 */
  get(id: number): Promise<PlanResponse> {
    return http.get<unknown, PlanResponse>(`/plans/${id}`);
  },

  /** 创建（POST /plans，201 + PlanResponse）。 */
  create(req: PlanCreateRequest): Promise<PlanResponse> {
    return http.post<unknown, PlanResponse>('/plans', req);
  },

  /** 局部更新（PUT /plans/{id}）。 */
  update(id: number, req: PlanUpdateRequest): Promise<void> {
    return http.put<unknown, void>(`/plans/${id}`, req);
  },

  /** 软删（DELETE /plans/{id}）。 */
  delete(id: number): Promise<void> {
    return http.delete<unknown, void>(`/plans/${id}`);
  },
};