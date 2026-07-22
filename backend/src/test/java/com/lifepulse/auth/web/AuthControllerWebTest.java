package com.lifepulse.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.auth.dto.AuthResponse;
import com.lifepulse.auth.service.AuthService;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1.3-A · {@link AuthController} 切片测试（plan §3-A / §8）。
 *
 * <p>目标（spec §03 §5.1 + §03 §3）：
 * <ul>
 *   <li>4 端点 happy path：201/200 响应 + 信封 code=0</li>
 *   <li>参数校验：1001 → 400（email 格式 / password 长度 / 字母数字 / blank）</li>
 *   <li>业务异常：1005 → 409、1006 → 429、1002 → 401、1401 → 401</li>
 *   <li>幂等 logout：未知 token 仍 200</li>
 *   <li>{@code X-Forwarded-For} 透传到 service</li>
 * </ul>
 *
 * <p>采用 {@code MockMvcBuilders.standaloneSetup(...)} 避开 Spring bean 扫描对 nested
 * 控制器的不可见问题，与 {@code GlobalExceptionHandlerTest} 同款。AuthService
 * 由 Mockito 替换；Spring Security 由 standalone 默认无 filter chain 隐式避开。
 */
class AuthControllerWebTest {

    private MockMvc mvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        // standaloneSetup 默认 Jackson 不自动发现 JSR310 模块；显式注册以序列化 Duration
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter conv = new MappingJackson2HttpMessageConverter(om);

        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(conv)
                .build();
    }

    // ---------- register ----------

    @Test
    void register_validRequest_returns201WithUserId() throws Exception {
        when(authService.register(any(), anyString())).thenReturn(42L);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"pass1234","nickname":"Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.userId").value(42));
    }

    @Test
    void register_invalidEmail_returns400WithCode1001() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"pass1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    @Test
    void register_shortPassword_returns400WithCode1001() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    @Test
    void register_passwordWithoutDigit_returns400WithCode1001() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"onlyletters\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    @Test
    void register_weakPassword_returns400WithCode1001() throws Exception {
        // HIGH-3：弱密码字典命中触发 1001。与 register_passwordWithoutDigit
        // 区别：长度与字符复杂度均合法，仅字典命中 → 验证弱密码维度独立工作。
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    @Test
    void register_emailTaken_returns409WithCode1005() throws Exception {
        when(authService.register(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_TAKEN, "email taken"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_TAKEN));
    }

    @Test
    void register_rateLimited_returns429WithCode1006() throws Exception {
        when(authService.register(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.LOGIN_RATE_LIMIT, "rate"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT));
    }

    @Test
    void register_xForwardedForFirstHopPropagatedToService() throws Exception {
        when(authService.register(any(), eq("203.0.113.7"))).thenReturn(99L);

        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(99));
    }

    // ---------- login ----------

    @Test
    void login_validRequest_returns200WithTokens() throws Exception {
        AuthResponse resp = new AuthResponse("access.jwt", "refresh.raw", Duration.ofHours(1));
        when(authService.login(any(), anyString())).thenReturn(resp);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.raw"));
    }

    @Test
    void login_invalidCredentials_returns401WithCode1002() throws Exception {
        when(authService.login(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.BAD_CREDENTIALS, "bad"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS));
    }

    @Test
    void login_rateLimited_returns429WithCode1006() throws Exception {
        when(authService.login(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.LOGIN_RATE_LIMIT, "rl"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_RATE_LIMIT));
    }

    // ---------- refresh ----------

    @Test
    void refresh_validToken_returnsNewTokens() throws Exception {
        AuthResponse resp = new AuthResponse("new.access", "new.refresh", Duration.ofHours(1));
        when(authService.refresh(any(), anyString())).thenReturn(resp);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old.refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("new.access"));
    }

    @Test
    void refresh_invalidToken_returns401WithCode1401() throws Exception {
        when(authService.refresh(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.REFRESH_INVALID, "x"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_INVALID));
    }

    @Test
    void refresh_blankToken_returns400WithCode1001() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }

    // ---------- logout ----------

    @Test
    void logout_validRequest_returns200() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some.token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logout_unknownToken_idempotentlyReturns200() throws Exception {
        // AuthService.logout 对未持久化的 refresh 幂等返回；controller 不应感知差异
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"never-issued\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logout_blankRefreshToken_returns400WithCode1001() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION));
    }
}
