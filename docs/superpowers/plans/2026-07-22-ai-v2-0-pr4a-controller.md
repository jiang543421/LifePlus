## PR 4 — Controller + 限流

涵盖 spec §13 T6.1 - T7.2。完成此 PR 后，2 个 REST 端点可用；含完整鉴权 + 限流。

### Task 6.1: AiInsightController + freshnessSeconds 计算

**Files:**
- Create: `backend/src/main/java/com/lifepulse/ai/web/AiInsightController.java`
- Create: `backend/src/main/java/com/lifepulse/ai/web/dto/AiInsightResponseMapper.java`

**Interfaces:**
- Consumes: `AiInsightService.getOrCompute / refresh`, `UserContext.current()`
- Produces: `MyResponse<AiInsightResponse>`

- [ ] **Step 1: 创建 `AiInsightResponseMapper.java`**

```java
package com.lifepulse.ai.web.dto;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.AiInsightPayload;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.ai.service.AiTemplateEngine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 将内部 {@link AiInsightPayload} 映射为前端 {@link AiInsightResponse}。
 *
 * <p>负责：固定 3 chip 槽位 + 副标渲染 + freshnessSeconds 现算。
 */
@Component
public class AiInsightResponseMapper {

    private final AiTemplateEngine templateEngine;

    public AiInsightResponseMapper(AiTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public AiInsightResponse toResponse(AiInsightPayload payload, Instant now) {
        long freshness = Math.max(
            0, Duration.between(payload.generatedAt(), now).getSeconds()
        );
        return new AiInsightResponse(
            payload.headline(),
            buildChips(payload.chips()),
            payload.generatedAt(),
            freshness
        );
    }

    private List<AiChipDto> buildChips(List<MetricValue> metrics) {
        if (metrics.isEmpty()) {
            return List.of();
        }
        List<AiChipDto> chips = new ArrayList<>();
        // 固定顺序：taskCompletion → weeklyExpense → planDensity
        chips.add(toChip(AiConstants.CHIP_TASK_COMPLETION, "任务完成率", "%",
            findMetric(metrics, 0)));
        chips.add(toChip(AiConstants.CHIP_WEEKLY_EXPENSE, "本周消费", "¥",
            findMetric(metrics, 1)));
        chips.add(toChip(AiConstants.CHIP_PLAN_DENSITY, "今日日程", "项",
            findMetric(metrics, 2)));
        return chips;
    }

    private MetricValue findMetric(List<MetricValue> metrics, int index) {
        return index < metrics.size() ? metrics.get(index) : null;
    }

    private AiChipDto toChip(String key, String label, String defaultUnit, MetricValue mv) {
        if (mv == null || !mv.isNonEmpty()) {
            return AiChipDto.empty(key, label);
        }
        String deltaText = templateEngine.formatChipDelta(
            key, mv.trend().name().toLowerCase(), mv.value().toPlainString()
        );
        return new AiChipDto(
            key, label, mv.value().toPlainString(), mv.unit(), mv.trend(), deltaText
        );
    }
}
```

- [ ] **Step 2: 创建 `AiInsightController.java`**

```java
package com.lifepulse.ai.web;

import com.lifepulse.ai.service.AiInsightService;
import com.lifepulse.ai.web.dto.AiInsightResponse;
import com.lifepulse.ai.web.dto.AiInsightResponseMapper;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.security.UserContext;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 洞察 REST 端点（spec §6.1）。
 *
 * <ul>
 *   <li>GET /api/v1/ai/insight/today — 返回当前洞察（缓存优先）</li>
 *   <li>POST /api/v1/ai/insight/refresh — 清缓存 + 立即重算</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai/insight")
public class AiInsightController {

    private final AiInsightService service;
    private final AiInsightResponseMapper mapper;

    public AiInsightController(AiInsightService service, AiInsightResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/today")
    public MyResponse<AiInsightResponse> getToday() {
        Long userId = UserContext.current();
        var payload = service.getOrCompute(userId);
        return MyResponse.ok(mapper.toResponse(payload, Instant.now()));
    }

    @PostMapping("/refresh")
    public MyResponse<AiInsightResponse> refresh() {
        Long userId = UserContext.current();
        var payload = service.refresh(userId);
        return MyResponse.ok(mapper.toResponse(payload, Instant.now()));
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
git add backend/src/main/java/com/lifepulse/ai/web/
git commit -m "feat(ai): add AiInsightController with 2 endpoints"
```

---
