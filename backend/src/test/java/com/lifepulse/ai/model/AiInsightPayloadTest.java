package com.lifepulse.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiInsightPayloadTest {

    @Test
    void chipsDefensiveCopy_callerMutationDoesNotAffectInternalState() {
        var mutable = new ArrayList<MetricValue>();
        mutable.add(new MetricValue(new BigDecimal("80"), "%", Trend.UP));

        var payload = new AiInsightPayload("今日任务完成率 80%。", mutable, Instant.parse("2026-07-21T10:00:00Z"));

        // 外部修改原 list 不影响 payload 内部
        mutable.clear();

        assertThat(payload.chips()).hasSize(1);
        assertThat(payload.chips().get(0).value()).isEqualByComparingTo(new BigDecimal("80"));
    }

    @Test
    void chipsNull_convertedToEmptyList() {
        var payload = new AiInsightPayload("尚未有数据", null, Instant.parse("2026-07-21T10:00:00Z"));

        assertThat(payload.chips()).isEmpty();
    }

    @Test
    void chipsImmutable_returnedListCannotBeMutated() {
        var chips = List.<MetricValue>of(new MetricValue(new BigDecimal("10"), "项", Trend.FLAT));
        var payload = new AiInsightPayload("ok", chips, Instant.parse("2026-07-21T10:00:00Z"));

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> payload.chips().add(new MetricValue(BigDecimal.ONE, "项", Trend.UP)));
    }
}