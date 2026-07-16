package com.lifepulse.auth.web;

import com.lifepulse.auth.dto.AuthResponse;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.LogoutRequest;
import com.lifepulse.auth.dto.RefreshRequest;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.auth.dto.RegisterResponse;
import com.lifepulse.auth.service.AuthService;
import com.lifepulse.common.web.MyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（spec §03 §5.1，plan §3-A A-008）。
 *
 * <p>4 个端点，全部委托 {@link AuthService}：
 * <ul>
 *   <li>{@code POST /api/v1/auth/register} → 201 {@link RegisterResponse}</li>
 *   <li>{@code POST /api/v1/auth/login} → 200 {@link AuthResponse}</li>
 *   <li>{@code POST /api/v1/auth/refresh} → 200 {@link AuthResponse}</li>
 *   <li>{@code POST /api/v1/auth/logout} → 200 {@code data=null}</li>
 * </ul>
 *
 * <p>所有 DTO 均标 {@code @Valid}（CLAUDE.md §4.6）；错误统一经
 * {@code GlobalExceptionHandler} 转 {@code MyResponse} 信封。{@code register /
 * login / refresh} 透传客户端 IP 给限流 key（{@code AuthConstants.*_RL_KEY_PREFIX}）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<MyResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest request) {
        Long userId = authService.register(req, clientIp(request));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MyResponse.ok(new RegisterResponse(userId)));
    }

    @PostMapping("/login")
    public MyResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request) {
        return MyResponse.ok(authService.login(req, clientIp(request)));
    }

    @PostMapping("/refresh")
    public MyResponse<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest req,
            HttpServletRequest request) {
        return MyResponse.ok(authService.refresh(req, clientIp(request)));
    }

    @PostMapping("/logout")
    public MyResponse<Void> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req);
        return MyResponse.ok(null);
    }

    /**
     * 客户端 IP 提取（CLAUDE.md §7.4 反滥用前提）。优先 {@code X-Forwarded-For} 第一跳，
     * 回落 {@code remoteAddr}。生产环境若使用 SLB/反代，{@code application.yml} 已开启
     * {@code forward-headers-strategy: native}（1.0 已配）。
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}