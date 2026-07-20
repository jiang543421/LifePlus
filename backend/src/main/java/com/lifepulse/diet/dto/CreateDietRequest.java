package com.lifepulse.diet.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifepulse.diet.DietConstants;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 创建饮食请求（POST /api/v1/diets body，spec 07-diet-design §5.1）。
 *
 * <p>营养字段允许为 0（用户「吃了但忘了热量」场景，PRD §4 故事 DIET-1），
 * 但不允许为负（{@code @DecimalMin("0.0")} + DB CHECK）。
 *
 * <p>{@code mealType} 由 service 层校验是否在 {@link com.lifepulse.diet.MealType} 枚举内
 * （{@code String} 而非 enum 接收，避免 Jackson 反序列化失败导致 500）。
 */
public record CreateDietRequest(
        @NotBlank String mealType,
        @NotBlank @Size(max = DietConstants.MAX_NAME_LEN) String name,
        @NotNull @DecimalMin("0.0") @Digits(integer = 5, fraction = 2) BigDecimal kcal,
        @NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal proteinG,
        @NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal carbG,
        @NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal fatG,
        @Size(max = DietConstants.MAX_NOTE_LEN) String note,
        @NotNull OffsetDateTime occurredAt
) {

    @JsonCreator
    public CreateDietRequest(
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