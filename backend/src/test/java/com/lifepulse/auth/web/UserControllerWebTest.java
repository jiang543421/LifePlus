package com.lifepulse.auth.web;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.auth.service.JwtService;
import com.lifepulse.auth.service.UserService;
import com.lifepulse.common.exception.GlobalExceptionHandler;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1.3-B · {@link UserController} 切片测试（plan §3-B / §4）。
 *
 * <p>2 case：
 * <ul>
 *   <li>不带 token → 401（{@code SecurityFilterChain} 拒）</li>
 *   <li>带有效 token → 200 UserResponse</li>
 * </ul>
 *
 * <p>{@code @WebMvcTest} 默认加载 Spring Security；{@code @Import(SecurityConfig.class)}
 * 覆盖默认 chain；{@link JwtAuthFilter} / {@link JwtAuthEntryPoint} / {@link JwtService} /
 * {@link UserMapper} 均 mock 避免 mybatis-plus 上下文依赖（1.3-D 才统一修）。
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class UserControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void bypassFilter() throws Exception {
        // mock JwtAuthFilter.doFilter 默认 no-op 会截断请求；显式转发到 chain
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

    @Test
    void getMe_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS))
                .andExpect(jsonPath("$.message").value("未登录或凭证失效"));
    }

    @Test
    void getMe_validToken_returnsUserResponse() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setNickname("Alice");
        user.setCreatedAt(OffsetDateTime.of(2026, 7, 16, 10, 0, 0, 0, ZoneOffset.ofHours(8)));
        when(userMapper.selectById(anyLong())).thenReturn(user);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mvc.perform(get("/api/v1/users/me").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("Alice"));
    }
}