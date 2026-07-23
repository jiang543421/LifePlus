## 合并与发布

### 步骤 1: 全量自检

```powershell
# 后端
cd backend; mvn clean verify

# 前端
cd frontend; pnpm install; pnpm test; pnpm exec tsc --noEmit; pnpm exec playwright test
```

### 步骤 2: 打 tag

```bash
git tag -a v2.0.0-ai -m "AI Analysis Module v2.0 — smart card + drawer, 5 providers, no LLM"
git log --oneline feat/ai-v2.0 ^main
```

> tag 不自动 push，等用户确认后由用户手动 push。

### 步骤 3: 合并到 main

```bash
git checkout main
git merge --squash feat/ai-v2.0
git commit -m "feat(ai): AI analysis module v2.0 (#<PR-number>)"
```

---

## Plan 完成总结

| PR | 标题 | 关键产出 |
|---|---|---|
| 1 | Backend Foundation | AiConstants/Properties/MetricValue/Payload/DTO/TemplateEngine |
| 2 | 5 Providers | Task/Plan/Expense/Diet/DailyAiProvider |
| 3 | Service 编排 | AiInsightService（3 层降级 + Redis 30min 缓存） |
| 4 | Controller + 限流 | 2 端点 + 限流注解 + WebMvcTest |
| 5 | Integration Tests | Testcontainers 5 用例 |
| 6 | Frontend Foundation | types/api/store/AiChipItem/AiInsightCard |
| 7 | Drawer + HomeView | AiInsightDrawer + HomeView 集成 |
| 8 | E2E | Playwright 5 用例 |
| 9 | Docs | 设计索引 + README + release notes |

**测试覆盖目标**：
- 后端 service ≥ 85%、controller 100%
- 前端 store 100%、关键组件 100%
- E2E：5 关键流程 100%

**风险与回滚**：见 [设计文档 §14](2026-07-21-ai-v2-design.md#14-风险与回滚)。

---
