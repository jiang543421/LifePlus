### Task 4.3: PlanAiProvider + 单测

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/provider/PlanAiProvider.java`
- Create: `backend/src/test/java/com/lifepulse/ai/provider/PlanAiProviderTest.java`
- Modify: `backend/src/main/java/com/lifepulse/plan/repository/PlanMapper.java`
- Modify: `backend/src/main/resources/mapper/plan/PlanMapper.xml`

**Interfaces:**
- Consumes: `PlanMapper.countTodayEvents(userId, date)`
- Produces: `MetricValue{value: 今日事件数, unit: "项", trend: BUSY/NORMAL/FREE/NONE}`（trend 用字符串区分，spec §6.3 的 Trend 枚举保留 NONE 状态）

> 简化：用 `Trend.FLAT` 表示普通，busy/free 由模板路由（spec §6.3 chip.planDensity.busy/normal/free）

- [ ] **Step 1: 扩展 `PlanMapper`**

追加方法：

```java
int countTodayEvents(@Param("userId") Long userId, @Param("date") LocalDate date);
```

XML 追加：

```xml
<select id="countTodayEvents" resultType="int">
    SELECT COUNT(*) FROM t_plan
    WHERE user_id = #{userId}
      AND deleted = 0
      AND DATE(start_at) = #{date}
</select>
```

- [ ] **Step 2: 写失败测试 `PlanAiProviderTest.java`**

```java
package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.plan.repository.PlanMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanAiProviderTest {

    @Mock private PlanMapper planMapper;
    @InjectMocks private PlanAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_fiveEvents_returnsNonEmpty() {
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(5);
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.value().intValue()).isEqualTo(5);
        assertThat(mv.unit()).isEqualTo("项");
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void collect_zeroEvents_returnsNotNonEmpty() {
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(0);
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.isNonEmpty()).isFalse();
        assertThat(mv.trend()).isEqualTo(Trend.NONE);
    }

    @Test
    void collect_passesUserIdToMapper() {
        when(planMapper.countTodayEvents(anyLong(), any())).thenReturn(1);
        provider.collect(99L, ctx);
        org.mockito.Mockito.verify(planMapper).countTodayEvents(
            org.mockito.ArgumentMatchers.eq(99L), any());
    }

    @Test
    void isEnabled_alwaysTrue() {
        assertThat(provider.isEnabled(1L)).isTrue();
    }
}
```

- [ ] **Step 3: 实现 `PlanAiProvider.java`**

```java
package com.lifepulse.ai.provider;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.plan.repository.PlanMapper;
import org.springframework.stereotype.Component;

@Component
public class PlanAiProvider implements AiInsightProvider {

    private final PlanMapper planMapper;

    public PlanAiProvider(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    @Override public String key() { return "plan"; }
    @Override public boolean isEnabled(Long userId) { return true; }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        int count = planMapper.countTodayEvents(userId, ctx.today());
        return new MetricValue(
            new java.math.BigDecimal(count),
            "项",
            count == 0 ? Trend.NONE : Trend.FLAT
        );
    }
}
```

- [ ] **Step 4: 运行测试 + Commit**

```powershell
cd backend; mvn -q test -Dtest=PlanAiProviderTest
```

预期：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。

```bash
git add backend/src/main/java/com/lifepulse/ai/provider/PlanAiProvider.java
git add backend/src/main/java/com/lifepulse/plan/repository/PlanMapper.java
git add backend/src/main/resources/mapper/plan/PlanMapper.xml
git add backend/src/test/java/com/lifepulse/ai/provider/PlanAiProviderTest.java
git commit -m "feat(ai): add PlanAiProvider with today event count"
```

---
