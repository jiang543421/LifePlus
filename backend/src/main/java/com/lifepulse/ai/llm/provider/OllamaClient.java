package com.lifepulse.ai.llm.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.llm.LlmClient;
import com.lifepulse.ai.llm.LlmProperties;
import com.lifepulse.ai.llm.LlmRequest;
import com.lifepulse.ai.llm.LlmResponse;
import com.lifepulse.ai.llm.exception.LlmResponseInvalidException;
import com.lifepulse.ai.llm.exception.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Ollama local HTTP client（spec §4.3 / CLAUDE.md §11）。
 *
 * <p>仅在 {@code lp.ai.llm.provider=ollama} 时装配。通过 Spring 内置
 * {@link RestClient} 调用 Ollama 原生协议 {@code POST {baseUrl}/api/chat}
 * （**非** OpenAI 兼容协议——路径与字段都不同）；本地 HTTP 无 apiKey。
 * 不引入任何新第三方依赖（CLAUDE.md §11.1 #3）。
 *
 * <p>错误映射（对齐 {@link LlmClient} 契约 + DeepSeekClient 行为一致）：
 * <ul>
 *   <li>HTTP 429 / 5xx / 网络超时 / 连接失败 → {@link LlmUnavailableException}（code=1513）</li>
 *   <li>HTTP 4xx（非 429）/ 响应体非 JSON / 缺字段 / 空体 → {@link LlmResponseInvalidException}（code=1512）</li>
 * </ul>
 * 本类不做重试；重试 / 熔断 / 配额由上层负责（CLAUDE.md §11.3 / §11.5）。
 * 日志仅记录 provider + endpoint host；Ollama 无 apiKey 但同样禁打完整 prompt（CLAUDE.md §7.1）。
 *
 * <p>关键差异 vs DeepSeekClient：
 * <ul>
 *   <li>路径 {@code /api/chat}（DeepSeek 为 {@code /v1/chat/completions}）</li>
 *   <li>响应字段 snake_case：{@code message.content} / {@code prompt_eval_count} / {@code eval_count}</li>
 *   <li>无 {@code Authorization} header</li>
 *   <li>{@code num_predict} 通过 {@code options} 嵌套传入</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "lp.ai.llm", name = "provider", havingValue = "ollama")
public class OllamaClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private static final String CHAT_PATH = "/api/chat";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    public OllamaClient(RestClient.Builder builder, LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = builder
            .baseUrl(props.baseUrl())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        long start = System.currentTimeMillis();
        String requestBody = buildRequestBody(request);
        try {
            String responseBody = restClient.post()
                .uri(CHAT_PATH)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    int code = res.getStatusCode().value();
                    if (code == HTTP_TOO_MANY_REQUESTS) {
                        throw new LlmUnavailableException("ollama rate limited: 429");
                    }
                    throw new LlmResponseInvalidException("ollama client error: " + code);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) ->
                    throwUnavailable("ollama server error: " + res.getStatusCode().value()))
                .body(String.class);

            return parse(responseBody, System.currentTimeMillis() - start);
        } catch (LlmUnavailableException | LlmResponseInvalidException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("ollama call failed (network): host={}", endpointHost(), ex);
            throw new LlmUnavailableException("ollama network failure", ex);
        } catch (RestClientException ex) {
            log.warn("ollama call failed (rest): host={}", endpointHost(), ex);
            throw new LlmUnavailableException("ollama rest failure", ex);
        }
    }

    private void throwUnavailable(String message) {
        throw new LlmUnavailableException(message);
    }

    private String buildRequestBody(LlmRequest request) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", props.model());
            root.put("stream", false);
            var options = root.putObject("options");
            options.put("num_predict", request.maxResponseTokens());
            var messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.systemPrompt());
            messages.addObject().put("role", "user").put("content", request.userPrompt());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new LlmUnavailableException("ollama request build failed", ex);
        }
    }

    private LlmResponse parse(String body, long latencyMs) {
        if (body == null || body.isBlank()) {
            throw new LlmResponseInvalidException("ollama empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode contentNode = root.path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new LlmResponseInvalidException("ollama missing message.content");
            }
            String content = contentNode.asText();
            // Ollama snake_case: prompt_eval_count / eval_count
            int promptTokens = root.path("prompt_eval_count").asInt(0);
            int responseTokens = root.path("eval_count").asInt(0);
            return new LlmResponse(content, promptTokens, responseTokens, latencyMs);
        } catch (JsonProcessingException ex) {
            throw new LlmResponseInvalidException("ollama json parse failed", ex);
        }
    }

    private String endpointHost() {
        String baseUrl = props.baseUrl();
        try {
            return java.net.URI.create(baseUrl).getHost();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }
}