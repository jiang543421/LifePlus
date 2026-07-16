package com.lifepulse.task.web;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2-D · Task 模块 Web 端到端集成测试（plan §2-D / spec §6.4）。
 *
 * <p>6 case：
 * <ol>
 *   <li>{@code register → login → POST /tasks → GET /tasks} 创建并出现在列表</li>
 *   <li>{@code PUT /tasks/{id}} 部分字段更新持久化</li>
 *   <li>{@code PATCH /tasks/{id}/status=1} + GET /tasks?status=1 仅返回已完成</li>
 *   <li>{@code DELETE /tasks/{id}} 软删后再 GET /tasks 不包含</li>
 *   <li>跨用户越权：userB 读 userA 的 task → 403 + 1003（实现 Phase 1 stub）</li>
 *   <li>{@code GET /tasks/by-plan/{planId}} 仅返回关联 task</li>
 * </ol>
 *
 * <p>基础设施与隔离策略与 {@code AuthFlowIT} 一致：
 * email / IP 用 {@code UUID} 拼接唯一；{@code @AfterEach} 清限流 Redis key。
 * 此外用 {@link JdbcTemplate} 物理清 t_task，避免容器复用时数据残留。
 */
@AutoConfigureMockMvc
class TaskFlowIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> trackedRateLimitKeys = new ArrayList<>();

    @BeforeEach
    void cleanTaskTable() {
        // t_task 唯一索引 idx_user_status_due / idx_user_plan 不含 deleted 列，
        // 单纯逻辑删除会留下历史行影响后续断言计数；改物理 DELETE 清空。
        jdbc.update("DELETE FROM t_task");
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
    void register_login_createTask_list_returnsCreated() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);

        // 1. register + login
        long userId = registerAndLogin(email, "Valid1Pass", ip);

        // 2. POST /tasks → 201 + data 含 userId=userId
        String body = """
                {"title":"买菜","priority":2,"dueDate":"%s","tag":"home"}
                """.formatted(LocalDate.now());
        MvcResult createResult = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessTokenFor(email, "Valid1Pass", ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("买菜"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andReturn();
        long taskId = readTree(createResult).path("data").path("id").asLong();

        // 3. GET /tasks → 列表大小 = 1，title 命中
        mvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + accessTokenFor(email, "Valid1Pass", ip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(taskId))
                .andExpect(jsonPath("$.data.items[0].title").value("买菜"));
    }

    // ---------- case 2: update partial ----------

    @Test
    void updateTask_partialFields_persists() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long taskId = createTask(accessToken, "旧标题", 1, "work", null);

        // PUT /tasks/{id} body 仅含 title
        String body = "{\"title\":\"新标题\"}";
        mvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // GET /tasks/{id} → title 已更新，priority/tag 保持原值
        mvc.perform(get("/api/v1/tasks/" + taskId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"))
                .andExpect(jsonPath("$.data.priority").value(1))
                .andExpect(jsonPath("$.data.tag").value("work"));
    }

    // ---------- case 3: patchStatus + status filter ----------

    @Test
    void patchStatus_done_filterListByStatus_returnsOnlyMatching() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long todoId = createTask(accessToken, "todo", 0, null, null);
        long doneId = createTask(accessToken, "done", 0, null, null);

        // PATCH done task → status=1
        mvc.perform(patch("/api/v1/tasks/" + doneId + "/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // GET /tasks?status=1 → 只返回 done，total=1
        mvc.perform(get("/api/v1/tasks").param("status", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(doneId))
                .andExpect(jsonPath("$.data.items[0].title").value("done"));

        // GET /tasks?status=0 → 只返回 todo
        mvc.perform(get("/api/v1/tasks").param("status", "0")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(todoId));
    }

    // ---------- case 4: soft delete ----------

    @Test
    void softDelete_listExcludes() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, "Valid1Pass".equals("Valid1Pass") ? ip : ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        long taskId = createTask(accessToken, "即将消失", 0, null, null);

        // DELETE /tasks/{id} → 200
        mvc.perform(delete("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // GET /tasks → total=0
        mvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());

        // GET /tasks/{id} → 403 + 1003（service 抛 ERR_CROSS_USER，软删后不可见）
        mvc.perform(get("/api/v1/tasks/" + taskId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- case 5: cross-user guard 1003（实现 Phase 1 AuthFlowIT 桩） ----------

    @Test
    void crossUserDefense_userBReadsUserAsTask_returns1003() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        String ipA = uniqueIp();
        String ipB = uniqueIp();
        trackRegisterAndLoginKeys(emailA, ipA);
        trackRegisterAndLoginKeys(emailB, ipB);

        // userA 注册 + 登录 + 创建 task
        registerAndLogin(emailA, "Valid1Pass", ipA);
        String accessTokenA = accessTokenFor(emailA, "Valid1Pass", ipA);
        long taskIdA = createTask(accessTokenA, "userA 的私密任务", 3, "secret", null);

        // userB 注册 + 登录
        registerAndLogin(emailB, "Valid1Pass", ipB);
        String accessTokenB = accessTokenFor(emailB, "Valid1Pass", ipB);

        // userB 试图读 userA 的 task → 403 + 1003
        mvc.perform(get("/api/v1/tasks/" + taskIdA).header("Authorization", "Bearer " + accessTokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该任务"));
    }

    // ---------- case 6: byPlan ----------

    @Test
    void byPlan_returnsLinkedTasks() throws Exception {
        String email = uniqueEmail();
        String ip = uniqueIp();
        trackRegisterAndLoginKeys(email, ip);
        registerAndLogin(email, "Valid1Pass", ip);
        String accessToken = accessTokenFor(email, "Valid1Pass", ip);

        // 创建 3 条 task，其中 2 条 plan_id=500
        String bodyA = "{\"title\":\"p1\",\"planId\":500}";
        String bodyB = "{\"title\":\"p2\",\"planId\":500}";
        String bodyC = "{\"title\":\"no-plan\"}";
        long a = createTaskRaw(accessToken, bodyA);
        long b = createTaskRaw(accessToken, bodyB);
        createTaskRaw(accessToken, bodyC);

        // GET /tasks/by-plan/500 → total=2
        mvc.perform(get("/api/v1/tasks/by-plan/500").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].title", org.hamcrest.Matchers.containsInAnyOrder("p1", "p2")));

        assertThat(a).isPositive();
        assertThat(b).isPositive();
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

    private long createTask(String accessToken, String title, int priority, String tag, Long planId) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"title\":\"").append(title).append("\"");
        if (priority > 0) sb.append(",\"priority\":").append(priority);
        if (tag != null) sb.append(",\"tag\":\"").append(tag).append("\"");
        if (planId != null) sb.append(",\"planId\":").append(planId);
        sb.append("}");
        return createTaskRaw(accessToken, sb.toString());
    }

    private long createTaskRaw(String accessToken, String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readTree(r).path("data").path("id").asLong();
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