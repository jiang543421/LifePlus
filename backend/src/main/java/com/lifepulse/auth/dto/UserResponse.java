package com.lifepulse.auth.dto;

import com.lifepulse.auth.entity.User;

import java.time.OffsetDateTime;

/**
 * {@code GET /api/v1/users/me} 响应 DTO（spec §03 §5.2；plan §3）。
 *
 * <p>字段集与 1.1 已落地 {@link User} 实体一致，但只暴露 {@code email / nickname}
 * 等可公开信息；密码哈希不外泄。{@link OffsetDateTime} 时间以 ISO-8601 序列化，
 * 配合 DB 列 {@code DATETIME} 与 dayjs TZ {@code Asia/Shanghai}。
 *
 * @param id        用户主键
 * @param email     邮箱
 * @param nickname  昵称（可空；注册时可省略）
 * @param createdAt 注册时间
 */
public record UserResponse(
        Long id,
        String email,
        String nickname,
        OffsetDateTime createdAt
) {

    /** 实体 → DTO 的静态工厂（CLAUDE.md §4.1 不可变性：返回新 record，不修改实体）。 */
    public static UserResponse from(User entity) {
        return new UserResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getNickname(),
                entity.getCreatedAt()
        );
    }
}
