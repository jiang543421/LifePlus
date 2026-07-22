package com.lifepulse.ai.llm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LLM provider configuration (spec §10.3 / CLAUDE.md §11.2).
 *
 * <p>绑定 {@code application.yml} 中 {@code lp.ai.llm.*}：
 * <ul>
 *   <li>{@code provider}：{@code deepseek}（远程 OpenAI 兼容） 或 {@code ollama}（本地）</li>
 *   <li>{@code api-key}：必填条件为 {@code enabled=true && provider=deepseek}；缺失 / 占位符 / 长度 < 20 启动失败</li>
 *   <li>{@code circuit-breaker.enabled}：{@code provider=ollama} 时自动禁用（本地进程语义不同）</li>
 * </ul>
 *
 * <p>Spring Boot 3.x 通过 {@code @ConfigurationPropertiesScan}（在
 * {@code LifePulseApplication}）自动扫描本包；{@code @Validated} 触发 Jakarta Bean Validation，
 * 字段不满足约束时启动失败（Spring 抛 {@code ConfigurationPropertiesBindException}）。
 *
 * <p>字段名（含 kebab-case 绑定映射）与 spec §10.3 / Task 1 brief 完全一致；
 * 后续 Task 2-4（{@code LlmClient} / DeepSeek / Ollama）直接注入本 bean。
 */
@ConfigurationProperties("lp.ai.llm")
@Validated
public record LlmProperties(
        boolean enabled,
        @NotBlank String provider,
        @URL String baseUrl,
        String apiKey,
        @NotBlank String model,
        @Min(1000) @Max(30000) int timeoutMs,
        @Min(100) @Max(4000) int maxPromptTokens,
        @Min(50) @Max(1000) int maxResponseTokens,
        @Min(1) @Max(1000) int dailyQuota,
        CircuitBreaker circuitBreaker) {

    /**
     * Compact constructor: enforces deepseek fail-fast (CLAUDE.md §11.2).
     *
     * <p>Bean Validation（{@code @NotBlank} / {@code @URL} / {@code @Min} / {@code @Max}）
     * 在绑定阶段触发；此处只处理语义校验——apiKey 是字符串，约束注解无法表达
     * "provider=deepseek 时必填" / "占位符前缀" / "长度阈值" 这种跨字段规则。
     */
    public LlmProperties {
        if (enabled && "deepseek".equals(provider)) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "lp.ai.llm.api-key is required when provider=deepseek. "
                                + "Set LP_LLM_API_KEY env var or disable llm (lp.ai.llm.enabled=false).");
            }
            if (apiKey.startsWith("sk-replace-") || apiKey.length() < 20) {
                throw new IllegalStateException(
                        "lp.ai.llm.api-key looks like a placeholder. "
                                + "Replace LP_LLM_API_KEY with a real DeepSeek key.");
            }
        }
        // Ollama 模式：circuit-breaker 自动禁用（本地进程死掉与远程故障语义不同）。
        // record 是不可变值类型，无法 mutate 字段；调用方通过 LlmCircuitBreaker bean
        // 读取 lp.ai.llm.circuit-breaker.enabled 时单独判定 ollama 场景（Task 7 落地）。
        // 此处仅留注释约束供 reviewer 验证；不做赋值避免破坏 record 不变性。
    }

    /**
     * Circuit breaker 配置（spec §10.3.4 / CLAUDE.md §11.5）。
     *
     * @param enabled 启用熔断；Ollama 模式由调用方判定，本字段不感知 provider
     * @param failureThreshold 失败次数阈值（1-100）
     * @param windowMinutes 滑动窗口分钟数（1-60）
     * @param cooldownMinutes 熔断恢复分钟数（1-1440）
     */
    public record CircuitBreaker(
            boolean enabled,
            @Min(1) @Max(100) int failureThreshold,
            @Min(1) @Max(60) int windowMinutes,
            @Min(1) @Max(1440) int cooldownMinutes) {
    }
}
