package com.lifepulse.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.LogoutRequest;
import com.lifepulse.auth.dto.RefreshRequest;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.it.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1.3-C · 认证全链路 Web 端到端集成测试（plan §3-C / §4）。
 *
 * <p>5 case：
 * <ol>
 *   <li>{@code register → login → /users/me → refresh → logout} happyPath</li>
 *   <li>refresh 重放：旧 refresh 再用 → 1401</li>
 *   <li>login 限流：同 IP+email 第 6 次 → 1006</li>
 *   <li>register 限流：同 IP 第 4 次 → 1006（{@code REGISTER_RL_MAX=3}）</li>
 *   <li>跨用户防御：迁移至 {@code TaskFlowIT.crossUserDefense_...}（Phase 2-D）</li>
 * </ol>
 *
 * <p>基础设施（继承 {@link AbstractIntegrationTest}）：
 * <ul>
 *   <li>MySQL：Testcontainers（{@code withReuse(true)}）</li>
 *   <li>Redis：本地 {@code localhost:6379/123456}</li>
 *   <li>完整 Spring Security 过滤器链：{@code JwtAuthFilter} 解析 Bearer、SecurityConfig 鉴权</li>
 * </ul>
 *
 * <p>用例隔离：email 用 {@code UUID} 拼接唯一；IP 用 {@code X-Forwarded-For} 头注入
 * 唯一；{@code @AfterEach} 清本用例产生的限流 Redis key，避免跨用例计数残留。
 * DB 行不删（UUID email 永不冲突，下次跑可观测复用 TC MySQL）。
 */
@AutoConfigureMockMvc
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;

    /** 本测试类产生的限流 key，{@code @AfterEach} 一次性清。 */
    private final List<String> trackedRateLimitKeys = new ArrayList<>();

    @AfterEach
    void cleanupRateLimitKeys() {
        if (!trackedRateLimitKeys.isEmpty()) {
            redis.delete(trackedRateLimitKeys);
            trackedRateLimitKeys.clear();
        }
    }

    // ---------- case 1: happy path ----------

    @Test
    void happyPath_register_login_me_refresh_logout() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);

        // 1. register → 201 + data.userId
        MvcResult regResult = mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RegisterRequest(email, "Valid1Pass", "alice"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").exists())
                .andReturn();
        long userId = readTree(regResult).path("data").path("userId").asLong();

        // 2. login → 200 + accessToken/refreshToken
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, "Valid1Pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();
        String accessToken = readTree(loginResult).path("data").path("accessToken").asText();
        String refreshToken1 = readTree(loginResult).path("data").path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken1).isNotBlank();

        // 3. /users/me → 200 + id/email/nickname 与注册一致
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("alice"));

        // 4. refresh → 旋转，旧 refreshToken1 已 revoked，新 refreshToken2 不同
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RefreshRequest(refreshToken1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();
        String refreshToken2 = readTree(refreshResult).path("data").path("refreshToken").asText();
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        // 5. logout(refreshToken2) → 200
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LogoutRequest(refreshToken2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 6. 已 revoke 的 refreshToken2 再用 → 1401/401（端到端验证 revoke 生效）
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RefreshRequest(refreshToken2))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_INVALID));
    }

    // ---------- case 2: refresh 重放 → 1401 ----------

    @Test
    void refresh_replayAfterRotate_returns1401() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);

        // register + login
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RegisterRequest(email, "Valid1Pass", null))))
                .andExpect(status().isCreated());
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, "Valid1Pass"))))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken1 = readTree(loginResult).path("data").path("refreshToken").asText();

        // 第一次 refresh：成功，refreshToken1 旋转 → refreshToken2，refreshToken1 被 revoke
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RefreshRequest(refreshToken1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 第二次用旧 refreshToken1 重放：被 revoke → 1401
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RefreshRequest(refreshToken1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_INVALID))
                .andExpect(jsonPath("$.message").exists());
    }

    // ---------- case 3: login 限流 → 第 6 次 1006 ----------

    @Test
    void login_rateLimit_sixthAttempt_returns1006() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);

        // 先注册一个合法用户
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RegisterRequest(email, "Valid1Pass", null))))
                .andExpect(status().isCreated());

        // 前 5 次错误密码 → 1002/401（限流计数 1..5，未达 LOGIN_RL_MAX=5）
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody(new LoginRequest(email, "Wrong1Pass"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));
        }

        // 第 6 次：限流计数 = 6 > 5 → 1006/429
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, "Wrong1Pass"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT))
                .andExpect(jsonPath("$.message").value("login rate limit exceeded"));
    }

    // ---------- case 4: register 限流 → 第 4 次 1006（REGISTER_RL_MAX=3） ----------

    @Test
    void register_rateLimit_fourthAttempt_returns1006() throws Exception {
        String ip = uniqueIp();
        // 只 track register 限流 key；login 限流 key 在本测试不产生
        trackedRateLimitKeys.add(AuthConstants.REGISTER_RL_KEY_PREFIX + ip);

        // 前 3 次 register：成功（UUID email 永不冲突）
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/v1/auth/register")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody(new RegisterRequest(uniqueEmail(), "Valid1Pass", null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(0));
        }

        // 第 4 次：限流计数 = 4 > 3 → 1006/429
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RegisterRequest(uniqueEmail(), "Valid1Pass", null))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT))
                .andExpect(jsonPath("$.message").value("register rate limit exceeded"));
    }

    // ---------- case 5: 跨用户防御（Phase 2-D 由 TaskFlowIT 覆盖） ----------

    /**
     * 跨用户越权防御的实际测试已迁至 {@code TaskFlowIT.crossUserDefense_...}，
     * 本类保留位置作为占位（Phase 1 stub 已删除；后续如需在 auth 层加 token 越权场景
     * 再补独立 case）。
     */

    // ---------- helpers ----------

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    /** 唯一 IPv4，X-Forwarded-For 注入以隔离 register 限流计数。 */
    private static String uniqueIp() {
        long n = System.nanoTime();
        int b1 = (int) ((n >>> 8) & 0xFF) | 0x10;   // 16..255，避免与 127/10/192 段冲突
        int b2 = (int) (n & 0xFF);
        return "172." + b1 + "." + b2 + ".1";
    }

    /**
     * 注册 + 登录两条路径产出的限流 key 一次性记入清理列表。
     * {@code email-suffix} 计算与 {@code AuthService.emailKeySuffix} 同逻辑
     * （SHA-256 → hex 前 8 位），这里是公开包无法直接调包私有方法的折中。
     */
    private void trackRegisterAndLoginKeys(String email, String ip) {
        trackedRateLimitKeys.add(AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
        trackedRateLimitKeys.add(AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + emailKeySuffix(email));
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

    private byte[] jsonBody(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
