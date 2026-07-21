package com.lifepulse.common.exception;

/**
 * 业务错误码集中定义（Review H-6：跨模块 magic number 散落）。
 *
 * <p>CLAUDE.md §4.2 / §4.5：所有 {@code BusinessException(code, msg)} 的
 * {@code code} 必须从此处取，禁止在 service / controller 中写魔法数字。
 *
 * <p>分组规则：
 * <ul>
 *   <li>{@code 1xxx} 通用业务：鉴权、跨用户、限流</li>
 *   <li>{@code 14xx} Refresh Token：与 JWT 旋转/重放相关</li>
 *   <li>{@code 15xx} 服务器内部：兜底错误</li>
 * </ul>
 *
 * <p>{@code AuthConstants.ERR_*} 保留为兼容 shim，等价引用；跨模块（task / plan）
 * 直接用本类即可。
 */
public final class ErrorCode {

    private ErrorCode() {
        // no instances
    }

    // ===== 通用 1xxx =====

    /** 1001：请求参数校验失败（@Valid 兜底或手动校验）。 */
    public static final int VALIDATION = 1001;

    /** 1002：未登录或凭证错误（统一返回防账号枚举）。 */
    public static final int BAD_CREDENTIALS = 1002;

    /** 1003：跨用户越权访问。CLAUDE.md §7.2 hard rule。 */
    public static final int CROSS_USER = 1003;

    /** 1004：资源不存在。 */
    public static final int NOT_FOUND = 1004;

    /** 1005：email 已被注册（含 DB 唯一索引兜底）。 */
    public static final int EMAIL_TAKEN = 1005;

    /** 1006：登录/注册限流。spec §7.2 "5 次失败/分钟"。 */
    public static final int LOGIN_RATE_LIMIT = 1006;

    // ===== Refresh Token 14xx =====

    /** 1401：refresh token 无效 / 重放 / 过期。CLAUDE.md §7.6 关键审计点。 */
    public static final int REFRESH_INVALID = 1401;

    // ===== 通用 15xx =====

    /** 1500：服务器内部错误兜底。 */
    public static final int INTERNAL = 1500;

    /** 1501：AI 洞察全部 provider 失败（spec §4.3）。 */
    public static final int AI_DEGRADED = 1501;
}