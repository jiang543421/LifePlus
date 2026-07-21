// Diet 模块类型（spec §07-diet-design section 5）。
//
// 与后端 Diet*Response / Diet*Request record 字段顺序严格对齐；
// 后端 BigDecimal 默认 Jackson 序列化为 number（与 ExpenseResponse.amount 一致）；
// OffsetDateTime 序列化为 ISO-8601 datetime with offset。
//
// 与消费模块 expenses 类型同位：本文件独立成 types/diet.ts，
// 不向 types/index.ts barrel 膨胀（避免循环依赖与巨型类型文件）。

/** 餐别字面值联合（与后端 MealType 4 值对齐）。 */
export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';

/** 饮食详情（POST /diets、GET /diets/{id} 响应）。
 * 字段顺序与后端 DietResponse record 完全一致。 */
export interface DietResponse {
  id: number;
  userId: number;
  mealType: MealType;
  name: string;
  /** DB DECIMAL(7,2)，UI 展示走 utils/number.formatKcal()（保留 1 位小数）。 */
  kcal: number;
  /** DECIMAL(6,2)。 */
  proteinG: number;
  /** DECIMAL(6,2)。 */
  carbG: number;
  /** DECIMAL(6,2)。 */
  fatG: number;
  note: string | null;
  /** ISO-8601 datetime with offset。 */
  occurredAt: string;
  /** ISO-8601 datetime with offset。 */
  createdAt: string;
  /** ISO-8601 datetime with offset。 */
  updatedAt: string;
}

/** 饮食列表项（GET /diets 响应中的精简字段；不含 userId/createdAt/updatedAt）。
 * 字段顺序与后端 DietListItem record 完全一致。 */
export interface DietListItem {
  id: number;
  mealType: MealType;
  name: string;
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  note: string | null;
  occurredAt: string;
}

/** 饮食分页响应（与后端 DietPageResponse 对齐）。 */
export interface DietListResponse {
  items: DietListItem[];
  total: number;
  page: number;
  size: number;
}

/** 创建请求（POST /diets body）。 */
export interface CreateDietRequest {
  mealType: MealType;
  name: string;
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  note?: string | null;
  occurredAt: string;
}

/** 更新请求（PATCH /diets/{id} body）— 所有字段可选，null-skip；
 * 与后端 UpdateDietRequest 8 字段对齐。 */
export interface UpdateDietRequest {
  mealType?: MealType;
  name?: string;
  kcal?: number;
  proteinG?: number;
  carbG?: number;
  fatG?: number;
  note?: string | null;
  occurredAt?: string;
}

/** 当日营养汇总（GET /diets/summary 响应）。
 * 字段顺序与后端 DietSummary record 完全一致；
 * kcalDeltaYesterday / kcalDeltaLastWeek 为 null 时表示对比日无数据（PRD §3.1）。 */
export interface DietSummary {
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
  /** null = 昨日或当日 kcal=0，无对比数据。 */
  kcalDeltaYesterday: number | null;
  /** null = 上周同日或当日 kcal=0，无对比数据。 */
  kcalDeltaLastWeek: number | null;
}

/** 高频名称聚合（GET /diets/frequent 响应元素）。
 * 字段顺序与后端 DietFrequentItem record 完全一致。 */
export interface DietFrequentItem {
  name: string;
  avgKcal: number;
  avgProteinG: number;
  avgCarbG: number;
  avgFatG: number;
  hitCount: number;
}

/** 列表过滤条件（与后端 DietFilter 对齐，page/size 必有）。 */
export interface DietFilter {
  mealType?: MealType;
  /** ISO-8601 datetime with offset；null = 无下界。 */
  from?: string;
  /** ISO-8601 datetime with offset；null = 无上界。 */
  to?: string;
  page: number;
  size: number;
}