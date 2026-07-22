package com.lifepulse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 鉴权入口点（plan §3-B / §6.3）。
 *
 * <p>当 Spring Security 拒未认证请求时统一输出 {@code MyResponse.error(1002, "未登录或凭证失效")}
 * + status 401 JSON。1002 由前端拦截器走静默 refresh 分支（spec §3）。
 *
 * <p>{@code 1401}（refresh 失效）由 {@code AuthService.refresh} 直接抛出，
 * 不经本入口；filter 解析失败也由本入口统一返回 1002，简化前端分支
 * （plan §6.3 「1002 vs 1401 区分」MVP1 暂合并为 1002）。
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper om;

    public JwtAuthEntryPoint() {
        // entry point 是 @Component 单例，初始化 ObjectMapper 一次性注册 JSR310
        this.om = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        MyResponse<Void> body = MyResponse.error(
                ErrorCode.BAD_CREDENTIALS, "未登录或凭证失效");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        om.writeValue(response.getWriter(), body);
    }
}
