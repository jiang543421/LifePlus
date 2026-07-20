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
    // Returns Map<categoryEnglishName, summedAmount>; categories with zero rows
    // are absent and must be backfilled with 0 by the service layer.
    Map<String, BigDecimal> summaryByCategory(@Param("userId") Long userId,
                                             @Param("from") OffsetDateTime from,
                                             @Param("to") OffsetDateTime to);

    // Range total amount; backs the totalAmount field of summary response.
    BigDecimal summaryTotal(@Param("userId") Long userId,
                            @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to);
}
