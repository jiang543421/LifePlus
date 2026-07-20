package com.lifepulse.expense;

import java.time.Duration;

/**
 * Expense 模块常量集中点（plan §5）。
 *
 * <p>错误码复用 {@link com.lifepulse.common.exception.ErrorCode}
 * （1001 校验 / 1002 未登录 / 1003 跨用户或不存在 / 1006 限流）。
 * 本类只声明 expense 模块特有的分页阈值与写端点限流参数。
 */
public final class ExpenseConstants {

    private ExpenseConstants() {
        // no instances
    }

    // ---- 写端点限流（spec §6 / plan §9）----
    /** Redis key 前缀：{@code lp:rl:expense:write:<userId>}。 */
    public static final String WRITE_RL_KEY_PREFIX = "lp:rl:expense:write:";

    /** 写端点每窗口最大调用次数。 */
    public static final int WRITE_RL_MAX = 10;

    /** 写端点限流窗口。 */
    public static final Duration WRITE_RL_WINDOW = Duration.ofMinutes(1);

    // ---- 分页默认值与上限（spec §5 GET /expenses）----
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // ---- 字段长度上限（spec §4 t_expense）----
    public static final int MAX_NOTE_LEN = 200;
}