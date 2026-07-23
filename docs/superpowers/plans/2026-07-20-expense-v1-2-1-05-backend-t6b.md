        .containsExactlyInAnyOrder("MEAL", "SHOPPING", "TRANSPORT", "SUBSCRIPTION", "OTHER");
  }

  // ---------- softDelete additional ----------
  @Test void softDelete_rateLimited_throws1006() {
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(true);
    assertThatThrownBy(() -> service.softDelete(100L))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.RATE_LIMIT);
  }

  // ---------- update rate-limited ----------
  @Test void update_rateLimited_throws1006() {
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(true);
    var req = new UpdateExpenseRequest(null, null, null, null);
    assertThatThrownBy(() -> service.update(100L, req))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.RATE_LIMIT);
  }
}
```

- [ ] **Step 2**: Run → FAIL（service 不存在）

Run: `cd backend && mvn test -Dtest=ExpenseServiceTest -q`
Expected: Compilation failure

- [ ] **Step 3**: GREEN — 实现 service

```java
package com.lifepulse.expense.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.ExpenseConstants;
import com.lifepulse.expense.dto.*;
import com.lifepulse.expense.entity.Expense;
import com.lifepulse.expense.repository.ExpenseMapper;
import com.lifepulse.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class ExpenseService {

  private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

  private final ExpenseMapper mapper;
  private final RateLimiter rateLimiter;

  public ExpenseService(ExpenseMapper mapper, RateLimiter rateLimiter) {
    this.mapper = mapper;
    this.rateLimiter = rateLimiter;
  }

  public ExpenseResponse create(CreateExpenseRequest req) {
    Long userId = requireUserId();
    requireWriteRateLimit(userId);
    ExpenseCategory cat = parseCategory(req.category());
    Expense e = new Expense();
    e.setUserId(userId);
    e.setAmount(req.amount());
    e.setCategory(cat);
    e.setNote(req.note());
    e.setOccurredAt(req.occurredAt());
    mapper.insert(e);
    log.debug("expense created uid={} id={} category={}", userId, e.getId(), cat);
    return ExpenseResponse.from(e);
  }

  public ExpenseResponse getById(long id) {
    Long userId = requireUserId();
    Expense e = mapper.findByUserAndId(userId, id);
    if (e == null) {
      log.warn("expense get cross-user or missing uid={} id={}", userId, id);
      throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该消费");
    }
    return ExpenseResponse.from(e);
  }

  public PageResponse<ExpenseResponse> list(ExpenseFilter f) {
    Long userId = requireUserId();
    int offset = (f.page() - 1) * f.size();
    List<Expense> rows = mapper.listByUser(userId, f.category(), f.from(), f.to(), offset, f.size());
    long total = mapper.countByUser(userId, f.category(), f.from(), f.to());
    List<ExpenseResponse> items = rows.stream().map(ExpenseResponse::from).toList();
    return PageResponse.of(items, total, f.page(), f.size());
  }

  public void update(long id, UpdateExpenseRequest req) {
    Long userId = requireUserId();
    requireWriteRateLimit(userId);
    Expense e = mapper.findByUserAndId(userId, id);
    if (e == null) {
      log.warn("expense update cross-user or missing uid={} id={}", userId, id);
      throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该消费");
    }
    if (req.amount() != null) e.setAmount(req.amount());
    if (req.category() != null) e.setCategory(parseCategory(req.category()));
    if (req.note() != null) e.setNote(req.note());
    if (req.occurredAt() != null) e.setOccurredAt(req.occurredAt());
    mapper.updateById(e);
    log.debug("expense updated uid={} id={}", userId, id);
  }

  public void softDelete(long id) {
    Long userId = requireUserId();
    requireWriteRateLimit(userId);
    Expense e = mapper.findByUserAndId(userId, id);
    if (e == null) {
      log.warn("expense delete cross-user or missing uid={} id={}", userId, id);
      throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该消费");
    }
    mapper.deleteById(id);
    log.debug("expense deleted uid={} id={}", userId, id);
  }

  public ExpenseSummary summary(int year, int month) {
    Long userId = requireUserId();
    YearMonth ym = YearMonth.of(year, month);
    LocalDateTime from = ym.atDay(1).atStartOfDay();
    LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);
    BigDecimal total = mapper.summaryTotal(userId, from, to);
    Map<String, BigDecimal> breakdown = mapper.summaryByCategory(userId, from, to);
    BigDecimal prevTotal = mapper.summaryTotal(userId,
        ym.minusMonths(1).atDay(1).atStartOfDay(),
        ym.minusMonths(1).atEndOfMonth().atTime(23, 59, 59));
    BigDecimal prevYearTotal = mapper.summaryTotal(userId,
        ym.minusYears(1).atDay(1).atStartOfDay(),
        ym.minusYears(1).atEndOfMonth().atTime(23, 59, 59));
    return new ExpenseSummary(
        total == null ? BigDecimal.ZERO : total,
        breakdown == null ? Map.of() : breakdown,
        computeDelta(total, prevTotal),
        computeDelta(total, prevYearTotal));
  }

  public List<CategoryItem> categories() {
    return Arrays.stream(ExpenseCategory.values()).map(CategoryItem::of).toList();
  }

  // ---------- helpers ----------
  private Long requireUserId() {
    Long userId = UserContext.current();
    if (userId == null) throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
    return userId;
  }

  private void requireWriteRateLimit(Long userId) {
    boolean limited = rateLimiter.hit(
        ExpenseConstants.WRITE_RL_KEY_PREFIX + userId,
        ExpenseConstants.WRITE_RL_MAX,
        ExpenseConstants.WRITE_RL_WINDOW);
    if (limited) throw new BusinessException(ErrorCode.RATE_LIMIT, "操作过于频繁，请稍后再试");
  }

  private ExpenseCategory parseCategory(String code) {
    try { return ExpenseCategory.valueOf(code); }
    catch (IllegalArgumentException ex) {
      throw new BusinessException(ErrorCode.VALIDATION, "category 不在枚举内: " + code);
    }
  }

  private BigDecimal computeDelta(BigDecimal current, BigDecimal previous) {
    if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
    return current.subtract(previous);
  }
}
```

- [ ] **Step 4**: Run → PASS

Run: `cd backend && mvn test -Dtest=ExpenseServiceTest -q`
Expected: BUILD SUCCESS, 16 tests passed

- [ ] **Step 5**: Commit

```bash
git add backend/src/main/java/com/lifepulse/expense/service/ExpenseService.java \
        backend/src/test/java/com/lifepulse/expense/service/ExpenseServiceTest.java
git commit -m "feat(expense): add ExpenseService with 7 methods and 16 unit tests"
```

---
