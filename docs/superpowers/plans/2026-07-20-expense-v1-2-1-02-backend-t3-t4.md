### T3. Expense entity

**Files:**
- Create: `backend/src/main/java/com/lifepulse/expense/entity/Expense.java`

**Interfaces:**
- Consumes: T2 (ExpenseCategory)
- Produces: MyBatis-Plus entity with `@TableLogic`

- [ ] **Step 1**: 创建 entity

```java
package com.lifepulse.expense.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lifepulse.expense.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_expense")
public class Expense {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private BigDecimal amount;
  private ExpenseCategory category;
  private String note;
  private LocalDateTime occurredAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  @TableLogic
  private Integer deleted;
}
```

- [ ] **Step 2**: 编译验证

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3**: Commit

```bash
git add backend/src/main/java/com/lifepulse/expense/entity/Expense.java
git commit -m "feat(expense): add Expense entity with TableLogic"
```

---

### T4. DTO records + validation test

**Files:**
- Create: `backend/src/main/java/com/lifepulse/expense/dto/CreateExpenseRequest.java`
- Create: `backend/src/main/java/com/lifepulse/expense/dto/UpdateExpenseRequest.java`
- Create: `backend/src/main/java/com/lifepulse/expense/dto/ExpenseResponse.java`
- Create: `backend/src/main/java/com/lifepulse/expense/dto/ExpenseSummary.java`
- Create: `backend/src/main/java/com/lifepulse/expense/dto/CategoryItem.java`
- Create: `backend/src/main/java/com/lifepulse/expense/dto/ExpenseFilter.java`
- Test: `backend/src/test/java/com/lifepulse/expense/dto/ExpenseDtoValidationTest.java`

**Interfaces:**
- Consumes: T2 (ExpenseCategory)
- Produces: 6 record DTO

- [ ] **Step 1**: 创建 6 个 record

```java
// CreateExpenseRequest.java
package com.lifepulse.expense.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record CreateExpenseRequest(
    @NotNull @DecimalMin(value="0.01") BigDecimal amount,
    @NotBlank String category,
    @Size(max=200) String note,
    @NotNull LocalDateTime occurredAt) {}

// UpdateExpenseRequest.java
public record UpdateExpenseRequest(
    @DecimalMin("0.01") BigDecimal amount,
    String category,
    @Size(max=200) String note,
    LocalDateTime occurredAt) {}

// ExpenseResponse.java
public record ExpenseResponse(Long id, BigDecimal amount, String category,
                              String note, LocalDateTime occurredAt,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
  public static ExpenseResponse from(Expense e) {
    return new ExpenseResponse(e.getId(), e.getAmount(), e.getCategory().name(),
        e.getNote(), e.getOccurredAt(), e.getCreatedAt(), e.getUpdatedAt());
  }
}

// ExpenseSummary.java
public record ExpenseSummary(BigDecimal totalAmount,
                              java.util.Map<String, BigDecimal> categoryBreakdown,
                              BigDecimal monthOverMonthDelta,
                              BigDecimal yearOverYearDelta) {}

// CategoryItem.java
public record CategoryItem(String code, String label) {
  public static CategoryItem of(ExpenseCategory c) {
    return new CategoryItem(c.name(), c.getLabel());
  }
}

// ExpenseFilter.java
public record ExpenseFilter(String category,
                             LocalDateTime from, LocalDateTime to,
                             int page, int size) {}
```

- [ ] **Step 2**: RED — 写 validation test

```java
package com.lifepulse.expense.dto;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ExpenseDtoValidationTest {
  private static Validator validator;
  @BeforeAll static void init() { validator = Validation.buildDefaultValidatorFactory().getValidator(); }

  @Test void createAmount_zero_violates() {
    var req = new CreateExpenseRequest(BigDecimal.ZERO, "MEAL", null, LocalDateTime.now());
    assertThat(validator.validate(req)).isNotEmpty();
  }
  @Test void createAmount_negative_violates() {
    var req = new CreateExpenseRequest(new BigDecimal("-1.00"), "MEAL", null, LocalDateTime.now());
    assertThat(validator.validate(req)).isNotEmpty();
  }
  @Test void createAmount_positive_ok() {
    var req = new CreateExpenseRequest(new BigDecimal("0.01"), "MEAL", null, LocalDateTime.now());
    assertThat(validator.validate(req)).isEmpty();
  }
  @Test void createCategory_blank_violates() {
    var req = new CreateExpenseRequest(new BigDecimal("1.00"), " ", null, LocalDateTime.now());
    assertThat(validator.validate(req)).isNotEmpty();
  }
  @Test void createNote_over200_violates() {
    String s = "x".repeat(201);
    var req = new CreateExpenseRequest(new BigDecimal("1.00"), "MEAL", s, LocalDateTime.now());
    assertThat(validator.validate(req)).isNotEmpty();
  }
  @Test void createOccurredAt_null_violates() {
    var req = new CreateExpenseRequest(new BigDecimal("1.00"), "MEAL", null, null);
    assertThat(validator.validate(req)).isNotEmpty();
  }
  @Test void updateAllNull_ok() {
    var req = new UpdateExpenseRequest(null, null, null, null);
    assertThat(validator.validate(req)).isEmpty();
  }
  @Test void updateAmount_zero_violates() {
    var req = new UpdateExpenseRequest(BigDecimal.ZERO, null, null, null);
    assertThat(validator.validate(req)).isNotEmpty();
  }
}
```

- [ ] **Step 3**: Run → PASS（DTO 已加注解）

Run: `cd backend && mvn test -Dtest=ExpenseDtoValidationTest -q`
Expected: BUILD SUCCESS, 8 tests passed

- [ ] **Step 4**: Commit

```bash
git add backend/src/main/java/com/lifepulse/expense/dto/ \
        backend/src/test/java/com/lifepulse/expense/dto/ExpenseDtoValidationTest.java
git commit -m "feat(expense): add 6 DTO records with validation"
```

---
