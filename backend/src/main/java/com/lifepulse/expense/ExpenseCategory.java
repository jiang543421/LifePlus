package com.lifepulse.expense;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消费分类枚举（spec 06-expense-design §4）。
 *
 * <p>{@code getValue()} 返回英文枚举名（与 DB CHECK 字面值一致），
 * 序列化为 JSON 时也输出英文名；中文 label 仅用于 UI 展示。
 */
public enum ExpenseCategory implements IEnum<String> {
    MEAL("餐饮"),
    SHOPPING("购物"),
    TRANSPORT("交通"),
    SUBSCRIPTION("订阅"),
    OTHER("其他");

    private final String label;

    ExpenseCategory(String label) {
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