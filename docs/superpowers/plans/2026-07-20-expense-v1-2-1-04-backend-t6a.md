### T6. ExpenseService + Mockito test

**Files:**
- Create: `backend/src/main/java/com/lifepulse/expense/service/ExpenseService.java`
- Test: `backend/src/test/java/com/lifepulse/expense/service/ExpenseServiceTest.java`

**Interfaces:**
- Consumes: T2/T3/T4/T5
- Produces: 7 公开方法

- [ ] **Step 1**: RED — 写 Mockito test

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
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

  @Mock ExpenseMapper mapper;
  @Mock RateLimiter rateLimiter;

  ExpenseService service;

  @BeforeEach
  void setUp() {
    service = new ExpenseService(mapper, rateLimiter);
    UserContext.set(1L);  // 假设 UserContext 有 set/clear
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  private Expense mkExpense(long id, String category, String amount) {
    Expense e = new Expense();
    e.setId(id);
    e.setUserId(1L);
    e.setAmount(new BigDecimal(amount));
    e.setCategory(ExpenseCategory.valueOf(category));
    e.setOccurredAt(LocalDateTime.now());
    e.setCreatedAt(LocalDateTime.now());
    e.setUpdatedAt(LocalDateTime.now());
    return e;
  }

  // ---------- create ----------
  @Test void create_happy_persists() {
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(false);
    when(mapper.insert(any(Expense.class))).thenAnswer(inv -> {
      Expense e = inv.getArgument(0);
      e.setId(100L);
      return 1;
    });
    var req = new CreateExpenseRequest(new BigDecimal("35.00"), "MEAL", "午餐", LocalDateTime.now());
    var resp = service.create(req);
    assertThat(resp.id()).isEqualTo(100L);
    assertThat(resp.amount()).isEqualByComparingTo("35.00");
    assertThat(resp.category()).isEqualTo("MEAL");
  }

  @Test void create_rateLimited_throws1006() {
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(true);
    var req = new CreateExpenseRequest(new BigDecimal("1.00"), "MEAL", null, LocalDateTime.now());
    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.RATE_LIMIT);
  }

  @Test void create_invalidCategory_throws1001() {
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(false);
    var req = new CreateExpenseRequest(new BigDecimal("1.00"), "INVALID", null, LocalDateTime.now());
    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.VALIDATION);
  }

  // ---------- getById ----------
  @Test void getById_found_returns() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(mkExpense(100, "MEAL", "10.00"));
    var resp = service.getById(100L);
    assertThat(resp.id()).isEqualTo(100L);
  }
  @Test void getById_crossUser_throws1003() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(null);
    assertThatThrownBy(() -> service.getById(100L))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
  }

  // ---------- list ----------
  @Test void list_happy_returnsPaged() {
    when(mapper.listByUser(anyLong(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(List.of(mkExpense(2, "MEAL", "5"), mkExpense(1, "TRANSPORT", "3")));
    when(mapper.countByUser(anyLong(), any(), any(), any())).thenReturn(2L);
    var f = new ExpenseFilter(null, null, null, 1, 20);
    PageResponse<ExpenseResponse> p = service.list(f);
    assertThat(p.total()).isEqualTo(2L);
    assertThat(p.items()).hasSize(2);
  }

  // ---------- update ----------
  @Test void update_partial_onlyChangesProvided() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(mkExpense(100, "MEAL", "10.00"));
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(false);
    var req = new UpdateExpenseRequest(new BigDecimal("20.00"), null, null, null);
    service.update(100L, req);
    verify(mapper).updateById(argThat(e ->
        e.getAmount().compareTo(new BigDecimal("20.00")) == 0
        && e.getCategory() == ExpenseCategory.MEAL));
  }
  @Test void update_crossUser_throws1003() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(null);
    var req = new UpdateExpenseRequest(null, null, null, null);
    assertThatThrownBy(() -> service.update(100L, req))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
  }
  @Test void update_invalidCategory_throws1001() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(mkExpense(100, "MEAL", "10.00"));
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(false);
    var req = new UpdateExpenseRequest(null, "BOGUS", null, null);
    assertThatThrownBy(() -> service.update(100L, req))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.VALIDATION);
  }

  // ---------- softDelete ----------
  @Test void softDelete_crossUser_throws1003() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(null);
    assertThatThrownBy(() -> service.softDelete(100L))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
  }
  @Test void softDelete_happy_callsMapper() {
    when(mapper.findByUserAndId(1L, 100L)).thenReturn(mkExpense(100, "MEAL", "10.00"));
    when(rateLimiter.hit(anyString(), anyInt(), any())).thenReturn(false);
    service.softDelete(100L);
    verify(mapper).deleteById(100L);
  }

  // ---------- summary ----------
  @Test void summary_happy_returnsTotalAndBreakdown() {
    when(mapper.summaryTotal(anyLong(), any(), any())).thenReturn(new BigDecimal("100.00"));
    when(mapper.summaryByCategory(anyLong(), any(), any())).thenReturn(Map.of(
        "MEAL", new BigDecimal("60.00"),
        "TRANSPORT", new BigDecimal("40.00")));
    var s = service.summary(2026, 7);
    assertThat(s.totalAmount()).isEqualByComparingTo("100.00");
    assertThat(s.categoryBreakdown()).containsEntry("MEAL", new BigDecimal("60.00"));
  }

  @Test void summary_empty_returnsZeros() {
    when(mapper.summaryTotal(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
    when(mapper.summaryByCategory(anyLong(), any(), any())).thenReturn(Map.of());
    var s = service.summary(2026, 7);
    assertThat(s.totalAmount()).isEqualByComparingTo("0");
    assertThat(s.categoryBreakdown()).isEmpty();
  }

  // ---------- categories ----------
  @Test void categories_returns5Items() {
    var cats = service.categories();
    assertThat(cats).hasSize(5);
    assertThat(cats).extracting(CategoryItem::code)
