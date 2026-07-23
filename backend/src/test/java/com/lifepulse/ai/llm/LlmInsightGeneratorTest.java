package com.lifepulse.ai.llm;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.llm.exception.LlmCircuitOpenException;
import com.lifepulse.ai.llm.exception.LlmQuotaExceededException;
import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import com.lifepulse.ai.llm.exception.LlmUnavailableException;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LlmInsightGenerator} 编排单测（Task 11）。
 *
 * <p>验证 quota → circuit → prompt → client → parser 的纯编排与异常传播：
 * 本组件不做降级，4 类 Llm*Exception 原样上抛（CLAUDE.md §11.3）。
 * client 成功 → recordSuccess；client 抛 / parser 抛 → recordFailure + rethrow。
 */
class LlmInsightGeneratorTest {

    private LlmClient client;
    private LlmJsonParser parser;
    private LlmQuotaGuard quota;
    private LlmCircuitBreaker breaker;
    private LlmPromptBuilder promptBuilder;
    private LlmInsightGenerator gen;
    private LlmProperties props;

    @BeforeEach
    void setUp() {
        client = mock(LlmClient.class);
        parser = mock(LlmJsonParser.class);
        quota = mock(LlmQuotaGuard.class);
        breaker = mock(LlmCircuitBreaker.class);
        promptBuilder = mock(LlmPromptBuilder.class);
        props = new LlmProperties(
            true, "deepseek", "https://x", "sk-test-valid-format-12345",
            "m", 5000, 1500, 300, 50,
            new LlmProperties.CircuitBreaker(true, 10, 5, 30));
        gen = new LlmInsightGenerator(client, parser, quota, breaker, promptBuilder, props);
    }

    private Map<String, MetricValue> metrics() {
        return Map.of(
            AiConstants.CHIP_TASK_COMPLETION, new MetricValue(new BigDecimal("80"), "%", Trend.UP),
            AiConstants.CHIP_WEEKLY_EXPENSE, new MetricValue(new BigDecimal("420"), "¥", Trend.DOWN),
            AiConstants.CHIP_DIET_INTAKE, new MetricValue(new BigDecimal("4"), "kcal", Trend.FLAT));
    }

    private LlmRequest request() {
        return new LlmRequest("sys", "user", 100, Duration.ofSeconds(5));
    }

    @Test
    void generate_quotaExceeded_throws1510() {
        doThrow(new LlmQuotaExceededException(1L, 51, 50)).when(quota).checkAndIncrement(anyLong());

        assertThatThrownBy(() -> gen.generate(1L, metrics(), LocalDate.now()))
            .isInstanceOf(LlmQuotaExceededException.class);
        verify(client, never()).generate(any());
        verify(breaker, never()).tryAcquire(anyLong());
    }

    @Test
    void generate_circuitOpen_throws1511() {
        doThrow(new LlmCircuitOpenException()).when(breaker).tryAcquire(anyLong());

        assertThatThrownBy(() -> gen.generate(1L, metrics(), LocalDate.now()))
            .isInstanceOf(LlmCircuitOpenException.class);
        verify(client, never()).generate(any());
    }

    @Test
    void generate_happyPath_returnsParsedPayload() {
        LlmRequest req = request();
        LlmResponse resp = new LlmResponse("{\"headline\":\"h\"}", 10, 20, 1500L);
        LlmInsightPayload payload = new LlmInsightPayload(
            "h with enough length", "advice text", "highlight text",
            Mood.POSITIVE, 10, 20, 1500L);
        when(promptBuilder.build(anyLong(), any(), any())).thenReturn(req);
        when(client.generate(req)).thenReturn(resp);
        when(parser.parse(resp)).thenReturn(payload);

        LlmInsightPayload result = gen.generate(1L, metrics(), LocalDate.now());

        assertThat(result).isEqualTo(payload);
        verify(breaker).recordSuccess();
        verify(breaker, never()).recordFailure();
    }

    @Test
    void generate_clientTimeout_throws1513_andRecordsFailure() {
        when(promptBuilder.build(anyLong(), any(), any())).thenReturn(request());
        when(client.generate(any())).thenThrow(new LlmUnavailableException("timeout"));

        assertThatThrownBy(() -> gen.generate(1L, metrics(), LocalDate.now()))
            .isInstanceOf(LlmUnavailableException.class);
        verify(breaker).recordFailure();
        verify(breaker, never()).recordSuccess();
        verify(parser, never()).parse(any());
    }

    @Test
    void generate_parserThrows_throws1512_andRecordsFailure() {
        when(promptBuilder.build(anyLong(), any(), any())).thenReturn(request());
        when(client.generate(any())).thenReturn(new LlmResponse("{}", 0, 0, 0L));
        when(parser.parse(any())).thenThrow(new LlmResponseInvalidException("missing headline"));

        assertThatThrownBy(() -> gen.generate(1L, metrics(), LocalDate.now()))
            .isInstanceOf(LlmResponseInvalidException.class);
        verify(breaker).recordFailure();
        verify(breaker, never()).recordSuccess();
    }

    @Test
    void generate_disabled_throwsIllegalState() {
        LlmProperties disabledProps = new LlmProperties(
            false, "deepseek", "https://x", "", "m", 5000, 1500, 300, 50,
            new LlmProperties.CircuitBreaker(true, 10, 5, 30));
        LlmInsightGenerator disabledGen = new LlmInsightGenerator(
            client, parser, quota, breaker, promptBuilder, disabledProps);

        assertThatThrownBy(() -> disabledGen.generate(1L, metrics(), LocalDate.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled");
        verify(quota, never()).checkAndIncrement(anyLong());
        verify(breaker, never()).tryAcquire(anyLong());
    }
}
