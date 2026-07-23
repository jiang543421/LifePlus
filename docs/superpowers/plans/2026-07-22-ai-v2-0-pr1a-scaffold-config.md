## PR 1 — 后端基础（脚手架 + 模型 + 模板引擎）

涵盖 spec §13 的 T1.1 - T3.4。完成此 PR 后，后端工程可编译、模板引擎有 100% 单测覆盖；后续 PR 在此基础上叠加。

### Task 1.1: 创建 ai 包 + 4 个子包 + AiConstants

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/AiConstants.java`
- Create: `backend/src/main/java/com/lifepulse/ai/web/.gitkeep`
- Create: `backend/src/main/java/com/lifepulse/ai/service/.gitkeep`
- Create: `backend/src/main/java/com/lifepulse/ai/provider/.gitkeep`
- Create: `backend/src/main/java/com/lifepulse/ai/model/.gitkeep`
- Create: `backend/src/main/java/com/lifepulse/ai/exception/.gitkeep`

**Interfaces:**
- Produces: `AiConstants` 静态常量（被后续 T1.2 / T2 / T3 / T5 引用）

- [ ] **Step 1: 创建 `AiConstants.java`**

```java
package com.lifepulse.ai;

/**
 * AI 模块全局常量（spec §6.1 / §6.3 / §8）。
 *
 * <p>所有魔法数字、键名集中在此，便于 review 与未来重构。
 */
public final class AiConstants {

    /** Redis 缓存键前缀（spec §9）。 */
    public static final String CACHE_KEY_PREFIX = "ai:insight:";

    /** 缓存 TTL 30 分钟（spec §6.1）。 */
    public static final long CACHE_TTL_MINUTES = 30L;

    /** 卡面固定 chip 数（spec §6.3）。 */
    public static final int CHIP_SLOT_COUNT = 3;

    /** headline 模板键。 */
    public static final String TMPL_HEADLINE_FULL = "headline.full";
    public static final String TMPL_HEADLINE_TASK_ONLY = "headline.taskOnly";
    public static final String TMPL_HEADLINE_EXPENSE_ONLY = "headline.expenseOnly";
    public static final String TMPL_HEADLINE_EMPTY = "headline.empty";

    /** chip 副标模板键前缀。 */
    public static final String TMPL_CHIP_PREFIX = "chip.";

    /** chip key 常量（与 DTO enum 对齐）。 */
    public static final String CHIP_TASK_COMPLETION = "taskCompletion";
    public static final String CHIP_WEEKLY_EXPENSE = "weeklyExpense";
    public static final String CHIP_PLAN_DENSITY = "planDensity";
    public static final String CHIP_DIET_INTAKE = "dietIntake";
    public static final String CHIP_DAILY_STREAK = "dailyStreak";

    /** Provider key 常量。 */
    public static final String PROVIDER_TASK = "task";
    public static final String PROVIDER_PLAN = "plan";
    public static final String PROVIDER_EXPENSE = "expense";
    public static final String PROVIDER_DIET = "diet";
    public static final String PROVIDER_DAILY = "daily";

    private AiConstants() {
        // 静态工具类，禁止实例化
    }
}
```

- [ ] **Step 2: 在 4 个子包下创建 `.gitkeep` 占位文件**

执行 PowerShell（Windows）：

```powershell
New-Item -ItemType File -Force backend/src/main/java/com/lifepulse/ai/web/.gitkeep,
  backend/src/main/java/com/lifepulse/ai/service/.gitkeep,
  backend/src/main/java/com/lifepulse/ai/provider/.gitkeep,
  backend/src/main/java/com/lifepulse/ai/model/.gitkeep,
  backend/src/main/java/com/lifepulse/ai/exception/.gitkeep
```

> 子包创建后无内容会被 git 忽略；`.gitkeep` 占位确保目录被追踪。

- [ ] **Step 3: 编译验证**

执行：
```powershell
cd backend; mvn -q compile -DskipTests
```

预期：`BUILD SUCCESS`，无编译错误。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/
git commit -m "feat(ai): add ai package scaffold with AiConstants"
```

---

### Task 1.2: 创建 AiInsightProperties 配置类

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/AiInsightProperties.java`

**Interfaces:**
- Consumes: `AiConstants.PROVIDER_*`
- Produces: `AiInsightProperties`（被 T1.3 application.yml 引用；被 DailyAiProvider 读取）

- [ ] **Step 1: 创建 `AiInsightProperties.java`**

```java
package com.lifepulse.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置项（spec §7.1）。
 *
 * <p>5 个 enabled 开关控制 provider 启用状态：
 * <ul>
 *   <li>task / plan / expense / diet 默认 true（对应模块已合入 main）</li>
 *   <li>daily 默认 false（v1.2.3 合并后改 true）</li>
 * </ul>
 *
 * <p>5 个开关独立，便于灰度与回滚。
 */
@ConfigurationProperties(prefix = "lp.ai")
public class AiInsightProperties {

    private boolean taskEnabled = true;
    private boolean planEnabled = true;
    private boolean expenseEnabled = true;
    private boolean dietEnabled = true;
    private boolean dailyEnabled = false;

    public boolean isTaskEnabled() {
        return taskEnabled;
    }

    public void setTaskEnabled(boolean taskEnabled) {
        this.taskEnabled = taskEnabled;
    }

    public boolean isPlanEnabled() {
        return planEnabled;
    }

    public void setPlanEnabled(boolean planEnabled) {
        this.planEnabled = planEnabled;
    }

    public boolean isExpenseEnabled() {
        return expenseEnabled;
    }

    public void setExpenseEnabled(boolean expenseEnabled) {
        this.expenseEnabled = expenseEnabled;
    }

    public boolean isDietEnabled() {
        return dietEnabled;
    }

    public void setDietEnabled(boolean dietEnabled) {
        this.dietEnabled = dietEnabled;
    }

    public boolean isDailyEnabled() {
        return dailyEnabled;
    }

    public void setDailyEnabled(boolean dailyEnabled) {
        this.dailyEnabled = dailyEnabled;
    }
}
```

- [ ] **Step 2: 在启动类扫描此配置类**

打开 `backend/src/main/java/com/lifepulse/LifePulseApplication.java`，确认类上有 `@ConfigurationPropertiesScan` 或显式 `@EnableConfigurationProperties(AiInsightProperties.class)` 注解。

若仅有 `@SpringBootApplication`，追加：
```java
import com.lifepulse.ai.AiInsightProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiInsightProperties.class)
public class LifePulseApplication {
    // ... 既有内容
}
```

- [ ] **Step 3: 编译验证**

```powershell
cd backend; mvn -q compile -DskipTests
```

预期：`BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/AiInsightProperties.java
git add backend/src/main/java/com/lifepulse/LifePulseApplication.java
git commit -m "feat(ai): add AiInsightProperties with 5 provider toggles"
```

---

### Task 1.3: 在 application.yml 显式声明 daily-enabled=false

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 追加 lp.ai 段**

打开 `backend/src/main/resources/application.yml`，在文件末尾追加：

```yaml
# AI 分析模块（spec §7.1）
# 4 个 enabled=true 用 Java 默认值；daily-enabled=false 必须显式声明
# 避免 v1.2.3 合并前误用日报表导致 SQL 错误
lp:
  ai:
    daily-enabled: false
```

- [ ] **Step 2: 启动应用确认配置生效**

```powershell
cd backend; mvn -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080" &
sleep 8
curl -s http://localhost:8080/actuator/health | findstr "UP"
# 看到 "UP" 后 Ctrl+C 终止
```

预期：actuator 返回 `{"status":"UP"}`，无配置错误。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application.yml
git commit -m "chore(ai): explicitly set daily-enabled=false in application.yml"
```

---
