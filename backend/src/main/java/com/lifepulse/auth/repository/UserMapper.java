package com.lifepulse.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * t_user MyBatis-Plus Mapper。
 *
 * <p>{@link BaseMapper} 提供 CRUD；{@link #findByEmail} 是登录查重唯一查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            SELECT * FROM t_user
            WHERE email = #{email} AND deleted = 0
            LIMIT 1
            """)
    User findByEmail(String email);
}
