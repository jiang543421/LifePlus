import http from './http';
import type {
  CategoryItem,
  CreateExpenseRequest,
  ExpenseFilter,
  ExpenseListResponse,
  ExpenseResponse,
  ExpenseSummary,
  UpdateExpenseRequest,
} from '@/types';

/**
 * Expense 模块 API（spec §06-expense section 5 + 后端 ExpenseController）。
 *
 * <p>axios 实例已配置 {@code baseURL='/api/v1'} + 鉴权拦截 + 信封拦截，
 * 路径只写 {@code /expenses...}；HTTP 4xx/5xx 与业务 code!=0 由拦截器抛
 * {@code ApiError(code, message)}，调用方只需关心成功路径。
 *
 * <p>{@link #list} 的 query 构造：null/undefined 字段不写入（避免空字符串污染）；
 * page/size 总是写入（与后端 1-based + 默认 1/20 配合）。
 */
export const expenseApi = {
  /**
   * 过滤 + 分页查询（GET /expenses）。
   * @param filter 后端 ExpenseFilter 对齐；page/size 必有，category/from/to 可选
   */
  list(filter: ExpenseFilter): Promise<ExpenseListResponse> {
    const params: Record<string, string | number> = {};
    if (filter.category) params.category = filter.category;
    if (filter.from) params.from = filter.from;
    if (filter.to) params.to = filter.to;
    params.page = filter.page;
    params.size = filter.size;
    return http.get<unknown, ExpenseListResponse>('/expenses', { params });
  },

  /** 详情（GET /expenses/{id}）。 */
  get(id: number): Promise<ExpenseResponse> {
    return http.get<unknown, ExpenseResponse>(`/expenses/${id}`);
  },

  /** 创建（POST /expenses，201 + ExpenseResponse）。 */
  create(req: CreateExpenseRequest): Promise<ExpenseResponse> {
    return http.post<unknown, ExpenseResponse>('/expenses', req);
  },

  /** 局部更新（PATCH /expenses/{id}）。 */
  update(id: number, req: UpdateExpenseRequest): Promise<void> {
    return http.patch<unknown, void>(`/expenses/${id}`, req);
  },

  /** 软删（DELETE /expenses/{id}）。 */
  delete(id: number): Promise<void> {
    return http.delete<unknown, void>(`/expenses/${id}`);
  },

  /**
   * 月度汇总（GET /expenses/summary?year=&month=）。
   * 返回 startMonth/endMonth/totalAmount + 5 个分类分桶（缺分类时为 0）。
   */
  summary(year: number, month: number): Promise<ExpenseSummary> {
    return http.get<unknown, ExpenseSummary>('/expenses/summary', {
      params: { year, month },
    });
  },

  /** 分类静态元数据（GET /expenses/categories）。 */
  categories(): Promise<CategoryItem[]> {
    return http.get<unknown, CategoryItem[]>('/expenses/categories');
  },
};