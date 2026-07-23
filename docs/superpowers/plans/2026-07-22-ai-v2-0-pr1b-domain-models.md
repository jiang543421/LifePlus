### Task 2.1: 创建 MetricValue record + Trend 枚举

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/model/MetricValue.java`
- Create: `backend/src/test/java/com/lifepulse/ai/model/MetricValueTest.java`

- [ ] **Step 1: 写失败测试 `MetricValueTest`**

```java
package com.lifepulse.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MetricValueTest {

    @Test
    void isNonEmpty_valueGreaterThanZero_returnsTrue() {
        var mv = new MetricValue(new BigDecimal("80"), "%", Trend.UP);
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void isNonEmpty_valueZero_returnsFalse() {
        var mv = new MetricValue(BigDecimal.ZERO, "项", Trend.NONE);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void isNonEmpty_valueNull_returnsFalse() {
        var mv = new MetricValue(null, "项", Trend.NONE);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void trend_upOrDown_isSignificant() {
        assertThat(Trend.UP.isSignificant()).isTrue();
        assertThat(Trend.DOWN.isSignificant()).isTrue();
        assertThat(Trend.FLAT.isSignificant()).isFalse();
        assertThat(Trend.NONE.isSignificant()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd backend; mvn -q test -Dtest=MetricValueTest
```

预期：编译失败（`MetricValue` 不存在）。

- [ ] **Step 3: 创建 `MetricValue.java`**

```java
package com.lifepulse.ai.model;

import java.math.BigDecimal;

/**
 * Provider 采集到的单个指标值（不可变 record）。
 *
 * <p>所有数值用 {@link BigDecimal} 避免浮点精度问题（任务完成率、消费额）。
 * {@link Trend} 描述相对变化方向，用于 chip 颜色与副标文案。
 *
 * @param value  数值；可为 {@code null}（表示"无数据"）
 * @param unit   显示单位（"%" / "¥" / "项" / "kcal"）
 * @param trend  变化方向
 */
public record MetricValue(BigDecimal value, String unit, Trend trend) {

    /** 是否包含有效数据。value 非 null 且 > 0。 */
    public boolean isNonEmpty() {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
```

```java
package com.lifepulse.ai.model;

/**
 * 指标变化方向（用于 chip 颜色与副标路由）。
 *
 * <p>{@code UP} 绿、{@code DOWN} 红、{@code FLAT} 灰、{@code NONE} 浅灰。
 */
public enum Trend {
    UP, DOWN, FLAT, NONE;

    /** 是否为"有意义"的变化方向（用于决定副标路由）。 */
    public boolean isSignificant() {
        return this == UP || this == DOWN;
    }
}
```

- [ ] **Step 4: 运行测试，预期 PASS**

```powershell
cd backend; mvn -q test -Dtest=MetricValueTest
```

预期：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/model/MetricValue.java
git add backend/src/test/java/com/lifepulse/ai/model/MetricValueTest.java
git commit -m "feat(ai): add MetricValue record and Trend enum"
```

---

### Task 2.2: 创建 AiInsightPayload 内部领域对象

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/model/AiInsightPayload.java`

- [ ] **Step 1: 创建 `AiInsightPayload.java`**

```java
package com.lifepulse.ai.model;

import java.time.Instant;
import java.util.List;

/**
 * AI 洞察内部领域对象（spec §6.2）。
 *
 * <p>这是 Service 层与 Controller 层之间的传递对象；不含 {@code freshnessSeconds}
 * （在 Controller 现算，spec §6.3）。可序列化为 Redis 缓存值。
 *
 * @param headline    中文主文，1-2 句
 * @param chips       3 个 chip（顺序固定）；全空数据时可为空列表
 * @param generatedAt 服务端生成时间
 */
public record AiInsightPayload(
    String headline,
    List<MetricValue> chips,
    Instant generatedAt
) {
    public AiInsightPayload {
        // 防御性拷贝，保证不可变
        chips = chips == null ? List.of() : List.copyOf(chips);
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
cd backend; mvn -q compile -DskipTests
```

预期：`BUILD SUCCESS`。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/model/AiInsightPayload.java
git commit -m "feat(ai): add AiInsightPayload domain record"
```

---

### Task 2.3: 创建 DTO（AiChipDto + AiInsightResponse）

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/web/dto/AiChipDto.java`
- Create: `backend/src/main/java/com/lifepulse/ai/web/dto/AiInsightResponse.java`

- [ ] **Step 1: 创建 `AiChipDto.java`**

```java
package com.lifepulse.ai.web.dto;

import com.lifepulse.ai.model.Trend;

/**
 * 单个指标 chip（spec §6.2）。
 *
 * <p>卡面固定 3 个 chip，顺序：taskCompletion → weeklyExpense → planDensity。
 * 全空数据时 {@code value="—"} {@code trend=NONE} {@code deltaText=""}。
 */
public record AiChipDto(
    String key,
    String label,
    String value,
    String unit,
    Trend trend,
    String deltaText
) {

    /** 全空数据占位 chip（用于 chips=[] 时的占位）。 */
    public static AiChipDto empty(String key, String label) {
        return new AiChipDto(key, label, "—", "", Trend.NONE, "");
    }
}
```

- [ ] **Step 2: 创建 `AiInsightResponse.java`**

```java
package com.lifepulse.ai.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * /api/v1/ai/insight/today 响应 data 字段（spec §6.2）。
 *
 * <p>{@code freshnessSeconds} 由 Controller 现算：
 * {@code Duration.between(generatedAt, Instant.now()).getSeconds()}，负值钳为 0。
 *
 * @param headline          中文主文
 * @param chips             3 个 chip
 * @param generatedAt       服务端生成时间
 * @param freshnessSeconds  距生成的秒数（负值钳为 0）
 */
public record AiInsightResponse(
    String headline,
    List<AiChipDto> chips,
    Instant generatedAt,
    long freshnessSeconds
) {

    /** 钳制 freshnessSeconds 不为负。 */
    public AiInsightResponse {
        if (freshnessSeconds < 0) {
            freshnessSeconds = 0;
        }
        chips = chips == null ? List.of() : List.copyOf(chips);
    }
}
```

- [ ] **Step 3: 编译验证**

```powershell
cd backend; mvn -q compile -DskipTests
```

预期：`BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/web/dto/
git commit -m "feat(ai): add AiChipDto and AiInsightResponse DTOs"
```

---
