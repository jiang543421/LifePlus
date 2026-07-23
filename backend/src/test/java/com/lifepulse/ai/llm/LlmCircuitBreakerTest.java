package com.lifepulse.ai.llm;

import com.lifepulse.ai.llm.exception.LlmCircuitOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmCircuitBreakerTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zops;
    private ValueOperations<String, String> vops;
    private LlmCircuitBreaker breaker;

    private static final String STATE_KEY = "lp:ai:circuit:state";
    private static final String OPENED_AT_KEY = "lp:ai:circuit:state:openedAt";
    private static final String FAILURES_KEY = "lp:ai:circuit:failures";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zops = mock(ZSetOperations.class);
        vops = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(vops);
        lenient().when(redis.opsForZSet()).thenReturn(zops);
        var props = new LlmProperties(
                true, "deepseek", "https://x", "sk-test-valid-format-12345",
                "m", 5000, 1500, 300, 50,
                new LlmProperties.CircuitBreaker(true, 10, 5, 30));
        breaker = new LlmCircuitBreaker(redis, props);
    }

    @Test
    void tryAcquire_stateClosed_returnsNormally() {
        when(vops.get(STATE_KEY)).thenReturn("CLOSED");
        when(zops.zCard(FAILURES_KEY)).thenReturn(3L);

        assertThatCode(() -> breaker.tryAcquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void tryAcquire_stateOpen_recentlyOpened_throwsCircuitOpen() {
        when(vops.get(STATE_KEY)).thenReturn("OPEN");
        when(vops.get(OPENED_AT_KEY))
                .thenReturn(String.valueOf(Instant.now().toEpochMilli() - 60_000L));

        assertThatThrownBy(() -> breaker.tryAcquire(1L))
                .isInstanceOf(LlmCircuitOpenException.class);
    }

    @Test
    void tryAcquire_stateOpen_cooldownExpired_transitionsToHalfOpen() {
        when(vops.get(STATE_KEY)).thenReturn("OPEN");
        when(vops.get(OPENED_AT_KEY))
                .thenReturn(String.valueOf(Instant.now().toEpochMilli() - 31 * 60_000L));
        when(zops.zCard(FAILURES_KEY)).thenReturn(3L);

        assertThatCode(() -> breaker.tryAcquire(1L)).doesNotThrowAnyException();
        verify(vops).set(STATE_KEY, "HALF_OPEN");
    }

    @Test
    void recordFailure_10FailuresIn5Min_opensCircuit() {
        when(vops.get(STATE_KEY)).thenReturn("CLOSED");
        when(zops.zCard(FAILURES_KEY)).thenReturn(10L);

        assertThatThrownBy(() -> breaker.tryAcquire(1L))
                .isInstanceOf(LlmCircuitOpenException.class);
        verify(vops).set(STATE_KEY, "OPEN");
    }

    @Test
    void tryAcquire_redisUnavailable_failClosed() {
        when(vops.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> breaker.tryAcquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void tryAcquire_ollamaMode_skipsCheck() {
        var ollamaProps = new LlmProperties(
                true, "ollama", "http://localhost:11434", "", "deepseek-r1:8b",
                5000, 1500, 300, 50,
                new LlmProperties.CircuitBreaker(false, 10, 5, 30));
        LlmCircuitBreaker ollamaBreaker = new LlmCircuitBreaker(redis, ollamaProps);

        assertThatCode(() -> ollamaBreaker.tryAcquire(1L)).doesNotThrowAnyException();

        // enabled=false 直接 return，完全不触碰 Redis
        verify(redis, never()).opsForValue();
        verify(redis, never()).opsForZSet();
    }

    @Test
    void recordSuccess_clearsFailuresAndClosesState() {
        breaker.recordSuccess();

        verify(vops).set(STATE_KEY, "CLOSED");
        verify(redis).delete(OPENED_AT_KEY);
        verify(redis).delete(FAILURES_KEY);
    }
}
