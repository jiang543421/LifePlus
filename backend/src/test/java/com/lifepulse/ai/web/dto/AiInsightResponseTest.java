package com.lifepulse.ai.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifepulse.ai.model.Trend;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiInsightResponseTest {

    @Test
    void freshnessSecondsNegative_clampedToZero() {
        var chip = AiChipDto.empty("taskCompletion", "任务完成率");
        var resp = new AiInsightResponse(
                "今日任务完成率 0%。",
                List.of(chip),
                Instant.parse("2026-07-21T10:00:00Z"),
                -5L);

        assertThat(resp.freshnessSeconds()).isEqualTo(0L);
    }

    @Test
    void freshnessSecondsPositive_preserved() {
        var resp = new AiInsightResponse(
                "今日任务完成率 80%。",
                List.of(),
                Instant.parse("2026-07-21T10:00:00Z"),
                120L);

        assertThat(resp.freshnessSeconds()).isEqualTo(120L);
    }

    @Test
    void chipsNull_convertedToEmptyList() {
        var resp = new AiInsightResponse(
                "ok",
                null,
                Instant.parse("2026-07-21T10:00:00Z"),
                0L);

        assertThat(resp.chips()).isEmpty();
    }

    @Test
    void chipsDefensiveCopy_callerMutationDoesNotAffectResponse() {
        var mutable = new ArrayList<AiChipDto>();
        mutable.add(AiChipDto.empty("taskCompletion", "任务完成率"));

        var resp = new AiInsightResponse(
                "ok",
                mutable,
                Instant.parse("2026-07-21T10:00:00Z"),
                0L);

        mutable.clear();

        assertThat(resp.chips()).hasSize(1);
        assertThat(resp.chips().get(0).key()).isEqualTo("taskCompletion");
    }

    @Test
    void chipsImmutable_returnedListCannotBeMutated() {
        var resp = new AiInsightResponse(
                "ok",
                List.of(AiChipDto.empty("taskCompletion", "任务完成率")),
                Instant.parse("2026-07-21T10:00:00Z"),
                0L);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> resp.chips().add(AiChipDto.empty("weeklyExpense", "本周消费")));
    }
}