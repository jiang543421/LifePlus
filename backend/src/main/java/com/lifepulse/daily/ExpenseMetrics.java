package com.lifepulse.daily;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 消费指标（plan §3 DTO / spec §2 数据聚合）。
 *
 * <p>{@code totalAmount} 单位为元，{@code BigDecimal} 类型（前端序列化为字符串避免
 * IEEE 754 失精），与 expense 模块的 DECIMAL(12,2) 对齐。{@code topCategories}
 * 按金额降序取前 3 名，与 {@link #categoryBreakdown} 子集保持一致。
 */
public record ExpenseMetrics(
        BigDecimal totalAmount,
        long count,
        Map<String, BigDecimal> categoryBreakdown,
        List<CategoryTop> topCategories
) {
    /**
     * 分类 Top N 排名项（plan §3）。
     *
     * <p>{@code code} 与 {@link com.lifepulse.expense.ExpenseCategory} 名对齐
     * （"MEAL" / "SHOPPING" / ...），前端通过常量字典映射中文文案。
     */
    public record CategoryTop(String code, BigDecimal amount) {
    }
}
