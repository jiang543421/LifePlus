package com.lifepulse.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * t_task 实体（spec §2.2）。
 *
 * <p>逻辑删除由 {@link TableLogic} 控制：{@code BaseMapper} 自动追加 {@code WHERE deleted = 0}。
 * {@code createdAt} / {@code updatedAt} 由 {@link com.lifepulse.common.mybatis.AutoFillMetaObjectHandler}
 * 注入，应用层不需手动设值。
 *
 * <p>{@code status} / {@code priority} 为 {@code TINYINT}，实体用 {@code Integer}
 * 直接承载（MyBatis-Plus 默认类型映射）；枚举字面值见
 * {@link com.lifepulse.task.TaskConstants}。{@code dueDate} 为 {@code DATE} 列，
 * 用 {@link LocalDate} 承载（不带时区）。
 */
@Data
@TableName("t_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long planId;

    private String title;

    /** 0=待办 / 1=已完成 / 2=已取消（见 {@link com.lifepulse.task.TaskConstants}）。 */
    private Integer status;

    /** 0=无 / 1=低 / 2=中 / 3=高（见 {@link com.lifepulse.task.TaskConstants}）。 */
    private Integer priority;

    private LocalDate dueDate;

    private String tag;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}