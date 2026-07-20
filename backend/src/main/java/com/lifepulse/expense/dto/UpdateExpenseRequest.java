package com.lifepulse.expense.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.ExpenseConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 更新消费请求体（spec §5 PUT /expenses/{id}）。
 *
 * <p>所有字段可选 — 服务端用 {@code null} 判定跳过；
 * {@code note=null} 保留原值，显式 {@code ""} 也保留原值
 * （{@code @Size} 仅约束非空输入）。
 *
 * <p>鉴权 1003 防御在 service 层完成：{@code findByUserAndId} empty → 抛 1003。
 */
public record UpdateExpenseRequest(
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        ExpenseCategory category,

        @Size(max = ExpenseConstants.MAX_NOTE_LEN)
        String note,

        OffsetDateTime occurredAt
) {
    @JsonCreator
    public UpdateExpenseRequest(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("category") ExpenseCategory category,
            @JsonProperty("note") String note,
            @JsonProperty("occurredAt") OffsetDateTime occurredAt
    ) {
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.occurredAt = occurredAt;
    }
}
