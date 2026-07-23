### Task 3.3: 验证端到端：PR 1 全量回归

- [ ] **Step 1: 全量编译 + 单测**

```powershell
cd backend; mvn -q test
```

预期：所有测试通过；JaCoCo 报告显示 `ai` 包行覆盖 ≥ 80%。

- [ ] **Step 2: Commit PR（合并到 feat/ai-v2.0 触发 CI）**

PR 标题：`feat(ai): backend foundation — ai package, models, template engine`

PR 描述模板：
```
## 改了什么
- 新增 com.lifepulse.ai 包（5 子包）
- AiConstants / AiInsightProperties 配置
- MetricValue record + Trend 枚举
- AiInsightPayload 内部对象
- AiChipDto / AiInsightResponse 响应 DTO
- ai-templates.properties（11+ 键值）
- AiTemplateEngine（format + fallback）

## 为什么
为 v2.0 AI 智能卡建立后端基础（spec §13 T1-T3）。

## 测试覆盖
- AiTemplateEngineTest: 8 用例全绿
- MetricValueTest: 4 用例全绿
- 行覆盖：ai 包 ≥ 80%

## 影响面
- 无新表 / 无新 migration
- 无新依赖
- 仅新增文件 + 1 个 application.yml 段 + 1 个 @EnableConfigurationProperties 注解
```

合并到 `feat/ai-v2.0` 后继续 PR 2。

---
