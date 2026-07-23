# UI 原型 · 08 — AI 智能卡 + 抽屉（v2.1）+ 独立分析页

> 版本：v0.1 · 日期：2026-07-22 · 视图：HomeView 第 1 卡 + 抽屉 + 独立分析页
> 输入：[2026-07-22-ai-v2-1-llm-design.md §11](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md) · [07-ai-llm-prd.md §4](../prd/07-ai-llm-prd.md) · [v2.0 原型 07-ai.md](./07-ai.md)（基线）
> 索引：[2026-07-22-ai-v2-1-llm-design.md §11](../superpowers/specs/2026-07-22-ai-v2-1-llm-design.md) · [09-ai-llm-data-model.md §1.12](../specs/09-ai-llm-data-model.md)

---

---

## 瀛愭枃浠剁储寮昤n
| 瀛愭枃浠?| 绔犺妭鏍囩 | 琛屽彿 | 璇存槑 |
|---|---|---|---|
| `08-ai-llm-01-scope-mapping.md` | 01-scope-mapping.md | 9..36 (28 琛? | §0 范围与基线 + §1 故事 → UI 要素映射 |
| `08-ai-llm-02-desktop-ascii.md` | 02-desktop-ascii.md | 37..282 (246 琛? | §2 桌面 ASCII（智能卡 + 抽屉 + 独立分析页 + 移动端） |
| `08-ai-llm-03-state-variants.md` | 03-state-variants.md | 283..404 (122 琛? | §3 空/加载/降级/错误状态 |
| `08-ai-llm-04-interaction-flows.md` | 04-interaction-flows.md | 405..532 (128 琛? | §4 交互流程（状态机 + 时序 + API 矩阵） |
| `08-ai-llm-05-routing-guards.md` | 05-routing-guards.md | 533..603 (71 琛? | §5 路由 + 鉴权守卫 + Pinia Store |
| `08-ai-llm-06-design-a11y-relations.md` | 06-design-a11y-relations.md | 604..652 (49 琛? | §6 设计令牌 + §7 可访问性 + §8 与 v2.0 关系 |
| `08-ai-llm-07-references.md` | 07-references.md | 653..663 (11 琛? | §9 引用 |

> 鍏?7 涓瓙鏂囦欢 + 鏈?INDEX锛?preambleEnd 琛?preamble锛?= 婧愭枃浠?663 琛?1:1 瀹屾暣瑕嗙洊銆俙n
## 鎷嗗垎瑙勫垯锛圕LAUDE.md 搂3 + 鐢ㄦ埛纭害鏉燂級

- 姣忓瓙鏂囦欢 <=300 琛宍n- 鎸夊師绔犺妭椤哄簭锛屽崟鏂囦欢鍐呬繚鎸佸師椤哄簭
- INDEX 淇濈暀鍘?preamble + 瀛愭枃浠?TOC
- 涓嶄慨鏀逛换浣曟鏂囷紱鍙寜琛屽垏鐗嘸n
