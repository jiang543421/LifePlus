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
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaClientTest {

    private static final String OLLAMA_URL = "http://localhost:11434";

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        // Ollama 模式：apiKey 为空，circuit-breaker 自动 false（本地进程语义）
        LlmProperties props = new LlmProperties(
            true, "ollama", OLLAMA_URL, "", "deepseek-r1:8b", 5000, 1500, 300, 50,
            new LlmProperties.CircuitBreaker(false, 10, 5, 30));
        client = new OllamaClient(builder, props, new ObjectMapper());
    }

    private LlmRequest req() {
        return new LlmRequest("sys", "user", 100, Duration.ofSeconds(5));
    }

    @Test
    void generate_validRequest_returnsParsedResponse() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {"message":{"role":"assistant","content":"hi"},"prompt_eval_count":3,"eval_count":2}
                """, MediaType.APPLICATION_JSON));

        var resp = client.generate(req());
        assertThat(resp.content()).isEqualTo("hi");
        assertThat(resp.promptTokens()).isEqualTo(3);
        assertThat(resp.responseTokens()).isEqualTo(2);
        mockServer.verify();
    }

    @Test
    void generate_http500_throwsLlmUnavailable() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_http429_throwsLlmUnavailable() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withStatus(TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_http401_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void generate_connectionRefused_throwsLlmUnavailable() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withException(new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void generate_invalidJson_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void generate_missingContent_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {"message":{"role":"assistant"},"prompt_eval_count":1,"eval_count":1}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }

    @Test
    void generate_emptyBody_throwsLlmResponseInvalid() {
        mockServer.expect(requestTo(OLLAMA_URL + "/api/chat"))
            .andExpect(method(POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(req()))
            .isInstanceOf(LlmResponseInvalidException.class);
    }
}