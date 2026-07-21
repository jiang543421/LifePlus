package com.lifepulse.daily;

/**
 * 周报对比（plan §3 DTO / spec §3 周报）。
 *
 * <p>三组对比项（任务完成率 / 日程事件数 / 消费总额），每组含本周当前值、上周对比值
 * 与差值（delta）。{@code delta} 为 null 表示上周（previous）为 0，差值无意义
 * （避免除零与符号歧义）。
 */
public record WeeklyComparison(
        WeeklyTriplet taskCompletion,
        WeeklyTriplet planEvents,
        WeeklyTriplet expenseAmount
) {
    /**
     * 单指标三元组（plan §3）。
     *
     * <p>字段类型统一为 double；前端按需格式化（百分比 / 整数 / 货币）。
     * 消费金额 delta 单位与 {@link ExpenseMetrics#totalAmount()} 一致（元）。
     */
    public record WeeklyTriplet(
            double current,
            double previous,
            Double delta
    ) {
    }
}
