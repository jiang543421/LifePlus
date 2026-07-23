## 13. 任务清单（v2.1 共 19 条，4 PR 串行）

### PR1：LLM 客户端脚手架

- **T1.1** `LlmClient` 接口 + `LlmRequest` / `LlmResponse` 不可变 record
- **T1.2** `LlmProperties`（`@ConfigurationProperties + @Validated`）+ 启动期 fail fast
- **T1.3** `DeepSeekClient`（OpenAI 兼容）+ 5s 超时 + `RestClient` interceptor
- **T1.4** `OllamaClient`（本地 HTTP）+ `format: "json"` 模式
- **T1.5** 两个客户端单测（各 ≥ 6 用例，`MockRestServiceServer`）
- **T1.6** `.env.example` + `application.yml` 模板 + `docker-compose.yml` ollama 可选服务
- **T1.7** `.gitleaks.toml` 新增 3 条规则

### PR2：LLM 业务编排

- **T2.1** `LlmPromptBuilder` 渲染 system + user prompt
- **T2.2** `LlmJsonParser` 解析 JSON + 字段缺失/超长/非法 mood 处理
- **T2.3** `LlmQuotaGuard`（Redis INCR+EXPIRE，fail-open）
- **T2.4** `LlmCircuitBreaker`（Redis ZSET 滑动窗口，Ollama 模式自动禁用，fail-closed）
- **T2.5** `LlmInsightGenerator` 编排 + 4 个异常类
- **T2.6** 改造 `AiInsightService` 加 L1→L2→L3 降级链 + `source` 字段
- **T2.7** `AiInsightResponse` 加 5 个新字段（可空）+ `LlmMeta`
- **T2.8** TTL 改 6h + POST refresh 限流改 3/min
- **T2.9** 4 个新错误码（1510/1511/1512/1513）+ `ErrorCode` 枚举
- **T2.10** `llm-prompt.properties` + `ai-templates.properties` 扩展
- **T2.11** 单测：Generator ≥ 11 用例，QuotaGuard ≥ 3，Breaker ≥ 6，JsonParser ≥ 5
- **T2.12** IT：Testcontainers ≥ 6 用例（含 Ollama 模式）

### PR3：独立分析页 + 前端

- **T3.1** `AiAnalysisController`（`GET /api/v1/ai/insight/analysis`，共享缓存）
- **T3.2** `AiAnalysisView.vue` 4 段内容 + 顶部栏 + source 标签
- **T3.3** `stores/aiAnalysis.ts` state + load + refresh
- **T3.4** `api/ai.ts` 加 `getAnalysis()` + `types/ai.ts` 加 `AiAnalysis` 类型
- **T3.5** 路由 `/ai-analysis` + 鉴权守卫
- **T3.6** 智能卡 source 标签 + 加载中状态
- **T3.7** 抽屉底部"查看完整分析 →"跳转
- **T3.8** 前端单测：≥ 15 用例（store + 关键组件 + 独立页）

### PR4：E2E + 文档 + 发布

- **T4.1** Playwright `ai-v2-1-flow.spec.ts` 5 个用例
- **T4.2** gitleaks + pre-commit 钩子接入
- **T4.3** 同步修订 v2.0 spec（移除"不引入 LLM"硬约束，加 v2.1 修订说明）
- **T4.4** `RELEASES/v2.1.0-ai.md`
- **T4.5** `README.md` §1 / §8 标记 v2.1
- **T4.6** `00-index.md` §3 交付节奏表更新（已提前做）
- **T4.7** `mvn verify` + `pnpm test` + `pnpm exec playwright test` 全绿
- **T4.8** 合并后打 tag `v2.1.0-ai`

---
