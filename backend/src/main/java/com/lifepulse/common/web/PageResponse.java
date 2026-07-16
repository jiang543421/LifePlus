package com.lifepulse.common.web;

import java.util.List;

/**
 * 通用分页响应载荷（spec §03 §5.3 列表端点）。
 *
 * <p>位于 {@code common.web} 包，仅承载"items + total + 当前页码 + 页大小"，不绑定
 * 任何业务类型；service 层负责构造，controller 层塞进 {@link MyResponse} 信封。
 *
 * <p>{@code items} 在构造时强制不可变视图（{@link List#copyOf}），符合 CLAUDE.md §4.1
 * "集合返回不可变视图"硬性约束。
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return new PageResponse<>(items, total, page, size);
    }
}