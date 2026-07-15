package com.lifepulse.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * t_refresh_token MyBatis-Plus Mapper（spec §2.4）。
 *
 * <p>{@code t_refresh_token} 表无 {@code deleted} 字段，所有查询直接按 hash 命中。
 * 撤销通过 {@link #revokeByHash} 写入 {@code revoked_at}。
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    @Select("""
            SELECT * FROM t_refresh_token
            WHERE token_hash = #{hash}
            LIMIT 1
            """)
    RefreshToken findByHash(@Param("hash") String hash);

    @Update("""
            UPDATE t_refresh_token
            SET revoked_at = #{now}
            WHERE token_hash = #{hash} AND revoked_at IS NULL
            """)
    int revokeByHash(@Param("hash") String hash, @Param("now") OffsetDateTime now);
}
