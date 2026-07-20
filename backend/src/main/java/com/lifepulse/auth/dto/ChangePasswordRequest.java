package com.lifepulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求 DTO（Settings v1.1，issue 2026-07-18-settings-v1-1）。
 *
 * <p>密码规则与 {@link RegisterRequest} 一致：8–64 字符，至少含 1 个字母 + 1 个数字。
 * 修改成功后服务端会撤销该用户所有现存 refresh token（spec §03 §5.3）。
 *
 * @param oldPassword 当前密码（必填，用于校验身份）
 * @param newPassword 新密码（必填，复用注册时的强度规则）
 */
public record ChangePasswordRequest(
        @NotBlank @Size(min = 8, max = 64) String oldPassword,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "password must contain at least one letter and one digit")
        String newPassword
) {
}