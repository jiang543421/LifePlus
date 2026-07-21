package com.lifepulse.diet.dto;

import com.lifepulse.diet.entity.Diet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 饮食详情响应（GET /diets/{id} / POST /diets 响应，spec 07-diet-design §5.1）。
 *
 * <p>{@code mealType} 输出枚举字面值（{@code "BREAKFAST" / "LUNCH" / ...}），
 * 中文 label 仅用于 UI 层常量映射。
 */
public record DietResponse(
        Long id,
        Long userId,
        String mealType,
        String name,
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal carbG,
        BigDecimal fatG,
        String note,
        OffsetDateTime occurredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DietResponse from(Diet d) {
        return new DietResponse(
                d.getId(),
                d.getUserId(),
                d.getMealType() != null ? d.getMealType().name() : null,
                d.getName(),
                d.getKcal(),
                d.getProteinG(),
                d.getCarbG(),
                d.getFatG(),
                d.getNote(),
                d.getOccurredAt(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}