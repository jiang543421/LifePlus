package com.lifepulse.plan;

/**
 * Plan 模块常量集中点（plan §5）。
 *
 * <p>错误码复用 {@link com.lifepulse.auth.AuthConstants}（1001/1003/1004），
 * 本类只声明 plan 模块特有的字段长度、reminder 默认值等常量，
 * 避免在 mapper / service / controller 中散落魔法数字。
 */
public final class PlanConstants {

    private PlanConstants() {
        // no instances
    }

    // ---- 字段长度上限（spec §2.3）----
    public static final int MAX_TITLE_LEN = 200;
    public static final int MAX_LOCATION_LEN = 200;
    /** TEXT 列建议上限：避免前端塞超大 payload。 */
    public static final int MAX_NOTE_LEN = 2000;

    // ---- reminder_min 默认值（spec §2.3：MVP1 占位字段）----
    public static final int DEFAULT_REMINDER_MIN = 15;
    /** reminder_min 校验上界（DTO @Max 用）。单位：分钟。 */
    public static final int MAX_REMINDER_MIN = 7 * 24 * 60; // 一周

    // ---- 分页默认值与上限（spec §5.4 GET /plans）----
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}