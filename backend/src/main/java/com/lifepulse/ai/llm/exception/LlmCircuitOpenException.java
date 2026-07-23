package com.lifepulse.ai.llm.exception;

/**
 * LLM 熔断器处于 OPEN 状态（CLAUDE.md §11.6 code=1511）。
 *
 * <p>由 {@code LlmCircuitBreaker} 在窗口内失败数达阈值 / 冷却期未过时抛出；
 * Service 层 catch 后降级到 L2 模板（不直接返回用户）。
 */
public class LlmCircuitOpenException extends RuntimeException {

    public LlmCircuitOpenException() {
        super("LLM circuit breaker is OPEN");
    }
}
