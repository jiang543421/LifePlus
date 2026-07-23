## 14. 风险

| 类别 | 风险 | 影响 | 缓解 |
|---|---|---|---|
| 技术 | DeepSeek API 抖动 / 限流 429 | 中 | 3 层降级 L2 自动切模板；限流计数 5min/10fail/30min 熔断 |
| 技术 | Ollama 本地模式首字延迟 5-10s，用户体验差 | 中 | GET 命中走缓存不调 LLM；独立分析页 loading 状态 ≥ 3s 显示 |
| 技术 | LLM 输出 JSON 格式错乱（DeepSeek 偶尔出现"json\n{...}"前缀）| 中 | prompt 显式要求"只输出 JSON 对象" + 解析器容忍前后空白 |
| 技术 | LLM 输出敏感词（虽概率低）| 低 | 关键词黑名单扫描，命中走 L2 降级 + log.error |
| 技术 | Spring `RestClient` 5s 超时不够（DeepSeek 高峰）| 中 | 留 `lp.ai.llm.timeout-ms` 配置项，可调到 10s |
| 技术 | `RestClient` 与现有 `RestTemplate` / `WebClient` 冲突 | 低 | v2.0 已用 `RestTemplate` 做邮件发送；新建 `LlmRestClient` Bean 不冲突 |
| 资源 | 单开发者节奏，4 PR 串行 1-2 周 | 中 | 每个 PR 独立可合并 / 可回滚；切分支 worktree 隔离 |
| 资源 | 配额 50/天被 1 用户独享用尽 | 低 | 单用户项目无并发；超限后切模板仍可用 |
| 资源 | 独立分析页 C1 用户是否真用未验证 | 中 | 列入 v2.2 验收观察项；埋点 7 天内跳转率 < 5% 则下线 |
| 成本 | DeepSeek 计费规则变更（涨价）| 低 | 切 Ollama 模式兜底；配置文件改 base URL 可换通义千问 |
| 成本 | `LP_LLM_API_KEY` 误提交到 git | **高** | CLAUDE.md §7.1 红线 + gitleaks 钩子 + 启动期占位符校验 |
| 合规 | v2.0 spec §1 "不引入 LLM" 硬约束被打破 | 低 | 同步在 v2.0 spec 加 "v2.1 修订说明" 小节，引用 v2.1 spec |
| 兼容 | v2.0 PR #16 已合并的字段（Trend enum、URL 前缀）与 v2.1 兼容 | 低 | v2.1 完全沿用 v2.0 字段，新增字段用 `@JsonInclude(NON_NULL)` 兼容 |
| 兼容 | v2.0 缓存键 `ai:insight:{userId}` 命中 v2.1 反序列化失败（新字段缺失）| 中 | DTO 用 `@JsonInclude(NON_NULL)` 兼容；新字段给默认值 |
| 安全 | 提示词注入（用户通过修改本地配置注入恶意 prompt）| 低 | LLM 输出只用作展示文本，不拼回 prompt 二次输入；不调用函数 |
| 安全 | LLM 返回内容含 XSS（理论上不可能，但兜底）| 低 | Vue 默认转义；禁 `v-html`；CSP 沿用 v2.0 配置 |
| 运维 | Ollama 容器镜像 5GB+，CI 跑测试慢 | 中 | Testcontainers 集成测试用 WireMock mock LLM，不拉真 Ollama |
| 运维 | 4 chip 输入数据有缺失（用户新注册无数据）| 低 | LLM 走"empty"模板；4 chip 全 `value="—"`；走降级同 v2.0 |
| 决策 | v2.0 spec §18.2 修订表与实际代码不一致（30 vs 60/min）| 低 | v2.1 spec 以实际代码为准（30/min）|
| 决策 | 1 个 GitHub issue 跟踪 `v2.0 spec §18.2 偏差表` 与 `v2.1 spec` 不一致 | 低 | v2.1 实施时同步修订 v2.0 spec §18.2 |

---

## 15. 开放问题

| # | 问题 | 状态 | 决定时机 |
|---|---|---|---|
| Q1 | DeepSeek API 真的比通义千问便宜 / 质量更好吗？ | 待验证 | T1.3 启动时跑 10 次真实调用做对比 |
| Q2 | 独立分析页用户是否真用？ | 待观察 | v2.2 验收期（7 天内跳转率 < 5% 则下线）|
| Q3 | 50/天配额是否够？ | 待观察 | v2.2 调优期（统计 1 个月实际使用量）|
| Q4 | Ollama 模式 `deepseek-r1:8b` 是否需要换成 `qwen2.5:7b`（中文更好）？| 待验证 | T1.4 启动时跑中文测试集 |
| Q5 | 熔断阈值 5min/10fail 是否合理？ | 待观察 | v2.2 调优期（观察 1 个月实际抖动）|
| Q6 | `mood` 字段前端是否展示？ | **已决定：否** | §11 UX 草图未列；只在 `LlmInsightPayload` 内部流转，便于后续扩展 |
| Q7 | `llmMeta` 字段前端是否可见？ | **已决定：否** | §11 UX 草图未列；仅 ops 通过 `/actuator/metrics` + 日志看，节省带宽 |

---
