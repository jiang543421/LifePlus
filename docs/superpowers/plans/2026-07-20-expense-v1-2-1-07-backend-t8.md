### T8. ExpenseServiceIT (Testcontainers MySQL)

**Files:**
- Create: `backend/src/test/java/com/lifepulse/it/ExpenseServiceIT.java`

**Interfaces:**
- Consumes: T6 service, Testcontainers MySQL

- [ ] **Step 1**: 创建 IT（沿用 `AbstractIntegrationTest`）

```java
package com.lifepulse.it;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.AuthTestSupport;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.expense.dto.*;
import com.lifepulse.expense.service.ExpenseService;
import com.lifepulse.security.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ExpenseServiceIT extends AbstractIntegrationTest {

  @Autowired ExpenseService service;
  @Autowired AuthTestSupport auth;

  Long userA, userB;
  String tokenA;

  @BeforeEach
  void setUp() {
    auth.resetDatabase();
    userA = auth.register("a@example.com", "Password1", "A");
    userB = auth.register("b@example.com", "Password1", "B");
    tokenA = auth.login("a@example.com", "Password1");
    UserContext.set(userA);
  }

  @AfterEach
  void tearDown() { UserContext.clear(); }

  @Test void create_list_summary_delete_roundtrip() {
    var e1 = service.create(new CreateExpenseRequest(new BigDecimal("35.00"), "MEAL", "午餐",
        LocalDateTime.of(2026, 7, 15, 12, 0)));
    var e2 = service.create(new CreateExpenseRequest(new BigDecimal("12.00"), "TRANSPORT", "地铁",
        LocalDateTime.of(2026, 7, 15, 9, 0)));
    assertThat(e1.id()).isNotNull();

    var list = service.list(new ExpenseFilter(null,
        LocalDateTime.of(2026, 7, 1, 0, 0),
        LocalDateTime.of(2026, 7, 31, 23, 59),
        1, 20));
    assertThat(list.total()).isEqualTo(2);

    var sum = service.summary(2026, 7);
    assertThat(sum.totalAmount()).isEqualByComparingTo("47.00");
    assertThat(sum.categoryBreakdown()).containsEntry("MEAL", new BigDecimal("35.00"));

    service.softDelete(e1.id());
    var list2 = service.list(new ExpenseFilter(null, null, null, 1, 20));
    assertThat(list2.total()).isEqualTo(1);
  }

  @Test void crossUser_getThrows1003() {
    var e = service.create(new CreateExpenseRequest(new BigDecimal("10.00"), "MEAL", null, LocalDateTime.now()));
    UserContext.set(userB);
    assertThatThrownBy(() -> service.getById(e.id()))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
  }

  @Test void softDeleteThenGetThrows1003() {
    var e = service.create(new CreateExpenseRequest(new BigDecimal("10.00"), "MEAL", null, LocalDateTime.now()));
    service.softDelete(e.id());
    assertThatThrownBy(() -> service.getById(e.id()))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
  }

  @Test void summary_empty_returnsZeros() {
    var sum = service.summary(2026, 7);
    assertThat(sum.totalAmount()).isEqualByComparingTo("0");
    assertThat(sum.categoryBreakdown()).isEmpty();
    assertThat(sum.monthOverMonthDelta()).isNull();
  }

  @Test void summary_monthOverMonth_computesDelta() {
    // 6 月一笔 50
    UserContext.set(userA);
    service.create(new CreateExpenseRequest(new BigDecimal("50.00"), "MEAL", null,
        LocalDateTime.of(2026, 6, 15, 12, 0)));
    // 7 月一笔 80
    service.create(new CreateExpenseRequest(new BigDecimal("80.00"), "MEAL", null,
        LocalDateTime.of(2026, 7, 15, 12, 0)));

    var sum = service.summary(2026, 7);
    assertThat(sum.totalAmount()).isEqualByComparingTo("80.00");
    assertThat(sum.monthOverMonthDelta()).isEqualByComparingTo("30.00");
  }

  @Test void categories_returns5() {
    assertThat(service.categories()).hasSize(5);
  }

  @Test void writeRateLimit_11thCallThrows1006() {
    for (int i = 0; i < AuthConstants.WRITE_RL_MAX_FOR_EXPENSE; i++) {
      service.create(new CreateExpenseRequest(new BigDecimal("1.00"), "MEAL", null, LocalDateTime.now()));
    }
    assertThatThrownBy(() -> service.create(new CreateExpenseRequest(
        new BigDecimal("1.00"), "MEAL", null, LocalDateTime.now())))
        .isInstanceOf(BusinessException.class)
        .extracting("code").isEqualTo(ErrorCode.RATE_LIMIT);
  }
}
```

> 注：`WRITE_RL_MAX_FOR_EXPENSE` 需在 `AuthConstants` 或 `ExpenseConstants` 暴露；测试可硬编码 `10`。若 `AbstractIntegrationTest` 或 `AuthTestSupport` 不存在，沿用 settings v1.1 已建的同款基类。

- [ ] **Step 2**: Run → PASS

Run: `cd backend && mvn verify -Dtest=ExpenseServiceIT -q`
Expected: BUILD SUCCESS, 7 tests passed

- [ ] **Step 3**: Commit

```bash
git add backend/src/test/java/com/lifepulse/it/ExpenseServiceIT.java
git commit -m "test(expense): add ExpenseServiceIT with Testcontainers MySQL"
```

---
