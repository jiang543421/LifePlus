package com.lifepulse.auth.dto;

import java.time.Duration;

/**
 * 认证响应 DTO（spec §03，plan §3-B）。
 *
 * <p>登录与刷新接口统一返回：access token、refresh token、access token TTL（秒）。
 *
 * @param accessToken  JWT access token
 * @param refreshToken JWT refresh token（SHA-256 后入库）
 * @param expiresIn    access token 剩余秒数（前端据此安排自动续期）
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        Duration expiresIn
) {

    /** 静态工厂：方便 AuthService 构造。 */
    public static AuthResponse of(String accessToken, String refreshToken, Duration expiresIn) {
        return new AuthResponse(accessToken, refreshToken, expiresIn);
    }
}