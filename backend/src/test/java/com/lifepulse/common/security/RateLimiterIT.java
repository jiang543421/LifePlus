package com.lifepulse.common.security;

import com.lifepulse.it.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.2-A · RateLimiter 集成测试（plan §9）。
 *
 * <p>使用真实 Redis（{@link AbstractIntegrationTest} 提供）验证 Lua 原子脚本：
 * INCR + EXPIRE NX；窗口内计数超过 max 返回 {@code true}；窗口过期重置。
 *
 * <p>每个测试用唯一 key 前缀（{@code testRunId}）避免并发/残留污染。
 */
class RateLimiterIT extends AbstractIntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    private final String testRunId = String.valueOf(System.nanoTime());

    private String uniqueKey(String suffix) {
        return "lp:test:rl:" + testRunId + ":" + suffix;
    }

    @Test
    void hit_underMax_returnsFalse() {
        // Arrange
        String key = uniqueKey("under");
        // Act + Assert: 前 5 次都未超限
        for (int i = 1; i <= 5; i++) {
            assertThat(rateLimiter.hit(key, 5, Duration.ofSeconds(30)))
                    .as("第 %d 次未达上限", i)
                    .isFalse();
        }
        // Cleanup
        redis.delete(key);
    }

    @Test
    void hit_overMax_returnsTrue() {
        // Arrange
        String key = uniqueKey("over");
        // Act: 前 5 次不超；第 6 次超
        for (int i = 1; i <= 5; i++) {
            assertThat(rateLimiter.hit(key, 5, Duration.ofSeconds(30))).isFalse();
        }
        // Assert
        assertThat(rateLimiter.hit(key, 5, Duration.ofSeconds(30)))
                .as("第 6 次必超限")
                .isTrue();
        // Cleanup
        redis.delete(key);
    }

    @Test
    void hit_afterWindowExpires_returnsFalse() throws InterruptedException {
        // Arrange
        String key = uniqueKey("expire");
        Duration shortWindow = Duration.ofSeconds(1);
        // Act: 打满上限
        for (int i = 1; i <= 6; i++) {
            rateLimiter.hit(key, 5, shortWindow);
        }
        // 等窗口过期
        Thread.sleep(1100);
        // Assert: 窗口过期后计数重置
        assertThat(rateLimiter.hit(key, 5, shortWindow))
                .as("窗口过期后再次 hit 应未超限")
                .isFalse();
        // Cleanup
        redis.delete(key);
    }
}
