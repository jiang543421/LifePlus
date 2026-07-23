### Task 4.5: DietAiProvider + 单测

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/provider/DietAiProvider.java`
- Create: `backend/src/test/java/com/lifepulse/ai/provider/DietAiProviderTest.java`
- Modify: `backend/src/main/java/com/lifepulse/diet/repository/DietMapper.java`（如不存在；否则找对应 mapper）
- Consumes: `AiInsightProperties.dietEnabled`

**Interfaces:**
- Consumes: `DietMapper.sumTodayKcal(userId, date)`, `DietMapper.findDailyTargetKcal(userId)`
- Produces: `MetricValue{value: 今日摄入 kcal, unit: "kcal", trend: FLAT/NONE}`

- [ ] **Step 1: 检查 Diet 模块是否已存在 mapper**

```powershell
ls backend/src/main/java/com/lifepulse/diet/
```

- 若 `repository/DietMapper.java` 不存在 → **降级**：本任务**简化为只读 user_diet_target 表或一个最小 stub**；spec §7.1 允许 Diet 不可用时降级。
- 若存在 → 继续。

- [ ] **Step 2（仅在 mapper 存在时执行）: 写测试 + 实现**

测试 `DietAiProviderTest.java`：

```java
package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.diet.repository.DietMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DietAiProviderTest {

    @Mock private DietMapper dietMapper;
    @Mock private AiInsightProperties props;
    @InjectMocks private DietAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_withMeals_returnsIntakeNonEmpty() {
        when(props.isDietEnabled()).thenReturn(true);
        when(dietMapper.sumTodayKcal(anyLong(), any())).thenReturn(new BigDecimal("1500"));
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(mv.unit()).isEqualTo("kcal");
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void collect_noMeals_returnsNotNonEmpty() {
        when(props.isDietEnabled()).thenReturn(true);
        when(dietMapper.sumTodayKcal(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void isEnabled_whenConfigDisabled_returnsFalse() {
        when(props.isDietEnabled()).thenReturn(false);
        assertThat(provider.isEnabled(1L)).isFalse();
    }

    @Test
    void isEnabled_whenConfigEnabled_returnsTrue() {
        when(props.isDietEnabled()).thenReturn(true);
        assertThat(provider.isEnabled(1L)).isTrue();
    }
}
```

实现 `DietAiProvider.java`：

```java
package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.diet.repository.DietMapper;
import org.springframework.stereotype.Component;

@Component
public class DietAiProvider implements AiInsightProvider {

    private final DietMapper dietMapper;
    private final AiInsightProperties props;

    public DietAiProvider(DietMapper dietMapper, AiInsightProperties props) {
        this.dietMapper = dietMapper;
        this.props = props;
    }

    @Override public String key() { return "diet"; }
    @Override public boolean isEnabled(Long userId) { return props.isDietEnabled(); }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        var kcal = dietMapper.sumTodayKcal(userId, ctx.today());
        return new MetricValue(kcal, "kcal", kcal.signum() == 0 ? Trend.NONE : Trend.FLAT);
    }
}
```

- [ ] **Step 3（若 diet 模块不存在，简化版）: 创建最小 stub**

跳过本任务至 PR 6 后由 Diet 模块维护者补全；本任务的 spec 设计是"模块未上线时降级"，所以即使不实现也不影响 AI 模块整体（spec §7.2）。

- [ ] **Step 4: 运行测试 + Commit**

```powershell
cd backend; mvn -q test -Dtest=DietAiProviderTest
```

预期：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`（如 diet 模块不存在则跳过此任务）。

```bash
git add backend/src/main/java/com/lifepulse/ai/provider/DietAiProvider.java
git add backend/src/main/java/com/lifepulse/diet/repository/DietMapper.java
git add backend/src/test/java/com/lifepulse/ai/provider/DietAiProviderTest.java
git commit -m "feat(ai): add DietAiProvider with today kcal intake"
```

---
