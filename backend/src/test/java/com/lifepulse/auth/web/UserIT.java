package com.lifepulse.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.it.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R-006 — 种子账号集成测试（v1.2.2 重构：移除 dev profile）。
 *
 * <p>覆盖 docs/issues/2026-07-18-r006-flyway-seed-account.md 全部 AC（修订）：
 * <ol>
 *   <li>{@code seedAccounts_loginWithSeededEmail_returns200} —
 *       {@code demo@lifepulse.test} / {@code alice@lifepulse.test} 用 {@code Demo123!}
 *       可直接登录返回 200</li>
 *   <li>幂等：{@code seedAccounts_idempotent_seedBeforeEachIsNoop} —
 *       {@code @BeforeEach} 走 {@code WHERE NOT EXISTS}，多次运行不产生重复行；
 *       Testcontainers MySQL {@code withReuse=true)} 跨 JVM 共享 DB 反复验证</li>
 * </ol>
 *
 * <p>v1.2.2 之前用 {@code @ActiveProfiles("dev")} 触发 {@code application-dev.yml}
 * 把 {@code classpath:db/seed} 加入 {@code spring.flyway.locations}，让 V5 自动 apply；
 * 但与默认 profile IT 共享同一 MySQL 容器（{@code withReuse(true)}）时，
 * 默认 profile IT 不加载 V5，dev profile IT 又期望 V5 → Flyway 校验失败。
 *
 * <p>v1.2.2 修复（plan v1.2.2 R-006）：dev profile 不再追加 db/seed；
 * 种子账号一律走 {@code @BeforeEach} 幂等 INSERT（BCrypt 重哈希），
 * 既不依赖 V5 迁移、也不依赖 failsafe 执行顺序。
 *
 * <p>设计：{@code @BeforeEach} 走 idempotent INSERT 自愈 ——
 * 即使其他 IT（{@code AuthMappersIT} / {@code UserSettingsIT}）
 * 物理 {@code DELETE FROM t_user} 清表，{@code UserIT} 仍可独立跑通。
 * 这样测试不依赖 failsafe 执行顺序，与项目既有"测试隔离"惯例一致。
 */
@AutoConfigureMockMvc
class UserIT extends AbstractIntegrationTest {

    private static final String DEMO_EMAIL = "demo@lifepulse.test";
    private static final String ALICE_EMAIL = "alice@lifepulse.test";
    private static final String SEED_PASSWORD = "Demo123!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void ensureSeedAccountsExist() {
        // 自愈：BCrypt 重哈希 + WHERE NOT EXISTS 幂等 INSERT。
        // V5 已 applied 的情况下这是 no-op；V5 没跑或被物理清表的极端情况下也能恢复。
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(AuthConstants.BCRYPT_STRENGTH);
        upsertSeed(DEMO_EMAIL, enc.encode(SEED_PASSWORD), "demo");
        upsertSeed(ALICE_EMAIL, enc.encode(SEED_PASSWORD), "alice");
    }

    // ---------- AC: 种子账号可登录 ----------

    @Test
    void seedAccounts_loginWithSeededEmail_returns200() throws Exception {
        // demo@lifepulse.test 用 Demo123! 登录 → 200 + tokens
        loginAndAssertOk(DEMO_EMAIL);
    }

    @Test
    void seedAccounts_loginBothSeededEmails_returns200() throws Exception {
        loginAndAssertOk(DEMO_EMAIL);
        loginAndAssertOk(ALICE_EMAIL);
    }

    // ---------- AC: 幂等 ----------

    @Test
    void seedAccounts_idempotent_seedBeforeEachIsNoop() {
        // 触发第二次 @BeforeEach（通过 @Test 体内手动调一次即可）
        ensureSeedAccountsExist();

        Integer demoCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE email = ?",
                Integer.class, DEMO_EMAIL);
        Integer aliceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE email = ?",
                Integer.class, ALICE_EMAIL);

        assertThat(demoCount)
                .as("demo 幂等：@BeforeEach 多次跑只 1 行")
                .isEqualTo(1);
        assertThat(aliceCount)
                .as("alice 幂等：@BeforeEach 多次跑只 1 行")
                .isEqualTo(1);
    }

    // ---------- helpers ----------

    private void upsertSeed(String email, String passwordHash, String nickname) {
        jdbc.update(
                "INSERT INTO t_user (email, password_hash, nickname, created_at, updated_at, deleted) "
                + "SELECT ?, ?, ?, NOW(3), NOW(3), 0 "
                + "WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE email = ?)",
                email, passwordHash, nickname, email);
    }

    private void loginAndAssertOk(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(
                                new LoginRequest(email, SEED_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
    }
}