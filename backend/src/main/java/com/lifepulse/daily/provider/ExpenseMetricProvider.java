package com.lifepulse.daily.provider;

import com.lifepulse.daily.DailyConstants;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.repository.ExpenseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expense 数据源的日指标聚合器（plan §5 T4）。
 *
 * <p>调用时机与 Task/Plan 同；详见 {@link TaskMetricProvider}。
 *
 * <p><b>day 范围约定</b>：{@code occurred_at} 为 DATETIME(3) 按 UTC 存储
 * （spec 06-expense §4）。本 Provider 接收 Asia/Shanghai 的 {@link LocalDate}，
 * 内部按 {@link DailyConstants#ZONE} 转 UTC 半开区间
 * {@code [dayStartUtc, nextDayStartUtc)} 后传入 mapper。
 *
 * <p><b>复用策略</b>：
 * <ul>
 *   <li>{@link ExpenseMapper#summaryTotal} → {@link ExpenseMetrics#totalAmount()}</li>
 *   <li>{@link ExpenseMapper#summaryByCategory} → {@link ExpenseMetrics#categoryBreakdown()}
 *       （Provider 端补 0 缺失类别，保证 5 类齐全）</li>
 *   <li>新增 {@link ExpenseMapper#countByUserOnDay} → {@link ExpenseMetrics#count()}</li>
 *   <li>{@link ExpenseMetrics#topCategories()} Top 3 由 Provider 在
 *       {@code categoryBreakdown} 上派生（避免新增 SQL），零金额不入榜</li>
 * </ul>
 *
 * <p>构造器显式注入（与 {@code TaskService} 同款），便于单测手写 mock。
 */
@Component
public class ExpenseMetricProvider implements MetricProvider<ExpenseMetrics> {

    private static final Logger log = LoggerFactory.getLogger(ExpenseMetricProvider.class);

    /** Top N 数量（v1.2.3 固定 3，与 PRD §5 口径一致）。 */
    static final int TOP_N_CATEGORIES = 3;

    private final ExpenseMapper expenseMapper;

    public ExpenseMetricProvider(ExpenseMapper expenseMapper) {
        this.expenseMapper = expenseMapper;
    }

    @Override
    public ExpenseMetrics aggregateDaily(long userId, LocalDate date) {
        OffsetDateTime dayStartUtc = date.atStartOfDay(DailyConstants.ZONE)
                .toInstant().atOffset(ZoneOffset.UTC);
        OffsetDateTime nextDayStartUtc = date.plusDays(1).atStartOfDay(DailyConstants.ZONE)
                .toInstant().atOffset(ZoneOffset.UTC);

        BigDecimal totalAmount = expenseMapper.summaryTotal(userId, dayStartUtc, nextDayStartUtc);
        long count = expenseMapper.countByUserOnDay(userId, dayStartUtc, nextDayStartUtc);
        Map<String, BigDecimal> breakdown = toBreakdown(
                expenseMapper.summaryByCategory(userId, dayStartUtc, nextDayStartUtc));
        List<ExpenseMetrics.CategoryTop> top = topN(breakdown, TOP_N_CATEGORIES);

        log.debug("ExpenseMetricProvider user={} date={} total={} count={} topSize={}",
                userId, date, totalAmount, count, top.size());

        return new ExpenseMetrics(totalAmount, count, breakdown, top);
    }

    /**
     * 把 mapper 返回的 {@code List<Map<String, Object>>}（key: "k" / "v"）
     * 转成 {@code Map<String, BigDecimal>}（key: 类别枚举名）。
     *
     * <p>5 个 {@link ExpenseCategory} 全部填 {@code BigDecimal.ZERO} 默认值，
     * mapper 返回的行覆盖实际值。<b>零值统一规范化为 {@link BigDecimal#ZERO}（scale 0）</b>，
     * 避免 DB {@code DECIMAL(12,2)} 返回 {@code 0.00}（scale 2）与默认 {@code ZERO}
     * （scale 0）的 {@code equals} 不一致问题——前端按值格式化展示，scale 漂移
     * 对用户无意义但会污染 JSON 字段一致性与断言可读性。
     *
     * <p>返回 map 为 immutable 视图。
     */
    private static Map<String, BigDecimal> toBreakdown(List<Map<String, Object>> rows) {
        // 按枚举声明顺序填零，保证返回 map 顺序与 ExpenseCategory.values() 一致
        Map<String, BigDecimal> result = new LinkedHashMap<>(ExpenseCategory.values().length);
        for (ExpenseCategory c : ExpenseCategory.values()) {
            result.put(c.name(), BigDecimal.ZERO);
        }
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("k");
            BigDecimal v = (BigDecimal) row.get("v");
            if (code != null && v != null) {
                result.put(code, v.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : v);
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 按金额降序取前 N 个非零类别。
     *
     * <p>零金额类别不入榜（与 {@code topCategories.size() ∈ [0, TOP_N]} 的 API 约定一致）。
     * 金额相等时按枚举声明顺序稳定排序（{@link ExpenseCategory} 顺序即稳定键）。
     */
    private static List<ExpenseMetrics.CategoryTop> topN(
            Map<String, BigDecimal> breakdown, int n) {
        // 枚举顺序索引，保证并列时按枚举声明顺序取靠前者
        Map<String, Integer> orderIndex = new HashMap<>();
        ExpenseCategory[] cats = ExpenseCategory.values();
        for (int i = 0; i < cats.length; i++) {
            orderIndex.put(cats[i].name(), i);
        }

        List<Map.Entry<String, BigDecimal>> ranked = new ArrayList<>(breakdown.size());
        for (Map.Entry<String, BigDecimal> e : breakdown.entrySet()) {
            BigDecimal amt = e.getValue();
            if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                ranked.add(e);
            }
        }
        ranked.sort(Comparator
                .<Map.Entry<String, BigDecimal>>comparingDouble(
                        en -> en.getValue().doubleValue())
                .reversed()
                .thenComparing(en -> orderIndex.getOrDefault(en.getKey(), Integer.MAX_VALUE)));

        List<ExpenseMetrics.CategoryTop> result = new ArrayList<>(Math.min(n, ranked.size()));
        for (int i = 0; i < Math.min(n, ranked.size()); i++) {
            Map.Entry<String, BigDecimal> e = ranked.get(i);
            result.add(new ExpenseMetrics.CategoryTop(e.getKey(), e.getValue()));
        }
        return List.copyOf(result);
    }
}