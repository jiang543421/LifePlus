package com.lifepulse.auth.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.config.JwtProperties;
import com.lifepulse.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1.2-C · JwtService 单元测试（plan §9）。
 *
 * <p>覆盖启动 secret 长度校验（≥32 字节，CLAUDE.md §7.2）、access/refresh 签发、
 * payload 字段、过期与篡改签名 → 1401。
 *
 * <p>使用纯 Java 实例（不走 {@code @SpringBootTest}），secret 在 {@code setUp()}
 * 中固定 32 字节 + 1 字符 padding（覆盖 spec 下限）。{@link JwtService#init()}
 * 的启动校验在此自然触发。
 */
class JwtServiceTest {

    private static final String VALID_SECRET =
            "test-only-secret-padded-to-32bytes!!"; // 37 chars (>32 bytes UTF-8)

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(VALID_SECRET);
        props.setAccessTtl(AuthConstants.ACCESS_TTL);
        props.setRefreshTtl(AuthConstants.REFRESH_TTL);
        jwtService = new JwtService(props);
        jwtService.init();
    }

    @Test
    void init_shortSecret_throwsIllegalState() {
        JwtProperties props = new JwtProperties();
        props.setSecret("short-secret-16b"); // 16 chars < 32
        props.setAccessTtl(AuthConstants.ACCESS_TTL);
        props.setRefreshTtl(AuthConstants.REFRESH_TTL);
        JwtService svc = new JwtService(props);

        assertThatThrownBy(svc::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void issueAccess_containsSubjectUserIdAndTyp() {
        // Act
        String token = jwtService.issueAccess(42L);

        // Assert: 解析后 subject=userId(String), typ="access"
        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
    }

    @Test
    void issueRefresh_typIsRefresh() {
        String token = jwtService.issueRefresh(42L);

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
    }

    @Test
    void parse_expiredToken_throwsBusinessException1401() throws InterruptedException {
        // Arrange: 用 1 秒 TTL 签发，再 sleep 1.1s
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(VALID_SECRET);
        shortProps.setAccessTtl(Duration.ofSeconds(1));
        shortProps.setRefreshTtl(AuthConstants.REFRESH_TTL);
        JwtService shortSvc = new JwtService(shortProps);
        shortSvc.init();
        String token = shortSvc.issueAccess(42L);

        Thread.sleep(1100);

        // Act + Assert
        assertThatThrownBy(() -> shortSvc.parse(token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_REFRESH_INVALID);
    }

    @Test
    void parse_tamperedSignature_throwsBusinessException1401() {
        // Arrange: 拿合法 token 改最后一位
        String token = jwtService.issueAccess(42L);
        char last = token.charAt(token.length() - 1);
        char tampered = last == 'A' ? 'B' : 'A';
        String bad = token.substring(0, token.length() - 1) + tampered;

        // Act + Assert
        assertThatThrownBy(() -> jwtService.parse(bad))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", AuthConstants.ERR_REFRESH_INVALID);
    }
}