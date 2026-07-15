package com.lifepulse.common.security;

import com.lifepulse.auth.AuthConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * 滑动窗口计数器形式的限流器（plan §3-A，§6）。
 *
 * <p>使用 Redis Lua 脚本 {@code INCR + EXPIRE NX} 原子地：
 * <ol>
 *   <li>对 key 计数 +1</li>
 *   <li>若是首次（cur==1）则设置 key 的过期时间（窗口长度）</li>
 *   <li>返回当前计数</li>
 * </ol>
 *
 * <p>{@link #hit(String, int, Duration)} 在 {@code count > max} 时返回 {@code true}
 * 表示已命中上限；调用方应拒绝请求。
 *
 * <p><b>失败开放</b>：Redis 不可达时 {@link DataAccessException} 被捕获并降级放行，
 * 同时记录 WARN 日志，避免因基础设施故障阻塞正常登录（plan §6）。
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    private final DefaultRedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(AuthConstants.RATE_LIMIT_LUA, Long.class);
    }

    /**
     * 对 key 触发一次计数；窗口内计数超过 max 时返回 {@code true}（命中限流）。
     *
     * @param key    完整 Redis key（含前缀）
     * @param max    窗口内允许的最大次数
     * @param window 窗口长度（首次写入时设置 TTL）
     * @return {@code true} 当本次计数后已超限
     */
    public boolean hit(String key, int max, Duration window) {
        long count;
        try {
            Long result = redis.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(window.toSeconds()));
            count = result == null ? 0L : result;
        } catch (DataAccessException e) {
            // 失败开放：Redis 不可达时降级放行，避免阻塞登录主链路
            log.warn("rate-limit unavailable, fail-open: key={}, err={}", key, e.getMessage());
            return false;
        }
        return count > max;
    }
}