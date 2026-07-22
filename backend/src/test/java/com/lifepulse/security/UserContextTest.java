package com.lifepulse.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.3-B · {@link UserContext} 单测（plan §3-B / §4）。
 *
 * <p>覆盖 set/get/clear 与多线程隔离。所有 case 结束后 {@code @AfterEach}
 * 显式 {@link UserContext#clear()} 防止 ThreadLocal 泄漏污染后续测试
 * （CLAUDE.md §4.1 + plan §9 「线程复用泄漏」风险）。
 */
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void set_thenCurrent_returnsSame() {
        UserContext.set(42L);

        assertThat(UserContext.current()).isEqualTo(42L);
    }

    @Test
    void clear_removesValue() {
        UserContext.set(42L);
        UserContext.clear();

        assertThat(UserContext.current()).isNull();
    }

    @Test
    void current_withoutSet_returnsNull() {
        assertThat(UserContext.current()).isNull();
    }

    @Test
    void differentThreads_haveIsolatedValues() throws ExecutionException, InterruptedException {
        UserContext.set(1L);

        CompletableFuture<Long> initialInOther = CompletableFuture.supplyAsync(() -> {
            // 其他线程进入时 ThreadLocal 为 null
            Long initial = UserContext.current();
            UserContext.set(2L);
            return initial;
        });

        // 主线程未被污染
        assertThat(initialInOther.get()).isNull();
        assertThat(UserContext.current()).isEqualTo(1L);
    }
}
