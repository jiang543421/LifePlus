package com.lifepulse.ai.llm;

import com.lifepulse.ai.llm.exception.LlmCircuitOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * LLM 熔断器（CLAUDE.md §11.4 / §11.5）。
 *
 * <p>基于 Redis 的滑动窗口熔断：
 * <ul>
 *   <li>{@code lp:ai:circuit:failures}（ZSET，score=epoch ms）记录窗口内失败时间戳</li>
 *   <li>{@code lp:ai:circuit:state} → CLOSED / OPEN / HALF_OPEN</li>
 *   <li>{@code lp:ai:circuit:state:openedAt} → 进入 OPEN 的 epoch ms</li>
 * </ul>
 * 窗口内失败数达 {@code failureThreshold} → OPEN，持续 {@code cooldownMinutes}；
 * 冷却期过后进入 HALF_OPEN 放行试探。
 *
 * <p>Ollama 模式（{@code circuit-breaker.enabled=false}）三方法直接 return——
 * 本地进程死掉与远程故障语义不同（§11.2）。
 *
 * <p>Redis 不可用时 <b>fail-closed</b>（不熔断、放行）：与 quota 的 fail-open 区分；
 * 熔断本身是保护性机制，Redis 抖动时不应误触发熔断（§11.5）。
 */
@Component
public class LlmCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(LlmCircuitBreaker.class);

    private static final String STATE_KEY = "lp:ai:circuit:state";
    private static final String OPENED_AT_KEY = "lp:ai:circuit:state:openedAt";
    private static final String FAILURES_KEY = "lp:ai:circuit:failures";

    private static final String STATE_OPEN = "OPEN";
    private static final String STATE_CLOSED = "CLOSED";
    private static final String STATE_HALF_OPEN = "HALF_OPEN";

    private static final Duration FAILURES_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int failureThreshold;
    private final long windowMs;
    private final long cooldownMs;

    public LlmCircuitBreaker(StringRedisTemplate redis, LlmProperties props) {
        this.redis = redis;
        LlmProperties.CircuitBreaker cb = props.circuitBreaker();
        this.enabled = cb.enabled();
        this.failureThreshold = cb.failureThreshold();
        this.windowMs = Duration.ofMinutes(cb.windowMinutes()).toMillis();
        this.cooldownMs = Duration.ofMinutes(cb.cooldownMinutes()).toMillis();
    }

    /**
     * 熔断放行检查。OPEN 且冷却未过时拒绝；窗口内失败达阈值时打开熔断。
     *
     * @param userId 当前用户（仅用于日志，熔断状态全局共享）
     * @throws LlmCircuitOpenException 熔断打开
     */
    public void tryAcquire(long userId) {
        if (!enabled) {
            return;
        }
        try {
            String state = redis.opsForValue().get(STATE_KEY);
            if (STATE_OPEN.equals(state)) {
                long openedAt = parseLong(redis.opsForValue().get(OPENED_AT_KEY));
                long now = Instant.now().toEpochMilli();
                if (now - openedAt < cooldownMs) {
                    throw new LlmCircuitOpenException();
                }
                // 冷却期已过 → HALF_OPEN，放行本次试探
                redis.opsForValue().set(STATE_KEY, STATE_HALF_OPEN);
            }

            long now = Instant.now().toEpochMilli();
            redis.opsForZSet().removeRangeByScore(FAILURES_KEY, 0, now - windowMs);
            Long failures = redis.opsForZSet().zCard(FAILURES_KEY);
            if (failures != null && failures >= failureThreshold) {
                redis.opsForValue().set(STATE_KEY, STATE_OPEN);
                redis.opsForValue().set(OPENED_AT_KEY, String.valueOf(now));
                throw new LlmCircuitOpenException();
            }
        } catch (RedisConnectionFailureException ex) {
            log.warn("LlmCircuitBreaker: redis unavailable, fail-closed for userId={}", userId);
        }
    }

    /** LLM 调用成功：关闭熔断并清空失败窗口。 */
    public void recordSuccess() {
        if (!enabled) {
            return;
        }
        try {
            redis.opsForValue().set(STATE_KEY, STATE_CLOSED);
            redis.delete(OPENED_AT_KEY);
            redis.delete(FAILURES_KEY);
        } catch (RedisConnectionFailureException ex) {
            log.warn("LlmCircuitBreaker.recordSuccess: redis unavailable, ignored");
        }
    }

    /** LLM 调用失败：向滑动窗口追加当前时间戳，并刷新窗口 TTL 防止 key 永驻。 */
    public void recordFailure() {
        if (!enabled) {
            return;
        }
        try {
            long now = Instant.now().toEpochMilli();
            redis.opsForZSet().add(FAILURES_KEY, String.valueOf(now), now);
            redis.expire(FAILURES_KEY, FAILURES_TTL);
        } catch (RedisConnectionFailureException ex) {
            log.warn("LlmCircuitBreaker.recordFailure: redis unavailable, ignored");
        }
    }

    private static long parseLong(String s) {
        try {
            return s == null ? 0L : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
