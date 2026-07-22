package com.lifepulse.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/**
 * WebCorsIT — CORS 白名单 + 启动 fail-fast（issue R-004）。
 *
 * <p>用 {@link MockMvc} 模拟 OPTIONS preflight：避免 TestRestTemplate/HttpClient5
 * 吞掉 OPTIONS 的 {@code Origin} header。MockMvc 直接走 Spring 的 filter chain
 * （含 CorsFilter），断言响应头。
 *
 * <p>覆盖 AC：
 * <ol>
 *   <li>合法 origin → preflight 返回 {@code Access-Control-Allow-Origin: <origin>}</li>
 *   <li>prod 风格 origin（不在白名单）→ preflight 不返回 allow-origin（浏览器阻断）</li>
 * </ol>
 *
 * <p>启动 fail-fast 校验由 {@link CorsPropertiesValidationTest} 单测覆盖，
 * 不依赖 Spring 上下文，更轻量。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(SecurityConfig.class)
class WebCorsIT {

    /** 白名单 origin（与 {@link #overrideProps} 一致）。 */
    private static final String ALLOWED_ORIGIN = "http://app.example.com";

    @SuppressWarnings("resource") // managed by Testcontainers + Ryuk
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lifepulse")
            .withUsername("lp")
            .withPassword("lp_dev_only")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("lp.cors.allowed-origins", () -> ALLOWED_ORIGIN);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://:123456@localhost:6379");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "300000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "60000");
        registry.add("spring.datasource.hikari.validation-timeout", () -> "3000");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        // 注入合法 JWT secret（≥32 字节、不含 'replace-me'）— JwtService.init fail-fast 校验拒绝占位符
        registry.add("lp.jwt.secret",
                () -> "test-only-jwt-secret-32bytes-min-padding-not-real-secret-aaaaaaaa");
    }

    @Test
    void preflight_withAllowedOrigin_returnsAccessControlAllowOrigin() throws Exception {
        MvcResult result = mockMvc.perform(
                        options("/api/v1/auth/login")
                                .header("Origin", ALLOWED_ORIGIN)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andReturn();

        // 命中 CorsConfigurationSource → echo origin
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                .as("白名单 origin preflight 必须回显 Allow-Origin")
                .isEqualTo(ALLOWED_ORIGIN);
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods"))
                .as("白名单 origin 必须列出允许的方法")
                .contains("POST");
        assertThat(result.getResponse().getHeader("Access-Control-Max-Age"))
                .as("preflight 缓存必须存在")
                .isEqualTo("3600");
    }

    @Test
    void preflight_withProdStyleOrigin_doesNotEchoAllowOrigin() throws Exception {
        // 模拟 prod 部署后被攻击者挂的恶意 origin（CLAUDE.md §7.5 白名单）
        String evilOrigin = "https://evil.example.com";
        MvcResult result = mockMvc.perform(
                        options("/api/v1/auth/login")
                                .header("Origin", evilOrigin)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andReturn();

        // Spring CORS：不匹配 allowOrigins → 不返回 Access-Control-Allow-Origin。
        // 浏览器据此阻断实际请求。
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                .as("非白名单 origin 必须不回显 allow-origin（浏览器阻断）")
                .isNull();
    }
}
