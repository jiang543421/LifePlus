package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.plan.repository.PlanMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 计划密度 provider（spec §7.2）。
 *
 * <p>返回今日日程事件数；trend 永为 NONE（无跨日对比）。
 * 模板按 value 阈值自行渲染 busy / normal / free 文案。
 */
@Component
public class PlanAiProvider implements AiInsightProvider {

    private final PlanMapper planMapper;

    public PlanAiProvider(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    @Override
    public String key() {
        return AiConstants.PROVIDER_PLAN;
    }

    @Override
    public boolean isEnabled(Long userId) {
        return true;
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        int count = planMapper.countTodayEvents(userId, ctx.today());
        return new MetricValue(
            new BigDecimal(count),
            "项",
            Trend.NONE
        );
    }
}