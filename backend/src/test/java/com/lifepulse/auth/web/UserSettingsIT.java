package com.lifepulse.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.ChangePasswordRequest;
import com.lifepulse.auth.dto.DeleteAccountRequest;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.RefreshRequest;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.auth.dto.UpdateNicknameRequest;
import com.lifepulse.it.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController Settings v1.1 集成测试（issue 2026-07-18-settings-v1-1）。
 *
 * <p>Testcontainers MySQL + 本地 Redis；用例隔离：
 * <ul>
 *   <li>UUID email 永不冲突；UNIQUE 索引（含 deleted）由 {@code @BeforeEach} 物理 DELETE 释放</li>
 *   <li>{@code @AfterEach} 清 register / login 限流 Redis key</li>
 *   <li>{@code X-Forwarded-For} 注入唯一 IP 隔离限流计数</li>
 * </ul>
 *
 * <p>覆盖 7 个 case：
 * <ol>
 *   <li>改昵称 happy → GET /me 反映新昵称</li>
 *   <li>清空昵称 → null 入库 + GET /me 不带 nickname</li>
 *   <li>改密码 happy → 旧 refresh 再用 → 1401</li>
 *   <li>改密码旧密码错 → 1002</li>
 *   <li>注销 happy → 软删 + 所有 refresh revoked + GET /me → 1001</li>
 *   <li>注销幂等 → 2nd DELETE 仍 200</li>
 *   <li>注销密码错 → 1002 + 用户未被删</li>
 *   <li>跨用户隔离 → Alice 改自己 nickname，Bob 仍看到自己的旧 nickname</li>
 * </ol>
 */
@AutoConfigureMockMvc
class UserSettingsIT extends AbstractIntegrationTest {

    private static final String PW = "Valid1Pass";  // ≥8 chars + letter + digit

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> trackedRateLimitKeys = new ArrayList<>();

    @BeforeEach
    void wipeTables() {
        // 物理 DELETE（@TableLogic 软删无法释放 UNIQUE 索引槽位）
        jdbc.update("DELETE FROM t_refresh_token");
        jdbc.update("DELETE FROM t_user");
    }

    @AfterEach
    void cleanupRateLimitKeys() {
        if (!trackedRateLimitKeys.isEmpty()) {
            redis.delete(trackedRateLimitKeys);
            trackedRateLimitKeys.clear();
        }
    }

    // ---------- case 1: 改昵称 happy ----------

    @Test
    void updateNickname_happy_persists_andVisibleOnMe() throws Exception {
        String email = uniqueEmail();
        String access = registerLoginGetAccess(email, "alice", null);

        mvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new UpdateNicknameRequest("小爱"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("小爱"));

        // GET /me 同步反映
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小爱"));
    }

    // ---------- case 2: 清空昵称 → null ----------

    @Test
    void updateNickname_blank_normalizedToNull() throws Exception {
        String email = uniqueEmail();
        String access = registerLoginGetAccess(email, "OriginalName", null);

        mvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new UpdateNicknameRequest("   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").doesNotExist());

        // GET /me nickname 字段不出现（Jackson 序列化 null 时 omitted by 配置）
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").doesNotExist());
    }

    // ---------- case 3: 改密码 happy + refresh 撤销 ----------

    @Test
    void changePassword_happy_revokesAllRefreshTokens() throws Exception {
        String email = uniqueEmail();
        String[] tokens = registerLoginGetTokens(email, null, null);
        String access = tokens[0];
        String refresh = tokens[1];

        mvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new ChangePasswordRequest(PW, "New1Pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 旧 refresh 再用 → 1401（撤销生效）
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RefreshRequest(refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_REFRESH_INVALID));

        // 新密码可以登录
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, "New1Pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- case 4: 改密码旧密码错 ----------

    @Test
    void changePassword_wrongOld_returns1002() throws Exception {
        String email = uniqueEmail();
        String access = registerLoginGetAccess(email, null, null);

        mvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new ChangePasswordRequest("Wrong12345", "New1Pass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));

        // 原密码仍可登录（没改成功）
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, PW))))
                .andExpect(status().isOk());
    }

    // ---------- case 5: 注销 happy + soft delete + refresh revoke ----------

    @Test
    void deleteAccount_happy_softDeletesAndRevokesRefresh() throws Exception {
        String email = uniqueEmail();
        String[] tokens = registerLoginGetTokens(email, null, null);
        String access = tokens[0];
        String refresh = tokens[1];

        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new DeleteAccountRequest(PW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 旧 refresh 撤销 → 1401
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RefreshRequest(refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_REFRESH_INVALID));

        // 当前 access token 仍可存活至过期（≤1h）：GET /me 在 token 仍 valid 的窗口内
        // 应返回 1001/404（user 已被 @TableLogic 软删，selectById 返回 null）。
        // 这里仅断言该窗口内的失败响应。
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_NOT_FOUND));
    }

    // ---------- case 6: 注销幂等 ----------

    @Test
    void deleteAccount_alreadyDeleted_isIdempotent() throws Exception {
        String email = uniqueEmail();
        String access = registerLoginGetAccess(email, null, null);

        // 第一次：200
        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new DeleteAccountRequest(PW))))
                .andExpect(status().isOk());

        // 第二次：仍然 200（幂等 no-op）
        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new DeleteAccountRequest(PW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- case 7: 注销密码错 ----------

    @Test
    void deleteAccount_wrongPassword_returns1002_andUserNotDeleted() throws Exception {
        String email = uniqueEmail();
        String access = registerLoginGetAccess(email, null, null);

        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new DeleteAccountRequest("Wrong12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));

        // 用户未删 → GET /me 仍 200
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    // ---------- case 8: 跨用户隔离 ----------

    @Test
    void crossUserIsolation_aliceChangeDoesNotAffectBob() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String accessA = registerLoginGetAccess(emailA, null, null);
        String accessB = registerLoginGetAccess(emailB, null, null);

        // Alice 改自己的 nickname
        mvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new UpdateNicknameRequest("AliceName"))))
                .andExpect(status().isOk());

        // Bob 仍看到自己（默认 null nickname）
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(emailB))
                .andExpect(jsonPath("$.data.nickname").doesNotExist());

        // Alice 看到自己新 nickname
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("AliceName"));
    }

    // ---------- helpers ----------

    /** 注册 → 登录 → 拿 access token（不计 refresh）。 */
    private String registerLoginGetAccess(String email, String nickname, String ipHint) throws Exception {
        return registerLoginGetTokens(email, nickname, ipHint)[0];
    }

    /** 注册 → 登录 → 拿 [access, refresh]。 */
    private String[] registerLoginGetTokens(String email, String nickname, String ipHint) throws Exception {
        String ip = ipHint != null ? ipHint : uniqueIp();
        trackRegisterAndLoginKeys(email, ip);

        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RegisterRequest(email, PW, nickname))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, PW))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = readTree(loginResult).path("data");
        return new String[]{data.path("accessToken").asText(), data.path("refreshToken").asText()};
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
        return om.writeValueAsBytes(value);
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    @SuppressWarnings("unused")  // 静态断言防 unused 警告
    private static void assertNotNull(Object o) {
        assertThat(o).isNotNull();
    }
}