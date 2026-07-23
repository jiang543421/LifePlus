package com.lifepulse.ai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.AiConstants;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.it.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 趋势图端点集成测试（spec §v2.2 trend / CLAUDE.md §11）。
 *
 * <p>6 case：
 * <ol>
 *   <li>{@code default window=14} → 200 + 4 槽 series + window/from/to + generatedAt</li>
 *   <li>{@code window=7 / 30 边界值} → 200（参数化）</li>
 *   <li>{@code window=11 非法} → 400/422 + 1001 VALIDATION</li>
 *   <li>缓存命中：第二次 GET 同 userId+window → Redis key 命中且 200</li>
 *   <li>限流：第 31 次 GET（同 userId+window）→ 1006 LOGIN_RATE_LIMIT</li>
 *   <li>跨用户隔离：Bob 注册后 GET /trend → 不会复用 Alice 的缓存（cacheKey + rlKey 独立）</li>
 *   <li>{@code 401}：无 token → 1002 BAD_CREDENTIALS</li>
 * </ol>
 *
 * <p>隔离策略：
 * email/IP 用 {@code UUID} 拼接；每个 case 在 {@code @AfterEach} 集中删除其注册 / 限流键；
 * 缓存键按 userId 隔离，{@code @AfterEach} 统一清掉。
 */
@AutoConfigureMockMvc
class AiTrendControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    private final List<String> trackedRedisKeys = new ArrayList<>();

    @AfterEach
    void cleanupRedis() {
        if (!trackedRedisKeys.isEmpty()) {
            redis.delete(trackedRedisKeys);
            trackedRedisKeys.clear();
        }
    }

    // ---------- case 1: 默认 window=14 → 200 + 4 槽 ----------

    @Test
    void trend_defaultWindow14_returns200With4Slots() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackTrendKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/ai/insight/trend")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            // window 默认 14
            .andExpect(jsonPath("$.data.window").value(AiConstants.TREND_DEFAULT_WINDOW))
            // from/to 存在
            .andExpect(jsonPath("$.data.from").exists())
            .andExpect(jsonPath("$.data.to").exists())
            // 4 槽
            .andExpect(jsonPath("$.data.metrics.length()").value(4))
            .andExpect(jsonPath("$.data.series.task.key").value("task"))
            .andExpect(jsonPath("$.data.series.plan.key").value("plan"))
            .andExpect(jsonPath("$.data.series.expense.key").value("expense"))
            .andExpect(jsonPath("$.data.series.diet.key").value("diet"))
            // diet 永久占位：points 空
            .andExpect(jsonPath("$.data.series.diet.points.length()").value(0))
            // generatedAt 存在
            .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    // ---------- case 2: window=7 / 30 边界值 ----------

    @Test
    void trend_windowBoundary7_returns200() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackTrendKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/ai/insight/trend")
                        .param("window", "7")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.window").value(7));
    }

    @Test
    void trend_windowBoundary30_returns200() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackTrendKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/ai/insight/trend")
                        .param("window", "30")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.window").value(30));
    }

    // ---------- case 3: 非法 window → 1001 ----------

    @Test
    void trend_window11_returns1001Validation() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackTrendKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/ai/insight/trend")
                        .param("window", "11")
                        .header("Authorization", "Bearer " + accessToken))
            // 验证异常由 @RestControllerAdvice 映射成 400
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    // ---------- case 4: 缓存命中 ----------

    @Test
    void trend_cacheHit_secondCallReturnsCachedPayload() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackTrendKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 第一次 GET → 写缓存
        MvcResult first = mvc.perform(get("/api/v1/ai/insight/trend")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
        String firstGeneratedAt = readTree(first).path("data").path("generatedAt").asText();

        // 验证缓存键已落
        String cacheKey = AiConstants.TREND_CACHE_KEY_PREFIX + userId + ":" + AiConstants.TREND_DEFAULT_WINDOW;
        assertThat(redis.hasKey(cacheKey)).isTrue();

        // 第二次 GET → cache hit；generatedAt 不变（同 payload）
        MvcResult second = mvc.perform(get("/api/v1/ai/insight/trend")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
        String secondGeneratedAt = readTree(second).path("data").path("generatedAt").asText();

        assertThat(secondGeneratedAt).isEqualTo(firstGeneratedAt);
    }

    // ---------- case 5: 限流：第 31 次 → 1006 ----------

    @Test
    void trend_rateLimit_31stCallReturns1006() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackTrendKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 前 30 次：window=14（用户同窗口连续打 30 次 → 第 31 次 1006）
        for (int i = 0; i < AiConstants.TREND_RL_MAX; i++) {
            mvc.perform(get("/api/v1/ai/insight/trend")
                            .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        }

        // 第 31 次：超限 → 1006
        mvc.perform(get("/api/v1/ai/insight/trend")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT));
    }

    // ---------- case 6: 跨用户隔离 ----------

    @Test
    void trend_crossUserIsolation_aliceCacheDoesNotLeakToBob() throws Exception {
        // Alice
        String aliceEmail = uniqueEmail();
        String aliceIp = uniqueIp();
        trackRegisterAndLoginKeys(aliceEmail, aliceIp);
        long aliceId = registerAndLogin(aliceEmail, "Valid1Pass", aliceIp);
        trackTrendKeys(aliceId);
        String aliceToken = accessTokenFor(aliceEmail, "Valid1Pass", aliceIp);

        // Alice 拉一次 → 缓存 + 限流键落
        mvc.perform(get("/api/v1/ai/insight/trend")
                        .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());

        // Bob
        String bobEmail = uniqueEmail();
        String bobIp = uniqueIp();
        trackRegisterAndLoginKeys(bobEmail, bobIp);
        long bobId = registerAndLogin(bobEmail, "Valid1Pass", bobIp);
        trackTrendKeys(bobId);
        String bobToken = accessTokenFor(bobEmail, "Valid1Pass", bobIp);

        // Bob 拉一次 → 应当独立写自己缓存键，不命中 Alice 缓存
        mvc.perform(get("/api/v1/ai/insight/trend")
                        .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk());

        // 验证：双方 cacheKey 独立存在 + 各自 rlKey 计数独立
        String aliceCacheKey = AiConstants.TREND_CACHE_KEY_PREFIX + aliceId + ":" + AiConstants.TREND_DEFAULT_WINDOW;
        String bobCacheKey = AiConstants.TREND_CACHE_KEY_PREFIX + bobId + ":" + AiConstants.TREND_DEFAULT_WINDOW;
        String aliceRlKey = AiConstants.TREND_RL_KEY_PREFIX + aliceId;
        String bobRlKey = AiConstants.TREND_RL_KEY_PREFIX + bobId;

        assertThat(aliceId).isNotEqualTo(bobId);
        assertThat(aliceCacheKey).isNotEqualTo(bobCacheKey);
        assertThat(redis.hasKey(aliceCacheKey)).isTrue();
        assertThat(redis.hasKey(bobCacheKey)).isTrue();

        // 限流计数：Alice 1 次 / Bob 1 次（互不影响）
        assertThat(redis.opsForValue().get(aliceRlKey)).isEqualTo("1");
        assertThat(redis.opsForValue().get(bobRlKey)).isEqualTo("1");
    }

    // ---------- case 7: 401 无 token ----------

    @Test
    void trend_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/ai/insight/trend"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));
    }

    // ===== helpers =====

    private long registerAndLogin(String email, String password, String ip) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        MvcResult reg = mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readTree(reg).path("data").path("userId").asLong();
    }

    private String accessTokenFor(String email, String password, String ip) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return readTree(login).path("data").path("accessToken").asText();
    }

    /**
     * 注册 trend 模块相关 Redis key（cache + 限流），保证测试间隔离。
     * Cache 按 userId + window 三档分别落键；RL 按 userId 单键。
     */
    private void trackTrendKeys(long userId) {
        for (int w : Set.of(7, 14, 30)) {
            trackedRedisKeys.add(AiConstants.TREND_CACHE_KEY_PREFIX + userId + ":" + w);
        }
        trackedRedisKeys.add(AiConstants.TREND_RL_KEY_PREFIX + userId);
    }

    private void trackRegisterAndLoginKeys(String email, String ip) {
        trackedRedisKeys.add(AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
        trackedRedisKeys.add(AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + emailKeySuffix(email));
    }

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    private static String uniqueIp() {
        long n = System.nanoTime();
        int b1 = (int) ((n >>> 8) & 0xFF) | 0x10;
        int b2 = (int) (n & 0xFF);
        return "172." + b1 + "." + b2 + ".1";
    }

    private static String emailKeySuffix(String email) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}