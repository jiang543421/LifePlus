package com.lifepulse.diet.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifepulse.diet.DietConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 修改饮食请求（PATCH /api/v1/diets/{id} body，spec 07-diet-design §5.1）。
 *
 * <p>所有字段可选，null-skip 语义（与 v1.2.1 expense UpdateExpenseRequest 对齐）。
 * 仅传需要修改的字段，未传字段保持原值不变。
 *
 * <p>空字段（空字符串 / 0）按业务决定：
 * <ul>
 *   <li>{@code mealType} 空串 / 不在 enum → 1001</li>
 *   <li>{@code name} 空串 → 1001（不允许清空名称）</li>
 *   <li>{@code note} 空串 → service 转 null（允许清空备注）</li>
 *   <li>营养字段 0 保留（语义同 create：允许「吃了但忘了热量」）</li>
 * </ul>
 */
public record UpdateDietRequest(
        String mealType,
        @Size(max = DietConstants.MAX_NAME_LEN) String name,
        @DecimalMin("0.0") @Digits(integer = 5, fraction = 2) BigDecimal kcal,
        @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal proteinG,
        @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal carbG,
        @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal fatG,
        @Size(max = DietConstants.MAX_NOTE_LEN) String note,
        OffsetDateTime occurredAt
) {

    @JsonCreator
    public UpdateDietRequest(
            @JsonProperty("mealType") String mealType,
            @JsonProperty("name") String name,
            @JsonProperty("kcal") BigDecimal kcal,
            @JsonProperty("proteinG") BigDecimal proteinG,
            @JsonProperty("carbG") BigDecimal carbG,
            @JsonProperty("fatG") BigDecimal fatG,
            @JsonProperty("note") String note,
            @JsonProperty("occurredAt") OffsetDateTime occurredAt
    ) {
        this.mealType = mealType;
        this.name = name;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.carbG = carbG;
        this.fatG = fatG;
        this.note = note;
        this.occurredAt = occurredAt;
    }
}