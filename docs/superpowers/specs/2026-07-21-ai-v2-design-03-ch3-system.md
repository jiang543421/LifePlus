## 8. 模板系统

模板集中在 `backend/src/main/resources/ai-templates.properties`：

```properties
headline.full=今日任务完成率 {0}%，{1}；本周消费 ¥{2}，{3}。
headline.taskOnly=今日任务完成率 {0}%，{1}。继续记录几天后将出现更全面的洞察。
headline.expenseOnly=本周消费 ¥{0}，{1}。
headline.empty=还没有数据，继续记录几天后将出现洞察。
chip.taskCompletion.up=较昨日 +{0}pp
chip.taskCompletion.down=较昨日 {0}pp
chip.taskCompletion.flat=与昨日持平
chip.weeklyExpense.up=较上周 +{0}%
chip.weeklyExpense.down=较上周 -{0}%
chip.weeklyExpense.flat=与上周持平
chip.planDensity.busy=今日 {0} 项（较忙）
chip.planDensity.normal=今日 {0} 项
chip.planDensity.free=今日 {0} 项（有空闲）
```

`AiTemplateEngine.formatHeadline(key, metrics)` 按 enabled+nonEmpty provider 数量路由键，调用 JDK `String.format` 渲染。`formatChipDelta(...)` 渲染 chip 副标。

**降级**（详见 §10）：
- 键缺失 → 启动期 fail fast（Spring 启动失败）
- 占位符数量不匹配 → `log.error` + 降级到 `"数据异常，请稍后重试"`

---

## 9. 缓存策略

| 操作 | 行为 |
|---|---|
| GET 命中 | 直接返回缓存，不延长 TTL |
| GET 未命中 | 计算 → 写缓存（TTL 30min）→ 返回 |
| POST refresh | `redis.delete(key)` → 走 GET 未命中路径 |
| Redis 不可用 | 读视作 MISS；写跳过；删跳过；log.warn |

**缓存键**：`ai:insight:{userId}`  
**TTL**：30 分钟  
**用户隔离**：天然按 `userId` 分键，不存在跨用户泄露

---

## 10. 错误处理与降级

### 10.1 场景总览

| 场景 | HTTP | 行为 |
|---|---|---|
| Provider 模块未部署 | 200 | isEnabled=false 跳过 |
| Provider 抛异常 | 200 | 单 provider 失败，整体成功 |
| 所有 provider 失败 | 503 | 1501 |
| Redis 不可用 | 200 | 降级为无缓存模式 |
| MySQL 不可用 | 503 | 走"所有 provider 失败"路径 |
| 新用户零数据 | 200 | headline.empty + chips 全 `—` |
| 鉴权失败 | 401 | Security 拦截 |
| 限流命中 | 429 | 沿用 1006 |
| 模板缺失 | 启动失败 | fail fast |
| 模板占位符错位 | 200 | 降级文案"数据异常" |

### 10.2 日志规范

- `log.warn` 级别：单 provider 失败、Redis 不可用
- `log.error` 级别：模板渲染异常
- **不打印**：PII（除 userId）、headline 全文、密码/token
- **打印**：provider key、userId、traceId

---
