package com.lifepulse.ai.llm;

import com.lifepulse.ai.model.MetricValue;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * LLM 业务编排入口（spec §5 / CLAUDE.md §11.3）。
 *
 * <p>纯编排：quota → circuit → prompt → client → parser。本组件<b>不做降级</b>——
 * 4 类 {@code Llm*Exception}（1510/1511/1512/1513）原样上抛，由
 * {@code AiInsightService}（Task 14）catch 后决定是否降级到 L2 模板。
 *
 * <p><b>熔断记账</b>：{@code client.generate} 与 {@code parser.parse} 同在 try 块内——
 * 二者全成功才 {@code recordSuccess}；任一抛错走 catch 记 {@code recordFailure} 后 rethrow。
 * 解析失败（1512）归 LLM 输出失败，故也触发 recordFailure。
 *
 * <p><b>依赖方向单向</b>（§11.1 #5）：Web → Service → 本类 → LlmClient；反向禁止。
 * <b>安全</b>（§7.1）：不打印完整 prompt / response body / 密钥。
 */
@Service
public class LlmInsightGenerator {

    private final LlmClient client;
    private final LlmJsonParser parser;
    private final LlmQuotaGuard quota;
    private final LlmCircuitBreaker breaker;
    private final LlmPromptBuilder promptBuilder;
    private final boolean enabled;

    public LlmInsightGenerator(LlmClient client, LlmJsonParser parser,
                               LlmQuotaGuard quota, LlmCircuitBreaker breaker,
                               LlmPromptBuilder promptBuilder, LlmProperties props) {
        this.client = client;
        this.parser = parser;
        this.quota = quota;
        this.breaker = breaker;
        this.promptBuilder = promptBuilder;
        this.enabled = props.enabled();
    }

    /**
     * 编排一次 LLM 洞察生成。
     *
     * @param userId  当前用户（由 JWT 解析得；配额 / 熔断 / prompt 均按此隔离）
     * @param metrics 4 chip 指标（key 取 {@code AiConstants.CHIP_*}）；缺数据 chip 传
     *                {@link MetricValue#none()}。契约由调用方（Service）保证非 null
     * @param today   当天日期（注入 user prompt 的 {date} 占位）
     * @return 校验通过的 {@link LlmInsightPayload}
     * @throws IllegalStateException        LLM 全局禁用（lp.ai.llm.enabled=false）
     * @throws com.lifepulse.ai.llm.exception.LlmQuotaExceededException    1510
     * @throws com.lifepulse.ai.llm.exception.LlmCircuitOpenException      1511
     * @throws com.lifepulse.ai.llm.exception.LlmResponseInvalidException  1512
     * @throws com.lifepulse.ai.llm.exception.LlmUnavailableException      1513
     */
    public LlmInsightPayload generate(long userId, Map<String, MetricValue> metrics, LocalDate today) {
        if (!enabled) {
            throw new IllegalStateException("LLM is disabled (lp.ai.llm.enabled=false)");
        }

        quota.checkAndIncrement(userId);
        breaker.tryAcquire(userId);

        LlmRequest request = promptBuilder.build(userId, metrics, today);
        try {
            LlmResponse response = client.generate(request);
            LlmInsightPayload payload = parser.parse(response);
            breaker.recordSuccess();
            return payload;
        } catch (RuntimeException ex) {
            breaker.recordFailure();
            throw ex;
        }
    }
}
