## 9. 模板与 Prompt 规范

### 9.1 LLM Prompt 模板（`backend/src/main/resources/llm-prompt.properties`）

```properties
# System prompt（角色定位）
system.role=你是一个个人数字生活助手。基于用户今日的 4 个维度数据，生成简洁的中文洞察和建议。要求：1) 只基于提供的数据，不要编造；2) 中文，1-2 句；3) 不超过 120 字；4) 包含 1 个判断（紧凑/略松/正常/异常）和 1 个数字；5) 语气像朋友建议。

# User prompt 模板（4 chip 数据）
user.chip.taskCompletion=【任务】完成率 {0}%，{1}
user.chip.weeklyExpense=【消费】本周 ¥{0}，{1}
user.chip.planDensity=【日程】今日 {0} 项
user.chip.dietIntake=【饮食】摄入 {0}/{1} kcal

# User prompt 引导
user.template=请基于以下数据生成洞察（JSON 格式，4 字段：headline, advice, highlight, mood）：

{0}

要求：headline 40-120 字；advice 30-80 字；highlight 20-60 字；mood ∈ positive/neutral/cautious。

# Chip 副标（v2.0 沿用，仅任务/消费/计划/饮食）
chip.taskCompletion.up=较昨日 +{0}pp
chip.taskCompletion.down=较昨日 {0}pp
chip.taskCompletion.flat=与昨日持平
chip.weeklyExpense.up=较上周 +{0}%
chip.weeklyExpense.down=较上周 -{0}%
chip.weeklyExpense.flat=与上周持平
chip.planDensity.busy=今日 {0} 项（较忙）
chip.planDensity.normal=今日 {0} 项
chip.planDensity.free=今日 {0} 项（有空闲）
chip.dietIntake.up=较昨日 +{0}%
chip.dietIntake.down=较昨日 -{0}%
chip.dietIntake.flat=与昨日持平

# Headline 模板（v2.0 沿用，L2 降级用）
headline.full=今日任务完成率 {0}%，{1}；本周消费 ¥{2}，{3}。
headline.taskOnly=今日任务完成率 {0}%，{1}。继续记录几天后将出现更全面的洞察。
headline.expenseOnly=本周消费 ¥{0}，{1}。
headline.dietOnly=今日饮食摄入 {0}/{1} kcal，{2}。
headline.empty=还没有数据，继续记录几天后将出现洞察。
fallback.headline=数据异常，请稍后重试
```

### 9.2 LLM 期望输出 JSON Schema

```json
{
  "type": "object",
  "required": ["headline", "advice", "highlight", "mood"],
  "properties": {
    "headline":  { "type": "string", "minLength": 20, "maxLength": 200 },
    "advice":    { "type": "string", "minLength": 10, "maxLength": 200 },
    "highlight": { "type": "string", "minLength": 10, "maxLength": 200 },
    "mood":      { "type": "string", "enum": ["positive", "neutral", "cautious"] }
  }
}
```

### 9.3 模板缺失 / 错位降级

- **模板键缺失** → 启动期 `IllegalStateException` → Spring 启动失败（fail fast）
- **占位符数量不匹配** → `log.error` + 走 `fallback.headline` 降级到 L2 → L3 1501

---
