package com.lifepulse.task;

/**
 * Task 模块常量集中点（plan §5）。
 *
 * <p>错误码复用 {@link com.lifepulse.auth.AuthConstants}（1001/1003/1004），
 * 本类只声明 task 模块特有的状态、优先级、字段长度、分页阈值等常量，
 * 避免在 mapper / service / controller 中散落魔法数字。
 */
public final class TaskConstants {

    private TaskConstants() {
        // no instances
    }

    // ---- 状态枚举字面值（spec §2.2）----
    public static final int STATUS_TODO = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_CANCELLED = 2;

    /** 状态校验下界（DTO @Min 用）。 */
    public static final int MIN_STATUS = 0;
    /** 状态校验上界（DTO @Max 用）。 */
    public static final int MAX_STATUS = 2;

    // ---- 优先级枚举字面值（spec §2.2）----
    public static final int PRIORITY_NONE = 0;
    public static final int PRIORITY_LOW = 1;
    public static final int PRIORITY_MEDIUM = 2;
    public static final int PRIORITY_HIGH = 3;

    /** 优先级校验上界（DTO @Max 用）。 */
    public static final int MAX_PRIORITY = 3;

    // ---- 字段长度上限（spec §2.2）----
    public static final int MAX_TITLE_LEN = 200;
    public static final int MAX_TAG_LEN = 64;

    // ---- 分页默认值与上限（spec §5.3 GET /tasks）----
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}