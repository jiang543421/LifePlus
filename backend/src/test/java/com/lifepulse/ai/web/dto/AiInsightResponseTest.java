package com.lifepulse.ai.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifepulse.ai.llm.LlmMeta;
import com.lifepulse.ai.llm.Mood;
import com.lifepulse.ai.model.Trend;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiInsightResponseTest {

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

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

    @Test
    void serialize_llmFields_includedWhenSet() throws Exception {
        var resp = new AiInsightResponse(
                "h", List.of(), Instant.parse("2026-07-22T17:30:00Z"), 0L,
                "llm", "advice", "highlight", Mood.POSITIVE,
                new LlmMeta(10, 20, 1500L));

        String json = om.writeValueAsString(resp);

        assertThat(json).contains("\"source\":\"llm\"");
        assertThat(json).contains("\"mood\":\"POSITIVE\"");
        assertThat(json).contains("\"promptTokens\":10");
    }

    @Test
    void serialize_templateFallback_omitsLlmFields() throws Exception {
        var resp = new AiInsightResponse(
                "h", List.of(), Instant.parse("2026-07-22T17:30:00Z"), 0L,
                "template", null, null, null, null);

        String json = om.writeValueAsString(resp);

        assertThat(json).contains("\"source\":\"template\"");
        assertThat(json).doesNotContain("advice");
        assertThat(json).doesNotContain("highlight");
    }

    @Test
    void deserialize_v20OldEntry_returnsNullLlmFields() throws Exception {
        String v20Json = """
                {"headline":"h","chips":[],"generatedAt":"2026-07-22T17:30:00Z","freshnessSeconds":0}
                """;

        AiInsightResponse response = om.readValue(v20Json, AiInsightResponse.class);

        assertThat(response.source()).isNull();
        assertThat(response.advice()).isNull();
        assertThat(response.mood()).isNull();
        assertThat(response.llmMeta()).isNull();
    }
}
