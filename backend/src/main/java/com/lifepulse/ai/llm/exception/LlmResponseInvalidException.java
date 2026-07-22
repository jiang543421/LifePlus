package com.lifepulse.ai.llm.exception;

/**
 * LLM 响应不可解析（spec §4.3 / CLAUDE.md §11.6 code=1512）。
 *
 * <p>触发场景：4xx（非 429）/ 响应体非 JSON / 缺字段 / 敏感词拦截。
 * Service 层 catch 后降级到 L2 模板；日志禁止打印 apiKey / 完整响应体。
 */
public class LlmResponseInvalidException extends RuntimeException {
    public LlmResponseInvalidException(String message) {
        super(message);
    }

    public LlmResponseInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
