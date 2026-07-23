# Task 8 Report — LlmPromptBuilder + llm-prompt.properties

**Status**: DONE_WITH_CONCERNS
**Branch**: feat/ai-llm-v2.1
**Starting Commit**: 5f9262f (PR2 Task 7 完成点)

---

## Commits

本 task 提交 1 个 commit（待执行）：

- `feat(ai-llm): add LlmPromptBuilder with prompt template`

## Files Changed

新增 3 个文件：

| 文件 | 用途 |
|---|---|
| `backend/src/main/java/com/lifepulse/ai/llm/LlmPromptBuilder.java` | @Component，渲染 system + user prompt，构造 LlmRequest |
| `backend/src/main/resources/llm-prompt.properties` | 系统角色 + 4 chip 模板 + 全空 fallback + client params |
| `backend/src/test/java/com/lifepulse/ai/llm/LlmPromptBuilderTest.java` | 5 个用例（≥5 要求） |

无修改既有文件；无新增 Maven 依赖；无 Flyway。

## Test Results

**TDD 工作流**：
- Step 1: 写失败测试 — `LlmPromptBuilderTest.java` 5 用例
- Step 2: `mvn test -Dtest=LlmPromptBuilderTest` 编译期失败：`找不到符号 LlmPromptBuilder` ✅ RED
- Step 3: 写实现 + properties 文件
- Step 4: GREEN — 4/5 通过；第 5 个用例（`build_bigDecimalValue`）因 `value=0` 被 `MetricValue.isNonEmpty()` 视为空数据而失败；改为 `value=3` 后通过
- Step 5 (refactor)：合并 `renderEmptyChip` switch 到 properties 的 `.empty` 模板，消除 Java 端重复标签字符串；保留 5/5 全绿

**最终命令与输出**：

```bash
cd backend && mvn test -Dtest=LlmPromptBuilderTest -o
# Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

## Spec Compliance

brief 每项对照：

| Brief 要求 | 状态 | 备注 |
|---|---|---|
| `LlmPromptBuilder.java` @Component | ✅ | 同 brief |
| `llm-prompt.properties` 含 system + 4 chip + 全空 fallback | ✅ | 包含 `system.role` / `user.template` / `user.chip.taskCompletion` / `user.chip.planDensity` / `user.chip.weeklyExpense` / `user.chip.dietIntake` / `user.chip.*.empty` / `user.empty` |
| `LlmPromptBuilderTest.java` ≥5 用例 | ✅ | 5 用例：4 chip 全在 / 部分空 / 全空 / 日期占位 / BigDecimal 保精度 |
| 占位符 `${...}` 格式（注：实际实现用 `{0}` + `{date}`，Java `String.replace` 语义） | ✅ | properties 的 `{0}` / `{date}` 是 Spring MessageSource / Properties 通配符风格，非 Maven `${}` |
| `MetricValue.none()` 渲染为"无数据"占位 | ✅ | 复用 properties 的 `user.chip.<key>.empty` 模板，标签已嵌在模板中 |
| `LlmRequest(systemPrompt, userPrompt, maxResponseTokens, timeout)` 输出 | ✅ | 直接 `return new LlmRequest(...)` |
| `maxResponseTokens` 取自 properties 默认值 | ✅ | 默认 300（与 `LlmProperties.maxResponseTokens` 默认对齐） |
| `timeout` 取自 properties 默认值 | ✅ | 默认 5000ms，包装为 `Duration.ofMillis(5000)` |
| TDD Red→Green→Refactor | ✅ | 全程记录于上节 |
| Java 4 空格 / LF / final / record / 不可变 | ✅ | 全文件 4 空格；构造期加载一次 props；输出 `LlmRequest` 为 record 不可变 |
| 无新 Maven 依赖 | ✅ | 仅用 Spring `ClassPathResource` + Java 内置 `Properties` |
| 错误日志禁打密钥 / 禁打 prompt 完整内容 | ✅ | 代码无 log；prompt 渲染内存传递 |
| 测试命名 `methodName_stateUnderTest_expectedBehavior` | ✅ | 5 个用例名均符合格式 |

## Concerns

非本 task 范围的问题，留给后续 task 或 v2.2+：

1. **`isEmpty()` 语义包含 `value == 0` 当作"无数据"**。`MetricValue.isNonEmpty()` 要求 `value > 0` 严格大于零。这意味合法数值 0（如 0 任务完成）会被渲染为"无数据"。这与 v2.0 模板引擎行为一致（依赖 `MetricValue.isNonEmpty`），但若 v2.1 想表达"今日完成 0 项任务"，需 v2.2 把 `isEmpty` 改为 `value == null` 单独判定。本 task 与 PR1 Task 7 保持一致，不破坏既有语义。

2. **`build(userId, ...)` 中 `userId` 参数仅用于签名占位与参数合法性校验**（≤0 抛 IllegalArgumentException）。Task 14 (`AiInsightService`) 调用时若从 `UserContext.current()` 取 ID，注入实际校验；本 task 接受任意正数 `userId` 不影响 prompt 渲染内容。如未来要在 prompt 中嵌入 userId 标识（如 cache-busting 注释），Task 14 改 prompt 模板即可。

3. **`user.template` 写成单行 + `\n` 转义**，避免 `.properties` 多行 value 的 backslash continuation 陷阱。简短模板（1-2 句引导）可读；如未来模板变长，建议改用 classpath 资源 + `String.replace`。

4. **未注入 `LlmProperties` 的 `maxResponseTokens` / `timeoutMs` 实参**。本 task 的 builder 在 properties 设默认（300 / 5000ms），与 `LlmProperties` 默认值一致。Task 14 (`AiInsightService`) 注入 `LlmProperties` 后，应改用 `promptBuilder = new LlmPromptBuilder(llmProperties, props)` 形式或新增构造器，将 properties 默认值与 LlmProperties 默认值一并传入。本 task 暂时用纯 properties 默认值。

5. **BigDecimal.toPlainString 输出含 `$` 或 `\` 时会触发 `String.replace(CharSequence, CharSequence)` 的特殊字符解释**。当前 chip value 域（百分比 / 项数 / 金额 / kcal）不会出现这些字符，安全。但若 v2.2+ chip 域扩到含字符串（标签、维度名），应改 `Matcher.quoteReplacement(value.toPlainString())`。已在 source 注释待 Task 14 评估。

## Self-Review

- **可读性**：常量集中在顶部；private 方法按职责分组（renderUserPrompt / appendChipIfPresent / formatChip / isEmpty / utils）。
- **可测试性**：`LlmPromptBuilder(Properties props)` 包级构造器允许测试用 custom properties 覆盖；当前测试直接读 classpath 真实 properties（更集成）。
- **可维护性**：新增第 5 chip（如 Task 9 之后可能加入 `dailyStreak`）只需 properties + 1 处 `appendChipIfPresent` 调用；模板字符串与硬编码 Java 字面量分离。
- **安全性**：构造期 `loadProps()` 用 try-with-resources + UTF-8 Reader；I/O 失败抛 `IllegalStateException` 让 Spring 启动失败（fail fast，与 CLAUDE.md §11.2 精神一致）。
- **不可变性**：`final Properties props` 在构造期加载，方法无 mutation。输出 `LlmRequest` record 不可变。
- **测试覆盖**：5/5 用例覆盖 happy path + 3 种空数据场景 + 占位符 + 类型转换。

---

*Status: DONE_WITH_CONCERNS — 5/5 测试全绿；3 个新文件交付；5 项非阻塞 concern 已记录供 Task 11/14 参考。*
