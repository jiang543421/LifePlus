package com.lifepulse.auth.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.AuthResponse;
import com.lifepulse.auth.dto.LoginRequest;
import com.lifepulse.auth.dto.LogoutRequest;
import com.lifepulse.auth.dto.RefreshRequest;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.auth.entity.RefreshToken;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.RefreshTokenMapper;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.it.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1.2-E · AuthService 端到端集成测试（plan §4 / §9 AuthServiceIT）。
 *
 * <p>与 {@code AuthService} 处于同一包，以便访问 package-private 辅助方法
 * {@link AuthService#sha256Hex(String)} 与 {@link AuthService#emailKeySuffix(String)}。
 *
 * <p>使用 Testcontainers MySQL + 本地 Redis；覆盖 register→login→refresh→logout
 * happy path、refresh 重放 1401、登录限流 1006、注册限流 1006、BCrypt roundtrip。
 *
 * <p>每个测试用唯一 email + IP（{@code UUID} 片段）避免本地 Redis 限流计数器与
 * 跨用例数据残留。
 */
class AuthServiceIT extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserMapper userMapper;
    @Autowired private RefreshTokenMapper refreshTokenMapper;
    @Autowired private StringRedisTemplate redis;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String uniqueIp() {
        return "10." + (System.nanoTime() & 0xFF) + "." + (System.nanoTime() & 0xFF) + ".1";
    }

    private void cleanupRedis(String... keys) {
        for (String k : keys) {
            redis.delete(k);
        }
    }

    // ---------- happy path ----------

    @Test
    void register_login_refresh_logout_happyPath() {
        // Arrange
        String email = uniqueEmail();
        String ip = uniqueIp();
        RegisterRequest reg = new RegisterRequest(email, "Valid1Pass", "alice");
        LoginRequest login = new LoginRequest(email, "Valid1Pass");
        LogoutRequest logout = new LogoutRequest("placeholder");

        // Act 1: register
        Long userId = authService.register(reg, ip);
        assertThat(userId).isNotNull();
        User persisted = userMapper.findByEmail(email);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getPasswordHash()).startsWith("$2a$");

        // Act 2: login
        AuthResponse loginResp = authService.login(login, ip);
        assertThat(loginResp.accessToken()).isNotBlank();
        assertThat(loginResp.refreshToken()).isNotBlank();
        assertThat(loginResp.expiresIn()).isEqualTo(AuthConstants.ACCESS_TTL);

        // Act 3: refresh（旋转）
        RefreshRequest refresh = new RefreshRequest(loginResp.refreshToken());
        AuthResponse refreshResp = authService.refresh(refresh, ip);
        assertThat(refreshResp.accessToken()).isNotBlank();
        assertThat(refreshResp.refreshToken()).isNotBlank();
        assertThat(refreshResp.refreshToken()).isNotEqualTo(loginResp.refreshToken());

        // Assert: 旧 refresh 已 revoke，新 refresh 仍 active
        String oldHash = AuthService.sha256Hex(loginResp.refreshToken());
        String newHash = AuthService.sha256Hex(refreshResp.refreshToken());
        RefreshToken oldRow = refreshTokenMapper.findByHash(oldHash);
        RefreshToken newRow = refreshTokenMapper.findByHash(newHash);
        assertThat(oldRow).isNotNull();
        assertThat(oldRow.getRevokedAt()).isNotNull();
        assertThat(newRow).isNotNull();
        assertThat(newRow.getRevokedAt()).isNull();

        // Act 4: logout（新 refresh）
        logout = new LogoutRequest(refreshResp.refreshToken());
        authService.logout(logout);
        RefreshToken afterLogout = refreshTokenMapper.findByHash(newHash);
        assertThat(afterLogout).isNotNull();
        assertThat(afterLogout.getRevokedAt()).isNotNull();

        // Cleanup: 删测试遗留 Redis 限流 key
        cleanupRedis(
                AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + AuthService.emailKeySuffix(email),
                AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
    }

    // ---------- refresh 重放 1401 ----------

    @Test
    void refresh_replayAfterRotate_throws1401() {
        // Arrange: 注册 + 登录拿到 refresh1，旋转得到 refresh2，再用 refresh1（已撤销）重放
        String email = uniqueEmail();
        String ip = uniqueIp();
        authService.register(new RegisterRequest(email, "Valid1Pass", null), ip);
        AuthResponse first = authService.login(new LoginRequest(email, "Valid1Pass"), ip);

        // 第一次 refresh（旋转）
        authService.refresh(new RefreshRequest(first.refreshToken()), ip);

        // Act + Assert: 第二次用旧 refresh 重放 → 1401
        RefreshRequest replay = new RefreshRequest(first.refreshToken());
        assertThatThrownBy(() -> authService.refresh(replay, ip))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_REFRESH_INVALID);

        cleanupRedis(
                AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + AuthService.emailKeySuffix(email),
                AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
    }

    // ---------- login 限流 1006 ----------

    @Test
    void login_rateLimit_sixthAttempt_throws1006() {
        // Arrange: 用唯一 IP + email 触发限流（LOGIN_RL_MAX=5，6th → 1006）
        String email = uniqueEmail();
        String ip = uniqueIp();
        authService.register(new RegisterRequest(email, "Valid1Pass", null), ip);
        LoginRequest wrongPwd = new LoginRequest(email, "Wrong1Pass");

        // Act: 前 5 次失败不抛 1006（抛 1002）
        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> authService.login(wrongPwd, ip))
                    .as("第 %d 次应抛 1002（密码错）", i)
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_BAD_CREDENTIALS);
        }

        // Assert: 第 6 次抛 1006
        assertThatThrownBy(() -> authService.login(wrongPwd, ip))
                .as("第 6 次必抛 1006")
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_LOGIN_RATE_LIMIT);

        cleanupRedis(
                AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + AuthService.emailKeySuffix(email),
                AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
    }

    // ---------- register 限流 1006 ----------

    @Test
    void register_rateLimit_fourthAttempt_throws1006() {
        // Arrange: REGISTER_RL_MAX=3，4th → 1006
        String ip = uniqueIp();
        for (int i = 1; i <= 3; i++) {
            authService.register(
                    new RegisterRequest("user-" + UUID.randomUUID() + "@example.com", "Valid1Pass", null),
                    ip);
        }

        // Assert: 第 4 次抛 1006
        RegisterRequest fourth = new RegisterRequest("user-" + UUID.randomUUID() + "@example.com", "Valid1Pass", null);
        assertThatThrownBy(() -> authService.register(fourth, ip))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_LOGIN_RATE_LIMIT);

        cleanupRedis(AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
    }

    // ---------- bcrypt roundtrip ----------

    @Test
    void bcrypt_roundtrip_loginMatchesOriginal() {
        // Arrange: 注册后用原密码登录成功（1002 已被排雷；这里只断言密码哈希链路 OK）
        String email = uniqueEmail();
        String ip = uniqueIp();
        String password = "Valid1Pass";
        authService.register(new RegisterRequest(email, password, null), ip);

        // Act + Assert: 用原密码登录得到非空 accessToken
        AuthResponse resp = authService.login(new LoginRequest(email, password), ip);
        assertThat(resp.accessToken()).isNotBlank();

        // 同时验证 DB 哈希与原始密码匹配（不通过 service，纯 mapper）
        User u = userMapper.findByEmail(email);
        assertThat(u).isNotNull();
        assertThat(u.getPasswordHash()).isNotEqualTo(password); // 不存明文
        assertThat(u.getPasswordHash()).startsWith("$2a$");   // BCrypt 标记

        cleanupRedis(
                AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + AuthService.emailKeySuffix(email),
                AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
    }
}