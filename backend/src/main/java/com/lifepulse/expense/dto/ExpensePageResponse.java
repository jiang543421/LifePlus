package com.lifepulse.expense.dto;

import java.util.List;

/**
 * 消费分页结果（spec §5 GET /expenses）。
 *
 * <p>使用 {@code total/page/size/list} 4 字段元组。
 * {@code page} 从 1 开始（与 controller 入参约定一致）。
 * {@code list} 内元素为 {@link ExpenseListItem}；详情场景用 {@link ExpenseResponse}。
 */
public record ExpensePageResponse(
        long total,
        int page,
        int size,
        List<ExpenseListItem> list
) {
    public static ExpensePageResponse of(long total, int page, int size, List<ExpenseListItem> list) {
        return new ExpensePageResponse(total, page, size, list);
    }
}
