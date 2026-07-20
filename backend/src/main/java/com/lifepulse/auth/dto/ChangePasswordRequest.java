package com.lifepulse.auth.dto;

import com.lifepulse.auth.security.PasswordPolicy;
import com.lifepulse.auth.security.StrongPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求 DTO（Settings v1.1，issue 2026-07-18-settings-v1-1）。
 *
 * <p>密码规则与 {@link RegisterRequest} 一致：通过 {@link StrongPassword} 复用
 * {@link PasswordPolicy} 三维校验（长度 + 字符复杂度 + 弱密码字典）。
 *
 * <p>修改成功后服务端会撤销该用户所有现存 refresh token（spec §03 §5.3）。
 *
 * @param oldPassword 当前密码（必填，用于校验身份）
 * @param newPassword 新密码（必填，强密码策略）
 */
public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank @StrongPassword String newPassword
) {
}