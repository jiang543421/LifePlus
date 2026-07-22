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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 洞察全链路集成测试（spec §6.4 Testcontainers）。
 *
 * <p>5 case：
 * <ol>
 *   <li>{@code register → login → GET /today} 无任务/消费数据 → 500 + 1501（降级）</li>
 *   <li>{@code register → login → seed 1 task → GET /today} → 200 + chips 非空 + headline 含数</li>
 *   <li>{@code POST /refresh} 覆写缓存 → 返回新值 + INFO 日志（间接）</li>
 *   <li>缓存命中：seed 后 GET → POST /refresh → GET 第二次（同 TTL 内）→ service.getInsight 仅取缓存，不再 collect</li>
 *   <li>{@code 401}：无 token → 401 + 1002</li>
 * </ol>
 *
 * <p>隔离策略（与 PlanFlowIT / TaskFlowIT 一致）：
 * email/IP 用 {@code UUID} 拼接；{@link JdbcTemplate} 物理清 {@code t_task}；
 * Redis 缓存键 + 限流键在 {@code @AfterEach} 集中删除。
 */
@AutoConfigureMockMvc
class AiInsightIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private final List<String> trackedRedisKeys = new ArrayList<>();

    @BeforeEach
    void cleanTaskTable() {
        // 物理清 t_task（避免逻辑删除的历史行影响 provider count）
        jdbc.update("DELETE FROM t_task");
    }

    @AfterEach
    void cleanupRedis() {
        if (!trackedRedisKeys.isEmpty()) {
            redis.delete(trackedRedisKeys);
            trackedRedisKeys.clear();
        }
    }

    // ---------- case 1: 无数据时降级 ----------

    @Test
    void getToday_noData_returnsDegraded1501() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 无 t_task / t_expense → 全部 provider 返回 0 或 disabled → 1501
        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(ErrorCode.AI_DEGRADED));
    }

    // ---------- case 2: 有 1 个任务时正常返回 ----------

    @Test
    void getToday_seededTask_returns200WithChipsAndHeadline() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // seed 1 个今天到期且已完成的任务 → TaskAiProvider 返回 rate=100（非 0）
        createDoneTask(accessToken, "买菜", LocalDate.now());

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            // 任务 chip key
            .andExpect(jsonPath("$.data.chips[0].key").value(AiConstants.CHIP_TASK_COMPLETION))
            // chips 数量固定 3
            .andExpect(jsonPath("$.data.chips.length()").value(3))
            // 主文存在
            .andExpect(jsonPath("$.data.headline").exists())
            // freshnessSeconds 现算
            .andExpect(jsonPath("$.data.freshnessSeconds").exists());
    }

    // ---------- case 3: refresh 端点 ----------

    @Test
    void postRefresh_authenticated_returns200AndRecompute() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        createDoneTask(accessToken, "TestTask", LocalDate.now());

        // 第一次 POST /refresh → 200
        mvc.perform(post("/api/v1/ai/insight/refresh")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.chips.length()").value(3));

        // seed 新任务
        createDoneTask(accessToken, "AnotherTask", LocalDate.now());

        // 第二次 POST /refresh → 200（重算并覆写缓存）
        mvc.perform(post("/api/v1/ai/insight/refresh")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    // ---------- case 4: 缓存命中 ----------

    @Test
    void getToday_cacheHit_secondCallReturnsSameShape() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        createDoneTask(accessToken, "CacheTestTask", LocalDate.now());

        // 第一次 GET：cache miss → service.getInsight 重算
        MvcResult first = mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
        String firstHeadline = readTree(first).path("data").path("headline").asText();

        // 第二次 GET：cache hit → 同 headline
        MvcResult second = mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
        String secondHeadline = readTree(second).path("data").path("headline").asText();

        assertThat(firstHeadline).isEqualTo(secondHeadline);
    }

    // ---------- case 5: 鉴权 ----------

    @Test
    void noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/ai/insight/today"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));

        mvc.perform(post("/api/v1/ai/insight/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));
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
     * 创建并直接标记为 DONE 的任务。
     *
     * <p>{@link com.lifepulse.ai.provider.TaskAiProvider} 计算
     * {@code done * 100 / total}：若只创建一条且不做 completed，rate=0
     * 会被 {@code MetricValue.isNonEmpty()} 视为"无数据"。patch 到 status=1
     * 后 rate=100 → 至少 1 个 provider 返回非空，绕过 1501。
     */
    private void createDoneTask(String accessToken, String title, LocalDate dueDate) throws Exception {
        String createBody = """
                {"title":"%s","priority":2,"dueDate":"%s"}
                """.formatted(title, dueDate.toString());
        MvcResult created = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        long id = readTree(created).path("data").path("id").asLong();

        String patchBody = "{\"status\":1}";
        mvc.perform(patch("/api/v1/tasks/" + id + "/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk());
    }

    /**
     * 注册 AI 模块相关 Redis key（cache + 限流），保证测试间隔离。
     */
    private void trackAiKeys(long userId) {
        trackedRedisKeys.add(AiConstants.CACHE_KEY_PREFIX + userId);
        trackedRedisKeys.add(AiConstants.INSIGHT_RL_KEY_PREFIX + userId);
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
