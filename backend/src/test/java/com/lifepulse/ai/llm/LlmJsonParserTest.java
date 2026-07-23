package com.lifepulse.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmJsonParserTest {

    private LlmJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new LlmJsonParser(new ObjectMapper());
    }

    @Test
    void parse_validJson_returnsPayload() {
        String body = """
            {"headline":"today you completed six tasks",\
            "advice":"keep steady pace","highlight":"finished the report",\
            "mood":"positive"}
            """;
        var resp = new LlmResponse(body, 10, 20, 1500L);

        var payload = parser.parse(resp);

        assertThat(payload.headline()).isEqualTo("today you completed six tasks");
        assertThat(payload.advice()).isEqualTo("keep steady pace");
        assertThat(payload.highlight()).isEqualTo("finished the report");
        assertThat(payload.mood()).isEqualTo(Mood.POSITIVE);
        assertThat(payload.llmMeta().promptTokens()).isEqualTo(10);
        assertThat(payload.llmMeta().responseTokens()).isEqualTo(20);
        assertThat(payload.llmMeta().latencyMs()).isEqualTo(1500L);
    }

    @Test
    void parse_missingHeadline_throwsInvalid() {
        String body = """
            {"advice":"keep steady pace","highlight":"finished the report",\
            "mood":"neutral"}
            """;
        var resp = new LlmResponse(body, 0, 0, 0L);

        assertThatThrownBy(() -> parser.parse(resp))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("headline");
    }

    @Test
    void parse_headlineTooShort_throwsInvalid() {
        String body = """
            {"headline":"short","advice":"keep steady pace",\
            "highlight":"finished the report","mood":"neutral"}
            """;
        var resp = new LlmResponse(body, 0, 0, 0L);

        assertThatThrownBy(() -> parser.parse(resp))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("headline");
    }

    @Test
    void parse_invalidMood_clampsToNeutral() {
        String body = """
            {"headline":"today you completed six tasks",\
            "advice":"keep steady pace","highlight":"finished the report",\
            "mood":"WAT"}
            """;
        var resp = new LlmResponse(body, 0, 0, 0L);

        var payload = parser.parse(resp);

        assertThat(payload.mood()).isEqualTo(Mood.NEUTRAL);
    }

    @Test
    void parse_invalidJson_throwsInvalid() {
        var resp = new LlmResponse("not json at all", 0, 0, 0L);

        assertThatThrownBy(() -> parser.parse(resp))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void parse_sensitiveTokenInAdvice_throwsInvalid() {
        String body = """
            {"headline":"today you completed six tasks",\
            "advice":"you should fuck off and rest","highlight":"finished the report",\
            "mood":"neutral"}
            """;
        var resp = new LlmResponse(body, 0, 0, 0L);

        assertThatThrownBy(() -> parser.parse(resp))
            .isInstanceOf(LlmResponseInvalidException.class)
            .hasMessageContaining("advice");
    }

    @Test
    void parse_emptyBody_throwsInvalid() {
        var resp = new LlmResponse("", 0, 0, 0L);

        assertThatThrownBy(() -> parser.parse(resp))
            .isInstanceOf(LlmResponseInvalidException.class);
    }
}
