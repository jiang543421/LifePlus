### Task 4.4: ExpenseAiProvider + 单测

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/provider/ExpenseAiProvider.java`
- Create: `backend/src/test/java/com/lifepulse/ai/provider/ExpenseAiProviderTest.java`
- Modify: `backend/src/main/java/com/lifepulse/expense/repository/ExpenseMapper.java`
- Modify: `backend/src/main/resources/mapper/expense/ExpenseMapper.xml`
- Consumes: `AiInsightProperties.expenseEnabled`

**Interfaces:**
- Consumes: `ExpenseMapper.sumThisWeek(userId, startOfWeek, endOfWeek)`, `ExpenseMapper.sumLastWeek(...)`
- Produces: `MetricValue{value: 本周消费额, unit: "¥", trend: UP/DOWN/FLAT/NONE}`

- [ ] **Step 1: 扩展 `ExpenseMapper`**

追加：

```java
java.math.BigDecimal sumThisWeek(@Param("userId") Long userId,
                                  @Param("weekStart") LocalDate weekStart,
                                  @Param("weekEnd") LocalDate weekEnd);
```

XML：

```xml
<select id="sumThisWeek" resultType="java.math.BigDecimal">
    SELECT COALESCE(SUM(amount), 0) FROM t_expense
    WHERE user_id = #{userId}
      AND deleted = 0
      AND expense_date BETWEEN #{weekStart} AND #{weekEnd}
</select>
```

- [ ] **Step 2: 写失败测试 `ExpenseAiProviderTest.java`**

```java
package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.expense.repository.ExpenseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseAiProviderTest {

    @Mock private ExpenseMapper expenseMapper;
    @Mock private AiInsightProperties props;
    @InjectMocks private ExpenseAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_higherThanLastWeek_returnsUpTrend() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(anyLong(), any(), any())).thenReturn(new BigDecimal("500"));
        when(expenseMapper.sumThisWeek(anyLong(), any(), any()))
            .thenReturn(new BigDecimal("500"), new BigDecimal("400"));
        // first call returns 500 (this week), second returns 400 (last week)
        // 用 thenReturn 不同参数覆盖，简化用 then 链：
        org.mockito.Mockito.stub(expenseMapper.sumThisWeek(anyLong(), any(), any()))
            .toReturn(new BigDecimal("500"));
        org.mockito.Mockito.stub(expenseMapper.sumThisWeek(anyLong(), any(), any()))
            .toReturn(new BigDecimal("500"), new BigDecimal("400"));
        // ↑ 上面的 stub 写法有冲突，简化：分两个不同方法
        // 实际实现中应拆成 sumThisWeek + sumLastWeek；本测试先假设两个方法

        // 简化版：先跳过趋势断言，仅断言金额
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(mv.unit()).isEqualTo("¥");
    }

    @Test
    void collect_noExpense_returnsNotNonEmpty() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void isEnabled_whenConfigDisabled_returnsFalse() {
        when(props.isExpenseEnabled()).thenReturn(false);
        assertThat(provider.isEnabled(1L)).isFalse();
    }

    @Test
    void isEnabled_whenConfigEnabled_returnsTrue() {
        when(props.isExpenseEnabled()).thenReturn(true);
        assertThat(provider.isEnabled(1L)).isTrue();
    }

    @Test
    void collect_passesUserIdToMapper() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(anyLong(), any(), any())).thenReturn(BigDecimal.ONE);
        provider.collect(77L, ctx);
        org.mockito.Mockito.verify(expenseMapper).sumThisWeek(eq(77L), any(), any());
    }
}
```

> ⚠️ 上面测试用了占位 `sumThisWeek` 同时承担两角色——实际 `ExpenseMapper` 需要 `sumThisWeek` + `sumLastWeek` 两个方法。在 Step 3 实现时同步修正。

- [ ] **Step 3: 实现 `ExpenseAiProvider.java`（含 `sumLastWeek` mapper 方法）**

```java
package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.expense.repository.ExpenseMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

@Component
public class ExpenseAiProvider implements AiInsightProvider {

    private final ExpenseMapper expenseMapper;
    private final AiInsightProperties props;

    public ExpenseAiProvider(ExpenseMapper expenseMapper, AiInsightProperties props) {
        this.expenseMapper = expenseMapper;
        this.props = props;
    }

    @Override public String key() { return "expense"; }

    @Override
    public boolean isEnabled(Long userId) {
        return props.isExpenseEnabled();
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        var weekStart = ctx.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var weekEnd = weekStart.plusDays(6);
        var lastWeekStart = weekStart.minusDays(7);
        var lastWeekEnd = weekStart.minusDays(1);

        BigDecimal thisWeek = expenseMapper.sumThisWeek(userId, weekStart, weekEnd);
        BigDecimal lastWeek = expenseMapper.sumThisWeek(userId, lastWeekStart, lastWeekEnd);

        Trend trend;
        if (thisWeek.compareTo(lastWeek) > 0) {
            trend = Trend.UP;
        } else if (thisWeek.compareTo(lastWeek) < 0) {
            trend = Trend.DOWN;
        } else {
            trend = Trend.FLAT;
        }
        return new MetricValue(thisWeek, "¥", trend);
    }
}
```

> 简化：用 `sumThisWeek` 复用同一方法，参数范围不同；也可拆为 `sumThisWeek` + `sumLastWeek` 两个 mapper 方法以提升可读性——本计划采用前者。

- [ ] **Step 4: 简化测试（去掉 stub 冲突）+ 运行**

简化后的测试（`ExpenseAiProviderTest.java` 完整版）：

```java
package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.expense.repository.ExpenseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseAiProviderTest {

    @Mock private ExpenseMapper expenseMapper;
    @Mock private AiInsightProperties props;
    @InjectMocks private ExpenseAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_higherThanLastWeek_returnsUpTrend() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("500"))
            .thenReturn(new BigDecimal("400"));
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(mv.trend()).isEqualTo(com.lifepulse.ai.model.Trend.UP);
    }

    @Test
    void collect_lowerThanLastWeek_returnsDownTrend() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("300"))
            .thenReturn(new BigDecimal("500"));
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.trend()).isEqualTo(com.lifepulse.ai.model.Trend.DOWN);
    }

    @Test
    void collect_equalToLastWeek_returnsFlatTrend() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(eq(1L), any(), any()))
            .thenReturn(new BigDecimal("400"));
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.trend()).isEqualTo(com.lifepulse.ai.model.Trend.FLAT);
    }

    @Test
    void collect_noExpense_returnsNotNonEmpty() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void isEnabled_whenConfigDisabled_returnsFalse() {
        when(props.isExpenseEnabled()).thenReturn(false);
        assertThat(provider.isEnabled(1L)).isFalse();
    }

    @Test
    void isEnabled_whenConfigEnabled_returnsTrue() {
        when(props.isExpenseEnabled()).thenReturn(true);
        assertThat(provider.isEnabled(1L)).isTrue();
    }

    @Test
    void collect_passesUserIdToMapper() {
        when(props.isExpenseEnabled()).thenReturn(true);
        when(expenseMapper.sumThisWeek(eq(77L), any(), any())).thenReturn(BigDecimal.ONE);
        provider.collect(77L, ctx);
        org.mockito.Mockito.verify(expenseMapper).sumThisWeek(eq(77L), any(), any());
    }
}
```

- [ ] **Step 5: 运行测试 + Commit**

```powershell
cd backend; mvn -q test -Dtest=ExpenseAiProviderTest
```

预期：`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。

```bash
git add backend/src/main/java/com/lifepulse/ai/provider/ExpenseAiProvider.java
git add backend/src/main/java/com/lifepulse/expense/repository/ExpenseMapper.java
git add backend/src/main/resources/mapper/expense/ExpenseMapper.xml
git add backend/src/test/java/com/lifepulse/ai/provider/ExpenseAiProviderTest.java
git commit -m "feat(ai): add ExpenseAiProvider with week-over-week comparison"
```

---
