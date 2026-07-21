package com.lifepulse.diet.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.diet.dto.CreateDietRequest;
import com.lifepulse.diet.dto.DietFrequentItem;
import com.lifepulse.diet.dto.DietFilter;
import com.lifepulse.diet.dto.DietListItem;
import com.lifepulse.diet.dto.DietPageResponse;
import com.lifepulse.diet.dto.DietResponse;
import com.lifepulse.diet.dto.DietSummary;
import com.lifepulse.diet.dto.UpdateDietRequest;
import com.lifepulse.diet.service.DietService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
 * DietController 切片测试（spec 07-diet-design §8.2：鉴权 / 校验 / 越权路径 100% 覆盖）。
 *
 * <p>覆盖路径：
 * <ul>
 *   <li>7 个 happy path（list / get / create / update / delete / summary / frequent）</li>
 *   <li>5 个 validation path（create 缺 mealType / name / 营养、page 越界 / size 越界、summary date 缺）</li>
 *   <li>3 个 cross-user path（get / update / delete → service 抛 1003 → 403）</li>
 *   <li>1 个 write rate-limit path（service 抛 1006 → 429）</li>
 *   <li>1 个 auth path（无 token → 401 / 1002）</li>
 *   <li>1 个 filter 透传 path（query 参数 → service DietFilter）</li>
 *   <li>1 个 frequent 参数透传 path（from/to/limit → service）</li>
 * </ul>
 *
 * <p>{@link UserContext} 是 static — 不能用 mock；
 * 通过 {@link SecurityContextHolder} 注入 {@link UsernamePasswordAuthenticationToken}
 * （{@code principal = userId}）。{@link JwtAuthFilter} mock no-op，让 MockMvc 走 Spring Security 标准链。
 */
@WebMvcTest(DietController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class DietControllerWebTest {

    private static final long USER_ID = 42L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DietService dietService;

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

    private static DietResponse buildResponse() {
        return new DietResponse(
                11L, USER_ID, "LUNCH", "name",
                new BigDecimal("500.00"), new BigDecimal("20.00"),
                new BigDecimal("60.00"), new BigDecimal("15.00"),
                null, fixedTime(), fixedTime(), fixedTime());
    }

    // ---------- list ----------

    @Test
    void list_defaultParams_returnsPagedItems() throws Exception {
        DietListItem item = new DietListItem(
                1L, "LUNCH", "name", new BigDecimal("500.00"),
                new BigDecimal("20.00"), new BigDecimal("60.00"),
                new BigDecimal("15.00"), null, fixedTime());
        DietPageResponse page = DietPageResponse.of(List.of(item), 1L, 1, 20);
        when(dietService.list(any(DietFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/diets").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].mealType").value("LUNCH"));

        ArgumentCaptor<DietFilter> cap = ArgumentCaptor.forClass(DietFilter.class);
        verify(dietService).list(cap.capture());
        DietFilter f = cap.getValue();
        assertThat(f.mealType()).isNull();
        assertThat(f.from()).isNull();
        assertThat(f.to()).isNull();
        assertThat(f.page()).isEqualTo(1);
        assertThat(f.size()).isEqualTo(20);
    }

    @Test
    void list_filtersApplied_mealTypeFromToPageSize_passesToService() throws Exception {
        DietPageResponse page = DietPageResponse.of(List.of(), 0L, 2, 10);
        when(dietService.list(any(DietFilter.class))).thenReturn(page);

        mvc.perform(get("/api/v1/diets")
                        .param("mealType", "LUNCH")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-31T23:59:59Z")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        ArgumentCaptor<DietFilter> cap = ArgumentCaptor.forClass(DietFilter.class);
        verify(dietService).list(cap.capture());
        DietFilter f = cap.getValue();
        assertThat(f.mealType()).isEqualTo("LUNCH");
        assertThat(f.from()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        assertThat(f.to()).isEqualTo(OffsetDateTime.parse("2026-07-31T23:59:59Z"));
        assertThat(f.page()).isEqualTo(2);
        assertThat(f.size()).isEqualTo(10);
    }

    @Test
    void list_pageZero_returns1001() throws Exception {
        mvc.perform(get("/api/v1/diets").param("page", "0").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION))
                .andExpect(jsonPath("$.message").value("page must be >= 1"));

        verify(dietService, never()).list(any(DietFilter.class));
    }

    @Test
    void list_sizeAboveMax_returns1001() throws Exception {
        mvc.perform(get("/api/v1/diets").param("size", "999").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(dietService, never()).list(any(DietFilter.class));
    }

    // ---------- get ----------

    @Test
    void get_ownerMatch_returnsDetail() throws Exception {
        when(dietService.getById(11L)).thenReturn(buildResponse());

        mvc.perform(get("/api/v1/diets/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.data.userId").value(USER_ID));
    }

    @Test
    void get_crossUser_returns1003_403() throws Exception {
        when(dietService.getById(11L))
                .thenThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该饮食"));

        mvc.perform(get("/api/v1/diets/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER))
                .andExpect(jsonPath("$.message").value("无权操作该饮食"));
    }

    // ---------- create ----------

    @Test
    void create_valid_returns201WithDetail() throws Exception {
        when(dietService.create(any(CreateDietRequest.class))).thenReturn(buildResponse());

        CreateDietRequest req = new CreateDietRequest(
                "LUNCH", "name", new BigDecimal("500.00"),
                new BigDecimal("20.00"), new BigDecimal("60.00"),
                new BigDecimal("15.00"), null, fixedTime());

        mvc.perform(post("/api/v1/diets")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.mealType").value("LUNCH"));
    }

    @Test
    void create_missingOccurredAt_returns400() throws Exception {
        // 缺 occurredAt → @NotNull → MethodArgumentNotValidException → 1001
        String body = """
                {"mealType":"LUNCH","name":"x","kcal":10,"proteinG":1,"carbG":1,"fatG":1}
                """;

        mvc.perform(post("/api/v1/diets")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(dietService, never()).create(any(CreateDietRequest.class));
    }

    @Test
    void create_blankMealType_returns400() throws Exception {
        String body = """
                {"mealType":"","name":"x","kcal":10,"proteinG":1,"carbG":1,"fatG":1,"occurredAt":"2026-07-15T12:00:00Z"}
                """;

        mvc.perform(post("/api/v1/diets")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(dietService, never()).create(any(CreateDietRequest.class));
    }

    @Test
    void create_negativeKcal_returns400() throws Exception {
        // -1 kcal → @DecimalMin("0.0") → MethodArgumentNotValidException → 1001
        String body = """
                {"mealType":"LUNCH","name":"x","kcal":-1,"proteinG":1,"carbG":1,"fatG":1,"occurredAt":"2026-07-15T12:00:00Z"}
                """;

        mvc.perform(post("/api/v1/diets")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));

        verify(dietService, never()).create(any(CreateDietRequest.class));
    }

    @Test
    void create_rateLimited_returns1006_429() throws Exception {
        when(dietService.create(any(CreateDietRequest.class)))
                .thenThrow(new BusinessException(AuthConstants.ERR_LOGIN_RATE_LIMIT, "操作过于频繁，请稍后再试"));

        CreateDietRequest req = new CreateDietRequest(
                "LUNCH", "name", new BigDecimal("500.00"),
                new BigDecimal("20.00"), new BigDecimal("60.00"),
                new BigDecimal("15.00"), null, fixedTime());

        mvc.perform(post("/api/v1/diets")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_LOGIN_RATE_LIMIT));
    }

    // ---------- update ----------

    @Test
    void update_partialFields_returns200() throws Exception {
        UpdateDietRequest req = new UpdateDietRequest(
                "DINNER", null, null, null, null, null, null, null);

        mvc.perform(patch("/api/v1/diets/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(dietService).update(11L, req);
    }

    @Test
    void update_crossUser_returns1003_403() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该饮食"))
                .when(dietService).update(anyLong(), any(UpdateDietRequest.class));

        UpdateDietRequest req = new UpdateDietRequest(
                "DINNER", null, null, null, null, null, null, null);

        mvc.perform(patch("/api/v1/diets/11")
                        .with(authentication(authToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- delete ----------

    @Test
    void delete_ownerMatch_returns200() throws Exception {
        mvc.perform(delete("/api/v1/diets/11").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(dietService).softDelete(11L);
    }

    @Test
    void delete_crossUser_returns1003_403() throws Exception {
        doThrow(new BusinessException(AuthConstants.ERR_CROSS_USER, "无权操作该饮食"))
                .when(dietService).softDelete(anyLong());

        mvc.perform(delete("/api/v1/diets/11").with(authentication(authToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_CROSS_USER));
    }

    // ---------- summary ----------

    @Test
    void summary_validDate_returns200() throws Exception {
        DietSummary s = new DietSummary(
                new BigDecimal("1650.00"), new BigDecimal("55.00"),
                new BigDecimal("220.00"), new BigDecimal("50.00"),
                new BigDecimal("120.00"), new BigDecimal("-80.00"));
        when(dietService.summary(any())).thenReturn(s);

        mvc.perform(get("/api/v1/diets/summary")
                        .param("date", "2026-07-15")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kcal").value(1650.00))
                .andExpect(jsonPath("$.data.kcalDeltaYesterday").value(120.00));
    }

    @Test
    void summary_missingDate_returns1001() throws Exception {
        mvc.perform(get("/api/v1/diets/summary").with(authentication(authToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION))
                .andExpect(jsonPath("$.message").value("date 不能为空"));

        verify(dietService, never()).summary(any(LocalDate.class));
    }

    // ---------- frequent ----------

    @Test
    void frequent_defaultParams_returns200() throws Exception {
        when(dietService.frequent(any(), any(), any())).thenReturn(List.of(
                new DietFrequentItem("米饭", new BigDecimal("230.00"),
                        new BigDecimal("5.00"), new BigDecimal("50.00"),
                        new BigDecimal("1.00"), 12)));

        mvc.perform(get("/api/v1/diets/frequent").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("米饭"))
                .andExpect(jsonPath("$.data[0].hitCount").value(12));
    }

    @Test
    void frequent_fromToLimit_passesToService() throws Exception {
        when(dietService.frequent(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/diets/frequent")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-07-01T00:00:00Z")
                        .param("limit", "20")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        verify(dietService).frequent(
                eq(OffsetDateTime.parse("2026-06-01T00:00:00Z")),
                eq(OffsetDateTime.parse("2026-07-01T00:00:00Z")),
                eq(20));
    }

    // ---------- auth ----------

    @Test
    void noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/diets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));

        verify(dietService, never()).list(any(DietFilter.class));
    }
}