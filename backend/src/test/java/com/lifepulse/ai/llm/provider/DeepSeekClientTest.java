package com.lifepulse.ai.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.llm.LlmProperties;
import com.lifepulse.ai.llm.LlmRequest;
import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import com.lifepulse.ai.llm.exception.LlmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekClientTest {

    private static final String ENDPOINT = "https://api.deepseek.com/v1/chat/completions";
    private static final String API_KEY = "sk-test-mock-key-xxxxxxxxxxxxxxxxxxx";

    private MockRestServiceServer mockServer;
    private DeepSeekClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        LlmProperties props = new LlmProperties(
            true, "deepseek", "https://api.deepseek.com", API_KEY,
            "deepseek-chat", 5000, 1500, 300, 50,
            new LlmProperties.CircuitBreaker(true, 10, 5, 30));
        client = new DeepSeekClient(builder, props, new ObjectMapper());
    }

    private LlmRequest req() {
        return new LlmRequest("sys", "user", 100, Duration.ofSeconds(5));
    }

    @Test
    void generate_validRequest_returnsParsedResponse() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer " + API_KEY))
            .andRespond(withSuccess("""
                {"choices":[{"message":{"content":"hello"}}],"usage":{"prompt_tokens":10,"completion_tokens":5}}
                """, MediaType.APPLICATION_JSON));

        var resp = client.generate(req());

        assertThat(resp.content()).isEqualTo("hello");
        assertThat(resp.promptTokens()).isEqualTo(10);
        assertThat(resp.responseTokens()).isEqualTo(5);
        assertThat(resp.latencyMs()).isGreaterThanOrEqualTo(0L);
        mockServer.verify();
    }

    @Test
    void generate_http500_throwsLlmUnavailable() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_http429_throwsLlmUnavailable() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withStatus(TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_http401_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void generate_http400_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withStatus(BAD_REQUEST));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void generate_connectionFailure_throwsLlmUnavailable() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withException(new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_emptyBody_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void generate_missingContentField_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(ENDPOINT))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {"choices":[{"message":{}}],"usage":{"prompt_tokens":1,"completion_tokens":2}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }
}
