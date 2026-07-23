package com.lifepulse.ai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.llm.LlmClient;
import com.lifepulse.ai.llm.LlmRequest;
import com.lifepulse.ai.llm.exception.LlmUnavailableException;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 洞察 v2.1 LLM 集成端到端 IT（spec §6.4 Testcontainers / CLAUDE.md §11）。
 *
 * <p>6 case：
 * <ol>
 *   <li>{@code llmUnavailable_fallbackToTemplate}：{@code llmClient.generate} 抛
 *       {@code LlmUnavailableException} → service catch 降级，响应 {@code source="template"}，
 *       advice / highlight / mood / llmMeta 全部 {@code NON_NULL} → 缺省</li>
 *   <li>{@code quotaExceeded_fallbackToTemplate}：预先在 Redis 写满配额计数
 *       {@code lp:ai:quota:<userId>:<date>=51} → service catch 降级到 L2 模板</li>
 *   <li>{@code cacheTtl_isSixHours}：写入触发 cache，验证 Redis TTL ∈ [21599, 21601]（避免 ±1s flaky）</li>
 *   <li>{@code crossUserIsolation}：userA / userB 独立 insight，{@code CACHE_KEY_PREFIX + userId} 完全隔离</li>
 *   <li>{@code postRefresh_usesNewBuildResponse}：POST /refresh 走 service.refreshInsight
 *       → 走新 buildResponse (source/advice/freshnessSeconds)</li>
 *   <li>{@code llmClientBean_wiredAndMockable}：{@code @MockBean LlmClient} 替换 deepseek 路径生效；
 *       不 seed task 触发 1501 验证降级链路不破坏</li>
 * </ol>
 *
 * <p>基础设施（继承 {@link AbstractIntegrationTest}）：
 * <ul>
 *   <li>MySQL：Testcontainers（{@code withReuse(true)}）</li>
 *   <li>Redis：本地 {@code localhost:6379/123456}</li>
 * </ul>
 *
 * <p>LLM 配置注入（基类继承 + 子类叠加）：
 * 子类通过 {@code @DynamicPropertySource} 注入合法 deepseek api-key 与不可达 base-url，
 * 避免任何真 HTTP 调用同时满足 {@code LlmProperties} fail-fast 校验（CLAUDE.md §11.2）。
 *
 * <p>隔离策略：email/IP 用 {@code UUID} 拼接；{@link JdbcTemplate} 物理清 {@code t_task}；
 * Redis 缓存 / 配额 / 限流键在 {@code @AfterEach} 集中删除。
 */
@AutoConfigureMockMvc
class AiAnalysisIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    /**
     * 替换默认 deepseek 客户端：避免任何真实 HTTP 调用；
     * 默认 {@code generate} 返回 null（不抛）→ 与 happy path 兼容；case 1 显式 stub 抛
     * {@code LlmUnavailableException}，验证 L1→L2 降级链。
     */
    @MockBean private LlmClient llmClient;

    private final List<String> trackedRedisKeys = new ArrayList<>();

    /**
     * LLM 配置（CLAUDE.md §11.2）：{@code provider=deepseek} 时 api-key 必填且非占位符
     * （非 {@code sk-replace-} 前缀且 ≥ 20 字符），否则 {@code LlmProperties} 启动 fail-fast。
     */
    @DynamicPropertySource
    static void llmProps(DynamicPropertyRegistry r) {
        r.add("lp.ai.llm.enabled", () -> "true");
        r.add("lp.ai.llm.provider", () -> "deepseek");
        r.add("lp.ai.llm.api-key", () -> "sk-it-valid-format-padded-to-20plus-chars");
        r.add("lp.ai.llm.base-url", () -> "http://localhost:9");
        r.add("lp.ai.llm.circuit-breaker.enabled", () -> "true");
    }

    @BeforeEach
    void setupCleanState() {
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

    // ---------- case 1: LLM 不可用 → 降级到 L2 模板 ----------

    @Test
    void llmUnavailable_fallbackToTemplate() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String token = accessTokenFor(email, "Valid1Pass", ip);

        // seed 1 个 done task：保证 TaskAiProvider 返回非空 (rate=100)，绕过 1501
        createDoneTask(token, "买菜", LocalDate.now());

        // stub：LLM 调用抛不可达异常 → service 内 catch → source="template"
        when(llmClient.generate(any(LlmRequest.class)))
            .thenThrow(new LlmUnavailableException("deepseek 5xx simulated"));

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.source").value("template"))
            // 降级时 advice / highlight / mood / llmMeta 均缺省（@JsonInclude(NON_NULL)）
            .andExpect(jsonPath("$.data.advice").doesNotExist())
            .andExpect(jsonPath("$.data.highlight").doesNotExist())
            .andExpect(jsonPath("$.data.mood").doesNotExist())
            .andExpect(jsonPath("$.data.llmMeta").doesNotExist())
            // L2 headline 来自模板渲染，必须存在
            .andExpect(jsonPath("$.data.headline").exists());
    }

    // ---------- case 2: 配额超限 → 降级到 L2 模板 ----------

    @Test
    void quotaExceeded_fallbackToTemplate() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String token = accessTokenFor(email, "Valid1Pass", ip);

        createDoneTask(token, "买菜", LocalDate.now());

        // 预先填配额到 51（> dailyQuota=50）→ LlmQuotaGuard.checkAndIncrement 抛 1510
        // service catch → source="template"
        String quotaKey = "lp:ai:quota:" + userId + ":" + LocalDate.now();
        redis.opsForValue().set(quotaKey, "51");
        trackedRedisKeys.add(quotaKey);

        // 不需要 stub llmClient：quota 检查在 generator 入口，到不了 client 调用

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.source").value("template"))
            .andExpect(jsonPath("$.data.advice").doesNotExist())
            .andExpect(jsonPath("$.data.headline").exists());
    }

    // ---------- case 3: cache TTL 6h（21600s ± 1s 漂移）----------

    @Test
    void cacheTtl_isSixHours() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String token = accessTokenFor(email, "Valid1Pass", ip);

        createDoneTask(token, "买菜", LocalDate.now());

        // 强制走 L2 模板（quota 拦截），避免 LLM 真实副作用
        String quotaKey = "lp:ai:quota:" + userId + ":" + LocalDate.now();
        redis.opsForValue().set(quotaKey, "51");
        trackedRedisKeys.add(quotaKey);

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // 验证 cache key 写入且 TTL ∈ [21599, 21601]
        //   AiInsightService.writeCache：set(key, json, 6h=21600s)
        //   使用 isBetween 容忍 ±1s 测量漂移，避免 CI flaky
        String cacheKey = AiConstants.CACHE_KEY_PREFIX + userId;
        Long ttl = redis.getExpire(cacheKey, TimeUnit.SECONDS);
        assertThat(ttl)
            .as("cache TTL must be ~6h (21600s); actual=%d", ttl)
            .isBetween(21599L, 21601L);
    }

    // ---------- case 4: 跨用户隔离（cache key 独立）----------

    @Test
    void crossUserIsolation() throws Exception {
        // userA 注册 + seed + GET → 触发 cache 写入
        String emailA = uniqueEmail();
        long userIdA = registerAndLogin(emailA, "Valid1Pass", uniqueIp());
        trackAiKeys(userIdA);
        String tokenA = accessTokenFor(emailA, "Valid1Pass", uniqueIp());
        createDoneTask(tokenA, "A的task", LocalDate.now());

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // userB 注册 + seed + GET → 自己独立缓存
        String emailB = uniqueEmail();
        long userIdB = registerAndLogin(emailB, "Valid1Pass", uniqueIp());
        trackAiKeys(userIdB);
        String tokenB = accessTokenFor(emailB, "Valid1Pass", uniqueIp());
        createDoneTask(tokenB, "B的task", LocalDate.now());

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // userId 必须不同 + userB 缓存键独立存在（userA 缓存不应跨用户命中）
        assertThat(userIdA).isNotEqualTo(userIdB);
        String userBCacheKey = AiConstants.CACHE_KEY_PREFIX + userIdB;
        assertThat(redis.opsForValue().get(userBCacheKey))
            .as("userB cache key must be set independently")
            .isNotNull();
    }

    // ---------- case 5: POST /refresh 走新 buildResponse ----------

    @Test
    void postRefresh_usesNewBuildResponse() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String token = accessTokenFor(email, "Valid1Pass", ip);

        createDoneTask(token, "买菜", LocalDate.now());

        // 配额拦截走 L2 模板（不让 stub 切到 happy path，验证的是 buildResponse 走通）
        String quotaKey = "lp:ai:quota:" + userId + ":" + LocalDate.now();
        redis.opsForValue().set(quotaKey, "51");
        trackedRedisKeys.add(quotaKey);

        // POST /refresh（spec §4.4）：跳过缓存读走 service.refreshInsight → recomputeAndCache → buildResponse
        mvc.perform(post("/api/v1/ai/insight/refresh")
                        .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            // v2.1 新字段：source 在 buildResponse 内固定写为 "template"（L2 命中）
            .andExpect(jsonPath("$.data.source").value("template"))
            // controller 层 withFreshness 现算，不为 null
            .andExpect(jsonPath("$.data.freshnessSeconds").exists());
    }

    // ---------- case 6: @MockBean LlmClient 替换 deepseek 路径生效 ----------

    @Test
    void llmClientBean_wiredAndMockable() throws Exception {
        // Spring context 已启动 + LlmClient bean 被 @MockBean 替换 → 注入到生成器
        assertThat(llmClient)
            .as("@MockBean LlmClient must replace deepseek path")
            .isNotNull();

        // 不 seed task → 全部 provider 返回 MetricValue.none() → 1501
        // 验证：mock 替换未破坏降级链路（1501 仍可抛）
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        long userId = registerAndLogin(email, "Valid1Pass", ip);
        trackAiKeys(userId);
        String token = accessTokenFor(email, "Valid1Pass", ip);

        mvc.perform(get("/api/v1/ai/insight/today")
                        .header("Authorization", "Bearer " + token))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(ErrorCode.AI_DEGRADED));
    }

    // ===== helpers （与 AiInsightIT 对齐；项目无 AuthTestHelper 约定）=====

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
     * 创建并直接 patch 为 DONE 的任务：保证 {@code TaskAiProvider.collect}
     * 返回 {@code rate=100}（非 0），至少 1 个 provider 返回非空，绕过 1501。
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

        mvc.perform(patch("/api/v1/tasks/" + id + "/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk());
    }

    /** 注册 AI 模块相关 Redis key（cache + GET / POST 限流），保证测试间隔离。 */
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
