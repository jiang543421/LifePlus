## PR 2 — 5 个 Provider 实现 + 单测

涵盖 spec §13 T4.1 - T4.6。完成此 PR 后，5 个数据源各有独立 `AiInsightProvider` 实现 + 100% 单测覆盖；Service 编排可基于这些 provider 工作。

> **实施提示**：5 个 provider 实现结构高度一致（接口 + collect + 单测），故 T4.2 给出 TaskAiProvider 完整 TDD 步骤；T4.3-T4.6 给出完整可复制的代码 + 单测方法名清单，**不要**"按 T4.2 套路做"——按本计划独立复制完整代码。

### Task 4.1: 定义 AiInsightProvider 接口

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/provider/AiCollectContext.java`
- Create: `backend/src/main/java/com/lifepulse/ai/provider/AiInsightProvider.java`

- [ ] **Step 1: 创建 `AiCollectContext.java`**

```java
package com.lifepulse.ai.provider;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Provider 采集上下文（spec §6）。
 *
 * <p>当前线程 userId 由 Provider 自行通过 {@link com.lifepulse.security.UserContext}
 * 读取；本上下文仅传日期相关参数，避免 Provider 内部重复计算"今天 / 本周"。
 */
public record AiCollectContext(
    LocalDate today,
    ZoneId zone
) {
    /** 默认上海时区今日。 */
    public static AiCollectContext nowInShanghai() {
        return new AiCollectContext(
            LocalDate.now(ZoneId.of("Asia/Shanghai")),
            ZoneId.of("Asia/Shanghai")
        );
    }
}
```

- [ ] **Step 2: 创建 `AiInsightProvider.java`**

```java
package com.lifepulse.ai.provider;

import com.lifepulse.ai.model.MetricValue;

/**
 * AI 洞察 provider 接口（spec §7）。
 *
 * <p>实现方：基于 {@link com.lifepulse.security.UserContext#current()} 拿 userId，
 * 自行调用对应 Mapper 聚合，返回 {@link MetricValue}。
 *
 * <p>{@link #isEnabled(Long)} 由 Service 在循环前调用；返回 false 时整个 provider 跳过。
 */
public interface AiInsightProvider {

    /** provider 唯一键（用于 metrics map key / 日志）。 */
    String key();

    /**
     * 是否启用。读 {@link com.lifepulse.ai.AiInsightProperties} 开关 + 运行时条件。
     */
    boolean isEnabled(Long userId);

    /**
     * 采集指标。失败时抛异常，由 Service catch（spec §4.3）；不可返回 null 替代抛错。
     */
    MetricValue collect(Long userId, AiCollectContext ctx);
}
```

- [ ] **Step 3: 编译验证**

```powershell
cd backend; mvn -q compile -DskipTests
```

预期：`BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lifepulse/ai/provider/
git commit -m "feat(ai): add AiInsightProvider interface and AiCollectContext"
```

---
