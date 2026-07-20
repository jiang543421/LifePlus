package com.lifepulse.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * t_user MyBatis-Plus Mapper。
 *
 * <p>{@link BaseMapper} 提供 CRUD；{@link #findByEmail} 是登录查重唯一查询；
 * {@link #updateNicknameById} 是 Settings v1.1 改昵称的专用更新（用显式 SQL
 * 以支持空昵称落为 {@code NULL}——BaseMapper.updateById 默认 FieldStrategy.NOT_NULL
 * 会跳过 null 字段，导致清空昵称静默失效）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            SELECT * FROM t_user
            WHERE email = #{email} AND deleted = 0
            LIMIT 1
            """)
    User findByEmail(String email);

    /**
     * 更新昵称（Settings v1.1）。{@code nickname} 可为 {@code null}，DB 列允许 NULL。
     * 顺手刷新 {@code updated_at}；{@code AND deleted = 0} 防御软删行。
     *
     * @return 受影响行数（0 = 用户不存在或已软删）
     */
    @Update("""
            UPDATE t_user
            SET nickname = #{nickname}, updated_at = NOW(6)
            WHERE id = #{userId} AND deleted = 0
            """)
    int updateNicknameById(@Param("userId") Long userId, @Param("nickname") String nickname);
}
