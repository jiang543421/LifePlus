package com.lifepulse.diet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifepulse.diet.entity.Diet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * t_diet MyBatis-Plus Mapper (spec 07-diet-design section 5).
 *
 * <p>BaseMapper provides generic CRUD: insert / updateById / selectById /
 * selectList / deleteById (auto-appends WHERE deleted = 0). Custom SQL lives
 * in the XML resource; MyBatis-Plus auto-scans classpath mapper directories.
 *
 * <p>All raw SQL MUST explicitly include AND deleted = 0; the BaseMapper
 * logical-delete filter does NOT apply to XML statements.
 *
 * <p>App-layer UTC + DATETIME(3) column; range queries are inclusive on both
 * ends (>= / <=). For per-day summary the service computes the [from, to]
 * window from a LocalDate.
 */
@Mapper
public interface DietMapper extends BaseMapper<Diet> {

    /**
     * Single-row lookup by userId + id (cross-user 1003 defense core query).
     * @return entity; null when missing / cross-user / soft-deleted.
     */
    Diet findByUserAndId(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * Filtered + paged list (spec section 5 GET /diets).
     *
     * <p>{@code mealType} exact match ({@code MealType.name()}); {@code from} /
     * {@code to} inclusive range; sort fixed at occurred_at DESC, id DESC.
     */
    List<Diet> listByUser(@Param("userId") Long userId,
                          @Param("mealType") String mealType,
                          @Param("from") java.time.OffsetDateTime from,
                          @Param("to") java.time.OffsetDateTime to,
                          @Param("offset") int offset,
                          @Param("size") int size);

    /** Total count paired with {@link #listByUser}; identical filter conditions. */
    long countByUser(@Param("userId") Long userId,
                     @Param("mealType") String mealType,
                     @Param("from") java.time.OffsetDateTime from,
                     @Param("to") java.time.OffsetDateTime to);

    /**
     * Per-day 4-nutrition aggregation (spec section 5 GET /diets/summary).
     *
     * <p>Returns a single row Map<String, Object> with keys: kcal, proteinG,
     * carbG, fatG (each BigDecimal, COALESCE to 0 when no rows match).
     */
    Map<String, Object> summaryOnDate(@Param("userId") Long userId,
                                      @Param("from") java.time.OffsetDateTime from,
                                      @Param("to") java.time.OffsetDateTime to);

    /**
     * Frequent name aggregation (spec section 5 GET /diets/frequent) -
     * data source for the "one-click reuse" dropdown.
     *
     * <p>Group by {@code name}; output per-group average nutrition +
     * hitCount; sort by hitCount DESC; limit by {@code limit}.
     *
     * <p>Returned map fields: name, avgKcal, avgProteinG, avgCarbG, avgFatG,
     * hitCount (Long from COUNT).
     */
    List<Map<String, Object>> frequentByUser(@Param("userId") Long userId,
                                             @Param("from") java.time.OffsetDateTime from,
                                             @Param("to") java.time.OffsetDateTime to,
                                             @Param("limit") int limit);
}