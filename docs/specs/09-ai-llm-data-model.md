# AI 分析模块 v2.1 数据模型设计

> 版本：v0.1 · 日期：2026-07-22 · 模块：`ai`（v2.1 LLM 增强）
> 输入：[2026-07-22-ai-v2-1-llm-design.md §4-§7, §10](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md) · [07-ai-llm-prd.md §5](../prd/07-ai-llm-prd.md)
> 索引：[2026-07-22-ai-v2-1-llm-design.md](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md) · [08-ai-llm.md](../ui-prototypes/08-ai-llm.md) · [ai-tech-architecture.md §3](../architecture/ai-tech-architecture.md)

---

## 0. 设计原则

| # | 原则 | 含义 |
|---|---|---|
| P1 | **零新增表** | 沿用 v2.0 + MVP2 5 张业务表（`t_user` / `t_refresh_token` / `t_task` / `t_plan` / `t_expense` / `t_diet` / `t_daily_report`），Flyway 零迁移 |
| P2 | **不修改既有字段** | 5 张业务表字段语义不变；AI 模块只读，不写 |
| P3 | **Redis 命名空间扩展** | 沿用 `lp:*` 前缀，新增 `lp:ai:quota:*` / `lp:ai:circuit:*` 两类键 |
| P4 | **DTO 向后兼容** | `AiInsightResponse` 新增字段用 `@JsonInclude(NON_NULL)` 序列化，v2.0 旧缓存条目可被 v2.1 代码反序列化（缺失字段取 `null`）|
| P5 | **不可变优先** | 所有 record + `@Builder` 替代 setter；集合返回 `List.copyOf` |
| P6 | **配置全部走 `@ConfigurationProperties`** | `LlmProperties` 启动期 fail fast（密钥缺失 / 占位符 → `IllegalStateException`）|

---

---

## 瀛愭枃浠剁储寮昤n
| 瀛愭枃浠?| 绔犺妭鏍囩 | 琛屽彿 | 璇存槑 |
|---|---|---|---|
| `09-ai-llm-data-model-01-java-core.md` | 01-java-core.md | 22..195 (174 琛? | §1.1-1.6 Java 核心类：overview + LlmProperties + records (Request/Response/Payload/Meta) |
| `09-ai-llm-data-model-02-java-orchestration.md` | 02-java-orchestration.md | 196..348 (153 琛? | §1.7-1.12 Java 编排：Client interface + Generator + PromptBuilder + CircuitBreaker + QuotaGuard + Exceptions + AiInsightResponse |
| `09-ai-llm-data-model-03-redis-keys.md` | 03-redis-keys.md | 349..526 (178 琛? | §2 Redis 键空间（5 类键 + 限流对照） |
| `09-ai-llm-data-model-04-no-new-table.md` | 04-no-new-table.md | 527..575 (49 琛? | §3 无新表声明（hard rule） |
| `09-ai-llm-data-model-05-field-boundaries.md` | 05-field-boundaries.md | 576..609 (34 琛? | §4 字段长度与边界总表 |
| `09-ai-llm-data-model-06-types-failure.md` | 06-types-failure.md | 610..673 (64 琛? | §5 类型与序列化 + §6 失败降级 + §7 不存内容 |
| `09-ai-llm-data-model-07-references.md` | 07-references.md | 674..686 (13 琛? | §8 引用 |

> 鍏?7 涓瓙鏂囦欢 + 鏈?INDEX锛?preambleEnd 琛?preamble锛?= 婧愭枃浠?686 琛?1:1 瀹屾暣瑕嗙洊銆俙n
## 鎷嗗垎瑙勫垯锛圕LAUDE.md 搂3 + 鐢ㄦ埛纭害鏉燂級

- 姣忓瓙鏂囦欢 <=300 琛宍n- 鎸夊師绔犺妭椤哄簭锛屽崟鏂囦欢鍐呬繚鎸佸師椤哄簭
- INDEX 淇濈暀鍘?preamble + 瀛愭枃浠?TOC
- 涓嶄慨鏀逛换浣曟鏂囷紱鍙寜琛屽垏鐗嘸n
