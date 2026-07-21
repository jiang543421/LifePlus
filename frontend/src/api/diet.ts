import http from './http';
import type {
  CreateDietRequest,
  DietFrequentItem,
  DietFilter,
  DietListResponse,
  DietResponse,
  DietSummary,
  UpdateDietRequest,
} from '@/types';

/**
 * Diet 模块 API（spec §07-diet-design section 5 + 后端 DietController）。
 *
 * <p>axios 实例已配置 {@code baseURL='/api/v1'} + 鉴权拦截 + 信封拦截，
 * 路径只写 {@code /diets...}；HTTP 4xx/5xx 与业务 code!=0 由拦截器抛
 * {@code ApiError(code, message)}，调用方只需关心成功路径。
 *
 * <p>{@link #list} 的 query 构造：null/undefined 字段不写入（避免空字符串污染）；
 * page/size 总是写入（与后端 1-based + 默认 1/20 配合）。
 */
export const dietApi = {
  /**
   * 过滤 + 分页查询（GET /diets）。
   * @param filter 后端 DietFilter 对齐；page/size 必有，mealType/from/to 可选
   */
  list(filter: DietFilter): Promise<DietListResponse> {
    const params: Record<string, string | number> = {};
    if (filter.mealType) params.mealType = filter.mealType;
    if (filter.from) params.from = filter.from;
    if (filter.to) params.to = filter.to;
    params.page = filter.page;
    params.size = filter.size;
    return http.get<unknown, DietListResponse>('/diets', { params });
  },

  /** 详情（GET /diets/{id}）。 */
  get(id: number): Promise<DietResponse> {
    return http.get<unknown, DietResponse>(`/diets/${id}`);
  },

  /** 创建（POST /diets，201 + DietResponse）。 */
  create(req: CreateDietRequest): Promise<DietResponse> {
    return http.post<unknown, DietResponse>('/diets', req);
  },

  /** 局部更新（PATCH /diets/{id}）。 */
  update(id: number, req: UpdateDietRequest): Promise<void> {
    return http.patch<unknown, void>(`/diets/${id}`, req);
  },

  /** 软删（DELETE /diets/{id}）。 */
  delete(id: number): Promise<void> {
    return http.delete<unknown, void>(`/diets/${id}`);
  },

  /**
   * 当日营养汇总（GET /diets/summary?date=YYYY-MM-DD）。
   * 返回 kcal/proteinG/carbG/fatG + 与昨日 / 上周同日 kcal 差值（无数据时为 null）。
   */
  summary(date: string): Promise<DietSummary> {
    return http.get<unknown, DietSummary>('/diets/summary', {
      params: { date },
    });
  },

  /**
   * 高频名称聚合（GET /diets/frequent?from=&to=&limit=）。
   * 默认窗口近 30 天 / top 10（后端兜底）；from/to/limit 均可不传。
   */
  frequent(from?: string, to?: string, limit?: number): Promise<DietFrequentItem[]> {
    const params: Record<string, string | number> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    if (limit !== undefined) params.limit = limit;
    return http.get<unknown, DietFrequentItem[]>('/diets/frequent', { params });
  },
};