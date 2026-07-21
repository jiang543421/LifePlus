package com.lifepulse.diet.dto;

import java.math.BigDecimal;

/**
 * 当日营养汇总（GET /diets/summary?date=YYYY-MM-DD 响应，spec 07-diet-design §5.1）。
 *
 * <p>4 项营养合计 + 与昨日 / 上周同日差值：
 * <ul>
 *   <li>kcalDeltaYesterday — 今日 - 昨日（null 表示昨日无数据）</li>
 *   <li>kcalDeltaLastWeek — 今日 - 上周同日（null 表示上周同日无数据）</li>
 * </ul>
 *
 * <p>无对比数据时返回 null（PRD §3.1 / 故事 DIET-2 AC：UI 显示「无对比数据」而非破折号）。
 */
public record DietSummary(
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal carbG,
        BigDecimal fatG,
        BigDecimal kcalDeltaYesterday,
        BigDecimal kcalDeltaLastWeek
) {
}