package com.lifepulse.auth;

import com.lifepulse.common.exception.ErrorCode;

import java.time.Duration;

/**
 * 认证模块常量集中点（plan §5）。
 *
 * <p>CLAUDE.md §4.2：禁止魔法数字，所有阈值、TLL、key 前缀、Lua 脚本均集中于此。
 * {@code application.yml} 中 {@code lp.jwt.*} 仍由 {@code JwtProperties} 注入，
 * 本类同名常量仅作编程内默认 / 单测用值。
 */
public final class AuthConstants {

    private AuthConstants() {
        // no instances
    }

    /** BCrypt 哈希强度（CLAUDE.md §7.2 hard rule）。 */
    public static final int BCRYPT_STRENGTH = 10;

    /** Access Token 默认 TTL（1 小时）。{@code lp.jwt.access-ttl} 可覆盖。 */
    public static final Duration ACCESS_TTL = Duration.ofHours(1);

    /** Refresh Token 默认 TTL（7 天）。{@code lp.jwt.refresh-ttl} 可覆盖。 */
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    /** 登录端点限流阈值：1 分钟内 5 次失败。 */
    public static final int LOGIN_RL_MAX = 5;
    public static final Duration LOGIN_RL_WINDOW = Duration.ofMinutes(1);

    /** 注册端点限流阈值：1 分钟内 3 次。 */
    public static final int REGISTER_RL_MAX = 3;
    public static final Duration REGISTER_RL_WINDOW = Duration.ofMinutes(1);

    /** Raw refresh token 字节数（256 bit 安全强度，spec §3.4）。 */
    public static final int REFRESH_TOKEN_BYTES = 32;

    /** JWT HS256 启动校验：密钥至少 32 字节（CLAUDE.md §7.2）。 */
    public static final int JWT_SECRET_MIN_BYTES = 32;

    /** 登录限流 Redis key 前缀。完整 key：{@code lp:rl:login:<ip>:<email-sha256-prefix-8>}。 */
    public static final String LOGIN_RL_KEY_PREFIX = "lp:rl:login:";

    /** 注册限流 Redis key 前缀。完整 key：{@code lp:rl:register:<ip>}。 */
    public static final String REGISTER_RL_KEY_PREFIX = "lp:rl:register:";

    /** RateLimiter INCR + EXPIRE NX 原子 Lua 脚本（plan §6）。 */
    public static final String RATE_LIMIT_LUA =
            "local cur = redis.call('INCR', KEYS[1])\n"
          + "if cur == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n"
          + "return cur";

    // 业务错误码（spec §03；与 GlobalExceptionHandler 1.3 配合）
    // Review H-6：实际定义在 common.exception.ErrorCode，此处保留兼容 shim，
    // 跨模块（task / plan）请直接 import ErrorCode。

    public static final int ERR_VALIDATION = ErrorCode.VALIDATION;
    public static final int ERR_BAD_CREDENTIALS = ErrorCode.BAD_CREDENTIALS;
    public static final int ERR_CROSS_USER = ErrorCode.CROSS_USER;
    public static final int ERR_NOT_FOUND = ErrorCode.NOT_FOUND;
    public static final int ERR_EMAIL_TAKEN = ErrorCode.EMAIL_TAKEN;
    public static final int ERR_LOGIN_RATE_LIMIT = ErrorCode.LOGIN_RATE_LIMIT;
    public static final int ERR_REFRESH_INVALID = ErrorCode.REFRESH_INVALID;
}