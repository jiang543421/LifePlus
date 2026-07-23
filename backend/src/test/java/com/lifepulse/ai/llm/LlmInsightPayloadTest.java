package com.lifepulse.ai.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmInsightPayloadTest {

    @Test
    void mood_fromString_clampsUnknownToNeutral() {
        assertThat(Mood.fromString("positive")).isEqualTo(Mood.POSITIVE);
        assertThat(Mood.fromString("NEUTRAL")).isEqualTo(Mood.NEUTRAL);
        assertThat(Mood.fromString("cautious")).isEqualTo(Mood.CAUTIOUS);
        assertThat(Mood.fromString("weird_value")).isEqualTo(Mood.NEUTRAL);
        assertThat(Mood.fromString(null)).isEqualTo(Mood.NEUTRAL);
    }

    @Test
    void payload_recordHoldsAllFields() {
        var meta = new LlmMeta(10, 20, 1500L);
        var p = new LlmInsightPayload("h", "a", "hi", Mood.POSITIVE, meta);
        assertThat(p.headline()).isEqualTo("h");
        assertThat(p.mood()).isEqualTo(Mood.POSITIVE);
        assertThat(p.llmMeta().latencyMs()).isEqualTo(1500L);
    }
}
