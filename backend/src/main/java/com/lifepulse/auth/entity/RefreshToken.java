package com.lifepulse.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * t_refresh_token 实体（spec §2.4）。
 *
 * <p>无 {@code deleted} 字段——refresh token 一旦撤销（{@code revoked_at} 非空）即视作过期；
 * 不使用逻辑删除。{@code createdAt} 由 {@link com.lifepulse.common.mybatis.AutoFillMetaObjectHandler} 注入。
 */
@Data
@TableName("t_refresh_token")
public class RefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tokenHash;

    private OffsetDateTime expiresAt;

    private OffsetDateTime revokedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
