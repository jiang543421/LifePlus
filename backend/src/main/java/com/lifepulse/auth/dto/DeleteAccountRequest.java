package com.lifepulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注销账号请求 DTO（Settings v1.1，issue 2026-07-18-settings-v1-1）。
 *
 * <p>二次验证：要求客户端传当前密码，服务端用 BCrypt 校验通过后才执行软删除；
 * 防止 token 泄露 / 会话劫持场景下的恶意注销。
 *
 * @param password 当前密码（必填）
 */
public record DeleteAccountRequest(
        @NotBlank @Size(max = 128) String password
) {
}