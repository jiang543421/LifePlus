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
 * <p><b>失败关闭（fail-closed）</b>：Redis 不可达时 {@link DataAccessException} 被捕获
 * 并降级为"视为已限流"（返回 {@code true}），同时记录 ERROR 日志 ——
 * 攻击者打瘫 Redis 绕过限流做暴力破解的风险不可接受，故选择安全优先。
 * （CLAUDE.md §7.2 + Review C-2 修订。）
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
     * @return {@code true} 当本次计数后已超限，或 Redis 不可达（fail-closed）
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
            // 失败关闭：Redis 不可达时视为已限流，防止攻击者绕过暴力破解防线
            log.error("rate-limit unavailable, fail-closed: key={}, err={}", key, e.getMessage());
            return true;
        }
        return count > max;
    }
}