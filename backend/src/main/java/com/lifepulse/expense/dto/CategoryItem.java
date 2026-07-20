package com.lifepulse.expense.dto;

/**
 * 分类元数据项（spec §5 GET /expenses/categories）。
 *
 * <p>{@code code} 是 {@link com.lifepulse.expense.ExpenseCategory#name()}
 * （与 DB CHECK 字面值一致），{@code name} 是中文 label（仅用于 UI 展示）。
 * 该端点不涉及 user-specific 数据 — 无须鉴权以外的任何前置条件。
 */
public record CategoryItem(String code, String name) {
}
