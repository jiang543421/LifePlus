## 4. 架构总览

### 4.1 后端包结构

```
backend/src/main/java/com/lifepulse/ai/
├─ AiConstants.java                  # 常量（缓存键、模板键、chip 数）
├─ AiInsightProperties.java          # @ConfigurationProperties("lp.ai")
├─ web/
│  ├─ AiInsightController.java      # GET /today, POST /refresh
│  └─ dto/
│     ├─ AiInsightResponse.java
│     └─ AiChipDto.java
├─ service/
│  ├─ AiInsightService.java         # 编排 + 缓存
│  └─ AiTemplateEngine.java          # 模板 format
├─ provider/
│  ├─ AiInsightProvider.java         # 接口
│  ├─ TaskAiProvider.java
│  ├─ PlanAiProvider.java
│  ├─ ExpenseAiProvider.java
│  ├─ DietAiProvider.java
│  └─ DailyAiProvider.java           # 默认 isEnabled=false
└─ model/
   ├─ AiInsightPayload.java          # 内部领域对象
   └─ MetricValue.java
```

### 4.2 前端包结构

```
frontend/src/
├─ views/AiInsightDrawerView.vue
├─ components/
│  ├─ AiInsightCard.vue
│  └─ AiChipItem.vue
├─ stores/aiInsight.ts
├─ api/ai.ts
└─ types/ai.ts
```

### 4.3 关键依赖

**全部既有**：Spring Security、`@Cacheable`、RedisTemplate、UserContext、TaskMapper/PlanMapper/ExpenseMapper/DietMapper、DailyAiProvider（复用）、Element Plus、Pinia、Axios。

### 4.4 架构边界

- **不依赖** `daily.web` / `daily.service.DailyReportService`（那是日报业务，不是 AI 输入）
- 仅**调用** `ai.provider.DailyAiProvider` 作为数据源
- 缓存命名空间独立 `ai:insight:*`（不与 `daily:*` 冲突）

---

## 5. 数据模型

**无新表、无新 migration。** AI 模块只读。

唯一持久化是 Redis 缓存条目，TTL 30 分钟。

---

## 6. API 规格

### 6.1 端点

| 方法 | 路径 | 说明 | 鉴权 | 限流 |
|---|---|---|---|---|
| GET | `/api/v1/ai/insight/today` | 返回当前洞察（缓存优先） | access token | 60 次/分/用户 |
| POST | `/api/v1/ai/insight/refresh` | 清缓存 + 立即重算 | access token | 6 次/分/用户 |

### 6.2 响应载荷

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "headline": "今日任务完成率 80%，较昨日提升 5 个百分点；本周消费 ¥420，较上周下降 12%。",
    "chips": [
      { "key": "taskCompletion", "label": "任务完成率", "value": "80", "unit": "%", "trend": "up",   "deltaText": "较昨日 +5pp" },
      { "key": "weeklyExpense",   "label": "本周消费",   "value": "420","unit": "¥", "trend": "down", "deltaText": "较上周 -12%" },
      { "key": "planDensity",     "label": "今日日程",   "value": "4",  "unit": "项","trend": "flat", "deltaText": "今日 4 项" }
    ],
    "generatedAt": "2026-07-21T17:30:00+08:00",
    "freshnessSeconds": 1234
  }
}
```

### 6.3 字段约束

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `headline` | string | ✅ | 40-120 字中文，1-2 句 |
| `chips` | array | ✅ | 固定 3 槽位（按 taskCompletion → weeklyExpense → planDensity 顺序），全空数据时 `chips=[]` |
| `chips[].key` | enum | ✅ | `taskCompletion` / `weeklyExpense` / `planDensity`（v2.0 仅 3 槽；`dietIntake` / `dailyStreak` 留 v2.1+） |
| `chips[].value` | string | ✅ | 无数据时 `"—"` |
| `chips[].trend` | enum | ✅ | `UP` / `DOWN` / `FLAT` / `NONE`（Java enum 大写，序列化按需转小写） |
| `chips[].deltaText` | string | ✅ | ≤ 16 字，无数据时 `""` |
| `generatedAt` | ISO-8601 | ✅ | 服务端生成时间，含时区 |
| `freshnessSeconds` | int | ✅ | 在 `AiInsightController` 层现算：`Duration.between(generatedAt, now()).getSeconds()`，前端展示"X 分钟前"；负值钳为 0 |

### 6.4 错误码

| code | 含义 | HTTP |
|---|---|---|
| 0 | 成功 | 200 |
| 1001 | 参数校验失败 | 400 |
| 1003 | 跨用户越权 | 403 |
| 1006 | 限流命中 | 429 |
| **1501** | **AI 服务整体不可用**（所有 provider 失败） | **503** |

> 1501 为 AI 模块唯一新增错误码；其余沿用全局。

---

## 7. Provider 契约

```java
public interface AiInsightProvider {
    String key();                                            // 用于 metrics map key
    boolean isEnabled(Long userId);                          // 配置开关 + 用户条件
    MetricValue collect(Long userId, AiCollectContext ctx);  // 聚合查询
}
```

### 7.1 Provider 清单

| Provider | key | isEnabled 默认 | 数据来源 |
|---|---|---|---|
| TaskAiProvider | `taskCompletion` | true | `t_task`（今日完成率） |
| PlanAiProvider | `planDensity` | true | `t_plan`（今日事件数） |
| ExpenseAiProvider | `weeklyExpense` | true | `t_expense`（本周 vs 上周） |
| DietAiProvider | `dietIntake` | true | `t_diet`（今日摄入/目标） |
| DailyAiProvider | `dailyStreak` | **false**（v1.2.3 合并后改 true）| `t_daily_report*`（连续生成天数） |

> 4 个 `isEnabled=true` 的 provider 在 `AiInsightProperties` 中以 Java 默认值生效，无需在 `application.yml` 显式配置；仅 `daily-enabled` 因默认 `false` 需在 yaml 写明以便运维确认与灰度。

### 7.2 降级语义

- **Provider 模块未部署** → `isEnabled=false` → Service 跳过
- **Provider 抛运行时异常** → Service 内 `try/catch` 捕获，`log.warn` 后跳过该 provider
- **所有 enabled provider 失败** → 抛 `BusinessException(1501)` → 503
- **部分 provider 失败** → 200 + 部分数据；template 路由到对应键

---
