package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import org.springframework.stereotype.Component;

/**
 * 日报 provider stub（spec §7.5）。
 *
 * <p>当前分支下 daily 模块尚未合并，本 provider 仅保留接口契约与配置开关读取。
 * {@link #isEnabled(Long)} 由 {@code lp.ai.daily-enabled} 控制（默认 false），
 * Service 在循环前会跳过未启用的 provider；即使被强制调用，{@link #collect} 仍返回
 * {@link MetricValue#none()}，避免引入不存在的 daily 依赖。
 *
 * <p>待 daily 分支合并后，将本类替换为真正的 {@code DailyReportService} 调用实现。
 */
@Component
public class DailyAiProvider implements AiInsightProvider {

    private final AiInsightProperties properties;

    public DailyAiProvider(AiInsightProperties properties) {
        this.properties = properties;
    }

    @Override
    public String key() {
        return AiConstants.PROVIDER_DAILY;
    }

    @Override
    public boolean isEnabled(Long userId) {
        return properties.isDailyEnabled();
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        return MetricValue.none();
    }
}