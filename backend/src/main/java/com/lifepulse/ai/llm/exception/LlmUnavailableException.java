package com.lifepulse.ai.llm.exception;

/**
 * LLM 远端不可用（spec §4.3 / CLAUDE.md §11.6 code=1513）。
 *
 * <p>触发场景：5xx / 429 / 网络超时 / 连接拒绝。
 * Service 层 catch 后降级到 L2 模板；日志禁止打印 apiKey / 完整 prompt。
 */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
