# AI v2.2 trend — 趋势图设计（spec §v2.2 trend / CLAUDE.md §11.1）

> **状态**：✅ 已落地（branch `feat/ai-trend-chart`）
> **基线**：`v2.1.0-ai`（`4d81aa5`）
> **范围**：4 槽 metrics 趋势图（task / plan / expense / diet），独立分析页 `/ai-analysis` 追加「趋势」段

---

## 1. 目标

在 v2.1 独立分析页已落地的"今日快照"基础上，扩展为"近期趋势"。让用户能看到 7 / 14 / 30 天内的任务完成率、日程事件数、消费金额变化（diet 槽位永久占位，CLAUDE.md §1 NOT-DO）。

## 2. 硬约束（继承 CLAUDE.md §11.1）

| # | 约束 | 本设计落实 |
|---|---|---|
| 1 | 无新表 | ✅ 只读 t_task / t_plan / t_expense / t_daily_report |
| 2 | 不修改既有字段 | ✅ 5 张业务表字段语义零变 |
| 3 | 不引入新第三方依赖 | ✅ 后端手写聚合；前端手写 inline SVG，0 新依赖 |
| 4 | 跨用户隔离 | ✅ 缓存键 / 限流键全部按 userId；端点不接受 userId |
| 5 | 依赖方向单向 | ✅ Web → Service → DailyReportService（已有，无新增层级） |
| 6 | 端点不接受 userId | ✅ `GET /api/v1/ai/insight/trend`，userId 由 JWT 解析 |

## 3. 端点设计

`GET /api/v1/ai/insight/trend?window=7|14|30`

| 字段 | 取值 |
|---|---|
| window 默认 | 14 |
| window 校验 | 必须 ∈ {7, 14, 30}，否则 1001 VALIDATION |
| 鉴权 | JWT 解析 userId |
| 限流 | `lp:rl:ai:trend:<userId>` 30/min/user（CLAUDE.md §11.5）；超限 1006 |
| 缓存 | `lp:ai:trend:<userId>:<windowDays>` TTL 6h（与 insight 同档） |
| 失败降级 | Redis 不可达 fail-open（直接走计算，不阻塞用户） |

## 4. 响应结构（与后端 record 一致）

```json
{
  "window": 14,
  "from": "2026-07-10",
  "to": "2026-07-23",
  "metrics": ["task", "plan", "expense", "diet"],
  "series": {
    "task":    { "key": "task",    "label": "任务完成率",    "unit": "%",  "points": [...] },
    "plan":    { "key": "plan",    "label": "日程事件",      "unit": "项", "points": [...] },
    "expense": { "key": "expense", "label": "消费金额",      "unit": "¥",  "points": [...] },
    "diet":    { "key": "diet",    "label": "饮食（永久占位）", "unit": "",   "points": [] }
  },
  "generatedAt": "2026-07-23T..."
}
```

每个 point：
```json
{ "date": "2026-07-23", "value": 0.85, "label": "85%" }
```

## 5. 性能预算

| 窗口 | mapper 调用次数 | 预期 P95 |
|---|---|---|
| 7 天 | 28 | < 250ms |
| 14 天（默认）| 56 | < 500ms |
| 30 天 | 120 | < 800ms |

无独立 IT 测试（沿用 `DailyReportServiceIT` P95 ≤ 200ms/日 基线）。

## 6. 前端组件分层

```
AiAnalysisView
  └─ TrendPanel                    # 2×2 网格 + ElRadioButton 7/14/30 + URL ?window= 同步
       ├─ SparklineChart × 4       # 单一折线组件，inline SVG，0 新依赖
       │   └─ TriStateEmpty        # 复用 v1.2.6 组件（diet 槽位触发）
       └─ TriStateError            # 复用 v1.2.6 组件（fetch 失败触发）
```

## 7. 颜色规范

| 指标 | 颜色 | 取值 |
|---|---|---|
| task | 蓝 | `#4A90D9` |
| plan | 绿 | `#52C41A` |
| expense | 橙 | `#FA8C16` |
| diet | 灰 | `#D9D9D9` |

## 8. 暂缓项（功能稳定后补）

- C4：后端 Testcontainers IT
- C6 / C8 / C10：单测补丁（SparklineChart / aiTrend store / TrendPanel 单测补全）
- E2E：Playwright `trend-chart.spec.ts`（7 天 / 14 天 / 30 天切换 + URL 同步）

## 9. 引用

- 后端：`AiTrendService` / `AiTrendController` / `AiConstants.TREND_*`
- 前端：`components/ai/SparklineChart.vue` / `components/ai/TrendPanel.vue` / `api/ai-trend.ts` / `stores/aiTrend.ts`
- spec：`2026-07-22-ai-v2-1-llm-design.md §11` (CLAUDE.md §11 引用)
- CLAUDE.md §11.1（AI 模块硬约束）
- CLAUDE.md §11.4（Redis 命名空间）
- CLAUDE.md §11.5（配额与熔断）
- v1.2.6 #4.1（TriStateEmpty / TriStateError 复用）