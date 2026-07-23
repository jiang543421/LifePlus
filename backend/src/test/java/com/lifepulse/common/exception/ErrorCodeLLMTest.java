package com.lifepulse.common.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeLLMTest {

    @Test
    void llmErrorCodes_areInRange1510to1513() {
        assertThat(ErrorCode.LLM_QUOTA_EXCEEDED).isEqualTo(1510);
        assertThat(ErrorCode.LLM_CIRCUIT_OPEN).isEqualTo(1511);
        assertThat(ErrorCode.LLM_RESPONSE_INVALID).isEqualTo(1512);
        assertThat(ErrorCode.LLM_UNAVAILABLE).isEqualTo(1513);
    }
}
