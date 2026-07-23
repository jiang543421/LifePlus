### Task 4.2: TaskAiProvider + 单测

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/provider/TaskAiProvider.java`
- Create: `backend/src/test/java/com/lifepulse/ai/provider/TaskAiProviderTest.java`

**Interfaces:**
- Consumes: `TaskMapper.findCompletionRate(userId, today)`（待 mapper 扩展；如未提供，spec §4.3 允许使用现有 mapper 组合查询）
- Produces: `MetricValue{value: 完成率 0-100, unit: "%", trend: UP/DOWN/FLAT/NONE}`

- [ ] **Step 1: 写失败测试**

```java
package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.task.repository.TaskMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskAiProviderTest {

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskAiProvider provider;

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void collect_allTasksDone_returnsCompletion100() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(10);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(10);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(mv.unit()).isEqualTo("%");
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void collect_halfDone_returnsCompletion50() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(10);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(5);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void collect_noTasks_returnsZeroAndNotNonEmpty() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(0);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(0);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void collect_passesUserIdToMapper() {
        when(taskMapper.countTodayTasks(anyLong(), any())).thenReturn(2);
        when(taskMapper.countTodayCompletedTasks(anyLong(), any())).thenReturn(1);

        provider.collect(42L, ctx);

        org.mockito.Mockito.verify(taskMapper).countTodayTasks(
            org.mockito.ArgumentMatchers.eq(42L), any());
    }

    @Test
    void isEnabled_alwaysTrue() {
        assertThat(provider.isEnabled(1L)).isTrue();
        assertThat(provider.isEnabled(999L)).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd backend; mvn -q test -Dtest=TaskAiProviderTest
```

预期：编译失败（`TaskAiProvider` 不存在 + `TaskMapper` 无 `countTodayTasks` 方法）。

- [ ] **Step 3: 扩展 `TaskMapper`**

打开 `backend/src/main/java/com/lifepulse/task/repository/TaskMapper.java`，追加两个方法：

```java
/**
 * 统计指定用户在指定日期的当日任务总数（含已逻辑删除过滤）。
 * 用于 AI 模块任务完成率聚合。
 */
int countTodayTasks(@Param("userId") Long userId, @Param("date") LocalDate date);

/**
 * 统计指定用户在指定日期的当日已完成任务数。
 * 用于 AI 模块任务完成率聚合。
 */
int countTodayCompletedTasks(@Param("userId") Long userId, @Param("date") LocalDate date);
```

> 若 Mapper 已有 XML 实现（`backend/src/main/resources/mapper/task/TaskMapper.xml`），追加对应 `<select>`：
>
> ```xml
> <select id="countTodayTasks" resultType="int">
>     SELECT COUNT(*) FROM t_task
>     WHERE user_id = #{userId}
>       AND deleted = 0
>       AND DATE(due_at) = #{date}
> </select>
>
> <select id="countTodayCompletedTasks" resultType="int">
>     SELECT COUNT(*) FROM t_task
>     WHERE user_id = #{userId}
>       AND deleted = 0
>       AND status = 'DONE'
>       AND DATE(due_at) = #{date}
> </select>
> ```
>
> ⚠️ 必须按 user_id 过滤（spec §7.2 + CLAUDE.md §7.2 越权防护）。

- [ ] **Step 4: 实现 `TaskAiProvider.java`**

```java
package com.lifepulse.ai.provider;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.task.repository.TaskMapper;
import org.springframework.stereotype.Component;

/**
 * 任务完成率 provider（spec §7.1）。
 *
 * <p>完成率 = 今日已完成任务数 / 今日总任务数 * 100。
 * 无任务时返回 0 + NONE（不算"有意义信号"）。
 */
@Component
public class TaskAiProvider implements AiInsightProvider {

    private final TaskMapper taskMapper;

    public TaskAiProvider(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public String key() {
        return "task";
    }

    @Override
    public boolean isEnabled(Long userId) {
        // Task 模块在 MVP1 已上线；无需配置开关
        return true;
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        int total = taskMapper.countTodayTasks(userId, ctx.today());
        int done = taskMapper.countTodayCompletedTasks(userId, ctx.today());
        int rate = total == 0 ? 0 : Math.round((float) done * 100 / total);
        return new MetricValue(
            new java.math.BigDecimal(rate),
            "%",
            total == 0 ? Trend.NONE : Trend.FLAT  // 简化：未对比昨日
        );
    }
}
```

- [ ] **Step 5: 运行测试，预期 PASS**

```powershell
cd backend; mvn -q test -Dtest=TaskAiProviderTest
```

预期：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/provider/TaskAiProvider.java
git add backend/src/main/java/com/lifepulse/task/repository/TaskMapper.java
git add backend/src/main/resources/mapper/task/TaskMapper.xml
git add backend/src/test/java/com/lifepulse/ai/provider/TaskAiProviderTest.java
git commit -m "feat(ai): add TaskAiProvider with task completion rate"
```

---
