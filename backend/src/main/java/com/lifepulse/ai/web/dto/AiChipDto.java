package com.lifepulse.ai.web.dto;

import com.lifepulse.ai.model.Trend;

/**
 * 单个指标 chip（spec §6.2）。
 *
 * <p>卡面固定 3 个 chip，顺序：taskCompletion → weeklyExpense → planDensity。
 * 全空数据时 {@code value="—"} {@code trend=NONE} {@code deltaText=""}。
 */
public record AiChipDto(
    String key,
    String label,
    String value,
    String unit,
    Trend trend,
    String deltaText
) {

    /** 全空数据占位 chip（用于 chips=[] 时的占位）。 */
    public static AiChipDto empty(String key, String label) {
        return new AiChipDto(key, label, "—", "", Trend.NONE, "");
    }
}