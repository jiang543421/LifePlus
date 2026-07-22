package com.lifepulse.daily.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.DietMetrics;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.daily.WeeklyComparison;
import com.lifepulse.daily.WeeklyReportPayload;
import com.lifepulse.daily.service.DailyReportService;
import com.lifepulse.security.JwtAuthEntryPoint;
import com.lifepulse.security.JwtAuthFilter;
import com.lifepulse.security.SecurityConfig;
import com.lifepulse.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DailyReportController 切片测试（spec §6.1：鉴权 / 校验 / 越权路径 100% 覆盖）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>2 个 happy path（daily 默认参数、daily 带参数、week 默认参数、week 带参数）</li>
 *   <li>2 个 validation path（非法 date 格式 → 400 / 1001；超 30 天窗口 → 1001）</li>
 *   <li>1 个 auth path（无 token → 401）</li>
 *   <li>1 个 defensive auth path（filter 旁路 → 1002）</li>
 * </ul>
 *
 * <p>{@link UserContext} 是静态 ThreadLocal——不能用 mock。本测试通过 Spring Security
 * 的 {@link SecurityContextHolder} 注入 {@link UsernamePasswordAuthenticationToken}
 * （{@code principal = userId}）模拟已认证状态；并显式 {@link UserContext#set(Long)}
 * 同步（因 stubbed JwtAuthFilter 不再走生产链调用 set）。
 *
 * <p>{@link JwtAuthFilter} mock 为 no-op（{@code doAnswer(_ → chain.doFilter）}），
 * 让 MockMvc 走 Spring Security 标准链。
 */
@WebMvcTest(DailyReportController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class DailyReportControllerWebTest {

    private static final long USER_ID = 100L;
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 7, 21);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DailyReportService service;

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
        when(service.today()).thenReturn(FIXED_TODAY);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        // 同时填 SecurityContext + UserContext（filter 旁路后业务层依赖 UserContext）
        UserContext.set(USER_ID);
        return new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ---------- daily ----------

    @Test
    @DisplayName("GET /api/v1/daily 无参 → 默认今天 → 200 + 完整 payload")
    void daily_noDate_returns200WithDefaultDate() throws Exception {
        DailyReportPayload p = mkPayload(FIXED_TODAY);
        when(service.daily(anyLong(), any(LocalDate.class))).thenReturn(p);

        mvc.perform(get("/api/v1/daily").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.date").value("2026-07-21"))
                .andExpect(jsonPath("$.data.task.completedCount").value(3))
                .andExpect(jsonPath("$.data.task.totalCount").value(5))
                .andExpect(jsonPath("$.data.task.completionRate").value(0.6))
                .andExpect(jsonPath("$.data.diet.enabled").value(false));

        ArgumentCaptor<Long> uidCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDate> dateCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).daily(uidCap.capture(), dateCap.capture());
        // verify(service).today() was invoked once (controller fallback uses it)
        verify(service).today();
    }

    @Test
    @DisplayName("GET /api/v1/daily?date=... → service 收到指定日期")
    void daily_withDateParam_passesThrough() throws Exception {
        LocalDate target = LocalDate.of(2026, 7, 15);
        when(service.daily(USER_ID, target)).thenReturn(mkPayload(target));

        mvc.perform(get("/api/v1/daily").param("date", "2026-07-15")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-07-15"));

        verify(service).daily(USER_ID, target);
        verify(service, never()).today();
    }

    @Test
    @DisplayName("GET /api/v1/daily?date=not-a-date → 400 / 1001（Spring 解析失败）")
    void daily_invalidDateFormat_returns1001() throws Exception {
        mvc.perform(get("/api/v1/daily").param("date", "not-a-date")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest());

        verify(service, never()).daily(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("GET /api/v1/daily 超 30 天窗口 → Service 抛 1001 / 400")
    void daily_outOfWindow_returns1001() throws Exception {
        when(service.daily(anyLong(), any(LocalDate.class)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION,
                        "date out of range: 2026-06-14 earlier than 2026-06-21"));

        mvc.perform(get("/api/v1/daily").param("date", "2026-06-14")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("out of range")));
    }

    // ---------- week ----------

    @Test
    @DisplayName("GET /api/v1/daily/week 无参 → 默认今天 → 200 + isoWeek")
    void week_noDate_returns200WithCurrentWeek() throws Exception {
        WeeklyReportPayload wp = mkWeeklyPayload("2026-W30",
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
        when(service.week(anyLong(), any(LocalDate.class))).thenReturn(wp);

        mvc.perform(get("/api/v1/daily/week").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.isoWeek").value("2026-W30"))
                .andExpect(jsonPath("$.data.weekStart").value("2026-07-20"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-07-26"));

        verify(service).week(anyLong(), any(LocalDate.class));
        verify(service).today();
    }

    @Test
    @DisplayName("GET /api/v1/daily/week?date=周日 → Service 收到该周日（内部 ISO 对齐）")
    void week_sundayDate_passesThrough() throws Exception {
        // 2026-07-19 (Sun) → service 自己用 previousOrSame(MONDAY) 对齐到 2026-07-13
        LocalDate sun = LocalDate.of(2026, 7, 19);
        when(service.week(USER_ID, sun)).thenReturn(
                mkWeeklyPayload("2026-W30", LocalDate.of(2026, 7, 13), sun));

        mvc.perform(get("/api/v1/daily/week").param("date", "2026-07-19")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-07-13"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-07-19"));

        verify(service).week(USER_ID, sun);
        verify(service, never()).today();
    }

    @Test
    @DisplayName("GET /api/v1/daily/week 超 30 天窗口 → Service 抛 1001 / 400")
    void week_outOfWindow_returns1001() throws Exception {
        when(service.week(anyLong(), any(LocalDate.class)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION,
                        "date out of range: 2026-06-14 earlier than 2026-06-21"));

        mvc.perform(get("/api/v1/daily/week").param("date", "2026-06-14")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    // ---------- auth ----------

    @Test
    @DisplayName("GET /api/v1/daily 无 token → 401 + 1002")
    void daily_noToken_returns401WithCode1002() throws Exception {
        // 必须清掉 UserContext（被其他测试 set 但 @AfterEach 也会清——这里显式保险）
        UserContext.clear();
        SecurityContextHolder.clearContext();

        mvc.perform(get("/api/v1/daily"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));

        verify(service, never()).daily(anyLong(), any(LocalDate.class));
    }

    @Test
    @DisplayName("filter 旁路时 userId 缺失 → 控制器防御性抛 1002 / 401")
    void daily_noUserIdInContext_returns1002() throws Exception {
        // 显式绕过 authToken()：只塞 SecurityContext 但不 set UserContext，
        // 模拟生产链 JwtAuthFilter.set 被 stub 跳过时的场景
        UserContext.clear();
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(token);

        mvc.perform(get("/api/v1/daily").with(authentication(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("missing authenticated user")));

        verify(service, never()).daily(anyLong(), any(LocalDate.class));
    }

    /**
     * Pin 跨用户隔离：控制器必须把当前线程 UserContext 的 userId 传给 service，
     * 不接受任何 query / path 参数覆盖。CLAUDE.md §7.2 硬约束。
     *
     * <p>做法：以 userA (id=100) 的 token 请求 → service.daily 必须收到 userId=100；
     * 然后以 userB (id=200) 的 token 请求 → service.daily 必须收到 userId=200。
     * 若未来有人新增 {@code ?userId=} query param 或 path variable 试图覆盖，
     * ArgumentCaptor 立刻 fail，强制走 review。
     */
    @Test
    @DisplayName("跨用户隔离：userA → service 收到 userA；userB → service 收到 userB（pin CLAUDE.md §7.2）")
    void daily_crossUser_userContextThreadedCorrectly() throws Exception {
        long userA = 100L;
        long userB = 200L;
        DailyReportPayload payloadA = mkPayload(FIXED_TODAY);
        DailyReportPayload payloadB = mkPayload(FIXED_TODAY.minusDays(1));
        when(service.daily(userA, FIXED_TODAY)).thenReturn(payloadA);
        when(service.daily(userB, FIXED_TODAY)).thenReturn(payloadB);

        // userA 请求
        UserContext.set(userA);
        UsernamePasswordAuthenticationToken tokenA = new UsernamePasswordAuthenticationToken(
                userA, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get("/api/v1/daily").with(authentication(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-07-21"));

        // userB 请求
        UserContext.set(userB);
        UsernamePasswordAuthenticationToken tokenB = new UsernamePasswordAuthenticationToken(
                userB, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get("/api/v1/daily").with(authentication(tokenB)))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> uidCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDate> dateCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(service, Mockito.times(2)).daily(uidCap.capture(), dateCap.capture());
        // 顺序：先 userA 再 userB（与请求顺序一致）
        assertThat(uidCap.getAllValues()).containsExactly(userA, userB);
        assertThat(dateCap.getAllValues()).containsExactly(FIXED_TODAY, FIXED_TODAY);
    }

    // ---------- helpers ----

    private static DailyReportPayload mkPayload(LocalDate date) {
        TaskMetrics task = new TaskMetrics(3, 5, 0.6, Map.of(), Map.of());
        PlanMetrics plan = new PlanMetrics(2, 240, Map.of(), 14);
        ExpenseMetrics expense = new ExpenseMetrics(new BigDecimal("47.00"), 2,
                Map.of(), List.of());
        DietMetrics diet = new DietMetrics(false, null, "diet module not enabled in v1.2.3");
        return new DailyReportPayload(date, task, plan, expense, diet);
    }

    private static WeeklyReportPayload mkWeeklyPayload(String isoWeek,
                                                       LocalDate weekStart,
                                                       LocalDate weekEnd) {
        // comparison 允许为 null（spec 未要求 payload 必填）
        WeeklyComparison.WeeklyTriplet taskT =
                new WeeklyComparison.WeeklyTriplet(0.5, 0.3, 0.2);
        WeeklyComparison.WeeklyTriplet planT =
                new WeeklyComparison.WeeklyTriplet(7.0, 3.0, 4.0);
        WeeklyComparison.WeeklyTriplet expT =
                new WeeklyComparison.WeeklyTriplet(350.0, 210.0, 140.0);
        return new WeeklyReportPayload(isoWeek, weekStart, weekEnd,
                new WeeklyComparison(taskT, planT, expT));
    }
}
