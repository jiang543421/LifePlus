package com.lifepulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO（spec §03，plan §3-B）。
 *
 * <p>密码规则：8–64 字符，至少含 1 个字母 + 1 个数字（spec §03 验证规则）。
 * CLAUDE.md §4.1：本 record 不可变；字段即组件，访问器为 {@code email()} 等。
 *
 * @param email    邮箱（必填，≤128 字符）
 * @param password 密码（必填，8–64 字符，字母+数字）
 * @param nickname 昵称（可选，≤32 字符）
 */
public record RegisterRequest(
        @Email @NotBlank @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "password must contain at least one letter and one digit")
        String password,
        @Size(max = 32) String nickname
) {
}