package com.lifepulse.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1.2-E · 最小 SecurityConfig 桥（plan §7）。
 *
 * <p>理由：{@code @SpringBootTest} 拉起完整 Spring 上下文时若无
 * {@link SecurityFilterChain} bean，Spring Security 6 默认链对所有
 * 请求要求 Basic Auth，IT 与未来的 MockMvc 测试都会被 401 拦截。
 *
 * <p>本配置只做「放行 /auth/** + /actuator/health + stateless + 禁 CSRF」，
 * 不引入 JwtAuthFilter 与 UserContext（1.3 落地）。
 *
 * <p>{@code // TODO(phase=1.3): replace with JwtAuthFilter chain + /users/me auth}
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}