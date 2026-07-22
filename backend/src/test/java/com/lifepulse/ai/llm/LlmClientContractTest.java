package com.lifepulse.ai.llm;

import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import com.lifepulse.ai.llm.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientContractTest {

    @Test
    void request_blankSystemPrompt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new LlmRequest("", "user", 100, Duration.ofSeconds(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void request_zeroMaxTokens_throwsIllegalArgument() {
        assertThatThrownBy(() -> new LlmRequest("sys", "user", 0, Duration.ofSeconds(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void response_empty_returnsZeroedValues() {
        assertThat(LlmResponse.empty().content()).isEmpty();
        assertThat(LlmResponse.empty().latencyMs()).isZero();
    }
}
