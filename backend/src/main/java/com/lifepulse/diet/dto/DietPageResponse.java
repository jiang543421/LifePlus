package com.lifepulse.diet.dto;

import java.util.List;

/**
 * 饮食分页响应（与后端 PageResponse&lt;T&gt; 对齐）。
 */
public record DietPageResponse(
        List<DietListItem> items,
        long total,
        int page,
        int size
) {

    public static DietPageResponse of(List<DietListItem> items, long total, int page, int size) {
        return new DietPageResponse(items, total, page, size);
    }
}