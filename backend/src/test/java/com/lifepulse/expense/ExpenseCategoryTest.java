package com.lifepulse.expense;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExpenseCategory} 单元测试（plan T2）。
 */
class ExpenseCategoryTest {

    @Test
    void enum_has5Values() {
        assertThat(ExpenseCategory.values()).hasSize(5);
    }

    @Test
    void label_isChinese() {
        assertThat(ExpenseCategory.MEAL.getLabel()).isEqualTo("餐饮");
        assertThat(ExpenseCategory.SHOPPING.getLabel()).isEqualTo("购物");
        assertThat(ExpenseCategory.TRANSPORT.getLabel()).isEqualTo("交通");
        assertThat(ExpenseCategory.SUBSCRIPTION.getLabel()).isEqualTo("订阅");
        assertThat(ExpenseCategory.OTHER.getLabel()).isEqualTo("其他");
    }

    @Test
    void getValue_isEnumName() {
        assertThat(ExpenseCategory.MEAL.getValue()).isEqualTo("MEAL");
        assertThat(ExpenseCategory.SUBSCRIPTION.getValue()).isEqualTo("SUBSCRIPTION");
    }

    @Test
    void labelValuesCoverAll5Categories() {
        var labels = java.util.Arrays.stream(ExpenseCategory.values())
                .map(ExpenseCategory::getLabel)
                .toList();
        assertThat(labels).containsExactlyInAnyOrder("餐饮", "购物", "交通", "订阅", "其他");
    }
}