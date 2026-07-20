package com.lifepulse.diet.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.diet.DietConstants;
import com.lifepulse.diet.MealType;
import com.lifepulse.diet.dto.CreateDietRequest;
import com.lifepulse.diet.dto.DietFrequentItem;
import com.lifepulse.diet.dto.DietFilter;
import com.lifepulse.diet.dto.DietListItem;
import com.lifepulse.diet.dto.DietPageResponse;
import com.lifepulse.diet.dto.DietResponse;
import com.lifepulse.diet.dto.DietSummary;
import com.lifepulse.diet.dto.UpdateDietRequest;
import com.lifepulse.diet.entity.Diet;
import com.lifepulse.diet.repository.DietMapper;
import com.lifepulse.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Diet 服务（spec 07-diet-design section 5）。
 *
 * <p>所有公开方法通过 {@link UserContext#current()} 取当前用户 id。
 * 跨用户 / 不存在 / 已软删 一律抛 {@link BusinessException}({@link ErrorCode#CROSS_USER})，
 * 严禁用 {@code Optional.empty()} 隐式掩盖（CLAUDE.md section 4.5 hard rule）。
 *
 * <p>构造器注入，不依赖 Lombok（与 TaskService / ExpenseService 风格一致）。
 */
@Service
public class DietService {

    private static final Logger log = LoggerFactory.getLogger(DietService.class);

    private final DietMapper mapper;
    private final RateLimiter rateLimiter;

    public DietService(DietMapper mapper, RateLimiter rateLimiter) {
        this.mapper = mapper;
        this.rateLimiter = rateLimiter;
    }

    // ---------- create ----------

    /**
     * 新增一笔饮食；写端点 1006 限流（10/min/user）。
     *
     * @throws BusinessException 1001 mealType 不在 enum / name blank；
     *                           1002 未登录；1006 限流
     */
    public DietResponse create(CreateDietRequest req) {
        Long userId = requireUserId();
        requireWriteRateLimit(userId);

        MealType meal = parseMealType(req.mealType());
        requireNotBlank(req.name(), "name");
        requireNonNegative(req.kcal(), "kcal");
        requireNonNegative(req.proteinG(), "proteinG");
        requireNonNegative(req.carbG(), "carbG");
        requireNonNegative(req.fatG(), "fatG");

        Diet d = new Diet();
        d.setUserId(userId);
        d.setMealType(meal);
        d.setName(req.name().trim());
        d.setKcal(req.kcal());
        d.setProteinG(req.proteinG());
        d.setCarbG(req.carbG());
        d.setFatG(req.fatG());
        d.setNote(req.note());
        d.setOccurredAt(req.occurredAt());
        mapper.insert(d);
        log.debug("diet created uid={} id={} meal={}", userId, d.getId(), meal);
        return DietResponse.from(d);
    }

    // ---------- getById ----------

    /**
     * 详情查询；跨用户 / 不存在 / 已软删 → 1003。
     */
    public DietResponse getById(long id) {
        Long userId = requireUserId();
        Diet d = mapper.findByUserAndId(userId, id);
        if (d == null) {
            log.warn("diet get cross-user or missing uid={} id={}", userId, id);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该饮食");
        }
        return DietResponse.from(d);
    }

    // ---------- list ----------

    /**
     * 过滤 + 分页列表。
     *
     * <p>{@code mealType} / {@code from} / {@code to} 均为可选 null。
     */
    public DietPageResponse list(DietFilter f) {
        Long userId = requireUserId();
        int offset = (f.page() - 1) * f.size();
        List<Diet> rows = mapper.listByUser(
                userId, f.mealType(), f.from(), f.to(), offset, f.size());
        long total = mapper.countByUser(userId, f.mealType(), f.from(), f.to());
        List<DietListItem> items = rows.stream().map(DietListItem::from).toList();
        return DietPageResponse.of(items, total, f.page(), f.size());
    }

    // ---------- update ----------

    /**
     * 修改已有饮食；null-skip 语义；写端点 1006 限流。
     *
     * @throws BusinessException 1001 mealType 非法 / name blank；
     *                           1003 跨用户；1006 限流
     */
    public void update(long id, UpdateDietRequest req) {
        Long userId = requireUserId();
        requireWriteRateLimit(userId);

        Diet d = mapper.findByUserAndId(userId, id);
        if (d == null) {
            log.warn("diet update cross-user or missing uid={} id={}", userId, id);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该饮食");
        }

        if (req.mealType() != null) {
            d.setMealType(parseMealType(req.mealType()));
        }
        if (req.name() != null) {
            requireNotBlank(req.name(), "name");
            d.setName(req.name().trim());
        }
        if (req.kcal() != null) d.setKcal(req.kcal());
        if (req.proteinG() != null) d.setProteinG(req.proteinG());
        if (req.carbG() != null) d.setCarbG(req.carbG());
        if (req.fatG() != null) d.setFatG(req.fatG());
        if (req.note() != null) {
            // 空字符串视为清空备注 → null
            d.setNote(req.note().trim().isEmpty() ? null : req.note().trim());
        }
        if (req.occurredAt() != null) d.setOccurredAt(req.occurredAt());

        mapper.updateById(d);
        log.debug("diet updated uid={} id={}", userId, id);
    }

    // ---------- softDelete ----------

    /**
     * 软删（{@code BaseMapper.deleteById} 由 {@code @TableLogic} 翻 {@code deleted=1}）。
     */
    public void softDelete(long id) {
        Long userId = requireUserId();
        requireWriteRateLimit(userId);

        Diet d = mapper.findByUserAndId(userId, id);
        if (d == null) {
            log.warn("diet delete cross-user or missing uid={} id={}", userId, id);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该饮食");
        }
        mapper.deleteById(d.getId());
        log.debug("diet deleted uid={} id={}", userId, id);
    }

    // ---------- summary ----------

    /**
     * 当日营养汇总 + vs 昨日 / vs 上周同日 kcal 差值。
     *
     * <p>「昨日 / 上周同日」窗口 kcal = 0 时视为无数据，对应 delta 返回 null
     * （PRD section 3.1 「无对比数据时显示『无对比数据』」）。
     */
    public DietSummary summary(LocalDate day) {
        Long userId = requireUserId();
        Map<String, Object> today = mapper.summaryOnDate(userId,
                dayStart(day), dayEnd(day));
        BigDecimal kcalToday = asDecimal(today.get("kcal"));
        BigDecimal proteinToday = asDecimal(today.get("proteinG"));
        BigDecimal carbToday = asDecimal(today.get("carbG"));
        BigDecimal fatToday = asDecimal(today.get("fatG"));

        BigDecimal kcalDeltaYesterday = computeKcalDelta(userId, day.minusDays(1), kcalToday);
        BigDecimal kcalDeltaLastWeek = computeKcalDelta(userId, day.minusDays(7), kcalToday);

        return new DietSummary(
                kcalToday, proteinToday, carbToday, fatToday,
                kcalDeltaYesterday, kcalDeltaLastWeek);
    }

    private BigDecimal computeKcalDelta(Long userId, LocalDate compareDay, BigDecimal kcalToday) {
        Map<String, Object> row = mapper.summaryOnDate(userId,
                dayStart(compareDay), dayEnd(compareDay));
        BigDecimal kcalCompare = asDecimal(row.get("kcal"));
        // 对比日 0 → 无数据；今日 0 → 也视为无数据，delta 为 null
        if (kcalCompare.compareTo(BigDecimal.ZERO) == 0
                || kcalToday.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return kcalToday.subtract(kcalCompare);
    }

    private static OffsetDateTime dayStart(LocalDate d) {
        return d.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime dayEnd(LocalDate d) {
        return d.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
    }

    // ---------- frequent ----------

    /**
     * 高频名称聚合（录入弹窗「一键复用」数据源）。
     *
     * <p>默认窗口近 {@link DietConstants#DEFAULT_FREQUENT_DAYS} 天；
     * 默认 top {@link DietConstants#DEFAULT_FREQUENT_LIMIT}；
     * limit 超过 {@link DietConstants#MAX_FREQUENT_LIMIT} 自动截断（防御性）。
     */
    public List<DietFrequentItem> frequent(OffsetDateTime from, OffsetDateTime to, Integer limit) {
        Long userId = requireUserId();
        OffsetDateTime fromEff = from != null ? from
                : OffsetDateTime.now().minusDays(DietConstants.DEFAULT_FREQUENT_DAYS);
        OffsetDateTime toEff = to != null ? to : OffsetDateTime.now();
        int limitEff = limit == null
                ? DietConstants.DEFAULT_FREQUENT_LIMIT
                : Math.min(Math.max(limit, 1), DietConstants.MAX_FREQUENT_LIMIT);
        List<Map<String, Object>> rows = mapper.frequentByUser(userId, fromEff, toEff, limitEff);
        return rows.stream()
                .map(r -> new DietFrequentItem(
                        (String) r.get("name"),
                        asDecimal(r.get("avgKcal")),
                        asDecimal(r.get("avgProteinG")),
                        asDecimal(r.get("avgCarbG")),
                        asDecimal(r.get("avgFatG")),
                        asInt(r.get("hitCount"))))
                .toList();
    }

    // ---------- private helpers ----------

    private Long requireUserId() {
        Long userId = UserContext.current();
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
        return userId;
    }

    private void requireWriteRateLimit(Long userId) {
        boolean limited = rateLimiter.hit(
                DietConstants.WRITE_RL_KEY_PREFIX + userId,
                DietConstants.WRITE_RL_MAX,
                DietConstants.WRITE_RL_WINDOW);
        if (limited) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMIT, "操作过于频繁，请稍后再试");
        }
    }

    private static MealType parseMealType(String s) {
        if (s == null || s.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "mealType 不合法");
        }
        try {
            return MealType.valueOf(s.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION, "mealType 不在合法范围");
        }
    }

    private static void requireNotBlank(String s, String field) {
        if (s == null || s.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, field + " 不能为空");
        }
    }

    private static void requireNonNegative(BigDecimal v, String field) {
        if (v != null && v.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, field + " 不能小于 0");
        }
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static Integer asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}