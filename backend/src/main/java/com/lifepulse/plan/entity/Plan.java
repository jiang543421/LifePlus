package com.lifepulse.plan.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * t_plan 实体（spec §2.3）。
 *
 * <p>逻辑删除由 {@link TableLogic} 控制：{@code BaseMapper} 自动追加 {@code WHERE deleted = 0}。
 * {@code createdAt} / {@code updatedAt} 由 {@link com.lifepulse.common.mybatis.AutoFillMetaObjectHandler}
 * 注入，应用层不需手动设值。
 *
 * <p>{@code startTime} / {@code endTime} 是 {@code DATETIME} 列，用 {@link LocalDateTime}
 * 承载（无时区信息）；序列化时由 Jackson 配合 Spring Boot 默认行为输出 ISO 字符串，
 * 前端 dayjs TZ {@code Asia/Shanghai} 解释。
 *
 * <p>{@code reminderMin} 为 MVP1 占位字段（spec §2.3），仅持久化不实现推送。
 */
@Data
@TableName("t_plan")
public class Plan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0=非全天 / 1=全天。 */
    private Integer allDay;

    private String location;

    private String note;

    /** MVP1 占位：默认值见 {@link com.lifepulse.plan.PlanConstants#DEFAULT_REMINDER_MIN}。 */
    private Integer reminderMin;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}