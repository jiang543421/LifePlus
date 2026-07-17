package com.lifepulse.plan.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.plan.entity.Plan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * t_plan MyBatis-Plus Mapper。
 *
 * <p>{@link BaseMapper} 提供 insert / updateById / selectById / selectList / deleteById 等
 * 通用 CRUD（自动 {@code WHERE deleted = 0}）。下方 3 个自定义方法覆盖：
 * <ul>
 *   <li>{@link #findByUserAndId} — 跨用户 1003 防御的基础</li>
 *   <li>{@link #pageByUser} — 日历范围查询（{@code start_time BETWEEN from AND to}），
 *       配套 {@link #countByUser} 算 total</li>
 * </ul>
 *
 * <p>所有 raw {@code @Select} 必须显式 {@code AND deleted = 0}，
 * {@code BaseMapper} 的逻辑删除过滤不作用于手写 SQL。
 *
 * <p><b>跨日事件语义</b>（CLAUDE.md Phase 3 决策）：DB 一条记录，前端 CalendarMonth
 * 跨日连续渲染。范围查询以 {@code start_time ∈ [from, to]} 为匹配条件，{@code end_time}
 * 超出 {@code to} 的事件（跨月）将不在本月范围结果中——属于已知限制，
 * 待 Phase 3+ 加 {@code (user_id, end_time)} 索引时升级为 overlap 查询。
 */
@Mapper
public interface PlanMapper extends BaseMapper<Plan> {

    /**
     * 按 userId + id 取单条（跨用户 1003 防御的核心查询）。
     *
     * <p>必须显式 {@code AND deleted = 0}，{@code BaseMapper} 自动过滤不生效。
     *
     * @return 命中返回 Optional，跨用户 / 不存在 / 已软删均返回 {@code Optional.empty()}
     */
    @Select("""
            SELECT * FROM t_plan
            WHERE user_id = #{userId} AND id = #{id} AND deleted = 0
            LIMIT 1
            """)
    Optional<Plan> findByUserAndId(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 日历范围查询（spec §2.3 + Phase 3 决策）：
     * {@code start_time ∈ [from, to]}（含两端），按 {@code start_time ASC} 排序。
     *
     * <p>前端 CalendarMonth 加载某月事件；MVP1 不引入分页插件，
     * 改用 {@code LIMIT/OFFSET} 手写，size 上限由 service 层兜底
     * （{@code <= PlanConstants.MAX_PAGE_SIZE}）。
     *
     * @param userId  当前用户（必填，强制跨用户隔离）
     * @param from    范围下界（含）；{@code null} 视为无下界
     * @param to      范围上界（含）；{@code null} 视为无上界
     * @param offset  SQL OFFSET
     * @param size    SQL LIMIT
     */
    @Select("""
            SELECT * FROM t_plan
            WHERE user_id = #{userId} AND deleted = 0
              AND (#{from} IS NULL OR start_time >= #{from})
              AND (#{to}   IS NULL OR start_time <= #{to})
            ORDER BY start_time ASC, id ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Plan> pageByUser(@Param("userId") Long userId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("offset") int offset,
                          @Param("size") int size);

    /**
     * 配套 {@link #pageByUser} 的 total 计数，过滤条件一致（无 LIMIT/OFFSET）。
     */
    @Select("""
            SELECT COUNT(*) FROM t_plan
            WHERE user_id = #{userId} AND deleted = 0
              AND (#{from} IS NULL OR start_time >= #{from})
              AND (#{to}   IS NULL OR start_time <= #{to})
            """)
    long countByUser(@Param("userId") Long userId,
                     @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);
}