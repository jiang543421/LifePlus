package com.lifepulse.plan.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.plan.dto.PlanCreateRequest;
import com.lifepulse.plan.dto.PlanFilter;
import com.lifepulse.plan.dto.PlanListItem;
import com.lifepulse.plan.dto.PlanResponse;
import com.lifepulse.plan.dto.PlanUpdateRequest;
import com.lifepulse.plan.service.PlanService;
import com.lifepulse.security.JwtAuthEntryPoint;
import com.lifepulse.security.JwtAuthFilter;
import com.lifepulse.security.SecurityConfig;
import com.lifepulse.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PlanController 切片测试（spec §6.1：鉴权 / 校验 / 越权路径 100% 覆盖）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>5 个 happy path（list/get/create/update/delete）</li>
 *   <li>4 个 validation path（缺 title / 缺 startTime / page 越界 / size 越界）</li>
 *   <li>1 个 cross-field path（endTime <= startTime → service 抛 1001 → 400）</li>
 *   <li>2 个 cross-user path（get 1003 / update 1003 / delete 1003）</li>
 *   <li>1 个 auth path（无 token → 401）</li>
 * </ul>
 *
 * <p>{@link UserContext} 是 static — 不能用 mock；通过 {@link SecurityContextHolder}
 * 注入 {@link UsernamePasswordAuthenticationToken}（{@code principal = userId}），
 * 由 Spring Security 在请求结束后清除。{@link JwtAuthFilter} mock no-op，
 * 让 MockMvc 走 Spring Security 标准链。
 */
@WebMvcTest(PlanController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class PlanControllerWebTest {

    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

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

    // ---------- list ----------

    @Test
    void list_defaultParams_returnsPagedItems() throws Exception {
        PageResponse<PlanListItem> page = PageResponse.of(List.of(), 0L, 1, 20);
        when(planService.pageByUser(any(PlanFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/plans").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<PlanFilter> cap = ArgumentCaptor.forClass(PlanFilter.class);
        verify(planService).pageByUser(cap.capture());
        PlanFilter f = cap.getValue();
        assertThat(f.page()).isEqualTo(1);
        assertThat(f.size()).isEqualTo(20);
        assertThat(f.from()).isNull();
        assertThat(f.to()).isNull();
    }

    @Test
    void list_filtersApplied_fromToPageSize_passesToService() throws Exception {
        PageResponse<PlanListItem> page = PageResponse.of(List.of(), 0L, 2, 10);
        when(planService.pageByUser(any(PlanFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/plans")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-31T23:59:00")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        ArgumentCaptor<PlanFilter> cap = ArgumentCaptor.forClass(PlanFilter.class);
        verify(planService).pageByUser(cap.capture());
        PlanFilter f = cap.getValue();
        assertThat(f.from()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0, 0));
        assertThat(f.to()).isEqualTo(LocalDateTime.of(2026, 8, 31, 23, 59, 0));
        assertThat(f.page()).isEqualTo(2);
        assertThat(f.size()).isEqualTo(10);
    }

    @Test
    void list_pageZero_returns1001() throws Exception {
        mvc.perform(get("/api/v1/plans").param("page", "0").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION))
                .andExpect(jsonPath("$.message").value("page must be >= 1"));

        verify(planService, never()).pageByUser(any());
    }

    @Test
    void list_sizeAboveMax_returns1001() throws Exception {
        mvc.perform(get("/api/v1/plans").param("size", "999").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));

        verify(planService, never()).pageByUser(any());
    }

    // ---------- get ----------

    @Test
    void get_ownerMatch_returnsDetail() throws Exception {
        PlanResponse res = new PlanResponse(11L, USER_ID, "周会",
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 11, 0),
                0, "会议室 A", "议程", 15,
                OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, ZoneOffset.ofHours(8)),
                OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, ZoneOffset.ofHours(8)));
        when(planService.getById(11L)).thenReturn(res);

        mvc.perform(get("/api/v1/plans/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("周会"))
                .andExpect(jsonPath("$.data.location").value("会议室 A"));
    }

    @Test
    void get_crossUser_returns1003_403() throws Exception {
        when(planService.getById(11L))
                .thenThrow(new BusinessException(ErrorCode.CROSS_USER, "无权操作该计划"));

        mvc.perform(get("/api/v1/plans/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该计划"));
    }

    // ---------- create ----------

    @Test
    void create_valid_returns201WithDetail() throws Exception {
        PlanResponse res = new PlanResponse(11L, USER_ID, "周会",
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 11, 0),
                0, null, null, 15,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(planService.create(any(PlanCreateRequest.class))).thenReturn(res);

        PlanCreateRequest req = new PlanCreateRequest(
                "周会",
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 11, 0),
                0, null, null, null);

        mvc.perform(post("/api/v1/plans")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("周会"))
                .andExpect(jsonPath("$.data.startTime").value("2026-08-01T10:00:00"));
    }

    @Test
    void create_blankTitle_returns1001() throws Exception {
        String body = "{\"title\":\"\",\"startTime\":\"2026-08-01T10:00:00\",\"endTime\":\"2026-08-01T11:00:00\"}";

        mvc.perform(post("/api/v1/plans")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));

        verify(planService, never()).create(any());
    }

    @Test
    void create_missingTitle_returns1001() throws Exception {
        String body = "{\"startTime\":\"2026-08-01T10:00:00\",\"endTime\":\"2026-08-01T11:00:00\"}";

        mvc.perform(post("/api/v1/plans")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));

        verify(planService, never()).create(any());
    }

    @Test
    void create_missingStartTime_returns1001() throws Exception {
        String body = "{\"title\":\"周会\",\"endTime\":\"2026-08-01T11:00:00\"}";

        mvc.perform(post("/api/v1/plans")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));

        verify(planService, never()).create(any());
    }

    @Test
    void create_allDayOutOfRange_returns1001() throws Exception {
        // allDay=2 超出 @Max(1)
        String body = """
                {"title":"周会","startTime":"2026-08-01T10:00:00",
                 "endTime":"2026-08-01T11:00:00","allDay":2}
                """;

        mvc.perform(post("/api/v1/plans")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));

        verify(planService, never()).create(any());
    }

    @Test
    void create_endTimeNotAfterStart_serviceThrows1001_returns400() throws Exception {
        // DTO 通过（DTO 无跨字段校验）；service 抛 1001
        when(planService.create(any(PlanCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION, "结束时间必须晚于开始时间"));

        PlanCreateRequest req = new PlanCreateRequest(
                "倒序",
                LocalDateTime.of(2026, 8, 1, 11, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0),
                0, null, null, null);

        mvc.perform(post("/api/v1/plans")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION))
                .andExpect(jsonPath("$.message").value("结束时间必须晚于开始时间"));
    }

    // ---------- update ----------

    @Test
    void update_valid_returns200() throws Exception {
        PlanUpdateRequest req = new PlanUpdateRequest(
                "新标题", null, null, null, null, null, null);

        mvc.perform(put("/api/v1/plans/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(planService).update(11L, req);
    }

    @Test
    void update_crossUser_returns1003() throws Exception {
        doThrow(new BusinessException(ErrorCode.CROSS_USER, "无权操作该计划"))
                .when(planService).update(anyLong(), any(PlanUpdateRequest.class));

        PlanUpdateRequest req = new PlanUpdateRequest(
                "新", null, null, null, null, null, null);

        mvc.perform(put("/api/v1/plans/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER));
    }

    // ---------- delete ----------

    @Test
    void delete_ownerMatch_returns200() throws Exception {
        mvc.perform(delete("/api/v1/plans/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(planService).softDelete(11L);
    }

    @Test
    void delete_crossUser_returns1003() throws Exception {
        doThrow(new BusinessException(ErrorCode.CROSS_USER, "无权操作该计划"))
                .when(planService).softDelete(anyLong());

        mvc.perform(delete("/api/v1/plans/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CROSS_USER));
    }

    // ---------- auth ----------

    @Test
    void noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/plans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));

        verify(planService, never()).pageByUser(any());
    }

    }
