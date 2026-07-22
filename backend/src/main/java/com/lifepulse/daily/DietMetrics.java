package com.lifepulse.daily;

import java.math.BigDecimal;

/**
 * 饮食指标（plan §3 DTO / 形状冻结）。
 *
 * <p><b>本 record 形状在 v1.2.3 永久冻结</b>：{@code enabled} 永远 false，
 * {@code value} 永远 null，{@code reason} 永远为非空字符串。DietMetricProvider
 * 单测 + 集成测试锁死该行为（DietMetricProviderTest / DailyReportIT）。
 *
 * <p>饮食模块（v1.2.2 独立排期）正式上线时，<b>只需</b>：
 * <ol>
 *   <li>DietMetricProvider 内部填 {@code enabled = true} 并实现 value</li>
 *   <li>解锁 DietMetricProviderTest 锁死断言</li>
 *   <li>本 record 字段顺序 / 名称不变</li>
 * </ol>
 * 前端 DietMetricsCard 组件零改动。
 */
public record DietMetrics(
        boolean enabled,
        DietValue value,
        String reason
) {
    /**
     * 饮食指标值（v1.2.3 永远 null；上线后由 DietMetricProvider 填充）。
     *
     * <p>单位：kcal 热量、protein/carb/fat 克数。沿用 07-diet-design §2.1 DECIMAL 精度。
     */
    public record DietValue(
            BigDecimal kcal,
            BigDecimal proteinG,
            BigDecimal carbG,
            BigDecimal fatG
    ) {
    }
}
