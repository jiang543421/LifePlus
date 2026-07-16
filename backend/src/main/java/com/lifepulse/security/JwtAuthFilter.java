package com.lifepulse.security;

import com.lifepulse.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器（plan §3-B / §6.3）。
 *
 * <p>从 {@code Authorization: Bearer <token>} 提取 token，调
 * {@link JwtService#parse(String)} 校验。成功则把
 * {@code UsernamePasswordAuthenticationToken(principal=userId, ROLE_USER)}
 * 注入 {@link SecurityContextHolder} 并写 {@link UserContext}；失败则清空
 * SecurityContext（让后续 {@link JwtAuthEntryPoint} 走 401）但不阻断 chain。
 *
 * <p>无论成功失败、chain 是否抛异常，{@code finally} 必清 {@link UserContext}
 * 以避免 servlet 线程池复用导致跨请求 userId 泄漏（plan §9）。
 *
 * <p>异常→401 的 code 区分遵循 plan §6.3：filter 解析失败由 entry point
 * 统一返回 1002（{@code ERR_BAD_CREDENTIALS}）；1401（{@code ERR_REFRESH_INVALID}）
 * 当前不在 filter 路径上对外暴露，由前端按 status 401 + code 1002 走
 * 静默 refresh。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_USER = "ROLE_USER";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                applyToken(header.substring(BEARER_PREFIX.length()));
            }
            chain.doFilter(request, response);
        } finally {
            // 线程复用前必须清空，否则 userId 跨请求泄漏
            UserContext.clear();
        }
    }

    /**
     * 解析 token 并填充 SecurityContext 与 UserContext。失败一律
     * 清 SecurityContext，由 {@code JwtAuthEntryPoint} 走 401 信封。
     */
    private void applyToken(String token) {
        try {
            Claims claims = jwtService.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority(ROLE_USER)));
            SecurityContextHolder.getContext().setAuthentication(auth);
            UserContext.set(userId);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            UserContext.clear();
            // WARN 而非 ERROR：401 是预期分支（过期 / 伪造 / 错格式），不污染错误日志
            log.warn("jwt auth failed: {}", ex.getMessage());
        }
    }
}