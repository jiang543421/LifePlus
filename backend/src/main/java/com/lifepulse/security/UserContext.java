package com.lifepulse.security;

/**
 * 当前线程 userId 持有器（plan §3-B / §6.1）。
 *
 * <p>线程级 {@link ThreadLocal} 存储已认证用户的 id。{@link JwtAuthFilter}
 * 在解析到合法 token 后调用 {@link #set(Long)}；filter 的 {@code finally}
 * 必须 {@link #clear()}，避免 servlet 线程池复用导致跨请求泄漏
 * （plan §9「线程复用泄漏」风险）。
 *
 * <p>双轨制（plan §6.1）：{@link org.springframework.security.core.Authentication}
 * 走 Spring Security 标准（{@code principal = userId}），{@code UserContext}
 * 作为业务层静态读取入口；Phase 2+ 的 {@code TaskService} 等通过
 * {@link #current()} 直接取 id，无需感知 Spring Security 上下文。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
        // 静态工具类，禁止实例化
    }

    /** 设置当前线程 userId；filter 在解析成功后调用。 */
    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    /** 返回当前线程 userId，未设置时为 {@code null}。 */
    public static Long current() {
        return CURRENT.get();
    }

    /** 清除当前线程 userId；filter finally 与测试 teardown 必须调用。 */
    public static void clear() {
        CURRENT.remove();
    }
}