package com.lifepulse.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类（Spec §6.4：禁止依赖外部 DB / Redis，必须 Testcontainers）。
 *
 * <p>约定：
 * <ul>
 *   <li>MySQL 用 Testcontainers 启动隔离环境（mysql:8.0）</li>
 *   <li>Redis 直接连本机已启 Redis 服务（密码见 {@link #LOCAL_REDIS_URL}），
 *       避免引入 testcontainers:redis 这个在 aliyun mirror 缺失的镜像</li>
 *   <li>所有 Flyway 迁移会自动运行（{@code spring.flyway.enabled=true}）</li>
 * </ul>
 *
 * 子类用 {@code @Autowired} 注入需要的服务即可。
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /**
     * 本机 Redis dev URL（含密码）。
     * 用户在 dev 环境单实例启动 Redis（端口 6379，密码 123456），
     * 若密码调整请同时改此处并提请更新文档。
     */
    static final String LOCAL_REDIS_URL = "redis://:123456@localhost:6379";

    @Container
    @SuppressWarnings("resource") // managed by Testcontainers
    public static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lifepulse")
            .withUsername("lp")
            .withPassword("lp_dev_only");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 复用 application.yml 中 spring.data.redis.* 项，但替换 url 注入本地密码
        registry.add("spring.data.redis.url", () -> LOCAL_REDIS_URL);
        // IT 强制开启 Flyway 与 Redis（SmokeTest 例外）
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.autoconfigure.exclude", () -> "");
    }
}
