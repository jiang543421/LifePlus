package com.lifepulse.auth.dto;

import com.lifepulse.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.3-A · {@link UserResponse} record 单测（plan §3 / §8）。
 *
 * <p>最小覆盖：实体 → DTO 字段透传。{@code GET /users/me} 端到端测试在 1.3-B
 * 的 {@code UserControllerWebTest}。
 */
class UserResponseTest {

    @Test
    void from_userEntity_extractsAllFields() {
        // Arrange
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 16, 9, 30, 0, 0, ZoneOffset.ofHours(8));
        User user = new User();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setNickname("Alice");
        user.setCreatedAt(now);

        // Act
        UserResponse r = UserResponse.from(user);

        // Assert
        assertThat(r.id()).isEqualTo(42L);
        assertThat(r.email()).isEqualTo("alice@example.com");
        assertThat(r.nickname()).isEqualTo("Alice");
        assertThat(r.createdAt()).isEqualTo(now);
    }

    @Test
    void from_userEntityWithNullNickname_carriesNullThrough() {
        User user = new User();
        user.setId(7L);
        user.setEmail("bob@example.com");
        user.setNickname(null);
        user.setCreatedAt(OffsetDateTime.now());

        UserResponse r = UserResponse.from(user);

        assertThat(r.nickname()).isNull();
    }
}
