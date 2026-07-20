package com.lifepulse.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * CORS 跨域白名单配置（issue R-004）。
 *
 * <p>绑定 {@code lp.cors.allowed-origins}（逗号分隔多 origin 或单 origin）。
 * 默认值 {@code http://localhost} 适用于 docker compose 前端容器在 80 端口服务的场景；
 * dev 用 {@code http://localhost:5173} 需在 {@code application-dev.yml} 或
 * 启动环境变量 {@code LP_CORS_ORIGINS=http://localhost:5173} 覆盖。
 *
 * <p>fail-fast 校验（{@link #validate()}）：
 * <ul>
 *   <li>空列表 → 启动失败（防止误配置成"全禁"）</li>
 *   <li>含通配符 {@code *} → 启动失败（CLAUDE.md §7.5：禁 {@code *}）</li>
 *   <li>每个 origin 必须是 {@code http(s)://host[:port]} 形式</li>
 * </ul>
 *
 * <p>启动期 throw → Spring Boot 进程退出码非 0，避免 prod 静默跑"无 CORS"或"全开放"配置。
 */
@ConfigurationProperties(prefix = "lp.cors")
public class CorsProperties {

    /** 允许的跨域 origin 列表。 */
    private List<String> allowedOrigins = List.of("http://localhost");

    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @PostConstruct
    void validate() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "lp.cors.allowed-origins must not be empty; set LP_CORS_ORIGINS env var");
        }
        for (String origin : allowedOrigins) {
            if (!StringUtils.hasText(origin)) {
                throw new IllegalStateException(
                        "lp.cors.allowed-origins contains blank entry");
            }
            if ("*".equals(origin.trim())) {
                throw new IllegalStateException(
                        "lp.cors.allowed-origins must NOT contain wildcard '*' (CLAUDE.md §7.5)");
            }
            String lower = origin.trim().toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                throw new IllegalStateException(
                        "lp.cors.allowed-origins entry must start with http:// or https://: " + origin);
            }
        }
    }
}