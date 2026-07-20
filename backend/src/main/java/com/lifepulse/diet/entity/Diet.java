package com.lifepulse.diet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lifepulse.diet.MealType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * t_diet 实体（spec 07-diet-design §2）。
 *
 * <p>逻辑删除由 {@link TableLogic} 控制：{@code BaseMapper} 自动追加 {@code WHERE deleted = 0}。
 * {@code createdAt} / {@code updatedAt} 由 {@link com.lifepulse.common.mybatis.AutoFillMetaObjectHandler}
 * 自动注入，应用层不需手动设值。{@code occurredAt} 由用户录入，由 service 显式写入。
 *
 * <p>{@code mealType} 持久化为枚举名（与 DB CHECK 字面值一致），
 * MyBatis-Plus 默认通过 {@code MybatisEnumTypeHandler} 走 {@code name()}。
 */
@Data
@TableName("t_diet")
public class Diet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 4 个枚举值之一（见 {@link MealType}）。 */
    private MealType mealType;

    private String name;

    private BigDecimal kcal;
    private BigDecimal proteinG;
    private BigDecimal carbG;
    private BigDecimal fatG;

    /** 用户录入的发生时间（应用层 UTC）。 */
    private OffsetDateTime occurredAt;

    private String note;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}