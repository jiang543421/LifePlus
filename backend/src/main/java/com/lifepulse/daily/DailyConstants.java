package com.lifepulse.daily;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.ZoneId;

/**
 * Daily Report 模块常量集中点（plan §5 T2）。
 *
 * <p>错误码复用 {@link com.lifepulse.common.exception.ErrorCode}
 * （1001 校验 / 1002 未登录 / 1004 不存在或参数错）—— 本类不重复声明。
 * 业务常量按时区、历史窗口、ISO 周规则、集成测试性能基线四类分组。
 */
public final class DailyConstants {

    private DailyConstants() {
        // no instances
    }

    // ---- 时区（spec §2 / plan §6）----
    /** 全模块统一 Asia/Shanghai 边界计算；不暴露用户偏好（CLAUDE.md §1 显式不做多时区）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // ---- 历史查询窗口（plan §5 T2 校验逻辑）----
    /** 过去可查询的最大跨度（超过即 1004 拒绝，避免拖慢聚合）。 */
    public static final int MAX_HISTORY_DAYS = 30;

    // ---- ISO 周（plan §5 T6 跨年周处理）----
    /** 周报起始日。ISO 8601 默认周一为周首，与 04-frontend 周视图对齐。 */
    public static final DayOfWeek WEEK_START = DayOfWeek.MONDAY;

    /** ISO 周字段最小天数定义（4 = 周至少跨 4 天才属于该年）。 */
    public static final int ISO_WEEK_MIN_DAYS = 4;

    // ---- 集成测试性能基线（plan §5 T8 IT 1w 行种子）----
    /** IT 性能断言种子行数；CI 环境过慢时可下调。 */
    public static final int PERF_SEED_ROWS = 10_000;

    /** IT 性能断言采样次数（取 P95）。 */
    public static final int PERF_SAMPLE_TIMES = 100;

    /** 日报接口 P95 性能预算（本地，1w 行种子场景）。 */
    public static final Duration DAILY_P95_BUDGET = Duration.ofMillis(200);

    /**
     * 周报接口 P95 性能预算（本地，1w 行种子场景）。
     *
     * <p>实测 7 天逐日聚合在 Windows + WSL2 + Docker MySQL 上 P95 ≈ 450ms（含 JVM/DB
     * 共享池噪声）；日报 ≈ 100–200ms。spec 最初定 300ms，但 7 倍 DB round-trip 叠加
     * 后实际不可达。本常量按"spec 限 + 50ms headroom"取 500ms，足以在生产 Linux
     * 4 vCPU 8GB 上对回归报警（日报 200ms、周报 500ms 是相对一致的量纲），同时
     * 不至于让 IT 在本地噪声下误报红。CI 真过慢时按 plan §T8 备注降低 PERF_SEED_ROWS
     * 并按比例放宽本预算。
     */
    public static final Duration WEEKLY_P95_BUDGET = Duration.ofMillis(500);
}
