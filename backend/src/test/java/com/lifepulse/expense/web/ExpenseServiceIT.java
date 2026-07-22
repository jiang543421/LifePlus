package com.lifepulse.expense.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.expense.ExpenseConstants;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase T8 · Expense 模块端到端集成测试（spec §6.4 Testcontainers + 本地 Redis）。
 *
 * <p>7 case：
 * <ol>
 *   <li>register → login → POST /expenses → GET /expenses → GET /summary → DELETE → 软删不回列表</li>
 *   <li>跨用户越权：userB 读 userA 的 expense → 403 + 1003</li>
 *   <li>软删后 GET → 403 + 1003</li>
 *   <li>空月汇总返回 5 个分类 0 元，total=0</li>
 *   <li>category + 日期范围过滤仅返回命中</li>
 *   <li>GET /expenses/categories → 5 条</li>
 *   <li>写端限流：前 10 次 create 全 OK；第 11 次 → 429 + 1006</li>
 * </ol>
 *
 * <p>基础设施与隔离策略与 {@code PlanFlowIT} 同款：
 * email / IP 用 {@code UUID} 拼接唯一；{@code @AfterEach} 清限流 Redis key；
 * 用 {@link JdbcTemplate} 物理清 t_expense，避免容器复用时数据残留。
 *
 * <p>注：plan §T8 给的是 service 级 IT，本测试改走 web 级以复用现有 helper 与
 * 鉴权链路验证（mockMvc + JWT bearer）。其余 case 数量与边界与 plan 同款。
 */
@AutoConfigureMockMvc
class ExpenseServiceIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> trackedRateLimitKeys = new ArrayList<>();

    @BeforeEach
    void cleanExpenseTable() {
        // 物理清 t_expense（与 TaskFlowIT/PlanFlowIT 风格一致）；逻辑删留下历史行
        // 会影响 list/summary 计数断言。
        jdbc.update("DELETE FROM t_expense");
    }

    @AfterEach
    void cleanupRateLimitKeys() {
        if (!trackedRateLimitKeys.isEmpty()) {
            redis.delete(trackedRateLimitKeys);
            trackedRateLimitKeys.clear();
        }
    }

    // ---------- case 1: full roundtrip ----------

    @Test
    void register_login_createExpense_list_summary_deleteRoundtrip() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 1. POST 2 笔 expense → 都 201
        long id1 = createExpense(accessToken, "10.00", "MEAL", "午餐",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        long id2 = createExpense(accessToken, "12.00", "TRANSPORT", "地铁",
                OffsetDateTime.of(2026, 7, 15, 9, 0, 0, 0, ZoneOffset.UTC));

        // 2. GET /expenses → total=2
        mvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // 3. GET /expenses/summary?year=2026&month=7 → total=22.00 + 类目分桶
        mvc.perform(get("/api/v1/expenses/summary")
                        .param("year", "2026").param("month", "7")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(22.00))
                .andExpect(jsonPath("$.data.amountByCategory.MEAL").value(10.00))
                .andExpect(jsonPath("$.data.amountByCategory.TRANSPORT").value(12.00));

        // 4. DELETE id1 → 200
        mvc.perform(delete("/api/v1/expenses/" + id1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 5. GET /expenses → total=1，剩下 id2
        mvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id2));
    }

    // ---------- case 2: cross-user 1003 ----------

    @Test
    void crossUserDefense_userBReadsUserAsExpense_returns1003() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String ipA = uniqueIp();
        String ipB = uniqueIp();
        trackRegisterAndLoginKeys(emailA, ipA);
        trackRegisterAndLoginKeys(emailB, ipB);

        registerAndLogin(emailA, "Valid1Pass", ipA);
        String tokenA = accessTokenFor(emailA, "Valid1Pass", ipA);
        long expenseIdA = createExpense(tokenA, "10.00", "MEAL", null,
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));

        registerAndLogin(emailB, "Valid1Pass", ipB);
        String tokenB = accessTokenFor(emailB, "Valid1Pass", ipB);

        mvc.perform(get("/api/v1/expenses/" + expenseIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该消费"));
    }

    // ---------- case 3: soft delete then 1003 ----------

    @Test
    void softDeleteThenGetReturns1003() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long expenseId = createExpense(accessToken, "10.00", "MEAL", null,
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));

        mvc.perform(delete("/api/v1/expenses/" + expenseId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 软删后 GET → 1003（service 抛 ERR_CROSS_USER；plan Risk §3 一致处理）
        mvc.perform(get("/api/v1/expenses/" + expenseId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER));
    }

    // ---------- case 4: empty summary ----------

    @Test
    void summary_empty_returnsZerosForAllCategories() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/expenses/summary")
                        .param("year", "2026").param("month", "7")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.amountByCategory.MEAL").value(0))
                .andExpect(jsonPath("$.data.amountByCategory.SHOPPING").value(0))
                .andExpect(jsonPath("$.data.amountByCategory.TRANSPORT").value(0))
                .andExpect(jsonPath("$.data.amountByCategory.SUBSCRIPTION").value(0))
                .andExpect(jsonPath("$.data.amountByCategory.OTHER").value(0));
    }

    // ---------- case 5: filter by category + date range ----------

    @Test
    void filterByCategoryAndDateRange_returnsMatchingOnly() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 7 月 MEAL / 7 月 TRANSPORT / 8 月 MEAL
        createExpense(accessToken, "10.00", "MEAL", "七月午餐",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        createExpense(accessToken, "20.00", "TRANSPORT", "七月地铁",
                OffsetDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneOffset.UTC));
        createExpense(accessToken, "30.00", "MEAL", "八月午餐",
                OffsetDateTime.of(2026, 8, 5, 12, 0, 0, 0, ZoneOffset.UTC));

        // category=MEAL & from = 2026-07-01 & to = 2026-07-31 → 仅命中 7 月 MEAL
        mvc.perform(get("/api/v1/expenses")
                        .param("category", "MEAL")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-31T23:59:59Z")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("MEAL"))
                .andExpect(jsonPath("$.data.items[0].note").value("七月午餐"));
    }

    // ---------- case 6: categories ----------

    @Test
    void categories_returns5WithCodes() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/expenses/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].code").value("MEAL"));
    }

    // ---------- case 7: write rate limit ----------

    @Test
    void writeRateLimit_11thCreateThrows1006_429() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // WRITE_RL_MAX = 10；前 10 次创建全部 201
        for (int i = 0; i < ExpenseConstants.WRITE_RL_MAX; i++) {
            createExpense(accessToken, "1.00", "MEAL", null,
                    OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        }

        // 第 11 次 → 429 + 1006（复用 LOGIN_RATE_LIMIT code，ErrorCode 实测值）
        mvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1.00\",\"category\":\"MEAL\","
                                + "\"note\":null,\"occurredAt\":\"2026-07-15T12:00:00Z\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT));
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

    private long createExpense(String accessToken, String amount, String category,
                               String note, OffsetDateTime when) throws Exception {
        String body = "{\"amount\":\"" + amount + "\","
                + "\"category\":\"" + category + "\","
                + "\"note\":" + (note == null ? "null" : "\"" + note + "\"") + ","
                + "\"occurredAt\":\"" + when + "\"}";
        MvcResult r = mvc.perform(post("/api/v1/expenses")
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
            throw new IllegalStateException(e);
        }
    }

    private byte[] jsonBody(Object value) throws Exception {
        return objectMapper.writeValueAsBytes(value);
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
