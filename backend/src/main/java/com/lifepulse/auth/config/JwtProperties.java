package com.lifepulse.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置（plan §3-C，§5）。
 *
 * <p>绑定 {@code application.yml} 中 {@code lp.jwt.*}：
 * <ul>
 *   <li>{@code lp.jwt.secret}（必填，HS256 密钥 ≥32 字节，由 JwtService 启动校验）</li>
 *   <li>{@code lp.jwt.access-ttl}（默认 PT1H，见 {@link com.lifepulse.auth.AuthConstants#ACCESS_TTL}）</li>
 *   <li>{@code lp.jwt.refresh-ttl}（默认 P7D，见 {@link com.lifepulse.auth.AuthConstants#REFRESH_TTL}）</li>
 * </ul>
 *
 * <p>Spring Boot 3.x 通过 {@code @ConfigurationPropertiesScan}（已在
 * {@code LifePulseApplication}）自动扫描本包。
 */
@ConfigurationProperties(prefix = "lp.jwt")
public class JwtProperties {

    /** HS256 密钥；来自 {@code ${LP_JWT_SECRET:...}}。 */
    private String secret;

    /** Access token TTL。 */
    private Duration accessTtl;

    /** Refresh token TTL。 */
    private Duration refreshTtl;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }
}