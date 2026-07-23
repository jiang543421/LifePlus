### Task 4.6: DailyAiProvider + 单测（重点 isEnabled 降级）

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/provider/DailyAiProvider.java`
- Create: `backend/src/test/java/com/lifepulse/ai/provider/DailyAiProviderTest.java`
- Consumes: `DailyAiProvider.computeStreak(userId)`（**前置依赖**：如日报 v1.2.3 未提供此方法，先在 daily 包加此方法；见 spec §15 Q5）

**Interfaces:**
- Consumes: `AiInsightProperties.dailyEnabled`（默认 false），`DailyAiProvider.computeStreak(userId)`
- Produces: `MetricValue{value: 连续日报天数, unit: "天", trend: FLAT/NONE}`

- [ ] **Step 1: 检查 DailyAiProvider 是否已提供 computeStreak**

```powershell
# DailyAiProvider 当前为 stub：collect() 直接返回 MetricValue.none()；无 daily 依赖
# 待 daily v1.2.3 合并后再扩展为读取 t_daily_report 的连续天数实现
```

- 若未提供 → 先在 daily 包加：
  ```java
  public MetricValue computeStreak(Long userId) {
      // 查询 t_daily_report 中 userId 连续有记录的天数
      // 返回 MetricValue(value=N, unit="天", trend=Trend.FLAT)
  }
  ```
  独立 commit，**不要**在 AI 分支合入 daily 包改动；改为在 daily 分支提交后合入 main，再 rebase AI 分支。
- 若已提供 → 继续。

- [ ] **Step 2: 写失败测试 `DailyAiProviderTest.java`**

```java
package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.provider.DailyAiProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyAiProviderTest {

    @Mock private DailyAiProvider dailyProvider;
    @Mock private AiInsightProperties props;
    @InjectMocks private DailyAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void isEnabled_whenConfigDisabled_returnsFalse() {
        when(props.isDailyEnabled()).thenReturn(false);
        assertThat(provider.isEnabled(1L)).isFalse();
    }

    @Test
    void isEnabled_whenConfigEnabled_returnsTrue() {
        when(props.isDailyEnabled()).thenReturn(true);
        assertThat(provider.isEnabled(1L)).isTrue();
    }

    @Test
    void collect_whenEnabled_invokesDailyProvider() {
        when(props.isDailyEnabled()).thenReturn(true);
        when(dailyProvider.computeStreak(1L))
            .thenReturn(new MetricValue(new BigDecimal("7"), "天", com.lifepulse.ai.model.Trend.FLAT));
        MetricValue mv = provider.collect(1L, ctx);
        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    void collect_whenDisabled_throwsIllegalState() {
        when(props.isDailyEnabled()).thenReturn(false);
        // Service 永远不会调用 collect（isEnabled=false 已被跳过）
        // 但 provider 自身防御性抛错
        assertThatThrownBy(() -> provider.collect(1L, ctx))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Daily module disabled");
    }

    @Test
    void collect_propagatesExceptionFromDailyProvider() {
        when(props.isDailyEnabled()).thenReturn(true);
        when(dailyProvider.computeStreak(1L))
            .thenThrow(new RuntimeException("DB down"));
        assertThatThrownBy(() -> provider.collect(1L, ctx))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("DB down");
    }
}
```

- [ ] **Step 3: 实现 `DailyAiProvider.java`**

```java
package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.provider.DailyAiProvider;
import org.springframework.stereotype.Component;

@Component
public class DailyAiProvider implements AiInsightProvider {

    private final DailyAiProvider dailyProvider;
    private final AiInsightProperties props;

    public DailyAiProvider(DailyAiProvider dailyProvider, AiInsightProperties props) {
        this.dailyProvider = dailyProvider;
        this.props = props;
    }

    @Override public String key() { return "daily"; }

    @Override
    public boolean isEnabled(Long userId) {
        return props.isDailyEnabled();
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        if (!isEnabled(userId)) {
            throw new IllegalStateException("Daily module disabled");
        }
        return dailyProvider.computeStreak(userId);
    }
}
```

- [ ] **Step 4: 运行测试 + Commit**

```powershell
cd backend; mvn -q test -Dtest=DailyAiProviderTest
```

预期：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。

```bash
git add backend/src/main/java/com/lifepulse/ai/provider/DailyAiProvider.java
git add backend/src/test/java/com/lifepulse/ai/provider/DailyAiProviderTest.java
git commit -m "feat(ai): add DailyAiProvider with module-disabled degradation"
```

---

### Task 4.7: PR 2 端到端验证

- [ ] **Step 1: 全量单测**

```powershell
cd backend; mvn -q test
```

预期：所有测试通过；`ai.provider` 包行覆盖 ≥ 80%。

- [ ] **Step 2: 提 PR**

PR 标题：`feat(ai): add 5 AiInsightProvider implementations + tests`

PR 描述：
```
## 改了什么
- TaskAiProvider：今日完成率
- PlanAiProvider：今日事件数
- ExpenseAiProvider：本周 vs 上周
- DietAiProvider：今日摄入 kcal
- DailyAiProvider：连续日报天数（默认 disabled）
- 扩展 TaskMapper / PlanMapper / ExpenseMapper（Diet 依赖现有）
- 24 个单测全绿

## 测试覆盖
- TaskAiProviderTest: 5 用例
- PlanAiProviderTest: 4 用例
- ExpenseAiProviderTest: 7 用例
- DietAiProviderTest: 4 用例
- DailyAiProviderTest: 5 用例
- 行覆盖：ai.provider ≥ 80%

## 影响面
- 新增 5 个 provider
- 3 个既有 mapper 加 count/sum 方法（向后兼容）
- 无新表 / 无新依赖
```

合并到 `feat/ai-v2.0`，进入 PR 3。

---
