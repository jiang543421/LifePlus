package com.lifepulse.auth.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.config.JwtProperties;
import com.lifepulse.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析服务（plan §3-C，A-007）。
 *
 * <p>HS256 签名；payload 固定包含 {@code sub=userId(String), iat, exp, typ}。
 * 启动时校验密钥字节数 ≥32（CLAUDE.md §7.2 hard rule）。
 *
 * <p>{@link #parse(String)} 在签名错误、过期、解析失败时统一抛
 * {@link BusinessException} {@link AuthConstants#ERR_REFRESH_INVALID}（1401），
 * 由 1.3 的 {@code GlobalExceptionHandler} 转统一信封。
 */
@Service
public class JwtService {

    /** JWT typ claim 值：access token。 */
    static final String TYP_ACCESS = "access";

    /** JWT typ claim 值：refresh token。 */
    static final String TYP_REFRESH = "refresh";

    private final JwtProperties props;
    private SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    /**
     * 启动校验：HS256 密钥字节数 ≥32（CLAUDE.md §7.2）；
     * 额外拒绝含 {@code replace-me}（大小写不敏感）的占位符串，防止误部署
     * 使用 {@code application.yml} 的 dev 默认密钥（CLAUDE.md §7.1 + Review C-3）。
     * 显式公开以便单测直接调用；{@code @PostConstruct} 在 Spring 上下文同样会触发。
     */
    @PostConstruct
    public void init() {
        String secret = props.getSecret();
        if (secret == null) {
            throw new IllegalStateException("lp.jwt.secret is not configured");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < AuthConstants.JWT_SECRET_MIN_BYTES) {
            throw new IllegalStateException(
                    "lp.jwt.secret must be at least 32 bytes, got " + bytes);
        }
        if (secret.toLowerCase().contains("replace-me")) {
            throw new IllegalStateException(
                    "lp.jwt.secret appears to be a placeholder ('replace-me' substring); "
                            + "set LP_JWT_SECRET env var with a real ≥32-byte secret "
                            + "(CLAUDE.md §7.1)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 access token；TTL 来自 {@code lp.jwt.access-ttl}。 */
    public String issueAccess(Long userId) {
        return issueToken(userId, TYP_ACCESS, ttlOrDefault(props.getAccessTtl(), AuthConstants.ACCESS_TTL));
    }

    /** 签发 refresh token；TTL 来自 {@code lp.jwt.refresh-ttl}。 */
    public String issueRefresh(Long userId) {
        return issueToken(userId, TYP_REFRESH, ttlOrDefault(props.getRefreshTtl(), AuthConstants.REFRESH_TTL));
    }

    /**
     * 解析 token，校验签名 + 有效期。
     *
     * @throws BusinessException 1401 当 token 过期、签名错误或格式非法
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "token expired");
        } catch (io.jsonwebtoken.JwtException e) {
            // 签名错误、格式非法、claim 缺失等统一归 1401
            throw new BusinessException(AuthConstants.ERR_REFRESH_INVALID, "invalid token");
        }
    }

    // ---------- private ----------

    private String issueToken(Long userId, String typ, Duration ttl) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        // jti：每次唯一 UUID，避免毫秒级同一秒内连续签发产生相同 token
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(exp)
                .claim("typ", typ)
                .signWith(signingKey)
                .compact();
    }

    private static Duration ttlOrDefault(Duration configured, Duration fallback) {
        return configured != null ? configured : fallback;
    }
}