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
import com.lifepulse.common.security.RateLimiter;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1.2-D · AuthService 单元测试（plan §9，Mockito）。
 *
 * <p>覆盖 4 个核心方法的成功与失败分支；rate-limit（1006）由 IT 用真 Redis 验证。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private RateLimiter rateLimiter;
    @Mock private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(AuthConstants.BCRYPT_STRENGTH);
        authService = new AuthService(userMapper, refreshTokenMapper, passwordEncoder,
                jwtService, rateLimiter);
    }

    // ---------- register ----------

    @Test
    void register_newEmail_persistsUserAndReturnsUserId() {
        // Arrange
        RegisterRequest req = new RegisterRequest("alice@example.com", "Valid1Pass", "alice");
        when(userMapper.findByEmail("alice@example.com")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L); // 模拟 AUTO_INCREMENT 回填
            return 1;
        });

        // Act
        Long userId = authService.register(req, "127.0.0.1");

        // Assert
        assertThat(userId).isEqualTo(42L);
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCap.capture());
        User saved = userCap.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getNickname()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("Valid1Pass", saved.getPasswordHash())).isTrue();
    }

    @Test
    void register_existingEmail_throwsBusinessException1005() {
        // Arrange
        RegisterRequest req = new RegisterRequest("alice@example.com", "Valid1Pass", null);
        when(userMapper.findByEmail("alice@example.com")).thenReturn(new User());

        // Act + Assert
        assertThatThrownBy(() -> authService.register(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_EMAIL_TAKEN);
        verify(userMapper, never()).insert(any(User.class));
    }

    // ---------- login ----------

    @Test
    void login_unknownEmail_throwsBusinessException1002() {
        // Arrange
        LoginRequest req = new LoginRequest("nobody@example.com", "anypass1");
        when(userMapper.findByEmail("nobody@example.com")).thenReturn(null);

        // Act + Assert
        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_BAD_CREDENTIALS);
        verify(jwtService, never()).issueAccess(any());
    }

    @Test
    void login_wrongPassword_throwsBusinessException1002() {
        // Arrange
        User u = new User();
        u.setId(7L);
        u.setEmail("alice@example.com");
        u.setPasswordHash(passwordEncoder.encode("Correct1Pass"));
        when(userMapper.findByEmail("alice@example.com")).thenReturn(u);

        LoginRequest req = new LoginRequest("alice@example.com", "Wrong1Pass");

        // Act + Assert
        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_BAD_CREDENTIALS);
        verify(jwtService, never()).issueAccess(any());
    }

    @Test
    void login_validCredentials_returnsAuthResponse() {
        // Arrange
        User u = new User();
        u.setId(7L);
        u.setEmail("alice@example.com");
        u.setPasswordHash(passwordEncoder.encode("Valid1Pass"));
        when(userMapper.findByEmail("alice@example.com")).thenReturn(u);
        when(jwtService.issueAccess(7L)).thenReturn("access.jwt.value");
        when(jwtService.issueRefresh(7L)).thenReturn("refresh.jwt.value");

        LoginRequest req = new LoginRequest("alice@example.com", "Valid1Pass");

        // Act
        AuthResponse resp = authService.login(req, "127.0.0.1");

        // Assert
        assertThat(resp.accessToken()).isEqualTo("access.jwt.value");
        assertThat(resp.refreshToken()).isEqualTo("refresh.jwt.value");
        assertThat(resp.expiresIn()).isEqualTo(AuthConstants.ACCESS_TTL);
        // 持久化新 refresh
        verify(refreshTokenMapper, times(1)).insert(any(RefreshToken.class));
    }

    // ---------- refresh ----------

    @Test
    void refresh_unknownToken_throwsBusinessException1401() {
        // Arrange: parseJwt 抛 1401 (unknown sub)
        when(jwtService.parse("bogus.token")).thenThrow(
                new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "invalid token"));
        RefreshRequest req = new RefreshRequest("bogus.token");

        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_REFRESH_INVALID);
    }

    @Test
    void refresh_revokedToken_throwsBusinessException1401() {
        // Arrange: JWT 合法，但 DB 中已被撤销或不存在
        when(jwtService.parse("raw.refresh.token")).thenReturn(stubRefreshClaims(7L));
        when(refreshTokenMapper.findByHash(anyString())).thenReturn(null);

        RefreshRequest req = new RefreshRequest("raw.refresh.token");

        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_REFRESH_INVALID);
    }

    @Test
    void refresh_expiredToken_throwsBusinessException1401() {
        // Arrange: JWT 合法、DB 命中但 expiresAt 已过
        io.jsonwebtoken.Claims claims = stubRefreshClaims(7L);
        when(jwtService.parse("raw.refresh.token")).thenReturn(claims);

        RefreshToken stored = new RefreshToken();
        stored.setUserId(7L);
        stored.setTokenHash("any-hash");
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(refreshTokenMapper.findByHash(anyString())).thenReturn(stored);

        RefreshRequest req = new RefreshRequest("raw.refresh.token");

        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_REFRESH_INVALID);
    }

    // ---------- logout ----------

    @Test
    void logout_unknownToken_isNoop() {
        // Arrange: 找不到 hash 不报错（幂等）
        when(refreshTokenMapper.findByHash(anyString())).thenReturn(null);

        LogoutRequest req = new LogoutRequest("any.raw.token");

        // Act + Assert: 不抛异常
        authService.logout(req);
        verify(refreshTokenMapper, never()).revokeByHash(anyString(), any());
    }

    // ---------- helper ----------

    private static io.jsonwebtoken.Claims stubRefreshClaims(Long userId) {
        // jjwt 0.12.x: 用 Jwts.claims() 工厂构造，避免 DefaultClaims protected 构造
        return Jwts.claims()
                .subject(String.valueOf(userId))
                .add("typ", "refresh")
                .build();
    }
}