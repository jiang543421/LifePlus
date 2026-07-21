package com.lifepulse.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.provider.AiCollectContext;
import com.lifepulse.ai.provider.AiInsightProvider;
import com.lifepulse.ai.web.dto.AiChipDto;
import com.lifepulse.ai.web.dto.AiInsightResponse;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 洞察编排层（spec §4 / §6 / §9）。
 *
 * <p>职责：
 * <ol>
 *   <li>缓存读写（Redis，TTL 30 min）</li>
 *   <li>串联 5 个 provider，任一失败 catch + log + 该 chip 占位</li>
 *   <li>模板渲染主文 + chip 副标</li>
 *   <li>全部失败 → 抛 {@code BusinessException(1501)}</li>
 * </ol>
 */
@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);

    /** plan density 阈值：≥5 busy / 1-4 normal / 0 free（spec §6.3）。 */
    private static final int PLAN_DENSITY_BUSY_THRESHOLD = 5;

    /** Jackson 用于缓存 JSON 序列化。 */
    private static final ObjectMapper OM = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<AiInsightProvider> providers;
    private final AiTemplateEngine templateEngine;
    private final StringRedisTemplate redis;

    public AiInsightService(List<AiInsightProvider> providers,
                            AiTemplateEngine templateEngine,
                            ObjectProvider<StringRedisTemplate> redisProvider) {
        this.providers = List.copyOf(providers);
        this.templateEngine = templateEngine;
        this.redis = redisProvider.getIfAvailable();
    }

    /**
     * 获取当前用户的洞察。Redis 优先；miss 时串联 5 个 provider。
     *
     * @throws BusinessException 1501 当全部 provider 均无有效数据
     */
    public AiInsightResponse getInsight(long userId) {
        String cacheKey = cacheKey(userId);
        AiInsightResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        AiCollectContext ctx = AiCollectContext.nowInShanghai();
        List<MetricValue> collected = new ArrayList<>(providers.size());
        int successCount = 0;
        for (AiInsightProvider provider : providers) {
            if (!provider.isEnabled(userId)) {
                collected.add(MetricValue.none());
                continue;
            }
            try {
                MetricValue mv = provider.collect(userId, ctx);
                collected.add(mv);
                if (mv.isNonEmpty()) {
                    successCount++;
                }
            } catch (RuntimeException ex) {
                log.warn("AI provider failed: key={}, userId={}, err={}",
                    provider.key(), userId, ex.toString());
                collected.add(MetricValue.none());
            }
        }

        if (successCount == 0) {
            log.error("AI insight all providers failed: userId={}", userId);
            throw new BusinessException(ErrorCode.AI_DEGRADED,
                "AI 洞察数据暂时不可用，请稍后重试");
        }

        AiInsightResponse response = buildResponse(collected);
        writeCache(cacheKey, response);
        return response;
    }

    // ===== 私有组装 =====

    private AiInsightResponse buildResponse(List<MetricValue> collected) {
        MetricValue task = valueAt(collected, AiConstants.PROVIDER_TASK);
        MetricValue expense = valueAt(collected, AiConstants.PROVIDER_EXPENSE);
        MetricValue plan = valueAt(collected, AiConstants.PROVIDER_PLAN);

        String headline = renderHeadline(task, expense);
        List<AiChipDto> chips = List.of(
            chipForTask(task),
            chipForExpense(expense),
            chipForPlan(plan)
        );

        return new AiInsightResponse(
            headline,
            chips,
            Instant.now(),
            0L
        );
    }

    private MetricValue valueAt(List<MetricValue> collected, String key) {
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).key().equals(key) && i < collected.size()) {
                return collected.get(i);
            }
        }
        return MetricValue.none();
    }

    private String renderHeadline(MetricValue task, MetricValue expense) {
        boolean hasTask = task.isNonEmpty();
        boolean hasExpense = expense.isNonEmpty();
        if (hasTask && hasExpense) {
            return templateEngine.formatHeadline(
                AiConstants.TMPL_HEADLINE_FULL,
                formatNumber(task.value(), 0),
                "",
                formatNumber(expense.value(), 2),
                ""
            );
        }
        if (hasTask) {
            return templateEngine.formatHeadline(
                AiConstants.TMPL_HEADLINE_TASK_ONLY,
                formatNumber(task.value(), 0),
                ""
            );
        }
        if (hasExpense) {
            return templateEngine.formatHeadline(
                AiConstants.TMPL_HEADLINE_EXPENSE_ONLY,
                formatNumber(expense.value(), 2),
                ""
            );
        }
        return templateEngine.formatHeadline(AiConstants.TMPL_HEADLINE_EMPTY);
    }

    private AiChipDto chipForTask(MetricValue task) {
        if (!task.isNonEmpty()) {
            return AiChipDto.empty(AiConstants.CHIP_TASK_COMPLETION, "任务完成");
        }
        return new AiChipDto(
            AiConstants.CHIP_TASK_COMPLETION,
            "任务完成",
            formatNumber(task.value(), 0),
            task.unit(),
            task.trend(),
            templateEngine.formatChipDelta(AiConstants.CHIP_TASK_COMPLETION,
                trendName(task), "")
        );
    }

    private AiChipDto chipForExpense(MetricValue expense) {
        if (!expense.isNonEmpty()) {
            return AiChipDto.empty(AiConstants.CHIP_WEEKLY_EXPENSE, "本周消费");
        }
        return new AiChipDto(
            AiConstants.CHIP_WEEKLY_EXPENSE,
            "本周消费",
            "¥" + formatNumber(expense.value(), 2),
            expense.unit(),
            expense.trend(),
            templateEngine.formatChipDelta(AiConstants.CHIP_WEEKLY_EXPENSE,
                trendName(expense), "")
        );
    }

    private AiChipDto chipForPlan(MetricValue plan) {
        if (plan.value() == null) {
            return AiChipDto.empty(AiConstants.CHIP_PLAN_DENSITY, "日程");
        }
        int n = plan.value().setScale(0, RoundingMode.HALF_UP).intValue();
        String subKey = n >= PLAN_DENSITY_BUSY_THRESHOLD ? "busy"
            : (n > 0 ? "normal" : "free");
        String templateKey = AiConstants.TMPL_CHIP_PREFIX
            + AiConstants.CHIP_PLAN_DENSITY + "." + subKey;
        String deltaText = templateEngine.formatHeadline(templateKey, n);
        return new AiChipDto(
            AiConstants.CHIP_PLAN_DENSITY,
            "日程",
            String.valueOf(n),
            plan.unit(),
            plan.trend(),
            deltaText
        );
    }

    private static String trendName(MetricValue mv) {
        return mv.trend().name().toLowerCase();
    }

    private static String formatNumber(BigDecimal v, int scale) {
        if (v == null) {
            return "0";
        }
        return v.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    // ===== 缓存 =====

    private String cacheKey(long userId) {
        return AiConstants.CACHE_KEY_PREFIX + userId;
    }

    private AiInsightResponse readCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return OM.readValue(json, AiInsightResponse.class);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("AI cache read failed: key={}, err={}", key, ex.toString());
            return null;
        }
    }

    private void writeCache(String key, AiInsightResponse response) {
        try {
            String json = OM.writeValueAsString(response);
            redis.opsForValue().set(key, json,
                AiConstants.CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("AI cache write failed: key={}, err={}", key, ex.toString());
        }
    }

    /** 刷新 freshnessSeconds；供 Controller 在响应前调用。 */
    public static long freshnessSeconds(AiInsightResponse r) {
        if (r.generatedAt() == null) {
            return 0L;
        }
        long s = Duration.between(r.generatedAt(), Instant.now()).getSeconds();
        return s < 0 ? 0L : s;
    }
}