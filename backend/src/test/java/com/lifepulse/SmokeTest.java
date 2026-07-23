package com.lifepulse;

import com.lifepulse.auth.repository.RefreshTokenMapper;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.auth.service.AuthService;
import com.lifepulse.auth.service.JwtService;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.diet.repository.DietMapper;
import com.lifepulse.expense.repository.ExpenseMapper;
import com.lifepulse.plan.repository.PlanMapper;
import com.lifepulse.security.UserContext;
import com.lifepulse.task.repository.TaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: 仅验证 Spring 上下文可加载（plan §6.5）。
 *
 * <p>禁用本测试环境下的 Flyway / Redis / Security 默认用户；真正的 datasource / Redis / Flyway
 * 集成测试在 {@code src/test/java/.../it/*IT.java} 中通过 Testcontainers 启动，
 * 不允许依赖外部 DB / Redis（CLAUDE.md §6.4）。
 *
 * <h3>修补要点（plan §6.5）</h3>
 * <ul>
 *   <li>1.1 起 MyBatis-Plus mapper 依赖真实 {@code DataSource}；本测试 exclude 了
 *       {@code DataSourceAutoConfiguration}，mapper 不会被自动构造。</li>
 *   <li>1.3-A 起 {@code AuthService} 依赖 4 个下游（UserMapper / RefreshTokenMapper /
 *       JwtService / RateLimiter / PasswordEncoder），下游链路全断会导致
 *       {@code contextLoads()} 抛 {@code NoSuchBeanDefinitionException}。</li>
 *   <li>用 {@code @MockBean} 替 5 个关键 bean 替身，使 Spring 上下文能在无 DB / Redis 的
 *       情况下完整装配；Mockito 默认返回值（null/0/false）满足 {@code contextLoads()}
 *       单纯"可加载"校验。</li>
 *   <li>{@code @AfterEach} 清 {@link UserContext}（1.3-B 引入的 {@code ThreadLocal}）
 *       与 {@link SecurityContextHolder}，避免同一 JVM 跨测试残留。</li>
 * </ul>
 *
 * <p>注：本测试**不**覆盖任何业务逻辑；覆盖率门禁由 {@code *IT.java} 端到端测试承担。
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
@TestPropertySource(properties = {
        "lp.jwt.secret=test-only-secret-test-only-secret-test",
        // v2.1 PR1（Task 1 引入 LlmProperties）：SmokeTest 不验证 LLM 链路，
        // 关掉 LLM 走 v2.0 模板路径，避免占位符 key 触发启动期 fail-fast。
        // LLM 路径由 LlmClientContractTest / DeepSeekClientTest / OllamaClientTest 单独覆盖。
        "lp.ai.llm.enabled=false",
        "lp.ai.llm.api-key="
})
class SmokeTest {

    @MockBean private UserMapper userMapper;
    @MockBean private RefreshTokenMapper refreshTokenMapper;
    @MockBean private TaskMapper taskMapper;  // Phase 2-C：TaskService 装配依赖
    @MockBean private PlanMapper planMapper;  // Phase 3-B：PlanService 装配依赖
    @MockBean private ExpenseMapper expenseMapper;  // v1.2.1 Expense：ExpenseService 装配依赖
    @MockBean private DietMapper dietMapper;  // v1.2.2 Diet：DietService 装配依赖
    @MockBean private RateLimiter rateLimiter;
    @MockBean private JwtService jwtService;
    @MockBean private AuthService authService;
    // PR2 v2.1：LlmCircuitBreaker / LlmQuotaGuard 直接依赖 StringRedisTemplate（与
    // AiInsightService 不同，没用 ObjectProvider）。本测试排除了 RedisAutoConfiguration
    // → StringRedisTemplate bean 不存在 → 装配炸。Mockito 替身让 context 能完整装配；
    // LLM 路径仍由 enabled=false 完全绕过（TestPropertySource line 59-61），
    // mock 不会被实际调用。
    @MockBean private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void tearDown() {
        // 1.3-B 引入的 ThreadLocal：必须清，否则跨测试残留
        UserContext.clear();
        // Security 上下文同样清，保持测试隔离
        SecurityContextHolder.clearContext();
    }

    @Test
    void contextLoads() {
        // 仅校验 Spring 上下文能完整装配，无业务断言
    }
}
