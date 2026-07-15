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
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 认证服务（plan §3-D，A-006）。
 *
 * <p>4 个公开方法：{@code register / login / refresh / logout}。所有写操作经
 * MyBatis-Plus Mapper；密码经 {@link PasswordEncoder#encode(CharSequence)}
 * BCrypt 强度 {@link AuthConstants#BCRYPT_STRENGTH}=10；refresh token 用
 * {@link SecureRandom} 生成 32 字节 → Base64 URL-safe → 给客户端，DB 存 SHA-256(raw)。
 *
 * <p>登录/注册前先调 {@link RateLimiter#hit}；超限抛 {@link BusinessException}
 * {@link AuthConstants#ERR_LOGIN_RATE_LIMIT}（1006）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String CLAIM_TYP = "typ";
    private static final SecureRandom RNG = new SecureRandom();

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    public AuthService(UserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RateLimiter rateLimiter) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    // ---------- register ----------

    public Long register(RegisterRequest req, String ip) {
        // 1. 注册限流（IP 维度，spec §03）
        String rlKey = AuthConstants.REGISTER_RL_KEY_PREFIX + ip;
        if (rateLimiter.hit(rlKey, AuthConstants.REGISTER_RL_MAX, AuthConstants.REGISTER_RL_WINDOW)) {
            throw new BusinessException(AuthConstants.ERR_LOGIN_RATE_LIMIT, "register rate limit exceeded");
        }

        // 2. email 唯一性
        if (userMapper.findByEmail(req.email()) != null) {
            throw new BusinessException(AuthConstants.ERR_EMAIL_TAKEN, "email already registered");
        }

        // 3. 持久化
        User u = new User();
        u.setEmail(req.email());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setNickname(req.nickname());
        userMapper.insert(u);

        // 4. 返回新用户 id
        return u.getId();
    }

    // ---------- login ----------

    public AuthResponse login(LoginRequest req, String ip) {
        // 1. 登录限流（IP + email 前缀，避免完整 email 落入 Redis key，CLAUDE.md §7.3）
        String rlKey = AuthConstants.LOGIN_RL_KEY_PREFIX + ip + ":" + emailKeySuffix(req.email());
        if (rateLimiter.hit(rlKey, AuthConstants.LOGIN_RL_MAX, AuthConstants.LOGIN_RL_WINDOW)) {
            throw new BusinessException(AuthConstants.ERR_LOGIN_RATE_LIMIT, "login rate limit exceeded");
        }

        // 2. 查 user；不存在或密码错统一 1002（防账号枚举）
        User u = userMapper.findByEmail(req.email());
        if (u == null) {
            throw new BusinessException(AuthConstants.ERR_BAD_CREDENTIALS, "invalid credentials");
        }
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new BusinessException(AuthConstants.ERR_BAD_CREDENTIALS, "invalid credentials");
        }

        // 3. 签发 JWT 对并持久化 refresh
        return issueAndPersist(u.getId());
    }

    // ---------- refresh ----------

    @Transactional
    public AuthResponse refresh(RefreshRequest req, String ip) {
        // 1. 解析 JWT；签名/过期/格式错 → JwtService 已抛 1401，直接冒泡
        Claims claims;
        try {
            claims = jwtService.parse(req.refreshToken());
        } catch (BusinessException e) {
            throw e;
        }

        // 2. typ 必须为 refresh（防御 access token 被滥用）
        String typ = claims.get(CLAIM_TYP, String.class);
        if (!JwtService.TYP_REFRESH.equals(typ)) {
            throw new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "not a refresh token");
        }

        Long userId = Long.valueOf(claims.getSubject());

        // 3. 查 DB 行；不存在或已撤销 → 1401（防 refresh 重放）
        String oldHash = sha256Hex(req.refreshToken());
        RefreshToken stored = refreshTokenMapper.findByHash(oldHash);
        if (stored == null || stored.getRevokedAt() != null) {
            throw new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "refresh revoked or unknown");
        }

        // 4. 跨用户校验（CLAUDE.md §7.2 hard rule）
        if (!stored.getUserId().equals(userId)) {
            log.warn("refresh cross-user detected: tokenUserId={}, storedUserId={}", userId, stored.getUserId());
            throw new BusinessException(AuthConstants.ERR_CROSS_USER, "cross-user access");
        }

        // 5. 过期校验
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "refresh expired");
        }

        // 6. 旋转：撤销旧行 → 签发新对 → 持久化新行
        refreshTokenMapper.revokeByHash(oldHash, OffsetDateTime.now());
        return issueAndPersist(userId);
    }

    // ---------- logout ----------

    public void logout(LogoutRequest req) {
        String hash = sha256Hex(req.refreshToken());
        RefreshToken stored = refreshTokenMapper.findByHash(hash);
        if (stored == null) {
            // 幂等：未知 token 不报错
            return;
        }
        refreshTokenMapper.revokeByHash(hash, OffsetDateTime.now());
    }

    // ---------- private helpers ----------

    private AuthResponse issueAndPersist(Long userId) {
        String accessToken = jwtService.issueAccess(userId);
        String refreshToken = jwtService.issueRefresh(userId);

        RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(sha256Hex(refreshToken));
        row.setExpiresAt(OffsetDateTime.now().plus(AuthConstants.REFRESH_TTL));
        refreshTokenMapper.insert(row);

        return AuthResponse.of(accessToken, refreshToken, AuthConstants.ACCESS_TTL);
    }

    /** SHA-256 → lowercase hex。 */
    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在 JVM 标准算法表中；不可能缺失
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Rate-limit key 中 email 的混淆后缀：SHA-256 前 8 字符 hex。
     * 避免完整 email 落入 Redis key 与 SLOWLOG（CLAUDE.md §7.3 禁打完整 email）。
     */
    static String emailKeySuffix(String email) {
        return sha256Hex(email.toLowerCase()).substring(0, 8);
    }

    /** 预留：raw refresh token 生成器（1.2-E 端到端 IT 可能调用验证）。 */
    @SuppressWarnings("unused")
    static String newRawRefreshToken() {
        byte[] buf = new byte[AuthConstants.REFRESH_TOKEN_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}