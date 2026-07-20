package com.lifepulse.expense.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.dto.CategoryItem;
import com.lifepulse.expense.dto.CreateExpenseRequest;
import com.lifepulse.expense.dto.ExpenseFilter;
import com.lifepulse.expense.dto.ExpenseListItem;
import com.lifepulse.expense.dto.ExpenseResponse;
import com.lifepulse.expense.dto.ExpenseSummaryResponse;
import com.lifepulse.expense.dto.UpdateExpenseRequest;
import com.lifepulse.expense.service.ExpenseService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExpenseController 切片测试（spec §6.1：鉴权 / 校验 / 越权路径 100% 覆盖）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>7 个 happy path（list / get / create / update / delete / summary / categories）</li>
 *   <li>5 个 validation path（create 缺 amount / 缺 category、page 越界 / size 越界、summary month 越界）</li>
 *   <li>3 个 cross-user path（get / update / delete → service 抛 1003 → 403）</li>
 *   <li>1 个 write rate-limit path（service 抛 1006 → 429）</li>
 *   <li>1 个 auth path（无 token → 401 / 1002）</li>
 *   <li>1 个 filter 透传 path（query 参数 → service ExpenseFilter）</li>
 * </ul>
 *
 * <p>{@link UserContext} 是 static — 不能用 mock；通过 {@link SecurityContextHolder}
 * 注入 {@link UsernamePasswordAuthenticationToken}（{@code principal = userId}），
 * 由 Spring Security 在请求结束后清除。{@link JwtAuthFilter} mock no-op，
 * 让 MockMvc 走 Spring Security 标准链。
 */
@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class ExpenseControllerWebTest {

    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseService expenseService;

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

    private static OffsetDateTime fixedTime() {
        return OffsetDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    // ---------- list ----------

    @Test
    void list_defaultParams_returnsPagedItems() throws Exception {
        ExpenseListItem item = new ExpenseListItem(1L, new BigDecimal("10.00"),
                ExpenseCategory.MEAL, "午餐", fixedTime());
        PageResponse<ExpenseListItem> page = PageResponse.of(List.of(item), 1L, 1, 20);
        when(expenseService.list(any(ExpenseFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/expenses").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].category").value("MEAL"));

        ArgumentCaptor<ExpenseFilter> cap = ArgumentCaptor.forClass(ExpenseFilter.class);
        verify(expenseService).list(cap.capture());
        ExpenseFilter f = cap.getValue();
        assertThat(f.category()).isNull();
        assertThat(f.from()).isNull();
        assertThat(f.to()).isNull();
        assertThat(f.page()).isEqualTo(1);
        assertThat(f.size()).isEqualTo(20);
    }

    @Test
    void list_filtersApplied_categoryFromToPageSize_passesToService() throws Exception {
        PageResponse<ExpenseListItem> page = PageResponse.of(List.of(), 0L, 2, 10);
        when(expenseService.list(any(ExpenseFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/expenses")
                        .param("category", "TRANSPORT")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-31T23:59:59Z")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        ArgumentCaptor<ExpenseFilter> cap = ArgumentCaptor.forClass(ExpenseFilter.class);
        verify(expenseService).list(cap.capture());
        ExpenseFilter f = cap.getValue();
        assertThat(f.category()).isEqualTo(ExpenseCategory.TRANSPORT);
        assertThat(f.from()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        assertThat(f.to()).isEqualTo(OffsetDateTime.parse("2026-07-31T23:59:59Z"));
        assertThat(f.page()).isEqualTo(2);
        assertThat(f.size()).isEqualTo(10);
    }

    @Test
    void list_pageZero_returns1001() throws Exception {
        mvc.perform(get("/api/v1/expenses").param("page", "0").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION))
                .andExpect(jsonPath("$.message").value("page must be >= 1"));

        verify(expenseService, never()).list(any(ExpenseFilter.class));
    }

    @Test
    void list_sizeAboveMax_returns1001() throws Exception {
        mvc.perform(get("/api/v1/expenses").param("size", "999").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(expenseService, never()).list(any(ExpenseFilter.class));
    }

    // ---------- get ----------

    @Test
    void get_ownerMatch_returnsDetail() throws Exception {
        ExpenseResponse res = new ExpenseResponse(11L, USER_ID,
                new BigDecimal("10.00"), ExpenseCategory.MEAL, "午餐",
                fixedTime(), fixedTime(), fixedTime());
        when(expenseService.getById(11L)).thenReturn(res);

        mvc.perform(get("/api/v1/expenses/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.category").value("MEAL"))
                .andExpect(jsonPath("$.data.userId").value(USER_ID));
    }

    @Test
    void get_crossUser_returns1003_403() throws Exception {
        when(expenseService.getById(11L))
                .thenThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该消费"));

        mvc.perform(get("/api/v1/expenses/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该消费"));
    }

    // ---------- create ----------

    @Test
    void create_valid_returns201WithDetail() throws Exception {
        ExpenseResponse res = new ExpenseResponse(11L, USER_ID,
                new BigDecimal("35.00"), ExpenseCategory.MEAL, "午餐",
                fixedTime(), fixedTime(), fixedTime());
        when(expenseService.create(any(CreateExpenseRequest.class))).thenReturn(res);

        CreateExpenseRequest req = new CreateExpenseRequest(
                new BigDecimal("35.00"), ExpenseCategory.MEAL, "午餐", fixedTime());

        mvc.perform(post("/api/v1/expenses")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.category").value("MEAL"));
    }

    @Test
    void create_zeroAmount_returns1001() throws Exception {
        // amount=0 触发 @DecimalMin(0.01) → MethodArgumentNotValidException → 1001
        String body = """
                {"amount":0,"category":"MEAL","note":"x","occurredAt":"2026-07-15T12:00:00Z"}
                """;

        mvc.perform(post("/api/v1/expenses")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(expenseService, never()).create(any(CreateExpenseRequest.class));
    }

    @Test
    void create_missingCategory_returns400() throws Exception {
        // 缺 category → @NotNull → MethodArgumentNotValidException → 1001
        String body = """
                {"amount":10.00,"note":"x","occurredAt":"2026-07-15T12:00:00Z"}
                """;

        mvc.perform(post("/api/v1/expenses")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(expenseService, never()).create(any(CreateExpenseRequest.class));
    }

    @Test
    void create_rateLimited_returns1006_429() throws Exception {
        // 复用 LOGIN_RATE_LIMIT 1006（与 ExpenseService.requireWriteRateLimit 一致）。
        when(expenseService.create(any(CreateExpenseRequest.class)))
                .thenThrow(new BusinessException(AuthConstants.ERR_LOGIN_RATE_LIMIT, "操作过于频繁，请稍后再试"));

        CreateExpenseRequest req = new CreateExpenseRequest(
                new BigDecimal("35.00"), ExpenseCategory.MEAL, "午餐", fixedTime());

        mvc.perform(post("/api/v1/expenses")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_LOGIN_RATE_LIMIT));
    }

    // ---------- update ----------

    @Test
    void update_partialFields_returns200() throws Exception {
        UpdateExpenseRequest req = new UpdateExpenseRequest(
                null, ExpenseCategory.TRANSPORT, null, null);

        mvc.perform(patch("/api/v1/expenses/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(expenseService).update(11L, req);
    }

    @Test
    void update_crossUser_returns1003_403() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该消费"))
                .when(expenseService).update(anyLong(), any(UpdateExpenseRequest.class));

        UpdateExpenseRequest req = new UpdateExpenseRequest(
                null, ExpenseCategory.TRANSPORT, null, null);

        mvc.perform(patch("/api/v1/expenses/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- delete ----------

    @Test
    void delete_ownerMatch_returns200() throws Exception {
        mvc.perform(delete("/api/v1/expenses/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(expenseService).softDelete(11L);
    }

    @Test
    void delete_crossUser_returns1003_403() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该消费"))
                .when(expenseService).softDelete(anyLong());

        mvc.perform(delete("/api/v1/expenses/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- summary ----------

    @Test
    void summary_happy_returns200() throws Exception {
        ExpenseSummaryResponse s = new ExpenseSummaryResponse(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1),
                Map.of("MEAL", new BigDecimal("100.00")),
                new BigDecimal("100.00"));
        when(expenseService.summary(2026, 7)).thenReturn(s);

        mvc.perform(get("/api/v1/expenses/summary")
                        .param("year", "2026")
                        .param("month", "7")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(100.00))
                .andExpect(jsonPath("$.data.amountByCategory.MEAL").value(100.00));
    }

    @Test
    void summary_badMonth_returns1001() throws Exception {
        mvc.perform(get("/api/v1/expenses/summary")
                        .param("year", "2026")
                        .param("month", "13")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION))
                .andExpect(jsonPath("$.message").value("year/month 非法"));

        verify(expenseService, never()).summary(anyInt(), anyInt());
    }

    @Test
    void summary_badYear_returns1001() throws Exception {
        mvc.perform(get("/api/v1/expenses/summary")
                        .param("year", "100")
                        .param("month", "7")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(expenseService, never()).summary(anyInt(), anyInt());
    }

    // ---------- categories ----------

    @Test
    void categories_returns5() throws Exception {
        when(expenseService.categories()).thenReturn(List.of(
                new CategoryItem("MEAL", "餐饮"),
                new CategoryItem("SHOPPING", "购物"),
                new CategoryItem("TRANSPORT", "交通"),
                new CategoryItem("SUBSCRIPTION", "订阅"),
                new CategoryItem("OTHER", "其他")));

        mvc.perform(get("/api/v1/expenses/categories").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].code").value("MEAL"));
    }

    // ---------- auth ----------

    @Test
    void noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/expenses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));

        verify(expenseService, never()).list(any(ExpenseFilter.class));
    }
}