package com.lifepulse.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
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
 * <h3>plan §13 已知问题修复（跨 IT class 容器接力）</h3>
 * <p>不要用 JUnit5 {@code @Container} 注解 — 它会在每个 IT class 结束时调用
 * {@code MYSQL.stop()}，即使 {@code withReuse(true)} 也救不回"先 stop 再 start"
 * 之间的容器丢失窗口。改成静态初始化块显式 {@code MYSQL.start()}，
 * 容器在 JVM 退出时才被 Ryuk 清理，配合 Failsafe 的 {@code reuseForks=true}，
 * 同 JVM 内多 IT class 共享一个容器实例。
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

    /**
     * MySQL 容器：{@code withReuse(true)} + 显式 start（不要 @Container）。
     * <p>复用条件：
     * <ul>
     *   <li>{@code ~/.testcontainers.properties} 写
     *       {@code testcontainers.reuse.enable=true}</li>
     *   <li>Failsafe plugin {@code <reuseForks>true</reuseForks>}
     *       + {@code <forkCount>1</forkCount>}（pom.xml 已配）</li>
     * </ul>
     */
    @SuppressWarnings("resource") // managed by Testcontainers + Ryuk
    public static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lifepulse")
            .withUsername("lp")
            .withPassword("lp_dev_only")
            .withReuse(true);

    static {
        // 显式启动：避免 JUnit5 @Container 在每个 IT class 结束时 stop。
        // start() 内部对已启动的容器是 no-op，所以多次调用安全。
        MYSQL.start();
    }

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
        // plan §13 已知问题修复：Hikari pool validation 三件套
        registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "300000");       // 5min
        registry.add("spring.datasource.hikari.idle-timeout", () -> "60000");         // 1min
        registry.add("spring.datasource.hikari.validation-timeout", () -> "3000");    // 3s
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }
}