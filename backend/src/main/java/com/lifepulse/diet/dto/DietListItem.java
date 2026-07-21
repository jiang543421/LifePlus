package com.lifepulse.diet.dto;

import com.lifepulse.diet.entity.Diet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 饮食列表项（GET /diets 响应中的精简字段；不含 userId / createdAt / updatedAt）。
 *
 * <p>字段顺序与 {@link DietResponse} 对齐，便于前端复用渲染。
 */
public record DietListItem(
        Long id,
        String mealType,
        String name,
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal carbG,
        BigDecimal fatG,
        String note,
        OffsetDateTime occurredAt
) {

    public static DietListItem from(Diet d) {
        return new DietListItem(
                d.getId(),
                d.getMealType() != null ? d.getMealType().name() : null,
                d.getName(),
                d.getKcal(),
                d.getProteinG(),
                d.getCarbG(),
                d.getFatG(),
                d.getNote(),
                d.getOccurredAt()
        );
    }
}