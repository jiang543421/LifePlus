package com.lifepulse.ai;

import java.time.Duration;

/**
 * AI 模块全局常量（spec §6.1 / §6.3 / §8）。
 *
 * <p>所有魔法数字、键名集中在此，便于 review 与未来重构。
 */
public final class AiConstants {

    /** Redis 缓存键前缀（spec §9）。 */
    public static final String CACHE_KEY_PREFIX = "ai:insight:";

    /** 缓存 TTL 30 分钟（spec §6.1）。 */
    public static final long CACHE_TTL_MINUTES = 30L;

    /** 卡面固定 chip 数（spec §6.3）。 */
    public static final int CHIP_SLOT_COUNT = 3;

    /** headline 模板键。 */
    public static final String TMPL_HEADLINE_FULL = "headline.full";
    public static final String TMPL_HEADLINE_TASK_ONLY = "headline.taskOnly";
    public static final String TMPL_HEADLINE_EXPENSE_ONLY = "headline.expenseOnly";
    public static final String TMPL_HEADLINE_EMPTY = "headline.empty";

    /** chip 副标模板键前缀。 */
    public static final String TMPL_CHIP_PREFIX = "chip.";

    /** chip key 常量（与 DTO enum 对齐）。 */
    public static final String CHIP_TASK_COMPLETION = "taskCompletion";
    public static final String CHIP_WEEKLY_EXPENSE = "weeklyExpense";
    public static final String CHIP_PLAN_DENSITY = "planDensity";
    public static final String CHIP_DIET_INTAKE = "dietIntake";
    public static final String CHIP_DAILY_STREAK = "dailyStreak";

    /** Provider key 常量。 */
    public static final String PROVIDER_TASK = "task";
    public static final String PROVIDER_PLAN = "plan";
    public static final String PROVIDER_EXPENSE = "expense";
    public static final String PROVIDER_DIET = "diet";
    public static final String PROVIDER_DAILY = "daily";

    /** AI 洞察端点限流（spec §7.4）。按 userId 维度。GET 60/min 抗前端频繁拉取。 */
    public static final int INSIGHT_GET_RL_MAX = 60;
    public static final Duration INSIGHT_GET_RL_WINDOW = Duration.ofMinutes(1);

    /** AI 洞察主动刷新端点限流：POST 3/min（v2.1 由 6/min 收紧，CLAUDE.md §11.4）。 */
    public static final int INSIGHT_REFRESH_RL_MAX = 3;
    public static final Duration INSIGHT_REFRESH_RL_WINDOW = Duration.ofMinutes(1);

    /** AI 洞察限流 Redis key 前缀：完整 key {@code lp:rl:ai:insight:<userId>}。 */
    public static final String INSIGHT_RL_KEY_PREFIX = "lp:rl:ai:insight:";

    private AiConstants() {
        // 静态工具类，禁止实例化
    }
}
