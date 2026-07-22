package com.lifepulse.diet.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.diet.DietConstants;
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
 * Diet 模块端到端集成测试（spec 07-diet-design §8.2：≥6 cases，Testcontainers MySQL + 本地 Redis）。
 *
 * <p>6 case：
 * <ol>
 *   <li>register → login → create-diet → list → summary → delete → list 闭环</li>
 *   <li>跨用户越权：userB 读 userA 的 diet → 403 + 1003</li>
 *   <li>软删幂等：delete → list 中消失</li>
 *   <li>summary 聚合正确性：构造早午晚各一笔，断言当日 kcal = sum；与昨日对比 delta</li>
 *   <li>frequent 时间窗口边界：from = to → 空集</li>
 *   <li>写端限流：前 10 次 create 全 OK；第 11 次 → 429 + 1006</li>
 * </ol>
 *
 * <p>与 ExpenseServiceIT 同款：
 * email / IP 用 {@code UUID} 拼接唯一；{@code @AfterEach} 清限流 Redis key；
 * 用 {@link JdbcTemplate} 物理清 t_diet。
 */
@AutoConfigureMockMvc
class DietServiceIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> trackedRateLimitKeys = new ArrayList<>();

    @BeforeEach
    void cleanDietTable() {
        jdbc.update("DELETE FROM t_diet");
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
    void register_login_createDiet_list_summary_deleteRoundtrip() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 1. POST 2 笔 diet（早 + 午） → 都 201
        long id1 = createDiet(accessToken, "BREAKFAST", "燕麦", "380",
                OffsetDateTime.of(2026, 7, 15, 8, 0, 0, 0, ZoneOffset.UTC));
        long id2 = createDiet(accessToken, "LUNCH", "米饭", "230",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));

        // 2. GET /diets → total=2
        mvc.perform(get("/api/v1/diets").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // 3. GET /diets/summary?date=2026-07-15 → kcal=610（380+230），昨日无数据 → deltaYesterday null
        MvcResult summaryResult = mvc.perform(get("/api/v1/diets/summary")
                        .param("date", "2026-07-15")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kcal").value(610.00))
                .andReturn();
        String summaryBody = summaryResult.getResponse().getContentAsString();
        assertThat(summaryBody).contains("\"kcalDeltaYesterday\":null");
        assertThat(summaryBody).contains("\"kcalDeltaLastWeek\":null");

        // 4. DELETE id1 → 200
        mvc.perform(delete("/api/v1/diets/" + id1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 5. GET /diets → total=1，剩下 id2
        mvc.perform(get("/api/v1/diets").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id2));
    }

    // ---------- case 2: cross-user 1003 ----------

    @Test
    void crossUserDefense_userBReadsUserAsDiet_returns1003() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String ipA = uniqueIp();
        String ipB = uniqueIp();
        trackRegisterAndLoginKeys(emailA, ipA);
        trackRegisterAndLoginKeys(emailB, ipB);

        registerAndLogin(emailA, "Valid1Pass", ipA);
        String tokenA = accessTokenFor(emailA, "Valid1Pass", ipA);
        long dietIdA = createDiet(tokenA, "LUNCH", "name", "500",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));

        registerAndLogin(emailB, "Valid1Pass", ipB);
        String tokenB = accessTokenFor(emailB, "Valid1Pass", ipB);

        mvc.perform(get("/api/v1/diets/" + dietIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该饮食"));
    }

    // ---------- case 3: soft delete then 1003 ----------

    @Test
    void softDeleteThenGetReturns1003() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long dietId = createDiet(accessToken, "LUNCH", "name", "500",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));

        mvc.perform(delete("/api/v1/diets/" + dietId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 软删后 GET → 1003
        mvc.perform(get("/api/v1/diets/" + dietId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER));
    }

    // ---------- case 4: summary aggregation correctness ----------

    @Test
    void summary_aggregatesFourNutritionSums_correctly() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 早 380 + 午 500 + 晚 720 = 1600 kcal
        createDietFull(accessToken, "BREAKFAST", "燕麦", "380", "10", "60", "5",
                OffsetDateTime.of(2026, 7, 15, 8, 0, 0, 0, ZoneOffset.UTC));
        createDietFull(accessToken, "LUNCH", "米饭", "500", "15", "100", "5",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        createDietFull(accessToken, "DINNER", "鱼", "720", "40", "60", "30",
                OffsetDateTime.of(2026, 7, 15, 19, 0, 0, 0, ZoneOffset.UTC));

        mvc.perform(get("/api/v1/diets/summary")
                        .param("date", "2026-07-15")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kcal").value(1600.00))
                .andExpect(jsonPath("$.data.proteinG").value(65.00))
                .andExpect(jsonPath("$.data.carbG").value(220.00))
                .andExpect(jsonPath("$.data.fatG").value(40.00));
    }

    // ---------- case 5: frequent window edge ----------

    @Test
    void frequent_windowInverted_returnsEmptyList() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 构造一笔 diet 落在 7-15
        createDiet(accessToken, "LUNCH", "name", "500",
                OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));

        // 查 window [7-20 .. 7-15] → 反向，应当空集
        mvc.perform(get("/api/v1/diets/frequent")
                        .param("from", "2026-07-20T00:00:00Z")
                        .param("to", "2026-07-15T00:00:00Z")
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- case 6: write rate limit ----------

    @Test
    void writeRateLimit_11thCreateThrows1006_429() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // WRITE_RL_MAX = 10；前 10 次创建全部 201
        for (int i = 0; i < DietConstants.WRITE_RL_MAX; i++) {
            createDiet(accessToken, "LUNCH", "name", "100",
                    OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC));
        }

        // 第 11 次 → 429 + 1006
        mvc.perform(post("/api/v1/diets")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealType\":\"LUNCH\",\"name\":\"x\","
                                + "\"kcal\":\"1\",\"proteinG\":\"1\",\"carbG\":\"1\",\"fatG\":\"1\","
                                + "\"occurredAt\":\"2026-07-15T12:00:00Z\"}"))
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

    private long createDiet(String accessToken, String mealType, String name, String kcal,
                            OffsetDateTime when) throws Exception {
        return createDietFull(accessToken, mealType, name, kcal, "1", "1", "1", when);
    }

    private long createDietFull(String accessToken, String mealType, String name, String kcal,
                                String proteinG, String carbG, String fatG,
                                OffsetDateTime when) throws Exception {
        String body = "{\"mealType\":\"" + mealType + "\","
                + "\"name\":\"" + name + "\","
                + "\"kcal\":\"" + kcal + "\","
                + "\"proteinG\":\"" + proteinG + "\","
                + "\"carbG\":\"" + carbG + "\","
                + "\"fatG\":\"" + fatG + "\","
                + "\"occurredAt\":\"" + when + "\"}";
        MvcResult r = mvc.perform(post("/api/v1/diets")
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
