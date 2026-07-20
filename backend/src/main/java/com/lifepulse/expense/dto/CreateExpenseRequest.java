package com.lifepulse.expense.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.ExpenseConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 新增消费请求体（spec §5 POST /expenses）。
 *
 * <p>{@code amount} 必填且 > 0，最多 2 位小数（与 DB {@code DECIMAL(12,2)} 对齐）；
 * {@code category} 必填；{@code occurredAt} 必填（应用层 UTC，
 * controller 负责把字符串解析为 {@link OffsetDateTime}）。
 * {@code note} 可空，{@code null} 与省略等价。
 */
public record CreateExpenseRequest(
        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @NotNull
        ExpenseCategory category,

        @Size(max = ExpenseConstants.MAX_NOTE_LEN)
        String note,

        @NotNull
        OffsetDateTime occurredAt
) {
    @JsonCreator
    public CreateExpenseRequest(
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
