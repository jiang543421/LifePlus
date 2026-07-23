# LifePulse AI 分析模块 v2.1 — 设计规格

- **Status:** Ready for User Review（spec 自检已通过）
- **Date:** 2026-07-22
- **Author:** 资深 PM 视角
- **Scope:** v2.1（LLM 增强 + 独立分析页；基于 v2.0 增量）
- **Branch:** `feat/ai-llm-v2.1`
- **Source of truth:** 本文件 + PRD `docs/prd/07-ai-llm-prd.md` + v2.0 spec `docs/superpowers/specs/2026-07-21-ai-v2-design.md`（基线）
- **替代:** v2.0 spec §1 硬约束"不引入 LLM"（同步在 v2.0 spec §19 加修订说明）

---

---

## 瀛愭枃浠剁储寮昤n
| 瀛愭枃浠?| 绔犺妭鏍囩 | 琛屽彿 | 璇存槑 |
|---|---|---|---|
| `2026-07-22-ai-v2-1-llm-design-01-background-goals.md` | 01-background-goals.md | 13..86 (74 琛? | §1 背景 + §2 目标/非目标 + §3 用户价值 |
| `2026-07-22-ai-v2-1-llm-design-02-arch-layers.md` | 02-arch-layers.md | 87..185 (99 琛? | §4 系统分层架构（含 v2.1 LLM 接入层） |
| `2026-07-22-ai-v2-1-llm-design-03-module-boundary.md` | 03-module-boundary.md | 186..322 (137 琛? | §5 模块职责边界（Generator vs Service） |
| `2026-07-22-ai-v2-1-llm-design-04-storage-redis.md` | 04-storage-redis.md | 323..531 (209 琛? | §6 数据存储（Redis 键空间 + TTL） |
| `2026-07-22-ai-v2-1-llm-design-05-api-spec.md` | 05-api-spec.md | 532..577 (46 琛? | §7 API 规格 |
| `2026-07-22-ai-v2-1-llm-design-06-fallback-chain.md` | 06-fallback-chain.md | 578..717 (140 琛? | §8 3 层降级链路 L1 LLM -> L2 模板 -> L3 1501 |
| `2026-07-22-ai-v2-1-llm-design-07-prompt-templates.md` | 07-prompt-templates.md | 718..783 (66 琛? | §9 模板与 Prompt 规范 |
| `2026-07-22-ai-v2-1-llm-design-08-security-arch.md` | 08-security-arch.md | 784..1044 (261 琛? | §10 安全架构（API Key + gitleaks + 鉴权） |
| `2026-07-22-ai-v2-1-llm-design-09-frontend-ux.md` | 09-frontend-ux.md | 1045..1136 (92 琛? | §11 前端 UX |
| `2026-07-22-ai-v2-1-llm-design-10-testing.md` | 10-testing.md | 1137..1241 (105 琛? | §12 测试策略 |
| `2026-07-22-ai-v2-1-llm-design-11-task-list.md` | 11-task-list.md | 1242..1292 (51 琛? | §13 任务清单（4 PR 串行） |
| `2026-07-22-ai-v2-1-llm-design-12-risks-open.md` | 12-risks-open.md | 1293..1333 (41 琛? | §14 风险 + §15 开放问题 |
| `2026-07-22-ai-v2-1-llm-design-13-references-next.md` | 13-references-next.md | 1334..1358 (25 琛? | §16 参考文献 + §17 后续步骤 |

> 鍏?13 涓瓙鏂囦欢 + 鏈?INDEX锛?preambleEnd 琛?preamble锛?= 婧愭枃浠?1358 琛?1:1 瀹屾暣瑕嗙洊銆俙n
## 鎷嗗垎瑙勫垯锛圕LAUDE.md 搂3 + 鐢ㄦ埛纭害鏉燂級

- 姣忓瓙鏂囦欢 <=300 琛宍n- 鎸夊師绔犺妭椤哄簭锛屽崟鏂囦欢鍐呬繚鎸佸師椤哄簭
- INDEX 淇濈暀鍘?preamble + 瀛愭枃浠?TOC
- 涓嶄慨鏀逛换浣曟鏂囷紱鍙寜琛屽垏鐗嘸n
