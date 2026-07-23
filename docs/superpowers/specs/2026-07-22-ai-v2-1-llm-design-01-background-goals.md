## 1. 背景

v2.0（已发布，tag `v2.0.0-ai`）实现了 AI 智能卡 + 详情抽屉，5 个 Provider 聚合 + 30min Redis 缓存 + 模板引擎降级（4 级 L0/L1/L2/L3）。但 v2.0 显式不做：
- LLM 调用（纯规则 + 模板）
- 独立分析页 `/ai-analysis`（抽屉里留了"v2.1 将推出..."承诺）
- 趋势迷你图 / 用户反馈 / chip 顺序可调

v2.1 目标：
1. **兑现 v2.0 承诺**：独立分析页 `/ai-analysis`
2. **突破 v2.0 限制**：引入 LLM（DeepSeek 主选 + Ollama 备选）生成更智能的 headline/advice/highlight
3. **保留 v2.0 基线**：模板引擎降级、缓存架构、Provider 聚合全部沿用

**约束（v2.0 红线）**：
- 1 人团队；显式不做团队/共享/图片识别/支付订阅/多语言
- 不引入新数据库表（PRD §5.3 红线）
- 不修改既有数据模型
- 引入 LLM 但通过 3 层降级保证 v2.0 模板引擎仍是 fallback

**约束（v2.1 新增）**：
- 不引入新第三方依赖（手写 Spring `RestClient` 调 OpenAI 兼容协议，0 Maven 依赖）
- LLM 调用月成本 ≤ 5 元（DeepSeek V3 估算）
- 每日 LLM 调用 ≤ 50/用户/天（配额硬上限）
- 启动期密钥缺失 / 占位符必失败（fail fast）

---

## 2. 目标与非目标

### 2.1 目标（v2.1 必须交付）

| # | 目标 | 衡量 |
|---|---|---|
| G1 | 智能卡 headline 升级为 LLM 生成 | headline 含"判断"（紧凑/略松/正常/异常）+ 数字；AC 见 PRD §4 US-1 |
| G2 | 抽屉 → 独立分析页 `/ai-analysis` 跳转 | 抽屉底部"v2.1 将推出..."改"查看完整分析 →"；AC 见 PRD §4 US-2 |
| G3 | 独立分析页 4 段内容（headline / advice / highlight / chips）| 单次 LLM 调用，JSON schema `{headline, advice, highlight, mood}` |
| G4 | 3 层降级链路（L1 LLM → L2 模板 → L3 1501）| L1 失败必走 L2；L2 失败必抛 1501；用户无感 |
| G5 | DeepSeek + Ollama 双 provider 切换 | `lp.ai.llm.provider` 配置切换；功能不变；AC 见 PRD §4 US-3 |
| G6 | 全套 6 项成本控制 | 缓存 6h / 限流 30+3 per min / 配额 50 per day / 熔断 5min-10fail-30min / token 1500+300 / 4 个新错误码 |
| G7 | 端到端鉴权 + 跨用户隔离 | 缓存键按 userId 分；4 Provider 全部按 userId 过滤 |
| G8 | 测试覆盖达标 | Service ≥ 85%；LlmInsightGenerator ≥ 90%；E2E ≥ 5 用例 |
| G9 | 安全：API Key 环境变量管理 | `LP_LLM_API_KEY` 走 `${...}` 占位符；启动期 fail fast；gitleaks 钩子 |
| G10 | 文档与发布 | v2.1 spec + v2.0 spec 修订 + RELEASES + README + 00-index |

### 2.2 非目标（v2.1 明确不做）

- ❌ 趋势迷你图 / 折线图（需历史数据存储，v2.0 spec §5 "无新表" 拒绝；留 v2.2）
- ❌ 历史 insight 落 `t_ai_insight` 表 + 7 天回看（同上）
- ❌ 多 tab 切换（今日 / 本周 / 本月）—— 每次 tab 调 LLM 成本 ×3
- ❌ "反馈"按钮（赞同/不赞同）—— 需新表 + 异步队列
- ❌ chip 顺序用户可调（持久化到 t_user）—— 需新字段 + UI
- ❌ 连续 N 天无 insight 引导 —— 等 v2.1 用户反馈再决定
- ❌ dry-run 模式（LLM 输出但不入响应）—— 调试改 yml 即可
- ❌ 用户级 LLM 开关（`t_user.ai_llm_enabled` 字段 + SettingsView toggle）—— 1 人项目无价值
- ❌ 月度预算上限 —— DeepSeek 成本 < 5 元/月，不需要
- ❌ 多语言 i18n —— v2.0 不做，v2.1 也不做
- ❌ Spring AI 库 —— 手写 RestClient 200 行够用
- ❌ 提示词可视化调试工具
- ❌ LLM 输出 A/B 测试

---

## 3. 用户价值

**目标用户**：LifePulse 单人用户（开发者本人）；自托管极客（Ollama 模式）。

**使用场景**：
- 每天打开首页 → 第一眼看到 AI 卡 headline（含"判断"和"建议"） → 1 秒判断今天能否收尾
- 需要时点开抽屉看完整洞察 → 点"查看完整分析 →"跳独立分析页 → 4 段结构化内容
- 不想用 SaaS AI 时改 yml 切换 Ollama → 全本地运行，token 不出境

**核心价值**：**"AI 升级的当日状态总览"** —— 智能卡从"数字播报"升级为"判断 + 建议 + 亮点/关注点"，独立分析页兑现 v2.0 承诺。

---
