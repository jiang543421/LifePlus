package com.lifepulse.ai.llm;

import com.lifepulse.ai.llm.exception.LlmQuotaExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmQuotaGuardTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private LlmQuotaGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(ops);
        guard = new LlmQuotaGuard(redis, 50);
    }

    @Test
    void checkAndIncrement_firstCall_incrementsAndExpires() {
        when(ops.increment(anyString())).thenReturn(1L);

        assertThatCode(() -> guard.checkAndIncrement(1L)).doesNotThrowAnyException();

        // count==1 触发 EXPIRE 25h（防止非原子窗口内 key 永驻）
        verify(redis).expire(anyString(), org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    @Test
    void checkAndIncrement_overLimit_throwsQuotaExceeded() {
        when(ops.increment(anyString())).thenReturn(51L);

        assertThatThrownBy(() -> guard.checkAndIncrement(1L))
                .isInstanceOf(LlmQuotaExceededException.class)
                .hasMessageContaining("51");
    }

    @Test
    void checkAndIncrement_redisUnavailable_failOpen() {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> guard.checkAndIncrement(1L)).doesNotThrowAnyException();
    }

    @Test
    void checkAndIncrement_keyFormatContainsUserIdAndDate() {
        when(ops.increment(anyString())).thenReturn(2L);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        guard.checkAndIncrement(1L);

        verify(ops).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .isEqualTo("lp:ai:quota:1:" + LocalDate.now());
    }
}
