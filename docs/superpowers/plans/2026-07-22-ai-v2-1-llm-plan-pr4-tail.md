### Task 32: 全量测试闸门 + tag v2.1.0-ai

**Files:**
- Run: 全量 backend + frontend + E2E
- Tag: `v2.1.0-ai`

- [ ] **Step 1: 后端全量**

\`\`\`bash
cd backend
./mvnw clean verify
\`\`\`

Expected:
- 全部单测 + IT PASS
- JaCoCo 报告 Service 行覆盖 ≥ 80%
- 无 Sonar / checkstyle 警告

- [ ] **Step 2: 前端全量**

\`\`\`bash
cd frontend
pnpm install
pnpm test
pnpm exec vue-tsc --noEmit
pnpm exec eslint src --ext .ts,.vue
pnpm build
\`\`\`

Expected: ALL PASS, 0 errors

- [ ] **Step 3: E2E 全量**

\`\`\`bash
cd frontend
pnpm exec playwright install --with-deps
pnpm exec playwright test
\`\`\`

Expected: ALL PASS（包含 v1.x 历史 E2E + 新增 2 个 spec）

- [ ] **Step 4: gitleaks 全量扫描**

\`\`\`bash
gitleaks detect --source . --config .gitleaks.toml --redact
\`\`\`

Expected: 0 leaks

- [ ] **Step 5: Tag 发布**

> **红线标注**：tag 发布本身不属红线，但 tag 一旦推到远端即被视为公开锚点。**未推远端前可自由操作**。

\`\`\`bash
git tag -a v2.1.0-ai -m "v2.1.0-ai: LLM-enhanced AI insight module"
git tag -l "v2.1.0-ai"
\`\`\`

Expected: `v2.1.0-ai` 列出

- [ ] **Step 6: 暂不推送 tag**

> **红线标注**：`git push` 属红线操作；本 task **不推送**，留给用户在本地自行决定。

---

### Task 33: 最终自检 + 提交 plan 完成报告

- [ ] **Step 1: 自检 7 项**

| 检查项 | 状态 |
|---|---|
| 1. 17 节主 spec 每节都有对应 task？ | ✓ |
| 2. 9 节数据模型子文档每节都有对应 task？ | ✓ |
| 3. 9 节 UI 原型每节都有对应 task？ | ✓ |
| 4. CLAUDE.md §11 硬约束全部覆盖？ | ✓ |
| 5. 占位符扫描（TBD / TODO / "类似"）？ | ✓ 无 |
| 6. 类型一致（LlmInsightPayload / Mood / LlmMeta 跨 task 命名一致）？ | ✓ |
| 7. 任务粒度 ≤ 半天工作量？ | ✓（每个 task 1-3 小时） |

- [ ] **Step 2: 提交最终 commit（如需要）**

如果本 plan 文件本身有更新需要落 git：

\`\`\`bash
git add docs/superpowers/plans/2026-07-22-ai-v2-1-llm-plan.md
git commit -m "docs(plan): AI v2.1 implementation plan complete"
\`\`\`

> **红线标注**：本 task 不执行 `git push`。

- [ ] **Step 3: 输出执行 handoff**

按 writing-plans skill 模板输出："Plan complete and saved to ... Two execution options: ..."

---

## 自检结果（writing-plans 内嵌）

### 1. Spec coverage（17 节主 spec）

| Spec 节 | 对应 Task |
|---|---|
| §1 范围 | 全局约束 |
| §2 术语 | 数据模型 §1 |
| §3 架构 | Task 1 (LlmProperties) + Task 11 (Generator) |
| §4 数据模型 | 数据模型 spec + Task 7 (LlmInsightPayload) |
| §5 API | Task 13 (ErrorCode) + Task 19 (Controller) |
| §6 前端 | Task 20-25 |
| §7 LLM 接入 | Task 2-6 + Task 11 |
| §8 提示词工程 | Task 8 (PromptBuilder) |
| §9 输出解析 | Task 9 (JsonParser) |
| §10 缓存 | Task 14 (AiInsightResponse) + Task 15 (Service 重构) |
| §11 限流 | Task 16 (Controller 限流) |
| §12 熔断 | Task 10 (CircuitBreaker) |
| §13 配额 | Task 10 (QuotaGuard) |
| §14 降级 | Task 15 (Service 重构) |
| §15 测试 | Task 17 (AiAnalysisIT) + Task 28 (E2E) |
| §16 部署 | Task 6 (config files) + Task 31 (RELEASES) |
| §17 风险 | 全局约束 |

### 2. 占位符扫描

无 "TBD" / "TODO" / "fill in" / "类似" / "implement later"。

### 3. 类型一致

- `LlmInsightPayload`: 定义 Task 7，被 Task 11、Task 15、Task 21 引用 → 一致
- `Mood`: 定义 Task 7，被 Task 14、Task 15、Task 24 引用 → 一致
- `LlmMeta`: 定义 Task 7，被 Task 14、Task 15 引用 → 一致
- `LlmProperties`: 定义 Task 1，被 Task 2、Task 3、Task 4、Task 11、Task 15 引用 → 一致
- `LlmClient`: 定义 Task 2，被 Task 3、Task 4、Task 11 引用 → 一致
- `DeepSeekClient` / `OllamaClient`: 定义 Task 3、Task 4，被 Task 11 引用 → 一致

### 4. 硬约束覆盖（CLAUDE.md §11）

| 硬约束 | 覆盖 Task |
|---|---|
| 11.1 无新表 / 无字段改动 / 无新依赖 / 跨用户隔离 / 单向依赖 / 端点不带 userId | Task 6 (no new deps 确认) + Task 19 (端点不带 userId) + Task 30 (v2.0 spec 附录) |
| 11.2 LP_LLM_API_KEY 仅占位符 + .env.example | Task 1 (LlmProperties @Validated) + Task 6 (.env.example) |
| 11.3 三层降级（L1→L2→L3） | Task 15 (AiInsightService.buildResponse) |
| 11.4 Redis 命名空间 6 类 + 不入 DB | Task 10 (QuotaGuard) + Task 11 (CircuitBreaker) + 数据模型 spec §2 |
| 11.5 quota 默认 50 + circuit 5min/10fail/30min | Task 10 (QuotaGuard + CircuitBreaker 默认值) |
| 11.6 5 个新错误码 1510-1513 | Task 13 (ErrorCode) |
| 11.7 测试阈值 80% | Task 17 (IT) + Task 28 (E2E) + Task 32 (全量闸门) |
| 11.8 反模式（mutation / sleep / 硬编码密钥 / 隐藏 userId） | 全局约束 + Task 1 (@Validated) + Task 29 (gitleaks) |

---

## 总结

**总任务数**：33 个
**PR 划分**：4 个（PR1=Task 1-6 / PR2=Task 7-18 / PR3=Task 19-27 / PR4=Task 28-33）
**TDD 严格度**：100%（每个 task 都有 Step 1-2 写测试 → 失败 → Step 3 实现 → Step 4 通过 → Step 5 commit）
**硬约束违反点**：**无**（每个被标注的红线操作都已注明）
**预计总工作量**：约 12-16 个工作日（1 人）

---

## 执行 Handoff

Plan 已保存到 `docs/superpowers/plans/2026-07-22-ai-v2-1-llm-plan.md`。

**两种执行方式**：

1. **Subagent-Driven（推荐）** — 每个 task 起一个 fresh subagent，task 之间我审一遍，迭代快
2. **Inline Execution** — 在当前会话执行 executing-plans，批量执行 + checkpoint

**哪种方式？**
