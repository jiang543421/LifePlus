package com.lifepulse.daily.provider;

import com.lifepulse.daily.DietMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DietMetricProvider 单元测试（plan §5 T5 / spec §2 冻结契约锁）。
 *
 * <p><b>冻结契约测试</b>：v1.2.3 起 DietMetricProvider 必须永远返回
 * {@code enabled=false} / {@code value=null} / {@code reason} 非空，
 * 任一断言失败 = 契约被破坏，必须先开 issue 评审再合并。
 *
 * <p>v1.2.4+ 解冻路径：
 * <ol>
 *   <li>移除本类全部锁死断言（{@code @Disabled} 化或删除）</li>
 *   <li>DietMetricProvider 加 {@code DietMapper} 依赖并实现真实聚合</li>
 *   <li>{@link DietMetrics} record 字段顺序 / 名称保持不变</li>
 * </ol>
 */
class DietMetricProviderTest {

    private DietMetricProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DietMetricProvider();
    }

    @Test
    @DisplayName("冻结锁：enabled=false, value=null, reason 非空匹配常量")
    void aggregateDaily_returnsFrozenDietMetrics() {
        DietMetrics m = provider.aggregateDaily(1L, LocalDate.of(2026, 7, 21));

        assertThat(m.enabled()).isFalse();
        assertThat(m.value()).isNull();
        assertThat(m.reason()).isNotBlank().isEqualTo(DietMetricProvider.FROZEN_REASON);
    }

    @Test
    @DisplayName("value 永远为 null（防御性重复断言，与 DietMetrics JavaDoc 锁定契约一致）")
    void aggregateDaily_valueIsAlwaysNull() {
        List<Long> userIds = List.of(0L, 1L, 7L, 999_999L);
        List<LocalDate> dates = List.of(
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 7, 21),
                LocalDate.of(2030, 12, 31));

        for (long uid : userIds) {
            for (LocalDate d : dates) {
                assertThat(provider.aggregateDaily(uid, d).value())
                        .as("userId=%s date=%s", uid, d)
                        .isNull();
            }
        }
    }

    @Test
    @DisplayName("任意 userId/date 输入都返回相同结构（不依赖外部状态）")
    void aggregateDaily_anyInput_returnsIdenticalOutput() {
        DietMetrics a = provider.aggregateDaily(1L, LocalDate.of(2026, 7, 21));
        DietMetrics b = provider.aggregateDaily(42L, LocalDate.of(2025, 1, 1));
        DietMetrics c = provider.aggregateDaily(Long.MAX_VALUE, LocalDate.of(1970, 1, 1));

        assertThat(a.enabled()).isEqualTo(b.enabled()).isEqualTo(c.enabled()).isFalse();
        assertThat(a.value()).isNull();
        assertThat(b.value()).isNull();
        assertThat(c.value()).isNull();
        assertThat(a.reason()).isEqualTo(b.reason()).isEqualTo(c.reason());
    }

    @Test
    @DisplayName("name() 默认返回类名")
    void name_returnsSimpleClassName() {
        assertThat(provider.name()).isEqualTo("DietMetricProvider");
    }
}
