## 12. 测试策略

### 12.1 测试金字塔

| 层 | 工具 | 目标 | 计划用例数 |
|---|---|---|---|
| 单元 | JUnit 5 + Mockito | Provider/Engine/Service 行覆盖 ≥ 80% | 38 |
| Slice | `@WebMvcTest` | 鉴权/错误码 100% | 6 |
| 集成 | Testcontainers | 关键闭环 100% | 5 |
| 前端 | Vitest | Store/关键组件 100% | 12 |
| E2E | Playwright | 关键用户路径 100% | 4 |

### 12.2 关键测试用例

**`AiInsightServiceTest`**（核心）：
- `getOrCompute_cacheHit_returnsCachedWithoutInvokingProviders`
- `getOrCompute_singleProviderThrows_skipsAndContinues`
- `getOrCompute_allProvidersThrow_throws1501`
- `getOrCompute_redisDownOnRead_fallsBackToRecompute`
- `refresh_evictsCacheAndRecomputes`

**`DailyAiProviderTest`**（降级关键）：
- `isEnabled_whenConfigDisabled_returnsFalse`
- `isEnabled_whenConfigEnabled_returnsTrue`
- `collect_whenDisabled_throwsIllegalStateException`

**`AiInsightControllerWebTest`**：
- `getToday_noAuth_returns401`
- `getToday_validToken_returns200WithPayload`
- `getToday_serviceThrows1501_returns503WithCode1501`

**`AiAnalysisIT`**（Testcontainers）：
- `endToEnd_firstCallMiss_secondCallHit`
- `endToEnd_realData_producesNonEmptyHeadline`
- `endToEnd_dailyDisabled_doesNotQueryDailyTables`
- `endToEnd_crossUserIsolation_userACannotSeeUserBInsight`

**E2E `ai-insight.spec.ts`**：
- 登录后看到 AI 卡与 headline + 3 chip
- 点击 refresh 时间戳更新
- 新用户看到空状态
- 未登录跳登录页

### 12.3 覆盖率门禁

| 层 | 阈值 | 失败后果 |
|---|---|---|
| Service | 行 ≥ 85% | `mvn verify` 失败 |
| Controller 鉴权/错误码 | 路径 100% | 同上 |
| Provider 各实现 | 行 ≥ 80% | 同上 |
| AiTemplateEngine | 行 ≥ 90% | 同上 |
| 前端 store 关键 action | 100% | `pnpm test` 失败 |
| E2E 4 个用例 | 全绿 | `pnpm exec playwright test` 失败 |

---
