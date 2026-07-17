package com.lifepulse.task.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * t_task MyBatis-Plus Mapper。
 *
 * <p>{@link BaseMapper} 提供 insert / updateById / selectById / selectList / deleteById 等
 * 通用 CRUD（自动 {@code WHERE deleted = 0}）。下方 5 个自定义方法覆盖：
 * <ul>
 *   <li>{@link #findByUserAndId} — 跨用户 1003 防御的基础</li>
 *   <li>{@link #updateStatusByUser} — 状态切换，返回受影响行数（0 → 1003/1004）</li>
 *   <li>{@link #listByPlan} — 按 plan_id 聚合</li>
 *   <li>{@link #pageByUser} — 过滤 + 分页（手写 {@code LIMIT/OFFSET}，不引入 MP 分页插件）</li>
 *   <li>{@link #countByUser} — 配合 pageByUser 计算 total</li>
 * </ul>
 *
 * <p>所有 raw {@code @Select}/{@code @Update} 必须显式 {@code AND deleted = 0}，
 * {@code BaseMapper} 的逻辑删除过滤不作用于手写 SQL。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 按 userId + id 取单条（跨用户 1003 防御的核心查询）。
     *
     * <p>必须显式 {@code AND deleted = 0}，{@code BaseMapper} 自动过滤不生效。
     *
     * @return 命中返回 Optional，跨用户 / 不存在 / 已软删均返回 {@code Optional.empty()}
     */
    @Select("""
            SELECT * FROM t_task
            WHERE user_id = #{userId} AND id = #{id} AND deleted = 0
            LIMIT 1
            """)
    Optional<Task> findByUserAndId(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 状态切换：只更新指定 user 的 task，受影响行数 = 0 即视为越权或不存在（→ 1003）。
     *
     * @return 受影响行数；{@code 0} 表示目标不存在或不属于当前 user
     */
    @Update("""
            UPDATE t_task
            SET status = #{status}, updated_at = NOW()
            WHERE user_id = #{userId} AND id = #{id} AND deleted = 0
            """)
    int updateStatusByUser(@Param("userId") Long userId,
                           @Param("id") Long id,
                           @Param("status") int status);

    /**
     * 列出某 plan 下的所有 task（跨 user 隔离；plan_id 为 NULL 时返回空列表）。
     */
    @Select("""
            SELECT * FROM t_task
            WHERE user_id = #{userId} AND plan_id = #{planId} AND deleted = 0
            ORDER BY id ASC
            """)
    List<Task> listByPlan(@Param("userId") Long userId, @Param("planId") Long planId);

    /**
     * 过滤 + 分页（spec §5.3 GET /tasks）。
     *
     * <p>所有过滤参数可空：{@code null} 字段在 SQL 中以 {@code IS NULL} 跳过该过滤项。
     * 排序固定为 {@code id ASC}（MVP1 不支持自定义排序）。
     *
     * @param userId  当前用户（必填，强制跨用户隔离）
     * @param status  状态过滤（{@code null} → 不过滤）
     * @param priority 优先级过滤（{@code null} → 不过滤）
     * @param tag     标签精确匹配（{@code null}/空串 → 不过滤）
     * @param dueFrom 截止日期下界（含）（{@code null} → 无下界）
     * @param dueTo   截止日期上界（含）（{@code null} → 无上界）
     * @param offset  SQL OFFSET
     * @param size    SQL LIMIT（最大 {@link com.lifepulse.task.TaskConstants#MAX_PAGE_SIZE}）
     */
    @Select("""
            SELECT * FROM t_task
            WHERE user_id = #{userId} AND deleted = 0
              AND (#{status}   IS NULL OR status   = #{status})
              AND (#{priority} IS NULL OR priority = #{priority})
              AND (#{tag}      IS NULL OR tag      = #{tag})
              AND (#{dueFrom}  IS NULL OR due_date >= #{dueFrom})
              AND (#{dueTo}    IS NULL OR due_date <= #{dueTo})
            ORDER BY id ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Task> pageByUser(@Param("userId") Long userId,
                          @Param("status") Integer status,
                          @Param("priority") Integer priority,
                          @Param("tag") String tag,
                          @Param("dueFrom") LocalDate dueFrom,
                          @Param("dueTo") LocalDate dueTo,
                          @Param("offset") int offset,
                          @Param("size") int size);

    /**
     * 配套 {@link #pageByUser} 的 total 计数，过滤条件一致（无 LIMIT/OFFSET）。
     */
    @Select("""
            SELECT COUNT(*) FROM t_task
            WHERE user_id = #{userId} AND deleted = 0
              AND (#{status}   IS NULL OR status   = #{status})
              AND (#{priority} IS NULL OR priority = #{priority})
              AND (#{tag}      IS NULL OR tag      = #{tag})
              AND (#{dueFrom}  IS NULL OR due_date >= #{dueFrom})
              AND (#{dueTo}    IS NULL OR due_date <= #{dueTo})
            """)
    long countByUser(@Param("userId") Long userId,
                     @Param("status") Integer status,
                     @Param("priority") Integer priority,
                     @Param("tag") String tag,
                     @Param("dueFrom") LocalDate dueFrom,
                     @Param("dueTo") LocalDate dueTo);
}