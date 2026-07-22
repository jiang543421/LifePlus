package com.lifepulse.ai.llm;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class LlmPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void enabledDeepseek_emptyApiKey_throwsIllegalState() {
        assertThatThrownBy(() -> new LlmProperties(
                true, "deepseek", "https://api.deepseek.com/v1", "", "deepseek-chat",
                5000, 1500, 300, 50,
                new LlmProperties.CircuitBreaker(true, 10, 5, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LP_LLM_API_KEY");
    }

    @Test
    void enabledDeepseek_placeholderApiKey_throwsIllegalState() {
        assertThatThrownBy(() -> new LlmProperties(
                true, "deepseek", "https://api.deepseek.com/v1", "sk-replace-with-real-key", "deepseek-chat",
                5000, 1500, 300, 50,
                new LlmProperties.CircuitBreaker(true, 10, 5, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void enabledOllama_emptyApiKey_ok() {
        assertThatCode(() -> new LlmProperties(
                true, "ollama", "http://localhost:11434", "", "deepseek-r1:8b",
                5000, 1500, 300, 50,
                new LlmProperties.CircuitBreaker(false, 10, 5, 30)))
                .doesNotThrowAnyException();
    }
}
