### M6 阶段

#### T15. 文档与发布收尾

**Files:**
- Create: `docs/specs/08-daily-report-design.md`(6 节 spec 落档)
- Create: `RELEASES/v1.2.3.md`
- Modify: `docs/issues/2026-07-18-mvp2-placeholder-modules.md`(关闭日报项)

- [ ] **Step 1]: 落档 spec

```markdown
# 08 — Daily Report（日报模块设计 v1.2.3）
[按 PRD §3 - §5 内容落档,含 4 Provider 接口 / 2 端点 / 7 DTO / 6 节测试策略]
```

- [ ] **Step 2]: 创建 RELEASES

```markdown
# RELEASES/v1.2.3 — Daily Report

## Scope
日报模块端到端闭环 —— 用户在 /daily 一个视图里看到今日/本周任务完成率、日程密度、消费总额与 Top 3 分类、饮食占位;激活首页占位卡。

## 数据模型
不新增业务表;V5 仅加索引 idx_user_completed_at。

## API
- GET /api/daily?date=YYYY-MM-DD
- GET /api/daily/week?week=YYYY-Www

## 文件清单
后端 12 文件 + 前端 13 文件 + E2E 1 文件 + 文档 3 文件

## 测试口径
mvn verify: 38 tests (4 Provider + 1 Service + 1 Controller + 1 IT + 集成)
pnpm test: 36 tests (10 store + 6 view + 6×3 components)
playwright: 8 tests (E2E)
Total: 82 tests / 0 failures
```

- [ ] **Step 3]: 关闭 issue

打开 `docs/issues/2026-07-18-mvp2-placeholder-modules.md`,在「占位模块上线状态」表加:
- [x] 日报模块 v1.2.3 已发布(2026-07-30)
- [ ] 饮食模块 v1.2.2(单独排期)

- [ ] **Step 4]: Commit + Tag

```bash
git add docs/specs/08-daily-report-design.md RELEASES/v1.2.3.md docs/issues/
git commit -m "docs(daily): release v1.2.3 with spec, RELEASES, and issue closeout"
git tag v1.2.3
```

---

## 7. Out-of-Scope Confirmation

### 7.1 v1.2.3 不做(与 PRD §5.2 一致)

- 月报视图(RICE 240,Reach 60%,留 E1)
- AI 摘要(Confidence 0.5,数据沉淀不足,留 E8)
- 报告导出 PDF/Markdown(Reach 40%,留 E6)
- 趋势图标(↑↓→,留 E2)
- 自定义指标(留 E7)
- 时区设置(留 E4)
- 推送通知(CLAUDE.md §1 显式不做)
- 报告对比(本月 vs 上月,留 E9)
- 报告订阅(邮件 / 浏览器通知,留 E10)
- 多用户协作 / 分享(CLAUDE.md §1 显式不做)
- 图片识别(消费小票 OCR,CLAUDE.md §1 显式不做)
- 多语言 i18n(CLAUDE.md §1 显式不做)
- 离线缓存 / PWA(CLAUDE.md §1 显式不做)
- 支付订阅(CLAUDE.md §1 显式不做)

### 7.2 v1.2.3 不改

- `User` / `RefreshToken` entity;`AuthService`;`JwtAuthFilter`;`SecurityConfig`;`UserContext`;`RateLimiter`(沿用 settings v1.1)
- `Task` / `Plan` / `Expense` entity / mapper / service(只读复用)
- `application.yml` 中 `lp.jwt.*` 与 `spring.data.redis.*`(零基础设施变更)

### 7.3 v1.2.3 不动

- `DietMetricProvider.enabled`(永久 false,直至饮食模块独立排期解锁)

---

## 8. Risks & Open Questions

| 风险 | 等级 | 处置 |
|---|---|---|
| 周报聚合 SQL 在大表(>10w 行)慢 | 中 | M0 索引必须命中;集成测试种 1w 行做 P95 断言;EXPLAIN 检查 |
| 时区边界 bug(`Asia/Shanghai` 跨 UTC) | 中 | Provider 单测固定 00:00 / 23:59 两端断言;IT 用 Asia/Shanghai 种子 |
| 索引变更锁表 | 低 | MySQL 8 INPLACE DDL;小表秒级;RELEASES 文档记录回滚 SQL |
| 饮食 Provider 误改 enabled=true | 低 | `DietMetricProviderTest` 锁死 enabled=false;PR 审阅卡 + IllegalStateException 运行时拦 |
| ECharts 等前端图表包体 | 不适用 | 日报不使用 ECharts(沿用 Element Plus 内置 Progress / Tag / List),首页卡不引入图表 |
| 周报边界被改成「滚动 7 天」 | 低 | 设计 §2.2 锁定 ISO 8601;US-3 AC 锁死周一为首 |
| 大用户(10w+ 行 t_task)首次加载日报超时 | 低 | 索引 + 分页(若需要);P95 监控;后续可加 Redis 缓存(留 E11) |
| 单开发者 6d 节奏不可预期 | 中 | 拆小任务到 ≤ 2d;每个可暂停点 commit;T1/T6/T10 为天然 checkpoint |
| M4 前端组件工作量超预期 | 中 | M3 完成后即开始 M4;若 spec 锁的组件细节过多,优先交付核心 3 个(任务/日程/消费),饮食/对比条可后置 |
| 与第三方工具(Notion Daily Note / Obsidian Daily)同质 | 中 | 差异化:与已有任务/日程/消费模块天然耦合;不需切换工具即可回顾 |

---

## 9. Total LOC 估算

| 模块 | 生产 | 测试 | 合计 |
|---|---|---|---|
| Backend (T1-T8) | ~450 | ~650 | ~1100 |
| Frontend (T9-T14) | ~550 | ~500 | ~1050 |
| E2E (T14) | — | ~150 | ~150 |
| Docs (T15) | ~400 | — | ~400 |
| **总计** | ~1400 | ~1300 | ~2700 |

15 个任务(T1-T15),每个 1 个 commit;后端→前端→E2E 严格顺序;JaCoCo + Vitest + Playwright 闸门全程强制。

---

*Generated 2026-07-21 · v1.2.3 daily report module planner output.*
