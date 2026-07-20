package com.lifepulse.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 消费汇总响应（spec §5 GET /expenses/summary）。
 *
 * <p>按 {@link com.lifepulse.expense.ExpenseCategory} 分桶汇总，固定 5 个键；
 * {@code totalAmount} 等于各类别之和（由 service 层汇总）。
 * {@code startMonth}/{@code endMonth} 是入参月份（{@code YYYY-MM-01}）的回显，
 * 用于前端确认查询区间。
 */
public record ExpenseSummaryResponse(
        LocalDate startMonth,
        LocalDate endMonth,
        Map<String, BigDecimal> amountByCategory,
        BigDecimal totalAmount
) {
}
