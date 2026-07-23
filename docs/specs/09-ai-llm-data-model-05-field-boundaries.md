## 4. 字段长度与边界总表

### 4.1 LLM 输出字段约束

| 字段 | 必填 | 最小长度 | 最大长度 | 校验失败处理 |
|---|---|---|---|---|
| `headline` | ✅ | 20 字 | 200 字 | 抛 `LlmResponseInvalidException` → L2 降级 |
| `advice` | ✅ | 10 字 | 200 字 | 同上 |
| `highlight` | ✅ | 10 字 | 200 字 | 同上 |
| `mood` | ✅ | — | — | 钳到 `NEUTRAL`（不算失败）|

> 长度按"字符数"算（不是字节数），中文 1 字 = 1 字符。

### 4.2 Redis 键与值边界

| 键 | Value 最大字节 | 估算 |
|---|---|---|
| `lp:ai:insight:<userId>` | ~2 KB | 4 chip × 200B + headline 200B + advice 200B + highlight 200B + llmMeta 100B + JSON 开销 |
| `lp:ai:quota:*` | 4 字节 | int（"50"）|
| `lp:ai:circuit:state` | 16 字节 | enum string |
| `lp:ai:circuit:state:openedAt` | 16 字节 | epoch ms |
| `lp:ai:circuit:failures` | ~300 B | 10 × "1700000000000" |

### 4.3 配额与熔断阈值边界

| 项 | 默认 | 范围 | 来源 |
|---|---|---|---|
| LLM 配额 | 50/用户/天 | 1-1000 | `lp.ai.llm.daily-quota` |
| 熔断阈值 | 10 失败/5min | 1-100 / 1-60min | `lp.ai.llm.circuit-breaker.*` |
| 熔断恢复 | 30min | 1-1440min | 同上 |
| 缓存 TTL | 6h | 60s-24h | `lp.ai.insight.cache-ttl`（可扩展配置项）|

---
