package com.lifepulse.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Phase 1.3-B · 全量 SecurityConfig（plan §3-B / §6.4）。
 *
 * <p>替换 1.2-E 占位桥：
 * <ul>
 *   <li>matcher 路径修正为 {@code /api/v1/auth/**}（1.2 写成 {@code /auth/**}
 *       与 controller 实际路径不一致，导致所有 auth 端点被拦 401）</li>
 *   <li>注册 {@link JwtAuthFilter} 到 {@code UsernamePasswordAuthenticationFilter}
 *       之前；认证入口点统一为 {@link JwtAuthEntryPoint}，输出信封 1002/401</li>
 *   <li>显式 {@code formLogin.disable()} / {@code httpBasic.disable()} /
 *       {@code logout.disable()} 避免 Spring 默认 Basic 弹窗与 logout filter</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   JwtAuthEntryPoint jwtAuthEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}