## 13. 任务清单（可追踪）

> 任务编号稳定化，便于跨 PR 引用。**v2.0 共 24 条**。

### T1：项目脚手架
- **T1.1** 创建 `com.lifepulse.ai` 包 + 5 个子包（web/service/provider/model）
- **T1.2** 创建 `AiInsightProperties`（`@ConfigurationProperties("lp.ai")`），含 5 个 enabled 开关（task/plan/expense/diet 默认 true；daily 默认 false）
- **T1.3** 在 `application.yml` 显式写入 `lp.ai.daily-enabled: false`（其余 4 个用 Java 默认值）

### T2：领域模型 + DTO
- **T2.1** `MetricValue` 不可变 record（value: BigDecimal, unit: String, trend: Trend）+ 方法 `isNonEmpty()`
- **T2.2** `AiInsightPayload` 不可变 record（headline, chips, generatedAt）
- **T2.3** `AiChipDto` + `AiInsightResponse`（含 freshnessSeconds 字段，DTO 层计算）

### T3：模板引擎
- **T3.1** `ai-templates.properties` 写入 §8 全量键值
- **T3.2** `AiTemplateEngine.formatHeadline(key, ctx)` 路由 + format
- **T3.3** `AiTemplateEngine.formatChipDelta(key, value)` 渲染副标
- **T3.4** 模板缺失/错位降级逻辑

### T4：Provider 实现 + 单测
- **T4.1** `AiInsightProvider` 接口 + `AiCollectContext`
- **T4.2** `TaskAiProvider` + 单测（5 个用例）
- **T4.3** `PlanAiProvider` + 单测（4 个用例）
- **T4.4** `ExpenseAiProvider` + 单测（7 个用例，含配置开关）
- **T4.5** `DietAiProvider` + 单测（4 个用例）
- **T4.6** `DailyAiProvider` + 单测（5 个用例，重点 isEnabled 降级）

### T5：Service 编排
- **T5.1** `AiInsightService.getOrCompute(userId)`：缓存 → provider 循环 → 模板 → 回写
- **T5.2** `AiInsightService.refresh(userId)`：evict + 重算
- **T5.3** 单 provider 异常 catch + 全失败抛 1501
- **T5.4** Redis 异常降级（读/写/删三路径）

### T6：Controller + 切片测试
- **T6.1** `AiInsightController.getToday()` + `.refresh()`
- **T6.2** `AiInsightControllerWebTest`（6 个用例：401/200/503/refresh）

### T7：限流 + 鉴权集成
- **T7.1** 在 `security/RateLimiter` 配置 `lp:rl:ai:insight:<userId>` 一组键（GET 60/min、POST 6/min 共用前缀）
- **T7.2** 验证 `code=1006` 命中

### T8：集成测试
- **T8.1** `AiAnalysisIT` 框架（Testcontainers + Flyway）
- **T8.2** 5 个集成用例（缓存 hit/miss、daily 降级、cross-user 隔离）

### T9：前端实现
- **T9.1** `types/ai.ts` 与后端 DTO 对齐
- **T9.2** `api/ai.ts`：getToday / refresh
- **T9.3** `stores/aiInsight.ts`：state + load + refresh
- **T9.4** `AiChipItem.vue` + 单测（4 个用例）
- **T9.5** `AiInsightCard.vue` + 单测（7 个用例）
- **T9.6** `AiInsightDrawerView.vue` + 单测（3 个用例）
- **T9.7** 替换 `HomeView` 中占位卡分支

### T10：E2E
- **T10.1** `ai-insight.spec.ts` 4 个用例
- **T10.2** 接入现有 Playwright helper（EP 2.x）

### T11：文档与发布
- **T11.1** 更新 `docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md` 索引
- **T11.2** 在 `README.md` 添加 v2.0 AI 卡说明
- **T11.3** 提 PR 描述 + review checklist

---
