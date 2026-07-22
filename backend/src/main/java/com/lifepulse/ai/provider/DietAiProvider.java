package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.diet.repository.DietMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 当日饮食摄入 provider（spec §7.4）。
 *
 * <p>复用现有 {@link DietMapper#summaryOnDate} 单日窗口聚合，提取 kcal 字段。
 * 复用而非新增 mapper 方法：与现有 summary 业务保持口径一致（CLAUDE.md DRY）。
 */
@Component
public class DietAiProvider implements AiInsightProvider {

    private final DietMapper dietMapper;

    public DietAiProvider(DietMapper dietMapper) {
        this.dietMapper = dietMapper;
    }

    @Override
    public String key() {
        return AiConstants.PROVIDER_DIET;
    }

    @Override
    public boolean isEnabled(Long userId) {
        return true;
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        OffsetDateTime from = ctx.today().atStartOfDay(ctx.zone()).toOffsetDateTime();
        OffsetDateTime to = ctx.today().plusDays(1).atStartOfDay(ctx.zone()).toOffsetDateTime();

        Map<String, Object> row = dietMapper.summaryOnDate(userId, from, to);
        BigDecimal kcal = extractKcal(row);
        return new MetricValue(kcal, "kcal", Trend.NONE);
    }

    private static BigDecimal extractKcal(Map<String, Object> row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        Object v = row.get("kcal");
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}