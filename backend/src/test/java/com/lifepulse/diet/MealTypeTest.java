package com.lifepulse.diet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MealType} 单元测试（diet v1.2.2 · plan T2）。
 *
 * <p>断言：
 * <ol>
 *   <li>枚举值数量与 spec §2 一致（4 项）</li>
 *   <li>中文 label 与 UI 文案一致</li>
 *   <li>{@code getValue()} 返回枚举名（与 DB CHECK 字面值对齐）</li>
 *   <li>label 集合覆盖全部 4 项</li>
 * </ol>
 */
class MealTypeTest {

    @Test
    void enum_has4Values() {
        assertThat(MealType.values()).hasSize(4);
    }

    @Test
    void label_isChinese() {
        assertThat(MealType.BREAKFAST.getLabel()).isEqualTo("早餐");
        assertThat(MealType.LUNCH.getLabel()).isEqualTo("午餐");
        assertThat(MealType.DINNER.getLabel()).isEqualTo("晚餐");
        assertThat(MealType.SNACK.getLabel()).isEqualTo("加餐");
    }

    @Test
    void getValue_isEnumName() {
        assertThat(MealType.BREAKFAST.getValue()).isEqualTo("BREAKFAST");
        assertThat(MealType.LUNCH.getValue()).isEqualTo("LUNCH");
        assertThat(MealType.DINNER.getValue()).isEqualTo("DINNER");
        assertThat(MealType.SNACK.getValue()).isEqualTo("SNACK");
    }

    @Test
    void labelValuesCoverAll4Meals() {
        var labels = java.util.Arrays.stream(MealType.values())
                .map(MealType::getLabel)
                .toList();
        assertThat(labels).containsExactlyInAnyOrder("早餐", "午餐", "晚餐", "加餐");
    }
}
