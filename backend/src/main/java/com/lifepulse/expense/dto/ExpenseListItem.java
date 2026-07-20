package com.lifepulse.expense.dto;

import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.entity.Expense;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 消费列表项（spec §5 GET /expenses）。
 *
 * <p>保留全部字段（金额 / 分类 / 时间 / 备注）— 列表项需要展示摘要+备注，
 * 列表场景与详情场景字段差异小，简化契约。{@code userId}/{@code createdAt}/{@code updatedAt}
 * 不下发（鉴权由 UserContext 完成，列表不需要 createdAt）。
 */
public record ExpenseListItem(
        Long id,
        BigDecimal amount,
        ExpenseCategory category,
        String note,
        OffsetDateTime occurredAt
) {
    public static ExpenseListItem from(Expense e) {
        return new ExpenseListItem(
                e.getId(),
                e.getAmount(),
                e.getCategory(),
                e.getNote(),
                e.getOccurredAt()
        );
    }
}
