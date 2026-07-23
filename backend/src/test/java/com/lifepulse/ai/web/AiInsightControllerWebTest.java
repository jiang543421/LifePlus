package com.lifepulse.ai.web;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.llm.LlmMeta;
import com.lifepulse.ai.llm.Mood;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.ai.service.AiInsightService;
import com.lifepulse.ai.web.dto.AiChipDto;
import com.lifepulse.ai.web.dto.AiInsightResponse;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.security.JwtAuthEntryPoint;
import com.lifepulse.security.JwtAuthFilter;
import com.lifepulse.security.SecurityConfig;
import com.lifepulse.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AiInsightController} 切片测试（spec §6.2 + §7.4）。
 *
 * <p>覆盖矩阵：
 * <ul>
 *   <li>鉴权：no token → 401 (code 1002)</li>
 *   <li>GET /today happy → 200 + 3 chips + freshnessSeconds ≥ 0</li>
 *   <li>POST /refresh happy → 200 + service.refreshInsight 调用 + INFO 日志（间接靠 verify）</li>
 *   <li>GET /today 被限流 → 429 (code 1006)</li>
 *   <li>POST /refresh 被限流 → 429 (code 1006)</li>
 *   <li>GET /today service 抛 1501（AI_DEGRADED）→ 500</li>
 *   <li>POST /refresh service 抛 1501 → 500</li>
 * </ul>
 *
 * <p>{@link RateLimiter} 是 {@code @Component}，{@link MockBean} 注入；mock 后
 * 不会真连 Redis，hit 行为完全受测试控制。
 */
@WebMvcTest(AiInsightController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class AiInsightControllerWebTest {

    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AiInsightService aiInsightService;

    @MockBean
    private RateLimiter rateLimiter;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void bypassFilter() throws Exception {
        Mockito.doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(
                Mockito.any(ServletRequest.class),
                Mockito.any(ServletResponse.class),
                Mockito.any(FilterChain.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static AiInsightResponse sampleResponse() {
        AiChipDto taskChip = new AiChipDto(AiConstants.CHIP_TASK_COMPLETION, "任务完成",
            "80", "%", Trend.FLAT, "与昨日持平");
        AiChipDto expenseChip = new AiChipDto(AiConstants.CHIP_WEEKLY_EXPENSE, "本周消费",
            "¥420", "¥", Trend.FLAT, "与上周持平");
        AiChipDto planChip = new AiChipDto(AiConstants.CHIP_PLAN_DENSITY, "日程",
            "3", "项", Trend.NONE, "今日 3 项");
        return new AiInsightResponse("今日任务完成率 80%", List.of(taskChip, expenseChip, planChip),
            Instant.now(), 0L);
    }

    /** v2.1 LLM 命中态：source/advice/highlight/mood/llmMeta 全有。 */
    private static AiInsightResponse sampleLlmResponse() {
        AiChipDto taskChip = new AiChipDto(AiConstants.CHIP_TASK_COMPLETION, "任务完成",
            "80", "%", Trend.FLAT, "与昨日持平");
        AiChipDto expenseChip = new AiChipDto(AiConstants.CHIP_WEEKLY_EXPENSE, "本周消费",
            "¥420", "¥", Trend.FLAT, "与上周持平");
        AiChipDto planChip = new AiChipDto(AiConstants.CHIP_PLAN_DENSITY, "日程",
            "3", "项", Trend.NONE, "今日 3 项");
        LlmMeta meta = new LlmMeta(120, 80, 850L);
        return new AiInsightResponse(
            "今日任务完成率 80%",
            List.of(taskChip, expenseChip, planChip),
            Instant.now(),
            0L,
            "llm",
            "继续保持节奏",
            "昨天完成了 5 个任务",
            Mood.POSITIVE,
            meta);
    }

    // ---------- GET /today happy ----------

    @Test
    void today_authenticated_returns200WithThreeChips() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any()))
            .thenReturn(false);
        when(aiInsightService.getInsight(USER_ID)).thenReturn(sampleResponse());

        mvc.perform(get("/api/v1/ai/insight/today").with(authentication(authToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.headline").value("今日任务完成率 80%"))
            .andExpect(jsonPath("$.data.chips.length()").value(3))
            .andExpect(jsonPath("$.data.chips[0].key").value(AiConstants.CHIP_TASK_COMPLETION))
            .andExpect(jsonPath("$.data.chips[1].key").value(AiConstants.CHIP_WEEKLY_EXPENSE))
            .andExpect(jsonPath("$.data.chips[2].key").value(AiConstants.CHIP_PLAN_DENSITY))
            .andExpect(jsonPath("$.data.freshnessSeconds").exists());

        verify(aiInsightService).getInsight(USER_ID);
        verify(rateLimiter).hit(eq(AiConstants.INSIGHT_RL_KEY_PREFIX + USER_ID),
            eq(AiConstants.INSIGHT_GET_RL_MAX), Mockito.any());
    }

    @Test
    void today_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/ai/insight/today"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));

        verify(aiInsightService, never()).getInsight(anyLong());
        verify(rateLimiter, never()).hit(anyString(), anyInt(), Mockito.any());
    }

    @Test
    void today_rateLimited_returns429WithCode1006() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(true);

        mvc.perform(get("/api/v1/ai/insight/today").with(authentication(authToken())))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT))
            .andExpect(jsonPath("$.message").value("AI 洞察请求过于频繁，请稍后重试"));

        verify(aiInsightService, never()).getInsight(anyLong());
    }

    @Test
    void today_serviceDegraded_returns503WithCode1501() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(false);
        when(aiInsightService.getInsight(USER_ID))
            .thenThrow(new BusinessException(ErrorCodeFixtures.AI_DEGRADED,
                "AI 洞察数据暂时不可用，请稍后重试"));

        mvc.perform(get("/api/v1/ai/insight/today").with(authentication(authToken())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(ErrorCodeFixtures.AI_DEGRADED));
    }

    // ---------- POST /refresh happy ----------

    @Test
    void refresh_authenticated_returns200AndCallsRefreshInsight() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(false);
        when(aiInsightService.refreshInsight(USER_ID)).thenReturn(sampleResponse());

        mvc.perform(post("/api/v1/ai/insight/refresh").with(authentication(authToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.headline").value("今日任务完成率 80%"));

        verify(aiInsightService).refreshInsight(USER_ID);
        verify(aiInsightService, never()).getInsight(anyLong());
        verify(rateLimiter).hit(eq(AiConstants.INSIGHT_RL_KEY_PREFIX + USER_ID),
            eq(AiConstants.INSIGHT_REFRESH_RL_MAX), Mockito.any());
    }

    @Test
    void refresh_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(post("/api/v1/ai/insight/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));

        verify(aiInsightService, never()).refreshInsight(anyLong());
    }

    @Test
    void refresh_rateLimited_returns429WithCode1006() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(true);

        mvc.perform(post("/api/v1/ai/insight/refresh").with(authentication(authToken())))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT));

        verify(aiInsightService, never()).refreshInsight(anyLong());
    }

    @Test
    void refresh_serviceDegraded_returns503WithCode1501() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(false);
        when(aiInsightService.refreshInsight(USER_ID))
            .thenThrow(new BusinessException(ErrorCodeFixtures.AI_DEGRADED,
                "AI 洞察数据暂时不可用，请稍后重试"));

        mvc.perform(post("/api/v1/ai/insight/refresh").with(authentication(authToken())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(ErrorCodeFixtures.AI_DEGRADED));
    }

    // ---------- GET /analysis (v2.1 PR3 独立分析页) ----------

    @Test
    void analysis_authenticated_returns200WithLlmMeta() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(false);
        when(aiInsightService.getInsight(USER_ID)).thenReturn(sampleLlmResponse());

        mvc.perform(get("/api/v1/ai/insight/analysis").with(authentication(authToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.headline").value("今日任务完成率 80%"))
            .andExpect(jsonPath("$.data.source").value("llm"))
            .andExpect(jsonPath("$.data.advice").value("继续保持节奏"))
            .andExpect(jsonPath("$.data.highlight").value("昨天完成了 5 个任务"))
            .andExpect(jsonPath("$.data.mood").value("POSITIVE"))
            .andExpect(jsonPath("$.data.llmMeta.promptTokens").value(120))
            .andExpect(jsonPath("$.data.llmMeta.responseTokens").value(80))
            .andExpect(jsonPath("$.data.freshnessSeconds").exists());

        verify(aiInsightService).getInsight(USER_ID);
        verify(rateLimiter).hit(eq(AiConstants.INSIGHT_RL_KEY_PREFIX + USER_ID),
            eq(AiConstants.INSIGHT_GET_RL_MAX), Mockito.any());
    }

    @Test
    void analysis_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/ai/insight/analysis"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));

        verify(aiInsightService, never()).getInsight(anyLong());
        verify(rateLimiter, never()).hit(anyString(), anyInt(), Mockito.any());
    }

    @Test
    void analysis_rateLimited_returns429WithCode1006() throws Exception {
        when(rateLimiter.hit(anyString(), anyInt(), Mockito.any())).thenReturn(true);

        mvc.perform(get("/api/v1/ai/insight/analysis").with(authentication(authToken())))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT))
            .andExpect(jsonPath("$.message").value("AI 洞察请求过于频繁，请稍后重试"));

        verify(aiInsightService, never()).getInsight(anyLong());
    }

    /** 单一来源：测试用错误码（避免循环 import）。 */
    private static final class ErrorCodeFixtures {
        static final int AI_DEGRADED = 1501;
    }
}

