package com.lifepulse.ai.llm.exception;

/**
 * LLM 每日配额超限（CLAUDE.md §11.6 code=1510）。
 *
 * <p>由 {@code LlmQuotaGuard} 在 INCR 后超过 {@code lp.ai.llm.daily-quota} 时抛出；
 * Service 层 catch 后降级到 L2 模板（不直接返回用户）。
 * 异常 message 仅含 userId + count + limit，不含密钥 / prompt 内容（§7.1）。
 */
public class LlmQuotaExceededException extends RuntimeException {

    private final long userId;
    private final long count;
    private final long limit;

    public LlmQuotaExceededException(long userId, long count, long limit) {
        super(String.format("userId=%d, quota=%d/%d", userId, count, limit));
        this.userId = userId;
        this.count = count;
        this.limit = limit;
    }

    public long userId() {
        return userId;
    }

    public long count() {
        return count;
    }

    public long limit() {
        return limit;
    }
}
