## 4. Task List（按依赖顺序）

### Task Dependency Diagram

```
T1 V4 Migration ─┐
                 ├─ T2 Enum+Constants ─┬─ T3 Entity ─┬─ T4 DTOs ─┬─ T5 Mapper ─┬─ T6 Service ─┬─ T7 Controller ─┬─ T8 IT
                                                                                                  └─────────────────┘
                                                                                                  
T9 API+Types+Constants ─┬─ T10 Store ─┬─ T11 Router+Home ─┬─ T12 ListItem+number ─┬─ T13 SummaryCard+Dlg ─┬─ T14 View+Detail ─┬─ T15 E2E
```

后端 T1→T8 串行；前端 T9→T14 串行；T8 完成后才进 T9，T15 收尾。

---

## 5. Backend Tasks

### T1. Flyway V4 migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__init_expense.sql`

**Interfaces:**
- Consumes: 06-spec §4
- Produces: `t_expense` 表 + 2 索引 + 2 CHECK

- [ ] **Step 1**: 创建文件

```sql
CREATE TABLE t_expense (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  user_id      BIGINT        NOT NULL,
  amount       DECIMAL(12,2) NOT NULL,
  category     VARCHAR(16)   NOT NULL,
  note         VARCHAR(200)  NULL,
  occurred_at  DATETIME(3)   NOT NULL,
  created_at   DATETIME(3)   NOT NULL,
  updated_at   DATETIME(3)   NOT NULL,
  deleted      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_occurred (user_id, occurred_at),
  KEY idx_user_category (user_id, category, occurred_at),
  CONSTRAINT chk_expense_amount_pos CHECK (amount > 0),
  CONSTRAINT chk_expense_category CHECK (category IN ('MEAL','SHOPPING','TRANSPORT','SUBSCRIPTION','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 2**: 验证

Run: `cd backend && mvn flyway:info -Dflyway.url=jdbc:mysql://localhost:3306/lifepulse -Dflyway.user=root -Dflyway.password=root`
Expected: V4 显示 pending → 运行 `mvn flyway:migrate` 后 applied

- [ ] **Step 3**: Commit

```bash
git add backend/src/main/resources/db/migration/V4__init_expense.sql
git commit -m "feat(expense): add V4 migration for t_expense table"
```

---

### T2. ExpenseCategory enum + ExpenseConstants

**Files:**
- Create: `backend/src/main/java/com/lifepulse/expense/ExpenseCategory.java`
- Create: `backend/src/main/java/com/lifepulse/expense/ExpenseConstants.java`
- Test: `backend/src/test/java/com/lifepulse/expense/ExpenseCategoryTest.java`

**Interfaces:**
- Produces: enum 5 值 + label map + write RL 常量

- [ ] **Step 1**: RED — 写测试

```java
package com.lifepulse.expense;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCategoryTest {
  @Test void enum_has5Values() {
    assertThat(ExpenseCategory.values()).hasSize(5);
  }
  @Test void label_isChinese() {
    assertThat(ExpenseCategory.MEAL.getLabel()).isEqualTo("餐饮");
    assertThat(ExpenseCategory.SHOPPING.getLabel()).isEqualTo("购物");
    assertThat(ExpenseCategory.TRANSPORT.getLabel()).isEqualTo("交通");
    assertThat(ExpenseCategory.SUBSCRIPTION.getLabel()).isEqualTo("订阅");
    assertThat(ExpenseCategory.OTHER.getLabel()).isEqualTo("其他");
  }
  @Test void getValue_stripsEnumName() {
    assertThat(ExpenseCategory.MEAL.getValue()).isEqualTo("MEAL");
  }
}
```

- [ ] **Step 2**: Run test → FAIL（类不存在）

Run: `cd backend && mvn test -Dtest=ExpenseCategoryTest -q`
Expected: `Compilation failure: ExpenseCategory not found`

- [ ] **Step 3**: GREEN — 实现 enum

```java
package com.lifepulse.expense;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExpenseCategory implements IEnum<String> {
  MEAL("餐饮"),
  SHOPPING("购物"),
  TRANSPORT("交通"),
  SUBSCRIPTION("订阅"),
  OTHER("其他");

  private final String label;
  ExpenseCategory(String label) { this.label = label; }
  @Override public String getValue() { return name(); }
  @JsonValue public String getLabel() { return label; }
}
```

- [ ] **Step 4**: 创建 ExpenseConstants

```java
package com.lifepulse.expense;

import java.time.Duration;

public final class ExpenseConstants {
  private ExpenseConstants() {}
  public static final String WRITE_RL_KEY_PREFIX = "lp:rl:expense:write:";
  public static final int WRITE_RL_MAX = 10;
  public static final Duration WRITE_RL_WINDOW = Duration.ofMinutes(1);
  public static final int MAX_PAGE_SIZE = 100;
  public static final int DEFAULT_PAGE_SIZE = 20;
}
```

- [ ] **Step 5**: Run test → PASS

Run: `cd backend && mvn test -Dtest=ExpenseCategoryTest -q`
Expected: BUILD SUCCESS, 3 tests passed

- [ ] **Step 6**: Commit

```bash
git add backend/src/main/java/com/lifepulse/expense/ExpenseCategory.java \
        backend/src/main/java/com/lifepulse/expense/ExpenseConstants.java \
        backend/src/test/java/com/lifepulse/expense/ExpenseCategoryTest.java
git commit -m "feat(expense): add ExpenseCategory enum and constants"
```

---
