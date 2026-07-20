package com.lifepulse.diet.dto;

import java.time.OffsetDateTime;

/**
 * 饮食列表过滤条件（GET /diets query，与后端 DietFilter 对齐，page/size 必有）。
 *
 * <p>{@code mealType} / {@code from} / {@code to} 均为可选。
 */
public record DietFilter(
        String mealType,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
) {
}