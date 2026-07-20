package com.lifepulse.diet;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Diet 模块常量集中点（spec 07-diet-design §5/§6/§7）。
 *
 * <p>错误码复用 {@link com.lifepulse.common.exception.ErrorCode}
 * （1001 校验 / 1002 未登录 / 1003 跨用户或不存在 / 1004 不存在 / 1006 限流）。
 * 本类声明 diet 模块特有的分页阈值、写端点限流、推荐摄入常量、字段长度上限。
 *
 * <p>推荐摄入常量为人群体均值（PRD §5.1 / spec §6.3）；个性化目标留待 v2.x
 * （届时引入 user_profile 表）。
 */
public final class DietConstants {

    private DietConstants() {
        // no instances
    }

    // ---- 写端点限流（spec §7）----
    /** Redis key 前缀：{@code lp:rl:diet:write:<userId>}。 */
    public static final String WRITE_RL_KEY_PREFIX = "lp:rl:diet:write:";

    /** 写端点每窗口最大调用次数（与消费模块对齐）。 */
    public static final int WRITE_RL_MAX = 10;

    /** 写端点限流窗口。 */
    public static final Duration WRITE_RL_WINDOW = Duration.ofMinutes(1);

    // ---- 分页默认值与上限（spec §5 GET /diets）----
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // ---- 字段长度上限（spec §2 t_diet）----
    public static final int MAX_NAME_LEN = 64;
    public static final int MAX_NOTE_LEN = 200;

    // ---- 推荐摄入常量（spec §6.3 / PRD §5.1，人群体均值）----
    public static final BigDecimal REC_DAILY_KCAL = new BigDecimal("2000");
    public static final BigDecimal REC_DAILY_PROTEIN_G = new BigDecimal("60");
    public static final BigDecimal REC_DAILY_CARB_G = new BigDecimal("300");
    public static final BigDecimal REC_DAILY_FAT_G = new BigDecimal("65");

    // ---- 一键复用（frequent）默认窗口（spec §5 + PRD §3.1）----
    /** 默认回溯天数（30 天）。 */
    public static final int DEFAULT_FREQUENT_DAYS = 30;
    /** frequent 默认 top N。 */
    public static final int DEFAULT_FREQUENT_LIMIT = 10;
    /** frequent 上限（防御性，防止前端误传 10000）。 */
    public static final int MAX_FREQUENT_LIMIT = 50;
}