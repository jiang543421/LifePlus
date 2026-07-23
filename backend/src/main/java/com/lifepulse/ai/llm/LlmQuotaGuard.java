package com.lifepulse.ai.llm;

import com.lifepulse.ai.llm.exception.LlmQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 每日 LLM 调用配额守卫（CLAUDE.md §11.5）。
 *
 * <p>Redis key：{@code lp:ai:quota:<userId>:<LocalDate.now()>}，按 userId 隔离（§11.1 #4）。
 * INCR 后若为首次（count==1）设 25h EXPIRE，避免非原子窗口内 key 永驻；
 * count 超过 {@code dailyQuota} 抛 {@link LlmQuotaExceededException}。
 *
 * <p>Redis 不可用时 <b>fail-open</b>（放行）：配额是防滥用的软限制，
 * Redis 抖动不应阻断用户体验（§11.5 "Redis 不可用降级：quota fail-open"）。
 */
@Component
public class LlmQuotaGuard {

    private static final Logger log = LoggerFactory.getLogger(LlmQuotaGuard.class);

    private static final Duration QUOTA_TTL = Duration.ofHours(25);

    private final StringRedisTemplate redis;
    private final long dailyQuota;

    @Autowired
    public LlmQuotaGuard(StringRedisTemplate redis, LlmProperties props) {
        this(redis, props.dailyQuota());
    }

    /** 测试友好构造器：直接注入 dailyQuota，跳过 LlmProperties fail-fast 逻辑。 */
    LlmQuotaGuard(StringRedisTemplate redis, long dailyQuota) {
        this.redis = redis;
        this.dailyQuota = dailyQuota;
    }

    /**
     * 递增当日配额计数并校验上限。
     *
     * @param userId 当前用户（由 JWT 解析得，端点不接受 userId 参数）
     * @throws LlmQuotaExceededException 当日调用数超过 dailyQuota
     */
    public void checkAndIncrement(long userId) {
        String key = "lp:ai:quota:" + userId + ":" + LocalDate.now();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, QUOTA_TTL);
            }
            if (count != null && count > dailyQuota) {
                throw new LlmQuotaExceededException(userId, count, dailyQuota);
            }
        } catch (RedisConnectionFailureException ex) {
            log.warn("LlmQuotaGuard: redis unavailable, fail-open for userId={}", userId);
        }
    }
}
