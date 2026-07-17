package com.lifepulse.plan.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.RegisterRequest;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3-C · Plan 模块 Web 端到端集成测试（spec §6.4 Testcontainers）。
 *
 * <p>6 case：
 * <ol>
 *   <li>{@code register → login → POST /plans → GET /plans?from&to} 创建并出现在日历</li>
 *   <li>{@code PUT /plans/{id}} 部分字段更新持久化</li>
 *   <li>{@code POST /plans} with {@code allDay=1} → DB 中 start=00:00:00 end=23:59:59（CLAUDE.md Phase 3 决策）</li>
 *   <li>{@code DELETE /plans/{id}} 软删后再 GET /plans 不包含</li>
 *   <li>跨用户越权：userB 读 userA 的 plan → 403 + 1003</li>
 *   <li>{@code GET /plans?from&to} 范围过滤仅返回范围内事件</li>
 * </ol>
 *
 * <p>基础设施与隔离策略与 {@code TaskFlowIT} 一致：
 * email / IP 用 {@code UUID} 拼接唯一；{@code @AfterEach} 清限流 Redis key。
 * 用 {@link JdbcTemplate} 物理清 t_plan，避免容器复用时数据残留。
 */
@AutoConfigureMockMvc
class PlanFlowIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> trackedRateLimitKeys = new ArrayList<>();

    @BeforeEach
    void cleanPlanTable() {
        // t_plan 索引 idx_user_start 不含 deleted 列，
        // 单纯逻辑删除会留下历史行影响后续断言计数；改物理 DELETE 清空。
        jdbc.update("DELETE FROM t_plan");
    }

    @AfterEach
    void cleanupRateLimitKeys() {
        if (!trackedRateLimitKeys.isEmpty()) {
            redis.delete(trackedRateLimitKeys);
            trackedRateLimitKeys.clear();
        }
    }

    // ---------- case 1: create + list ----------

    @Test
    void register_login_createPlan_list_returnsCreated() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);

        long userId = registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // POST /plans → 201 + data 含 userId
        String body = """
                {"title":"周会","startTime":"%s","endTime":"%s","location":"会议室 A"}
                """.formatted(
                        LocalDateTime.of(2026, 8, 1, 10, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        LocalDateTime.of(2026, 8, 1, 11, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        MvcResult createResult = mvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("周会"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andReturn();
        long planId = readTree(createResult).path("data").path("id").asLong();

        // GET /plans?from=2026-08-01T00:00:00&to=2026-08-31T23:59:00
        mvc.perform(get("/api/v1/plans")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-31T23:59:00")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(planId))
                .andExpect(jsonPath("$.data.items[0].title").value("周会"));
    }

    // ---------- case 2: update partial ----------

    @Test
    void updatePlan_partialFields_persists() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long planId = createPlan(accessToken, "旧标题",
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 11, 0));

        // PUT /plans/{id} body 仅含 title
        mvc.perform(put("/api/v1/plans/" + planId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // GET /plans/{id} → title 已更新，location 保持 null
        mvc.perform(get("/api/v1/plans/" + planId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"))
                .andExpect(jsonPath("$.data.location").doesNotExist())  // list 字段不带
                .andExpect(jsonPath("$.data.note").doesNotExist());     // list 字段不带
    }

    // ---------- case 3: allDay normalizes on create (Phase 3 关键业务规则) ----------

    @Test
    void createPlan_allDayTrue_normalizesStartEndToDayBounds() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 跨日事件：8/15 22:00 → 8/16 02:00
        String body = """
                {"title":"全天出差","startTime":"%s","endTime":"%s","allDay":1}
                """.formatted(
                        LocalDateTime.of(2026, 8, 15, 22, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        LocalDateTime.of(2026, 8, 16, 2, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        MvcResult createResult = mvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long planId = readTree(createResult).path("data").path("id").asLong();

        // GET /plans/{id} → 详情中 start/end 已被 service 归一化
        mvc.perform(get("/api/v1/plans/" + planId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allDay").value(1))
                .andExpect(jsonPath("$.data.startTime").value("2026-08-15T00:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("2026-08-16T23:59:59"));
    }

    // ---------- case 4: soft delete ----------

    @Test
    void softDelete_listExcludes() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long planId = createPlan(accessToken, "即将消失",
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 11, 0));

        // DELETE /plans/{id} → 200
        mvc.perform(delete("/api/v1/plans/" + planId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // GET /plans 范围查询 → total=0
        mvc.perform(get("/api/v1/plans")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-31T23:59:00")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());

        // GET /plans/{id} → 403 + 1003（service 抛 ERR_CROSS_USER，软删后不可见）
        mvc.perform(get("/api/v1/plans/" + planId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- case 5: cross-user guard 1003 ----------

    @Test
    void crossUserDefense_userBReadsUserAsPlan_returns1003() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String ipA = uniqueIp();
        String ipB = uniqueIp();
        trackRegisterAndLoginKeys(emailA, ipA);
        trackRegisterAndLoginKeys(emailB, ipB);

        // userA 注册 + 登录 + 创建 plan
        registerAndLogin(emailA, "Valid1Pass", ipA);
        String accessTokenA = accessTokenFor(emailA, "Valid1Pass", ipA);
        long planIdA = createPlan(accessTokenA, "userA 的私密计划",
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 11, 0));

        // userB 注册 + 登录
        registerAndLogin(emailB, "Valid1Pass", ipB);
        String accessTokenB = accessTokenFor(emailB, "Valid1Pass", ipB);

        // userB 试图读 userA 的 plan → 403 + 1003
        mvc.perform(get("/api/v1/plans/" + planIdA).header("Authorization", "Bearer " + accessTokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该计划"));

        // userB 试图更新 userA 的 plan → 403 + 1003
        mvc.perform(put("/api/v1/plans/" + planIdA)
                        .header("Authorization", "Bearer " + accessTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));

        // userB 试图删除 userA 的 plan → 403 + 1003
        mvc.perform(delete("/api/v1/plans/" + planIdA).header("Authorization", "Bearer " + accessTokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- case 6: range filter ----------

    @Test
    void rangeFilter_returnsOnlyPlansInRange() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 7 月、8 月、9 月各 1 个事件
        createPlan(accessToken, "七月事件",
                LocalDateTime.of(2026, 7, 15, 10, 0),
                LocalDateTime.of(2026, 7, 15, 11, 0));
        long augustId = createPlan(accessToken, "八月事件",
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 11, 0));
        createPlan(accessToken, "九月事件",
                LocalDateTime.of(2026, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 15, 11, 0));

        // GET /plans?from=2026-08-01&to=2026-08-31 → 只返回 8 月
        mvc.perform(get("/api/v1/plans")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-31T23:59:00")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(augustId))
                .andExpect(jsonPath("$.data.items[0].title").value("八月事件"));
    }

    // ---------- helpers ----------

    private long registerAndLogin(String email, String password, String ip) throws Exception {
        MvcResult reg = mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new RegisterRequest(email, password, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return readTree(reg).path("data").path("userId").asLong();
    }

    private String accessTokenFor(String email, String password, String ip) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return readTree(login).path("data").path("accessToken").asText();
    }

    private long createPlan(String accessToken, String title,
                            LocalDateTime start, LocalDateTime end) throws Exception {
        String body = """
                {"title":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(
                        title,
                        start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        MvcResult r = mvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long id = readTree(r).path("data").path("id").asLong();
        assertThat(id).isPositive();
        return id;
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
        return objectMapper.writeValueAsBytes(value);
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}