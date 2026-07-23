### Task 3.1: 写入 ai-templates.properties

**Files:**
- Create: `backend/src/main/resources/ai-templates.properties`

- [ ] **Step 1: 创建 `ai-templates.properties`**

```properties
# AI 洞察主文模板（spec §8）
# 占位符 {0}..{n} 顺序由 AiTemplateEngine.formatHeadline 决定
headline.full=今日任务完成率 {0}%，{1}；本周消费 ¥{2}，{3}。
headline.taskOnly=今日任务完成率 {0}%，{1}。继续记录几天后将出现更全面的洞察。
headline.expenseOnly=本周消费 ¥{0}，{1}。
headline.empty=还没有数据，继续记录几天后将出现洞察。

# chip 副标模板（spec §8）
chip.taskCompletion.up=较昨日 +{0}pp
chip.taskCompletion.down=较昨日 {0}pp
chip.taskCompletion.flat=与昨日持平
chip.taskCompletion.none=—

chip.weeklyExpense.up=较上周 +{0}%
chip.weeklyExpense.down=较上周 -{0}%
chip.weeklyExpense.flat=与上周持平
chip.weeklyExpense.none=—

chip.planDensity.busy=今日 {0} 项（较忙）
chip.planDensity.normal=今日 {0} 项
chip.planDensity.free=今日 {0} 项（有空闲）
chip.planDensity.none=—

# 占位符数不匹配时降级（spec §10.1）
fallback.headline=数据异常，请稍后重试
```

- [ ] **Step 2: 验证 properties 加载（写一个临时测试）**

跳过此步（properties 加载由 Spring 在 AiTemplateEngine 构造时验证；下一步 TDD 会覆盖）。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/ai-templates.properties
git commit -m "feat(ai): add ai-templates.properties with 11+ template keys"
```

---

### Task 3.2: 创建 AiTemplateEngine（TDD：先写测试 → 实现）

**Files:**
- Create: `backend/src/test/java/com/lifepulse/ai/service/AiTemplateEngineTest.java`
- Create: `backend/src/main/java/com/lifepulse/ai/service/AiTemplateEngine.java`

**Interfaces:**
- Consumes: `AiConstants.TMPL_*`, `ai-templates.properties`
- Produces: `formatHeadline(templateKey, args...) → String`（被 T5 Service 调用）

- [ ] **Step 1: 写失败测试**

```java
package com.lifepulse.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiTemplateEngineTest {

    private AiTemplateEngine engine;

    @BeforeEach
    void setUp() {
        // 加载 classpath 下的 ai-templates.properties
        engine = new AiTemplateEngine();
        engine.loadFromClasspath();
    }

    @Test
    void formatHeadline_emptyKey_rendersWelcomeText() {
        String result = engine.formatHeadline("headline.empty");
        assertThat(result).isEqualTo("还没有数据，继续记录几天后将出现洞察。");
    }

    @Test
    void formatHeadline_fullKey_rendersAllPlaceholders() {
        String result = engine.formatHeadline(
            "headline.full",
            "80", "较昨日 +5pp", "420", "较上周 -12%"
        );
        assertThat(result).isEqualTo(
            "今日任务完成率 80%，较昨日 +5pp；本周消费 ¥420，较上周 -12%。"
        );
    }

    @Test
    void formatHeadline_taskOnlyKey_omitsExpense() {
        String result = engine.formatHeadline(
            "headline.taskOnly",
            "80", "较昨日 +5pp"
        );
        assertThat(result).isEqualTo(
            "今日任务完成率 80%，较昨日 +5pp。继续记录几天后将出现更全面的洞察。"
        );
    }

    @Test
    void formatChipDelta_up_returnsPlusDeltaText() {
        String result = engine.formatChipDelta("taskCompletion", "up", "5");
        assertThat(result).isEqualTo("较昨日 +5pp");
    }

    @Test
    void formatChipDelta_down_returnsMinusDeltaText() {
        String result = engine.formatChipDelta("weeklyExpense", "down", "12");
        assertThat(result).isEqualTo("较上周 -12%");
    }

    @Test
    void formatChipDelta_flat_returnsNeutralText() {
        String result = engine.formatChipDelta("taskCompletion", "flat", "0");
        assertThat(result).isEqualTo("与昨日持平");
    }

    @Test
    void formatChipDelta_none_returnsDash() {
        String result = engine.formatChipDelta("taskCompletion", "none", "");
        assertThat(result).isEqualTo("—");
    }

    @Test
    void formatHeadline_placeholderCountMismatch_fallsBackToErrorText() {
        // headline.full 需要 4 个参数，这里只给 2 个，触发 IllegalFormatException
        String result = engine.formatHeadline("headline.full", "80", "up");
        assertThat(result).isEqualTo("数据异常，请稍后重试");
    }
}
```

- [ ] **Step 2: 运行测试，预期 FAIL**

```powershell
cd backend; mvn -q test -Dtest=AiTemplateEngineTest
```

预期：编译失败（`AiTemplateEngine` 不存在）。

- [ ] **Step 3: 实现 `AiTemplateEngine.java`**

```java
package com.lifepulse.ai.service;

import com.lifepulse.ai.AiConstants;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 模板引擎（spec §8）。
 *
 * <p>从 {@code classpath:ai-templates.properties} 加载模板键值，
 * 使用 JDK {@link String#format(String, Object...)} 渲染。
 *
 * <p>降级语义（spec §10.1）：
 * <ul>
 *   <li>键缺失 → 启动期 fail fast（构造时即抛异常）</li>
 *   <li>占位符数量不匹配 → log.error + 返回 {@code fallback.headline}</li>
 * </ul>
 */
@Component
public class AiTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(AiTemplateEngine.class);

    private static final String RESOURCE_PATH = "ai-templates.properties";

    private final Properties templates = new Properties();

    /** Spring 启动时调用（构造注入）。 */
    public AiTemplateEngine() {
        loadFromClasspath();
    }

    /** 显式加载（测试用）。 */
    public void loadFromClasspath() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE_PATH);
            }
            templates.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    /**
     * 渲染主文。
     *
     * @param key  模板键（如 {@code headline.full}）
     * @param args 占位符参数
     * @return 渲染结果；占位符错位时降级为 {@code fallback.headline}
     */
    public String formatHeadline(String key, Object... args) {
        String template = templates.getProperty(key);
        if (template == null) {
            log.error("Missing template key: {}", key);
            return templates.getProperty(AiConstants.TMPL_HEADLINE_EMPTY);
        }
        try {
            return String.format(template, args);
        } catch (IllegalFormatException e) {
            log.error("Template format error: key={}, args={}", key, args.length, e);
            return templates.getProperty("fallback.headline", "数据异常，请稍后重试");
        }
    }

    /**
     * 渲染 chip 副标。
     *
     * <p>键由 {@code chip.<key>.<trend>} 拼接；缺失时返回 "—"
     *
     * @param chipKey chip key（如 {@code taskCompletion}）
     * @param trend   trend（如 {@code up}/{@code down}/{@code flat}/{@code none}）
     * @param value   数值（用于格式化）
     */
    public String formatChipDelta(String chipKey, String trend, Object value) {
        String key = AiConstants.TMPL_CHIP_PREFIX + chipKey + "." + trend;
        String template = templates.getProperty(key);
        if (template == null) {
            return "—";
        }
        try {
            return String.format(template, value);
        } catch (IllegalFormatException e) {
            log.error("Chip delta format error: key={}, trend={}", chipKey, trend, e);
            return "—";
        }
    }
}
```

> 注意：类中 `import java.util.IllegalFormatException` 需替换为具体类（见下一步补全）。

- [ ] **Step 3.1: 修正 import**

```java
import java.util.IllegalFormatException;
```

- [ ] **Step 4: 运行测试，预期 PASS**

```powershell
cd backend; mvn -q test -Dtest=AiTemplateEngineTest
```

预期：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/service/AiTemplateEngine.java
git add backend/src/test/java/com/lifepulse/ai/service/AiTemplateEngineTest.java
git commit -m "feat(ai): add AiTemplateEngine with format and fallback"
```

---
