package com.lifepulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 退出登录请求 DTO（spec §03，plan §3-B）。
 *
 * @param refreshToken 要撤销的 refresh token（必填）
 */
public record LogoutRequest(
        @NotBlank @Size(max = 2048) String refreshToken
) {
}