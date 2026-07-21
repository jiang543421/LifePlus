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

    /**
     * 撤销某用户所有未撤销的 refresh token（Settings v1.1，issue 2026-07-18-settings-v1-1）。
     *
     * <p>用于改密码 / 注销账号后的强制下线：调用成功后该用户所有现存 refresh token
     * 均不可用于刷新 access token，但已签发的 access token 仍可存活至自然过期
     * （≤15min，{@code lp.jwt.access-ttl: PT15M}，issue 2026-07-18 HIGH-2 决策）。
     * MVP1 暂无 JWT deny-list。
     *
     * <p>幂等：重复调用或对无 token 用户调用均返回 0，不抛错。
     *
     * @param userId 用户 id
     * @param now    撤销时间
     * @return 受影响行数
     */
    @Update("""
            UPDATE t_refresh_token
            SET revoked_at = #{now}
            WHERE user_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
