package com.lifepulse.security;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.service.JwtService;
import com.lifepulse.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 1.3-B · {@link JwtAuthFilter} 单测（plan §3-B / §4）。
 *
 * <p>5 case：valid token → auth + userContext；missing header → 不解析；
 * non-Bearer 前缀 → 不解析；invalid token → 清 auth + chain 继续；chain 抛异常
 * 时 finally 仍清 userContext。
 *
 * <p>直接 {@code new JwtAuthFilter(mock JwtService)}，避开 Spring 上下文
 * （与 1.3-A AuthControllerWebTest 同款策略）。SecurityContextHolder 与
 * UserContext 在 {@code @AfterEach} 双清。
 */
class JwtAuthFilterTest {

    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthFilter(jwtService);
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @Test
    void doFilter_validToken_setsAuthAndUserContextDuringChain() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(jwtService.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // 在 chain 执行瞬间抓 UserContext 快照（filter finally 会清）
        Long[] capturedDuringChain = new Long[1];
        FilterChain chain = (r, res) -> capturedDuringChain[0] = UserContext.current();

        filter.doFilter(req, resp, chain);

        assertThat(capturedDuringChain[0]).isEqualTo(42L);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
        // filter finally 已清
        assertThat(UserContext.current()).isNull();
    }

    @Test
    void doFilter_missingHeader_chainProceedsWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_nonBearerPrefix_doesNotParse() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_invalidToken_clearsAuthAndChainProceeds() throws Exception {
        when(jwtService.parse("bad.token"))
                .thenThrow(new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "bad"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_chainThrows_finallyStillClearsUserContext() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(jwtService.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (r, res) -> {
            throw new ServletException("boom");
        };

        try {
            filter.doFilter(req, resp, chain);
        } catch (ServletException expected) {
            // 预期：chain 抛异常向上传播
        }

        assertThat(UserContext.current()).isNull();
    }
}