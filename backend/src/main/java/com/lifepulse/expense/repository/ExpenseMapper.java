package com.lifepulse.expense.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.expense.entity.Expense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// t_expense MyBatis-Plus Mapper (plan T5).
//
// BaseMapper supplies insert / updateById / selectById / selectList / deleteById
// generic CRUD (auto WHERE deleted = 0). Five custom SQL queries live in
// src/main/resources/mapper/expense/ExpenseMapper.xml
// (MyBatis-Plus default scan classpath*:/mapper/**/*.xml, auto-loaded).
//   findByUserAndId - cross-user 1003 defense core query
//   listByUser - category + occurred_at range + page
//   countByUser - total count paired with listByUser
//   summaryByCategory - per-category aggregated amount
//   summaryTotal - range total amount
//
// All raw SQL must explicitly include AND deleted = 0;
// the BaseMapper logical-delete filter does NOT apply to XML statements.
//
// App-layer UTC + DATETIME(3) column per spec 06-expense section 4 / section 6;
// range queries use inclusive >= / <= on both ends.
@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

    // Single-row lookup by userId + id (cross-user 1003 defense core query).
    // @return entity when found; null when missing / cross-user / soft-deleted
    Expense findByUserAndId(@Param("userId") Long userId, @Param("id") Long id);

    // Filtered + paged list (spec section 5 GET /expenses).
    //
    // category exact match (ExpenseCategory.name());
    // from/to inclusive range;
    // sort fixed at occurred_at DESC, id DESC.
    //
    // @param userId   current user (mandatory; enforces cross-user isolation)
    // @param category filter category (ExpenseCategory.name() or null for no filter)
    // @param from     occurred_at lower bound inclusive (null means no lower bound)
    // @param to       occurred_at upper bound inclusive (null means no upper bound)
    // @param offset   SQL OFFSET (service computes as (page - 1) * size)
    // @param size     SQL LIMIT (bounded by ExpenseConstants.MAX_PAGE_SIZE)
    List<Expense> listByUser(@Param("userId") Long userId,
                             @Param("category") String category,
                             @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to,
                             @Param("offset") int offset,
                             @Param("size") int size);

    // Total count paired with listByUser; identical filter conditions.
    long countByUser(@Param("userId") Long userId,
                     @Param("category") String category,
                     @Param("from") OffsetDateTime from,
                     @Param("to") OffsetDateTime to);

    // Per-category aggregated amount (spec section 5 GET /expenses/summary).
    // Returns a row list of {k=category, v=summedAmount}; categories with zero
    // rows are absent and must be backfilled with 0 by the service layer.
    // Why List<Map> instead of Map<String, BigDecimal>: the SELECT projects two
    // columns (category + SUM), so each row IS a map; with @MapKey MyBatis
    // would build Map<String, Map> (not Map<String, BigDecimal>). List<Map> lets
    // the service convert BigDecimal-typed "v" once.
    List<Map<String, Object>> summaryByCategory(@Param("userId") Long userId,
                                                @Param("from") OffsetDateTime from,
                                                @Param("to") OffsetDateTime to);

    // Range total amount; backs the totalAmount field of summary response.
    BigDecimal summaryTotal(@Param("userId") Long userId,
                            @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to);

    // ===== 日报聚合查询（v1.2.3 / daily 模块） =====
    // 设计说明：occurred_at 为 DATETIME(3) 按 UTC 存储（spec 06-expense §4）。
    // "某日"语义由调用方把 Asia/Shanghai 日期转为 UTC 半开区间传入；
    // V4 既有的 idx_user_occurred (user_id, occurred_at) 完美覆盖本查询。

    /**
     * 日报聚合：指定用户在目标日的事件笔数（UTC 半开区间 [dayStart, nextDayStart)）。
     *
     * <p>{@code dayStart} / {@code nextDayStart} 由 Provider 计算传入，
     * 避免 mapper 引入时区转换逻辑。
     */
    long countByUserOnDay(@Param("userId") Long userId,
                          @Param("dayStart") OffsetDateTime dayStart,
                          @Param("nextDayStart") OffsetDateTime nextDayStart);
}
