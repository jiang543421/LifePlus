package com.lifepulse.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Phase 1.3-B · 全量 SecurityConfig（plan §3-B / §6.4 / issue R-004）。
 *
 * <p>R-004 增量：CORS 白名单改为读取 {@link CorsProperties}（{@code lp.cors.allowed-origins}），
 * 替代硬编码。prod 必须显式注入 {@code LP_CORS_ORIGINS}，禁通配符 {@code *}（启动 fail-fast）。
 *
 * <p>匹配规则保留原样：{@code /api/v1/auth/**} + {@code /actuator/health} 放行，
 * 其余需 JWT。{@link JwtAuthFilter} 注入到 {@code UsernamePasswordAuthenticationFilter} 之前，
 * 401 由 {@link JwtAuthEntryPoint} 统一输出信封。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   JwtAuthEntryPoint jwtAuthEntryPoint,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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

    /**
     * CORS 配置源（R-004）。
     *
     * <p>仅暴露 {@code Origin} / 常用方法 / 常用头；credentials=false（CLAUDE.md §7.5
     * 后端无 cookie-based 鉴权，JWT 走 {@code Authorization: Bearer}）；preflight
     * 缓存 1h，避免每次跨域请求都打 OPTIONS。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.copyOf(props.getAllowedOrigins()));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Forwarded-For", "X-Trace-Id"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}