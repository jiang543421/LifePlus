package com.lifepulse.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * RateLimiter 单元测试（CLAUDE.md §7.2 hard rule）。
 *
 * <p>重点覆盖 fail-closed 行为：Redis 不可达时，认证类限流必须视为已限流（返回 true），
 * 不能 fail-open 放行——避免攻击者通过打瘫 Redis 绕过登录限流。
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(redis);
    }

    @Test
    void hit_countWithinMax_returnsFalse() {
        // count = 3, max = 5 → 未超限
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(3L);

        boolean limited = rateLimiter.hit("lp:rl:login:1.2.3.4:abc12345",
                5, Duration.ofMinutes(1));

        assertThat(limited).isFalse();
    }

    @Test
    void hit_countExceedsMax_returnsTrue() {
        // count = 6, max = 5 → 超限
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(6L);

        boolean limited = rateLimiter.hit("lp:rl:login:1.2.3.4:abc12345",
                5, Duration.ofMinutes(1));

        assertThat(limited).isTrue();
    }

    @Test
    void hit_redisDown_returnsTrue_failClosed() {
        // CLAUDE.md §7.2 + Review C-2: Redis 不可达必须 fail-closed（返回 true），
        // 防止攻击者打瘫 Redis 后绕过登录限流做暴力破解
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new QueryTimeoutException("redis down"));

        boolean limited = rateLimiter.hit("lp:rl:login:1.2.3.4:abc12345",
                5, Duration.ofMinutes(1));

        assertThat(limited).isTrue();
    }

    @Test
    void hit_redisReturnsNull_returnsFalse() {
        // 极端情况：Redis 返回 null（脚本执行但没结果）→ 视为 0 计数
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(null);

        boolean limited = rateLimiter.hit("lp:rl:login:1.2.3.4:abc12345",
                5, Duration.ofMinutes(1));

        assertThat(limited).isFalse();
    }
}