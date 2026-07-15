package com.lifepulse.common.security;

import com.lifepulse.auth.AuthConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt {@link PasswordEncoder} 配置（plan §3-A，A-005）。
 *
 * <p>强度固定 {@link AuthConstants#BCRYPT_STRENGTH}=10（CLAUDE.md §7.2 hard rule）；
 * 不暴露为配置项，便于测试基线和代码审查一致。
 *
 * <p>无单测：由 {@code AuthServiceIT.register_login_refresh_logout_happyPath}
 * 与 {@code bcrypt_roundtrip_loginMatchesOriginal} 隐式覆盖密码编码链路。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(AuthConstants.BCRYPT_STRENGTH);
    }
}