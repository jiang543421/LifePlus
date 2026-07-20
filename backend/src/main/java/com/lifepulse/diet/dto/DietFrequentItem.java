package com.lifepulse.diet.dto;

import java.math.BigDecimal;

/**
 * 高频饮食项（GET /diets/frequent 响应元素，spec 07-diet-design §5.1）。
 *
 * <p>近 N 天窗口按 {@code name} 聚合：avgKcal / avgProteinG / avgCarbG / avgFatG
 * 基于该名称所有历史记录的均值；hitCount 为出现次数。
 *
 * <p>数据源：录入弹窗「一键复用」下拉（PRD §3.1 + 故事 DIET-3）。
 */
public record DietFrequentItem(
        String name,
        BigDecimal avgKcal,
        BigDecimal avgProteinG,
        BigDecimal avgCarbG,
        BigDecimal avgFatG,
        Integer hitCount
) {
}