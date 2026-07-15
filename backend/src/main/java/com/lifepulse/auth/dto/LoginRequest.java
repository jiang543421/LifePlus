package com.lifepulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求 DTO（spec §03，plan §3-B）。
 *
 * <p>登录端点对密码强度无校验（用户可能用旧密码）；此处仅校验非空与长度上限，
 * 错误密码的语义校验在 {@code AuthService.login}（1002）执行。
 *
 * @param email    邮箱（必填）
 * @param password 密码（必填，≤128 字符避免暴力输入）
 */
public record LoginRequest(
        @Email @NotBlank @Size(max = 128) String email,
        @NotBlank @Size(max = 128) String password
) {
}