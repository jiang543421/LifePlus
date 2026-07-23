// 后端 DTO 对齐（spec §08-daily-report-design §6 + 后端 Daily*Record）。
// 与其他模块 types/<module>.ts 同款：纯类型导出，无运行时。

/**
 * 任务指标（与后端 TaskMetrics record 对齐）。
 *
 * <p>{@code statusDistribution} / {@code priorityDistribution} 键为字面量字符串
 * （"TODO" / "DONE" / "CANCELLED" / "NONE" / "LOW" / "MEDIUM" / "HIGH"），
 * Provider 端强制空状态 bucket 仍以 0 填充（保证前端按固定 key 渲染不缺位）。
 */
export interface TaskMetrics {
  completedCount: number;
  totalCount: number;
  /** 0..1 之间的小数；total=0 时为 0.0。 */
  completionRate: number;
  statusDistribution: Record<string, number>;
  priorityDistribution: Record<string, number>;
}

/**
 * 日程指标（与后端 PlanMetrics record 对齐）。
 *
 * <p>{@code totalMinutes} <b>排除全天事件</b>（{@code allDay = 1}）；
 * {@code busiestHour} 为 0–23 整数，一天内无事件时为 null。
 * MVP1 t_plan 无 category 字段，{@code categoryDistribution} 当前永远为空 Map
 * （spec §2.3 未定义）。
 */
export interface PlanMetrics {
  eventCount: number;
  totalMinutes: number;
  categoryDistribution: Record<string, number>;
  /** 0..23；null = 当日无事件。 */
  busiestHour: number | null;
}

/** 单分类 Top N 排名项（与后端 ExpenseMetrics.CategoryTop 对齐）。 */
export interface ExpenseCategoryTop {
  /** 分类代码（ExpenseCategory 字面值）。 */
  code: string;
  /** 金额（元）；后端 BigDecimal → number。 */
  amount: number;
}

/**
 * 消费指标（与后端 ExpenseMetrics record 对齐）。
 *
 * <p>{@code totalAmount} 单位为元（DB DECIMAL(12,2) → number 序列化为浮点）；
 * UI 展示走 utils/number.formatAmount() 保留 2 位小数。
 * {@code categoryBreakdown} <b>固定 5 键</b>（ExpenseCategory 枚举值），
 * 零金额类别显式以 0 填充。
 */
export interface ExpenseMetrics {
  totalAmount: number;
  count: number;
  categoryBreakdown: Record<string, number>;
  topCategories: ExpenseCategoryTop[];
}

/**
 * 饮食指标值（与后端 DietMetrics.DietValue 对齐）。
 *
 * <p>v1.2.3 冻结契约下 DietMetricProvider 永远返回 enabled=false / value=null；
 * 解冻饮食指标时（本字段仍保持形状不变）由 Provider 填充。
 */
export interface DietValue {
  kcal: number;
  proteinG: number;
  carbG: number;
  fatG: number;
}

/**
 * 饮食指标（与后端 DietMetrics record 对齐，spec §3.1）。
 *
 * <p><b>v1.2.3 冻结</b>：{@code enabled} 永远 false，{@code value} 永远 null，
 * {@code reason} 永远非空。DietMetricProviderTest / DailyReportIT 锁死该行为。
 * 前端按 {@code enabled === false} 渲染「暂未启用」占位卡。
 */
export interface DietMetrics {
  enabled: boolean;
  value: DietValue | null;
  /** 解冻前为冻结原因文案；解冻后 provider 可省略或返回 null。 */
  reason: string;
}

/**
 * 单日日报响应（与后端 DailyReportPayload record 对齐）。
 *
 * <p>{@code date} ISO-8601 date（YYYY-MM-DD，与请求入参回显一致）。
 * 服务端请求时实时聚合 t_task / t_plan / t_expense，不写持久化数据。
 */
export interface DailyReportPayload {
  date: string;
  task: TaskMetrics;
  plan: PlanMetrics;
  expense: ExpenseMetrics;
  diet: DietMetrics;
}

/**
 * 周报对比三元组（与后端 WeeklyComparison.WeeklyTriplet 对齐）。
 *
 * <p>{@code delta} 为可空 Double：上周（previous）为 0 时返回 null，
 * 避免除零与符号歧义（"下降 100%" vs "下降 0"）；
 * 前端按 {@code delta === null} 渲染"—"。
 */
export interface WeeklyTriplet {
  current: number;
  previous: number;
  delta: number | null;
}

/** 周报对比（与后端 WeeklyComparison record 对齐）。 */
export interface WeeklyComparison {
  taskCompletion: WeeklyTriplet;
  planEvents: WeeklyTriplet;
  expenseAmount: WeeklyTriplet;
}

/**
 * 周报响应（与后端 WeeklyReportPayload record 对齐）。
 *
 * <p>{@code isoWeek} 形如 "2026-W29"（ISO 8601）；
 * {@code weekStart} 必为周一，{@code weekEnd} 必为周日（基于 DailyConstants.WEEK_START）。
 * 跨年周按 ISO 8601 规则：例如 2026-W01 起始为 2025-12-29。
 */
export interface WeeklyReportPayload {
  isoWeek: string;
  weekStart: string;
  weekEnd: string;
  comparison: WeeklyComparison;
}
