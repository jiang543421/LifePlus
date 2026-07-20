package com.lifepulse.expense.dto;

import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.entity.Expense;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 消费详情响应（spec §5 GET /expenses/{id}、POST /expenses）。
 *
 * <p>字段顺序与实体一致；{@code from(Expense)} 在 service 层调用，避免 DTO 反向依赖 mapper。
 */
public record ExpenseResponse(
        Long id,
        Long userId,
        BigDecimal amount,
        ExpenseCategory category,
        String note,
        OffsetDateTime occurredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getUserId(),
                e.getAmount(),
                e.getCategory(),
                e.getNote(),
                e.getOccurredAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
