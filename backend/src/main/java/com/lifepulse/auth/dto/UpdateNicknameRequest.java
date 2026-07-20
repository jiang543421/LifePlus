package com.lifepulse.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新昵称请求 DTO（Settings v1.1，issue 2026-07-18-settings-v1-1）。
 *
 * <p>昵称允许为空（{@code null} 或空字符串）：空值会在 service 层 trim 后落为 {@code null}，
 * DB 列 {@code t_user.nickname VARCHAR(64) NULL} 直接承接。
 *
 * @param nickname 昵称（可选，trim 后 ≤32 字符；空表示清空）
 */
public record UpdateNicknameRequest(
        @Size(max = 32) String nickname
) {
}