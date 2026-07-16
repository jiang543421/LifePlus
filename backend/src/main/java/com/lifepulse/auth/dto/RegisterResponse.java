package com.lifepulse.auth.dto;

/**
 * 注册成功响应 DTO（spec §03 §5.1，plan §3-B）。
 *
 * <p>仅返回新用户主键；后续登录由前端再次调用 {@code POST /api/v1/auth/login}
 * 获取 token 对。
 *
 * @param userId 新用户主键（{@code t_user.id}）
 */
public record RegisterResponse(Long userId) {
}