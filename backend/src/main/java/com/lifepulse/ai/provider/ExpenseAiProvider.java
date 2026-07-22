package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.expense.repository.ExpenseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 本周消费 provider（spec §7.3）。
 *
 * <p>返回从本周一（按 ctx.zone）到 ctx.today 的支出汇总。
 * UTC 存储（{@code occurred_at}）→ 应用层 ZoneId 转换后再下推。
 */
@Component
public class ExpenseAiProvider implements AiInsightProvider {

    private final ExpenseMapper expenseMapper;

    public ExpenseAiProvider(ExpenseMapper expenseMapper) {
        this.expenseMapper = expenseMapper;
    }

    @Override
    public String key() {
        return AiConstants.PROVIDER_EXPENSE;
    }

    @Override
    public boolean isEnabled(Long userId) {
        return true;
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        LocalDate weekStart = ctx.today().with(java.time.DayOfWeek.MONDAY);
        OffsetDateTime from = weekStart.atStartOfDay(ctx.zone()).toOffsetDateTime();
        OffsetDateTime to = ctx.today().plusDays(1).atStartOfDay(ctx.zone()).toOffsetDateTime();

        BigDecimal sum = expenseMapper.sumByUserOccurredBetween(userId, from, to);
        if (sum == null) {
            sum = BigDecimal.ZERO;
        }
        return new MetricValue(
            sum,
            "¥",
            Trend.NONE
        );
    }
}