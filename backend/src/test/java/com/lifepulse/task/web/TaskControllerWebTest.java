package com.lifepulse.task.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.security.JwtAuthEntryPoint;
import com.lifepulse.security.JwtAuthFilter;
import com.lifepulse.security.SecurityConfig;
import com.lifepulse.security.UserContext;
import com.lifepulse.task.dto.TaskCreateRequest;
import com.lifepulse.task.dto.TaskFilter;
import com.lifepulse.task.dto.TaskListItem;
import com.lifepulse.task.dto.TaskResponse;
import com.lifepulse.task.dto.TaskStatusRequest;
import com.lifepulse.task.dto.TaskUpdateRequest;
import com.lifepulse.task.service.TaskService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskController 切片测试（spec §6.1：鉴权 / 校验 / 越权路径 100% 覆盖）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>7 个 happy path（list/by-plan/get/create/update/patchStatus/delete）</li>
 *   <li>4 个 validation path（缺 title / status 越界 / page 越界 / size 越界）</li>
 *   <li>1 个 auth path（无 token → 401）</li>
 *   <li>1 个 cross-user path（service 抛 1003 → 403）</li>
 * </ul>
 *
 * <p>{@link UserContext} 是 static — 不能用 mock；通过 {@link SecurityContextHolder}
 * 注入 {@link UsernamePasswordAuthenticationToken}（{@code principal = userId}），
 * 由 Spring Security 在请求结束后清除。{@link JwtAuthFilter} mock no-op，
 * 让 MockMvc 走 Spring Security 标准链。
 */
@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class TaskControllerWebTest {

    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

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
        PageResponse<TaskListItem> page = PageResponse.of(List.of(), 0L, 1, 20);
        when(taskService.pageByUser(any(TaskFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/tasks").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<TaskFilter> cap = ArgumentCaptor.forClass(TaskFilter.class);
        verify(taskService).pageByUser(cap.capture());
        TaskFilter f = cap.getValue();
        assertThat(f.page()).isEqualTo(1);
        assertThat(f.size()).isEqualTo(20);
    }

    @Test
    void list_filtersApplied_statusPriorityTagDueRange_passesToService() throws Exception {
        PageResponse<TaskListItem> page = PageResponse.of(List.of(), 0L, 2, 10);
        when(taskService.pageByUser(any(TaskFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/tasks")
                        .param("status", "0")
                        .param("priority", "2")
                        .param("tag", "work")
                        .param("dueFrom", "2026-08-01")
                        .param("dueTo", "2026-08-31")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        ArgumentCaptor<TaskFilter> cap = ArgumentCaptor.forClass(TaskFilter.class);
        verify(taskService).pageByUser(cap.capture());
        TaskFilter f = cap.getValue();
        assertThat(f.status()).isEqualTo(0);
        assertThat(f.priority()).isEqualTo(2);
        assertThat(f.tag()).isEqualTo("work");
        assertThat(f.dueFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(f.dueTo()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(f.page()).isEqualTo(2);
        assertThat(f.size()).isEqualTo(10);
    }

    @Test
    void list_pageZero_returns1001() throws Exception {
        mvc.perform(get("/api/v1/tasks").param("page", "0").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION))
                .andExpect(jsonPath("$.message").value("page must be >= 1"));

        verify(taskService, never()).pageByUser(any());
    }

    @Test
    void list_sizeAboveMax_returns1001() throws Exception {
        mvc.perform(get("/api/v1/tasks").param("size", "999").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(taskService, never()).pageByUser(any());
    }

    // ---------- byPlan ----------

    @Test
    void byPlan_returnsList() throws Exception {
        TaskListItem item = new TaskListItem(1L, "p1", 0, 0, null, null);
        when(taskService.listByPlan(100L)).thenReturn(List.of(item));

        mvc.perform(get("/api/v1/tasks/by-plan/100").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value("p1"));

        verify(taskService).listByPlan(100L);
    }

    // ---------- get ----------

    @Test
    void get_ownerMatch_returnsDetail() throws Exception {
        TaskResponse res = new TaskResponse(11L, USER_ID, null, "买菜", 0, 0, null, null,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8)),
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8)));
        when(taskService.getById(11L)).thenReturn(res);

        mvc.perform(get("/api/v1/tasks/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("买菜"));
    }

    @Test
    void get_crossUser_returns1003_403() throws Exception {
        when(taskService.getById(11L))
                .thenThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该任务"));

        mvc.perform(get("/api/v1/tasks/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该任务"));
    }

    // ---------- create ----------

    @Test
    void create_valid_returns201WithDetail() throws Exception {
        LocalDate pastDue = LocalDate.of(2026, 7, 10);  // @PastOrPresent 要求 ≤ 今天 (2026-07-16)
        TaskResponse res = new TaskResponse(11L, USER_ID, null, "买菜", 0, 2,
                pastDue, "home",
                OffsetDateTime.now(), OffsetDateTime.now());
        when(taskService.create(any(TaskCreateRequest.class))).thenReturn(res);

        TaskCreateRequest req = new TaskCreateRequest("买菜", 2, pastDue, "home", null);

        mvc.perform(post("/api/v1/tasks")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("买菜"))
                .andExpect(jsonPath("$.data.priority").value(2));
    }

    @Test
    void create_blankTitle_returns1001() throws Exception {
        // 通过构造缺失 title 的 JSON（绕过 @NotBlank 校验）→ GlobalExceptionHandler 抛 1001
        String body = "{\"title\":\"\",\"priority\":2}";

        mvc.perform(post("/api/v1/tasks")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(taskService, never()).create(any());
    }

    @Test
    void create_missingTitle_returns1001() throws Exception {
        String body = "{\"priority\":2}";

        mvc.perform(post("/api/v1/tasks")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(taskService, never()).create(any());
    }

    // ---------- update ----------

    @Test
    void update_valid_returns200() throws Exception {
        TaskUpdateRequest req = new TaskUpdateRequest("新标题", null, null, null, null, null);

        mvc.perform(put("/api/v1/tasks/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).update(11L, req);
    }

    @Test
    void update_crossUser_returns1003() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该任务"))
                .when(taskService).update(anyLong(), any(TaskUpdateRequest.class));

        TaskUpdateRequest req = new TaskUpdateRequest("新", null, null, null, null, null);

        mvc.perform(put("/api/v1/tasks/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- patchStatus ----------

    @Test
    void patchStatus_valid_returns200() throws Exception {
        TaskStatusRequest req = new TaskStatusRequest(1);

        mvc.perform(patch("/api/v1/tasks/11/status")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).patchStatus(11L, 1);
    }

    @Test
    void patchStatus_statusOutOfRange_returns1001() throws Exception {
        // status=9 超出 @Min(0)@Max(2)
        String body = "{\"status\":9}";

        mvc.perform(patch("/api/v1/tasks/11/status")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(taskService, never()).patchStatus(anyLong(), anyInt());
    }

    @Test
    void patchStatus_noMatch_returns1003() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该任务"))
                .when(taskService).patchStatus(anyLong(), anyInt());

        TaskStatusRequest req = new TaskStatusRequest(1);

        mvc.perform(patch("/api/v1/tasks/11/status")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- delete ----------

    @Test
    void delete_ownerMatch_returns200() throws Exception {
        mvc.perform(delete("/api/v1/tasks/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).softDelete(11L);
    }

    @Test
    void delete_crossUser_returns1003() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该任务"))
                .when(taskService).softDelete(anyLong());

        mvc.perform(delete("/api/v1/tasks/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- auth ----------

    @Test
    void noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));

        verify(taskService, never()).pageByUser(any());
    }

    // ---------- assertj import alias (avoid unused warning) ----------
    private static org.assertj.core.api.AbstractAssert<?, ?> assertThat(Object o) {
        return org.assertj.core.api.Assertions.assertThat((Object) o);
    }
}