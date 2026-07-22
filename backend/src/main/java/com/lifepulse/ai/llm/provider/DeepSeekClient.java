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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * DeepSeek LLM 客户端（OpenAI 兼容协议，spec §4.3 / CLAUDE.md §11）。
 *
 * <p>仅在 {@code lp.ai.llm.provider=deepseek} 时装配。通过 Spring 内置
 * {@link RestClient} 调用 {@code POST {baseUrl}/v1/chat/completions}，
 * 用 Bearer token 鉴权；不引入任何新第三方依赖（CLAUDE.md §11.1 #3）。
 *
 * <p>错误映射（对齐 {@link LlmClient} 契约）：
 * <ul>
 *   <li>HTTP 429 / 5xx / 网络超时 / 连接失败 → {@link LlmUnavailableException}（code=1513）</li>
 *   <li>HTTP 4xx（非 429）/ 响应体非 JSON / 缺字段 / 空体 → {@link LlmResponseInvalidException}（code=1512）</li>
 * </ul>
 * 本类不做重试；重试 / 熔断 / 配额由上层负责（CLAUDE.md §11.3 / §11.5）。
 * 日志仅记录 provider + endpoint host，禁止打印 apiKey / 完整 prompt（CLAUDE.md §7.1）。
 */
@Component
@ConditionalOnProperty(prefix = "lp.ai.llm", name = "provider", havingValue = "deepseek")
public class DeepSeekClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(RestClient.Builder builder, LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = builder
            .baseUrl(props.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        long start = System.currentTimeMillis();
        String requestBody = buildRequestBody(request);
        try {
            String responseBody = restClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    int code = res.getStatusCode().value();
                    if (code == HTTP_TOO_MANY_REQUESTS) {
                        throw new LlmUnavailableException("deepseek rate limited: 429");
                    }
                    throw new LlmResponseInvalidException("deepseek client error: " + code);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) ->
                    throwUnavailable("deepseek server error: " + res.getStatusCode().value()))
                .body(String.class);

            return parse(responseBody, System.currentTimeMillis() - start);
        } catch (LlmUnavailableException | LlmResponseInvalidException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            // 网络超时 / 连接拒绝 / IO 错误
            log.warn("deepseek call failed (network): host={}", endpointHost(), ex);
            throw new LlmUnavailableException("deepseek network failure", ex);
        } catch (RestClientException ex) {
            log.warn("deepseek call failed (rest): host={}", endpointHost(), ex);
            throw new LlmUnavailableException("deepseek rest failure", ex);
        }
    }

    private void throwUnavailable(String message) {
        throw new LlmUnavailableException(message);
    }

    private String buildRequestBody(LlmRequest request) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", props.model());
            root.put("max_tokens", request.maxResponseTokens());
            root.put("stream", false);
            var messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", request.systemPrompt());
            messages.addObject().put("role", "user").put("content", request.userPrompt());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            // 请求体构造失败属本地问题，视为不可用而非响应无效
            throw new LlmUnavailableException("deepseek request build failed", ex);
        }
    }

    private LlmResponse parse(String body, long latencyMs) {
        if (body == null || body.isBlank()) {
            throw new LlmResponseInvalidException("deepseek empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull() || !contentNode.isTextual()) {
                throw new LlmResponseInvalidException("deepseek missing choices[0].message.content");
            }
            String content = contentNode.asText();
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int responseTokens = root.path("usage").path("completion_tokens").asInt(0);
            return new LlmResponse(content, promptTokens, responseTokens, latencyMs);
        } catch (JsonProcessingException ex) {
            throw new LlmResponseInvalidException("deepseek json parse failed", ex);
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
