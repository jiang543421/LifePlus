## 12. 测试策略

### 12.1 测试金字塔

| 层 | 工具 | 目标 | 计划用例数 |
|---|---|---|---|
| 单元 | JUnit 5 + Mockito | LLM 客户端/解析/熔断/配额/Generator 行覆盖 ≥ 80% | 35 |
| Slice | `@WebMvcTest` | 鉴权/错误码 100% | 4 |
| 集成 | Testcontainers | 关键闭环 100% | 5 |
| 前端 | Vitest | Store/关键组件 100% | 15 |
| E2E | Playwright | 关键用户路径 100% | 5 |

### 12.2 关键测试用例

**`LlmInsightGeneratorTest`**（核心）：
- `generate_quotaExceeded_throws1510`
- `generate_circuitOpen_throws1511`
- `generate_clientSuccess_parserSuccess_returnsPayload`
- `generate_clientTimeout_throws1513`
- `generate_client5xx_throws1513`
- `generate_client429_throws1513`
- `generate_parserInvalidJson_throws1512`
- `generate_parserMissingHeadline_throws1512`
- `generate_parserHeadlineTooShort_throws1512`
- `generate_parserInvalidMood_clampsToNeutral`
- `generate_circuitBreakerDisabled_skipsCheck`（Ollama 模式）

**`DeepSeekClientTest`**：
- `generate_validRequest_returnsResponse`
- `generate_http500_throwsLlmUnavailable`
- `generate_http429_throwsLlmUnavailable`
- `generate_http400_throwsLlmResponseInvalid`
- `generate_timeout_throwsLlmUnavailable`
- `generate_emptyBody_throwsLlmResponseInvalid`

**`OllamaClientTest`**：
- `generate_validRequest_returnsResponse`
- `generate_connectionRefused_throwsLlmUnavailable`
- `generate_invalidJsonResponse_throwsLlmResponseInvalid`
- `generate_timeout_throwsLlmUnavailable`

**`LlmJsonParserTest`**：
- `parse_validJson_returnsPayload`
- `parse_missingHeadline_throwsInvalid`
- `parse_invalidMood_clampsToNeutral`
- `parse_extraFields_ignored`
- `parse_emptyJson_throwsInvalid`

**`LlmCircuitBreakerTest`**：
- `tryAcquire_stateClosed_returnsNormally`
- `tryAcquire_stateOpen_recentlyOpened_throwsCircuitOpen`
- `tryAcquire_stateOpen_cooldownExpired_transitionsToHalfOpen`
- `recordFailure_10FailuresIn5Min_opensCircuit`
- `tryAcquire_redisUnavailable_failClosed`
- `tryAcquire_ollamaMode_skipsCheck`

**`LlmQuotaGuardTest`**：
- `checkAndIncrement_firstCall_incrementsAndExpires`
- `checkAndIncrement_overLimit_throwsQuotaExceeded`
- `checkAndIncrement_redisUnavailable_failOpen`

**`AiInsightServiceTest`**（v2.0 基础上扩展）：
- `getInsight_cacheHit_returnsCachedWithSource`
- `getInsight_llmSuccess_returnsWithSourceLlm`
- `getInsight_llmFails_usesTemplateWithSourceTemplate`
- `getInsight_templateFails_throws1501`
- `getInsight_allProvidersFail_skipsLlm_usesEmptyTemplate`
- `getInsight_llmDisabled_skipsLlm_usesTemplate`

**`AiInsightControllerWebTest`**（v2.0 基础上扩展）：
- `getToday_noAuth_returns401`
- `getToday_validToken_returns200WithSource`
- `getAnalysis_validToken_returns200WithFullPayload`
- `getToday_serviceThrows1501_returns503`

**`AiAnalysisIT`**（Testcontainers）：
- `endToEnd_realData_llmEnabled_sourceIsLlm`
- `endToEnd_redisDown_fallsBackToRecompute`
- `endToEnd_quotaExceeded_usesTemplateSource`
- `endToEnd_circuitOpen_usesTemplateSource`
- `endToEnd_crossUserIsolation_userACannotSeeUserBInsight`
- `endToEnd_ollamaMode_usesLocalLlm`

**E2E `ai-v2-1-flow.spec.ts`**：
- 登录后看到 AI 卡与 headline（带 source="AI 生成" 标签）
- 抽屉底部显示"查看完整分析 →"链接
- 点击跳转 `/ai-analysis` 看到 4 段内容
- 独立分析页 refresh 按钮触发 source 变化
- 配置切换到 Ollama 后端到端仍可用

### 12.3 覆盖率门禁

| 层 | 阈值 | 失败后果 |
|---|---|---|
| Service | 行 ≥ 85% | `mvn verify` 失败 |
| Controller 鉴权/错误码 | 路径 100% | 同上 |
| `LlmInsightGenerator` | 行 ≥ 90% | 同上 |
| `LlmClient` 各实现 | 行 ≥ 80% | 同上 |
| `LlmCircuitBreaker` | 行 ≥ 85% | 同上 |
| `LlmQuotaGuard` | 行 ≥ 85% | 同上 |
| 前端 store 关键 action | 100% | `pnpm test` 失败 |
| E2E 5 个用例 | 全绿 | `pnpm exec playwright test` 失败 |

---
