package com.lifepulse.diet;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 饮食模块餐别枚举（spec 07-diet-design §2）。
 *
 * <p>{@code getValue()} 返回英文枚举名（与 DB CHECK 字面值 / SQL CHECK 一致），
 * 序列化为 JSON 时也输出英文名；中文 label 仅用于 UI 展示。
 *
 * <p>SNACK 餐别可重复使用（早 / 下午茶 / 宵夜均归 SNACK），spec 决策不扩展自定义餐别。
 */
public enum MealType implements IEnum<String> {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐");

    private final String label;

    MealType(String label) {
        this.label = label;
    }

    /** 中文标签，用于 UI。 */
    public String getLabel() {
        return label;
    }

    @Override
    @JsonValue
    public String getValue() {
        return name();
    }
}