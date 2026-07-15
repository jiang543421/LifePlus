package com.lifepulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新令牌请求 DTO（spec §03，plan §3-B）。
 *
 * @param refreshToken 原 refresh token（必填；上限 2048 防滥用）
 */
public record RefreshRequest(
        @NotBlank @Size(max = 2048) String refreshToken
) {
}