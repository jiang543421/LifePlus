package com.lifepulse.auth.dto;

import com.lifepulse.auth.security.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO（spec §03，plan §3-B）。
 *
 * <p>密码规则：8–64 字符 + ≥1 字母 + ≥1 数字 + 不在常见弱密码字典，
 * 由 {@link StrongPassword} 约束聚合校验（issue 2026-07-18 HIGH-3）。
 *
 * <p>CLAUDE.md §4.1：本 record 不可变；字段即组件，访问器为 {@code email()} 等。
 *
 * @param email    邮箱（必填，≤128 字符）
 * @param password 密码（必填，强密码策略）
 * @param nickname 昵称（可选，≤32 字符）
 */
public record RegisterRequest(
        @Email @NotBlank @Size(max = 128) String email,
        @NotBlank @StrongPassword String password,
        @Size(max = 32) String nickname
) {
}