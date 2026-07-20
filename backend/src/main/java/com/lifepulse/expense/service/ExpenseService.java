package com.lifepulse.expense.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.ExpenseConstants;
import com.lifepulse.expense.dto.CategoryItem;
import com.lifepulse.expense.dto.CreateExpenseRequest;
import com.lifepulse.expense.dto.ExpenseFilter;
import com.lifepulse.expense.dto.ExpenseListItem;
import com.lifepulse.expense.dto.ExpenseResponse;
import com.lifepulse.expense.dto.ExpenseSummaryResponse;
import com.lifepulse.expense.dto.UpdateExpenseRequest;
import com.lifepulse.expense.entity.Expense;
import com.lifepulse.expense.repository.ExpenseMapper;
import com.lifepulse.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expense service (spec 06-expense section 5).
//
// All public methods read current user id via UserContext.current().
// Cross-user / missing / soft-deleted uniformly throw
// BusinessException(ErrorCode.CROSS_USER) -- never silently mask via Optional.
//
// Constructor injection keeps the unit-testable surface explicit (no Lombok
// @RequiredArgsConstructor; matches TaskService style).
@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseMapper mapper;
    private final RateLimiter rateLimiter;

    public ExpenseService(ExpenseMapper mapper, RateLimiter rateLimiter) {
        this.mapper = mapper;
        this.rateLimiter = rateLimiter;
    }

    // ---------- create ----------

    // Create an expense for the current user.
    // Write-side 1006 rate-limit (10 ops/min/user, plan Risk 6).
    public ExpenseResponse create(CreateExpenseRequest req) {
        Long userId = requireUserId();
        requireWriteRateLimit(userId);

        Expense e = new Expense();
        e.setUserId(userId);
        e.setAmount(req.amount());
        e.setCategory(req.category());
        e.setNote(req.note());
        e.setOccurredAt(req.occurredAt());
        mapper.insert(e);
        log.debug("expense created uid={} id={} category={}", userId, e.getId(), req.category());
        return ExpenseResponse.from(e);
    }

    // ---------- getById ----------

    // Detail lookup; cross-user / missing / soft-deleted all -> 1003.
    public ExpenseResponse getById(long id) {
        Long userId = requireUserId();
        Expense e = mapper.findByUserAndId(userId, id);
        if (e == null) {
            log.warn("expense get cross-user or missing uid={} id={}", userId, id);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该消费");
        }
        return ExpenseResponse.from(e);
    }

    // ---------- list ----------

    // Paged list with optional category + occurred_at range.
    // Filter params: null = no filter (mapper IS NULL branches).
    public PageResponse<ExpenseListItem> list(ExpenseFilter f) {
        Long userId = requireUserId();
        int offset = (f.page() - 1) * f.size();
        String categoryName = f.category() == null ? null : f.category().name();
        List<Expense> rows = mapper.listByUser(
                userId, categoryName, f.from(), f.to(), offset, f.size());
        long total = mapper.countByUser(userId, categoryName, f.from(), f.to());
        List<ExpenseListItem> items = rows.stream().map(ExpenseListItem::from).toList();
        return PageResponse.of(items, total, f.page(), f.size());
    }

    // ---------- update ----------

    // Partial update: null fields skipped. Cross-user / missing -> 1003.
    // Write-side 1006 rate-limit.
    public void update(long id, UpdateExpenseRequest req) {
        Long userId = requireUserId();
        requireWriteRateLimit(userId);

        Expense e = mapper.findByUserAndId(userId, id);
        if (e == null) {
            log.warn("expense update cross-user or missing uid={} id={}", userId, id);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该消费");
        }

        if (req.amount() != null) e.setAmount(req.amount());
        if (req.category() != null) e.setCategory(req.category());
        if (req.note() != null) e.setNote(req.note());
        if (req.occurredAt() != null) e.setOccurredAt(req.occurredAt());

        mapper.updateById(e);
        log.debug("expense updated uid={} id={}", userId, id);
    }

    // ---------- softDelete ----------

    // Soft delete (BaseMapper.deleteById flips deleted=1 via @TableLogic).
    public void softDelete(long id) {
        Long userId = requireUserId();
        requireWriteRateLimit(userId);

        Expense e = mapper.findByUserAndId(userId, id);
        if (e == null) {
            log.warn("expense delete cross-user or missing uid={} id={}", userId, id);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该消费");
        }
        mapper.deleteById(e.getId());
        log.debug("expense deleted uid={} id={}", userId, id);
    }

    // ---------- summary ----------

    // Monthly summary for current user.
    // Returns per-category amount (zero-filled for absent categories) + total.
    public ExpenseSummaryResponse summary(int year, int month) {
        Long userId = requireUserId();
        YearMonth ym = YearMonth.of(year, month);
        OffsetDateTime from = ym.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        // Exclusive upper bound: first instant of next month (mapper uses < to).
        OffsetDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        BigDecimal total = nz(mapper.summaryTotal(userId, from, to));
        Map<String, BigDecimal> raw = mapper.summaryByCategory(userId, from, to);
        Map<String, BigDecimal> byCategory = zeroFilled(raw);
        return new ExpenseSummaryResponse(
                LocalDate.of(year, month, 1),
                LocalDate.of(year, month, 1),
                byCategory,
                total
        );
    }

    // ---------- categories ----------

    // Static metadata listing for UI dropdowns / labels (spec §5 GET /expenses/categories).
    // No user-specific data; order matches ExpenseCategory declaration.
    public List<CategoryItem> categories() {
        return Arrays.stream(ExpenseCategory.values())
                .map(c -> new CategoryItem(c.name(), c.getLabel()))
                .toList();
    }

    // ---------- private helpers ----------

    // Read current userId; defensive 1002 if filter chain missed.
    private Long requireUserId() {
        Long userId = UserContext.current();
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
        return userId;
    }

    // Write-side rate limit (1006) - 10 ops/min/user (plan Risk 6 + section 5).
    private void requireWriteRateLimit(Long userId) {
        boolean limited = rateLimiter.hit(
                ExpenseConstants.WRITE_RL_KEY_PREFIX + userId,
                ExpenseConstants.WRITE_RL_MAX,
                ExpenseConstants.WRITE_RL_WINDOW);
        if (limited) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMIT, "操作过于频繁，请稍后再试");
        }
    }

    // null -> BigDecimal.ZERO (defensive; mapper SUM is non-null in MySQL).
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // Build a stable-key Map with all 5 categories present, zero-filled.
    // Insertion order = ExpenseCategory enum order (UI-friendly).
    private static Map<String, BigDecimal> zeroFilled(Map<String, BigDecimal> raw) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (ExpenseCategory c : ExpenseCategory.values()) {
            BigDecimal v = raw == null ? null : raw.get(c.name());
            out.put(c.name(), nz(v));
        }
        return out;
    }
}
