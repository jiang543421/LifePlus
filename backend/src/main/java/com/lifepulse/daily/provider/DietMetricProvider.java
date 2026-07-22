package com.lifepulse.daily.provider;

import com.lifepulse.daily.DietMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Diet 数据源的日指标聚合器（plan §5 T5 / spec §2）。
 *
 * <p><b>永久冻结契约（v1.2.3 起）</b>：本 Provider <b>永远</b>返回
 * {@code new DietMetrics(false, null, FROZEN_REASON)}，不查询 {@code t_diet}，
 * 不注入 {@code DietMapper}。{@link DietMetrics} record 形状已锁定，
 * 前端 {@code DietMetricsCard} 组件按 {@code enabled=false} 渲染占位。
 *
 * <p>冻结期解除路径（v1.2.4+ 排期）：
 * <ol>
 *   <li>本类解除"不注入 mapper"的实现限制，加入 {@code DietMapper} 依赖</li>
 *   <li>实现真实聚合逻辑填入 {@code DietMetrics.value}</li>
 *   <li>{@link DietMetricProviderTest} 解锁锁死断言（{@code @Disabled} → 删除）</li>
 *   <li>{@link DietMetrics} record 字段顺序 / 名称不变</li>
 * </ol>
 *
 * <p><b>单元测试锁死契约</b>：{@code DietMetricProviderTest} 强制断言
 * {@code enabled=false} / {@code value=null} / {@code reason} 非空，
 * 任一断言失败 = 契约被破坏，必须先开 issue 评审再合并。
 *
 * <p>构造器无 mapper 依赖（与 {@code TaskService} 同款风格，显式构造器注入），
 * 仅用于与 Spring 组件扫描保持一致；单测可直接 {@code new DietMetricProvider()}。
 */
@Component
public class DietMetricProvider implements MetricProvider<DietMetrics> {

    private static final Logger log = LoggerFactory.getLogger(DietMetricProvider.class);

    /**
     * 冻结原因文本。v1.2.3 上线时定稿；上线日调整需同步更新
     * {@code DietMetricProviderTest#aggregateDaily_returnsFrozenDietMetrics} 断言。
     */
    static final String FROZEN_REASON =
            "diet module not enabled in v1.2.3; scheduled for v1.2.4+";

    public DietMetricProvider() {
        // 冻结期无依赖；v1.2.4+ 解冻时改为:
        //   public DietMetricProvider(DietMapper dietMapper) { this.dietMapper = dietMapper; }
    }

    @Override
    public DietMetrics aggregateDaily(long userId, LocalDate date) {
        log.debug("DietMetricProvider frozen: user={} date={} -> enabled=false", userId, date);
        return new DietMetrics(false, null, FROZEN_REASON);
    }
}