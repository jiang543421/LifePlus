package com.lifepulse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: 仅验证 Spring 上下文可加载。
 *
 * <p>禁用本测试环境下的 Flyway / Redis / Security 默认用户；真正的 datasource / Redis / Flyway
 * 集成测试在 {@code src/test/java/.../it/*IT.java} 中通过 Testcontainers 启动，
 * 不允许依赖外部 DB / Redis（CLAUDE.md §6.4）。
 */
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=false",
                "spring.data.redis.repositories.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
@TestPropertySource(properties = "lp.jwt.secret=test-only-secret-test-only-secret-test")
class SmokeTest {
    @Test
    void contextLoads() {
    }
}
