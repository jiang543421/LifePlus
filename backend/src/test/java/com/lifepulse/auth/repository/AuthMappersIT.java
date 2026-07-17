package com.lifepulse.auth.repository;

import com.lifepulse.auth.entity.RefreshToken;
import com.lifepulse.auth.entity.User;
import com.lifepulse.it.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-002 + A-003 集成测试（spec §6.4）。
 *
 * <p>UserMapper 与 RefreshTokenMapper 合并到同一个 IT class：两个 mapper 共用同一个
 * Spring {@code @SpringBootTest} context 与 HikariPool，避免多 IT class 触发 Spring context
 * 缓存 miss 导致 HikariPool 重建、容器在端口被改 / 容器被 stop 后抢连接超时的链路问题。
 *
 * <p>对覆盖率与回归校验等价：所有断言保持 TDD（每个 mapper 2 个测试），且运行于同一
 * Flyway-managed 真实 MySQL + 本地 Redis（spec §6.4 强制）。
 *
 * <p>{@code @BeforeEach} 清表（Phase 2-C 修复）：容器 {@code withReuse=true} 在共享 JVM
 * 内累积旧测试数据；硬编码 {@code alice@example.com} / {@code sha256-hex-of-some-token} /
 * {@code once-only-hash} 在重跑时撞 UNIQUE 约束失败。{@link User} / {@link RefreshToken}
 * 的 UNIQUE 索引 ({@code uq_email} / {@code uq_token_hash}) 不含 {@code deleted} 列，
 * 单纯逻辑删除无法释放索引槽位；改用 {@link JdbcTemplate} 走原生 SQL 物理删除。
 */
class AuthMappersIT extends AbstractIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void wipeTables() {
        // 物理 DELETE（不走 ORM @TableLogic），UNIQUE 索引同时释放
        // 先 refresh_token（FK 引用 user.id 虽未声明，但保持顺序以防 schema 演进）
        jdbc.update("DELETE FROM t_refresh_token");
        jdbc.update("DELETE FROM t_user");
    }

    // ---------- UserMapper (A-002) ----------

    @Test
    void insert_and_findByEmail_returnsInsertedUser() {
        // Arrange
        User u = new User();
        u.setEmail("alice@example.com");
        u.setPasswordHash("$2a$10$dummy-hash-for-test-only");
        u.setNickname("alice");

        // Act
        userMapper.insert(u);
        User found = userMapper.findByEmail("alice@example.com");

        // Assert
        assertThat(u.getId()).as("ID 由 DB AUTO_INCREMENT 回填").isNotNull();
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(u.getId());
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
        assertThat(found.getPasswordHash()).startsWith("$2a$");
        assertThat(found.getNickname()).isEqualTo("alice");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getDeleted()).isEqualTo(0);
    }

    @Test
    void findByEmail_unknownEmail_returnsNull() {
        assertThat(userMapper.findByEmail("nobody@example.com")).isNull();
    }

    // ---------- RefreshTokenMapper (A-003) ----------

    @Test
    void insert_findByHash_revokeByHash_roundTrips() {
        // Arrange
        RefreshToken t = new RefreshToken();
        t.setUserId(1L);
        t.setTokenHash("sha256-hex-of-some-token");
        t.setExpiresAt(OffsetDateTime.now().plusDays(7));
        refreshTokenMapper.insert(t);

        // Act
        RefreshToken found = refreshTokenMapper.findByHash("sha256-hex-of-some-token");

        // Assert: round-trip + 初始未撤销
        assertThat(t.getId()).isNotNull();
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(t.getId());
        assertThat(found.getUserId()).isEqualTo(1L);
        assertThat(found.getTokenHash()).isEqualTo("sha256-hex-of-some-token");
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();

        // Act: 撤销
        OffsetDateTime now = OffsetDateTime.now();
        int rows = refreshTokenMapper.revokeByHash("sha256-hex-of-some-token", now);

        // Assert: 撤销成功
        assertThat(rows).isEqualTo(1);
        RefreshToken after = refreshTokenMapper.findByHash("sha256-hex-of-some-token");
        assertThat(after.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeByHash_idempotent_secondRevokeReturnsZero() {
        // 写入一条已撤销的 token
        RefreshToken t = new RefreshToken();
        t.setUserId(2L);
        t.setTokenHash("once-only-hash");
        t.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenMapper.insert(t);
        refreshTokenMapper.revokeByHash("once-only-hash", OffsetDateTime.now());

        // 重复 revoke 不应再改行（revoked_at IS NULL 条件失败）
        int again = refreshTokenMapper.revokeByHash("once-only-hash", OffsetDateTime.now());

        assertThat(again).isZero();
    }
}
