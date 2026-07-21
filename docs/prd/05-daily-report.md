# LifePulse 日报模块 PRD (v1.2.3)

> 版本: v1.0 草案 · 日期: 2026-07-21 · 面向读者: PM / 研发 / QA
> 设计规格: `docs/specs/08-daily-report-design.md`(落档中)
> 实施计划: `docs/superpowers/plans/2026-07-21-daily-v1-2-3.md`(待 writing-plans)
> 项目背景: `docs/prd/00-index.md` §6.1(显式不做 → Phase 2 扩展) + `docs/prd/04-future-prd.md` §2.1
> 流程来源: `/superpowers:brainstorming` × `/ecc:prp-prd`;6 节设计 + 8 项决策已全部用户确认

---

## 1. 产品目标 (SMART)

### 1.1 核心价值

> **让 LifePulse 用户在一个视图里,看到「今天/本周做了什么、效率如何、钱花在哪」,不需要在多个工具间切换。**

日报模块基于已有任务/日程/消费数据,**请求时实时聚合**,**只读不可改**,用户**零录入成本**。

### 1.2 SMART 指标

| 维度 | 指标 | 目标值 | 达成时间 |
|---|---|---|---|
| **S**pecific | 日报模块功能完整度 | 8 项 E2E 用例 + 5 类指标卡全部上线 | v1.2.3 发布 |
| **M**easurable | 后端服务层行覆盖率 | ≥ 80%(JaCoCo 强制闸门) | 发布前 |
| **M**easurable | 前端 store + 关键组件覆盖率 | store ≥ 70% / 关键组件 100% | 发布前 |
| **M**easurable | 日报接口 P95 响应时间(本地) | ≤ 200ms | 发布前 |
| **M**easurable | 周报接口 P95 响应时间(本地) | ≤ 300ms | 发布前 |
| **A**chievable | 首页日报卡激活 | 占位卡 → 模块卡(完成跳转) | 发布时 |
| **A**chievable | 跨用户越权拦截 | 100%(接口无 `{id}` 路径,天然不可达) | 发布前 |
| **R**elevant | 401 自动续期成功率 | 100% | 发布前 |
| **R**elevant | 饮食 disabled 锁死测试 | 100%(diet 上线前任何人改 `enabled` 必须改测试) | 发布前 |
| **T**ime-bound | 全部交付 | 8.5 工作日 | 2026-07-30 |

### 1.3 交付里程碑

| 阶段 | 内容 | 估时 | 累计 |
|---|---|---|---|
| M0 | 数据库索引迁移 `V5__daily_indexes.sql` | 0.5d | 0.5d |
| M1 | 后端骨架 + 4 Provider + Service + Controller + 单测 | 3d | 3.5d |
| M2 | 后端集成测试(Testcontainers 全链路) | 1d | 4.5d |
| M3 | 前端类型 + API + Store | 1d | 5.5d |
| M4 | 前端 View + 4 Card | 1.5d | 7d |
| M5 | E2E + 首页激活 | 1d | 8d |
| M6 | 文档 + RELEASES + issue 关闭 | 0.5d | 8.5d |

---

## 2. 用户画像

### 画像 A:忙碌独立工作者「小李」

| 项 | 内容 |
|---|---|
| 身份 | 30 岁独立产品经理,自由职业,节奏自主但日程碎片化 |
| 痛点 | 任务用滴答清单,日程用系统日历,消费用微信账单。**晚 10 点想知道「今天做了什么、花了多少」需要打开 3 个 App**,经常忘记回顾 |
| 核心任务 | 一天结束时,一眼看完今日产出与花费;周日晚规划下周 |
| 典型场景 | 周一早上在首页点「日报」→ 看到上周任务完成率 65%、花费 ¥800、餐饮占 40% → 决定本周减少外卖 |
| 日报诉求 | **自动化、零录入;只看一眼就有结论** |

### 画像 B:自我驱动学生「小王」

| 项 | 内容 |
|---|---|
| 身份 | 22 岁计算机大四学生,准备考研与期末 |
| 痛点 | 学习任务多(网课、刷题、论文),临时课程安排总被打乱;**不知道自己这周学习投入了多少时间** |
| 核心任务 | 按周看学习/复习时长分布;识别低效日 |
| 典型场景 | 周日点「周报」→ 看到本周日程密度周二最高(8h),其他日只有 2h → 调整下周自习安排 |
| 日报诉求 | **关注时间分布,不关注花费** |

### 画像 C:远程办公「小张」

| 项 | 内容 |
|---|---|
| 身份 | 28 岁远程研发工程师,会议密集,时区弹性 |
| 痛点 | 工作与个人事件混在公司日历太杂;**周末想回顾本周私人产出与开销,需要分离视图** |
| 核心任务 | 区分工作 vs 私人任务完成情况;周末看消费汇总 |
| 典型场景 | 周五晚点「本周」→ 看到私人任务完成率 80%、消费 ¥1200(餐饮 + 健身) → 周末补做未完成项 |
| 日报诉求 | **工作/私人分离;周末消费概览** |

> **共通点**:三类用户都希望「**自动聚合 + 零录入**」,无人愿意手写日报。这是日报模块选择「自动聚合报表」形态(而非自由日记/模板填空)的核心约束。

---

## 3. 功能清单(Task Checklist)

### 3.1 MVP(v1.2.3 必须交付)

#### 后端 — 数据库与索引

- [ ] **M0-1** 新建 `V5__daily_indexes.sql`,为 `t_task` 添加 `idx_user_completed_at(user_id, completed_at)`
- [ ] **M0-2** 复核 `t_plan.idx_user_start_at` 与 `t_expense.idx_user_occurred_at` 存在性,缺则补

#### 后端 — 模块骨架与 DTO

- [ ] **M1-1** 创建 `com.lifepulse.daily` 包 + DTO record(`DailyReportPayload` / `WeeklyReportPayload` / `TaskMetrics` / `PlanMetrics` / `ExpenseMetrics` / `DietMetrics` / `WeeklyComparison`)
- [ ] **M1-2** 定义 `MetricProvider<T>` 接口;实现 `DietMetricProvider` 骨架(永远返回 `enabled=false`)

#### 后端 — 三个聚合 Provider

- [ ] **M1-3** `TaskMetricProvider`:任务完成数(基于 `completed_at`)+ 状态分布 + 优先级分布
- [ ] **M1-4** `PlanMetricProvider`:事件数 + 总分钟(**排除全天事件**)+ 分类占比 + 最忙小时
- [ ] **M1-5** `ExpenseMetricProvider`:总额(分)+ 笔数 + 分类占比 + Top 分类

#### 后端 — Service 与 Controller

- [ ] **M1-6** `DailyReportService`:`aggregateDaily(userId, date)` + `aggregateWeekly(userId, week)`,沿用 `UserContext.current()`
- [ ] **M1-7** `DailyReportController`:GET `/api/daily?date=YYYY-MM-DD` + GET `/api/daily/week?week=YYYY-Www`

#### 后端 — 测试

- [ ] **M1-U1** `DailyReportServiceTest`:6 用例(含 1004 未来日 / 1004 格式错 / 周报聚合 / ISO 周跨年)
- [ ] **M1-U2** 4 个 `MetricProviderTest`:每个 ≥ 80% 行覆盖,**锁死饮食 disabled 行为**
- [ ] **M1-U3** `DailyReportControllerTest`:7 用例(GET 200 / 400 / 401 / 周报 + 上下边界)
- [ ] **M2-I1** `DailyReportIT`:Testcontainers 全链路 + 空数据零值 + 跨用户隔离 + 软删除过滤
- [ ] **M2-I2** 周报 IT:7 天分布种子 + current/previous/delta 校验
- [ ] **M2-I3** 饮食 disabled 集成断言 + 大表(1w 行)P95 性能断言

#### 前端 — 类型 / API / Store

- [ ] **M3-1** `types/daily.ts`:5 个 interface,`DietMetrics.enabled` 永远 boolean
- [ ] **M3-2** `api/daily.ts`:`getDailyReport` + `getWeeklyReport`(沿用 `http.ts` 拦截器)
- [ ] **M3-3** `stores/daily.ts`:Pinia setup 风格,9 个测试覆盖,coverage ≥ 70%

#### 前端 — 视图与组件

- [ ] **M4-1** `DailyReportHeader.vue`:日期/周选择器 + 上一/下一按钮 + 视图切换 chip
- [ ] **M4-2** `TaskMetricsCard.vue` + spec(完成率 + 状态/优先级分布)
- [ ] **M4-3** `PlanMetricsCard.vue` + spec(总分钟 HH:MM + 分类占比)
- [ ] **M4-4** `ExpenseMetricsCard.vue` + spec(¥X.XX 格式化 + Top 3 分类)
- [ ] **M4-5** `DietMetricsCard.vue` + spec(**永远 disabled 视觉锁定**)
- [ ] **M4-6** `WeeklyComparisonBar.vue` + spec(delta 符号规则)
- [ ] **M4-7** `DailyReportView.vue` + 路由 `/daily` + 鉴权守卫,6 个 View 测试

#### E2E 与首页激活

- [ ] **M5-1** `e2e/daily/daily-report.spec.ts`:8 用例(主页跳转 / 正常日 / 空日 / 饮食 disabled / 未来日 1004 / 周报对比 / 跨用户隔离 / 上下日导航)
- [ ] **M5-2** 首页卡激活:`HOME_CARDS.daily` 由 `placeholder` → `module`,`to:'/daily'`;同步更新 `HomeView.spec.ts` + 首页 E2E
- [ ] **M5-3** 回归全套:`pnpm test` + `pnpm exec playwright test` + `mvn verify` 全绿

#### 文档与发布

- [ ] **M6-1** 落档 `docs/specs/08-daily-report-design.md`(6 节详细设计)
- [ ] **M6-2** `RELEASES/v1.2.3.md`:scope / 数据模型 / API / 文件清单 / 测试口径
- [ ] **M6-3** 关闭 issue `2026-07-18-mvp2-placeholder-modules.md` 增补日报完成项 + 拆分饮食独立 issue

---

### 3.2 扩展/迭代(v1.3+)

#### 短期增强(v1.3.x — 1~2 个月内)

- [ ] **E1** 月报视图(基于 ISO 自然月,与周报同模式聚合)
- [ ] **E2** 趋势图标(↑/↓/→)基于 delta 阈值(如 ±5pp)
- [ ] **E3** 任务完成数口径切换:增加「截止日 = 当天 且 status = DONE」作为副口径
- [ ] **E4** 时区设置(从硬编码 `Asia/Shanghai` 改为用户偏好)

#### 中期增强(v1.4.x — 3~6 个月)

- [ ] **E5** 饮食模块正式接入:取消 `DietMetricProvider` 的 `enabled=false` 锁,接入热量/营养指标
- [ ] **E6** 报告导出(Markdown / PDF)供分享与备份
- [ ] **E7** 自定义指标:用户可选 1-3 项关注的指标加入「我的日报」

#### 长期愿景(v2.x — 6 个月+,依赖 AI 模块)

- [ ] **E8** AI 摘要:基于日报数据生成「本周一句话总结 + 3 条建议」(依赖 AI 分析模块)
- [ ] **E9** 报告对比:本月 vs 上月、Q1 vs Q2 等更长周期对比
- [ ] **E10** 报告订阅:每周日晚邮件/浏览器通知推送本周报(需引入订阅中心)

---

### 3.3 KANO 与 RICE 影响说明

#### KANO 划分

| 层级 | 含义 | 归属 | 本期代表功能 |
|---|---|---|---|
| **基本型(Must-be)** | 缺失即产品不可用 | **MVP 必做** | 鉴权、跨用户隔离、日期格式校验、空数据零值结构 |
| **期望型(One-dimensional)** | 越多越好,用户满意度线性增长 | **MVP 必做**(高 RICE) | 任务/日程/消费聚合、周报对比、上一/下一日导航 |
| **兴奋型(Attractive)** | 缺失不致命,有了惊喜 | **扩展**(E1-E10) | AI 摘要、报告导出、趋势图标 |
| **无差异(Indifferent)** | 用户不在意 | **不做** | 动画细节、主题色切换 |

#### RICE 评分与排序

| 候选 | Reach | Impact | Confidence | Effort | 分数 | 归属 |
|---|---|---|---|---|---|---|
| 任务指标聚合 | 100% | 高(3) | 高(1.0) | 中(0.5) | **600** | MVP |
| 日程指标聚合 | 100% | 高(3) | 高(1.0) | 中(0.5) | **600** | MVP |
| 消费指标聚合 | 80% | 中(2) | 高(1.0) | 中(0.5) | **320** | MVP |
| 周报 + 本周 vs 上周 | 90% | 中(2) | 中(0.8) | 低(0.3) | **480** | MVP |
| 饮食预留接口 | 100% | 低(1) | 高(1.0) | 低(0.2) | **500** | MVP(成本极低,留下扩展点) |
| 月报视图 | 60% | 中(2) | 高(1.0) | 中(0.5) | **240** | 扩展 |
| AI 摘要 | 80% | 高(3) | **低(0.5)** | 高(1.5) | **80** | 扩展(Confidence 低,数据沉淀不足) |
| 报告导出 PDF | 40% | 低(1) | 中(0.8) | 高(1.0) | **32** | 扩展 |
| 趋势图标 | 70% | 低(1) | 高(1.0) | 低(0.2) | **350** | 短期扩展 |

**关键决策解释**:

- **饮食预留接口进 MVP**:虽 Reach 100% 但 Impact 低,但 Effort 仅 0.2(几行代码 + 一个锁死测试),RICE 分数高 → **留下扩展点几乎无成本**;
- **AI 摘要放扩展**:虽然 Impact=3,但 Confidence=0.5(数据沉淀不足,贸然上线给空泛结论会伤害品牌),分数仅 80 → 延后到 AI 模块就绪;
- **周报进 MVP**:Confidence 中等(0.8)但 Effort 极低(0.3),周报是基于日报聚合,**SQL 不增加复杂度**,因此放进 MVP 而非扩展;
- **月报放扩展**:Reach 仅 60%(多数用户月度复盘需求弱),但实现要重新设计聚合逻辑,Effort 高 → 延后;
- **趋势图标放短期扩展**:Reach 70% / Effort 0.2 分数看似高,但 Impact 仅 1(微改善体验,非必需),KANO 属「期望型」中靠后 → 排在 E1-E4 之后。

---

## 4. 用户故事列表

> 格式:作为 [角色], 我需要 [功能], 以便于 [价值]。每条故事附 3 条 AC。

### US-1:今日日报自动展示

> 作为 **忙碌独立工作者小李**, 我需要 **打开首页点「日报」自动看到今天的任务完成率、日程密度、消费总额**, 以便于 **不需要在 3 个 App 间切换就能完成一日回顾**。

**AC:**
- [ ] 用户登录后访问 `/daily`,URL 自动补 `?date=今天` 并加载今日报告
- [ ] 报告显示任务完成率(百分比 + 进度条)、日程事件数 + 总分钟、消费总额(¥X.XX)+ Top 分类
- [ ] 饮食卡片始终渲染为灰色 disabled 状态,显示「饮食模块未上线」

### US-2:查看历史某一天

> 作为 **远程办公小张**, 我需要 **通过日期选择器查看任意过去某天的日报**, 以便于 **回看上周某天的具体产出与开销**。

**AC:**
- [ ] `DailyReportHeader` 提供 `el-date-picker`,可选范围为「过去 30 天」(避免拖慢接口)
- [ ] 选择过去日期后 URL 更新为 `?date=YYYY-MM-DD`,报告显示该日数据
- [ ] 选择未来日期 → 页面展示 1004 错误态,含「回到今天」按钮一键跳转

### US-3:周报含本周 vs 上周对比

> 作为 **学生小王**, 我需要 **查看本周日报 + 上周对比(delta%)**, 以便于 **识别学习投入增减趋势**。

**AC:**
- [ ] 视图切换到周报(`?week=2026-W29`)显示 ISO 自然周(周一-周日)聚合
- [ ] `WeeklyComparisonBar` 显示 3 组对比:任务完成率 / 日程事件数 / 消费总额,各带 `current` / `previous` / `delta` 三值
- [ ] 周一为周首(用户看到 `weekStart=2026-07-13 Monday`),跨年周按 ISO 规则展示

### US-4:上下日快捷导航

> 作为 **小李**, 我需要 **点「上一天」/「下一天」按钮快速翻看**, 以便于 **不需要反复打开日期选择器**。

**AC:**
- [ ] 按钮在日期边界禁用(最早 30 天前 / 最晚今天)
- [ ] 点击后 URL 同步更新,新报告加载期间显示骨架屏(`ElSkeleton`)
- [ ] 浏览器前进/后退按钮可还原浏览历史

### US-5:空数据日零值展示

> 作为 **新注册用户小张**, 我需要 **没有任何数据的某天也能查看日报(显示 0)**, 以便于 **不会因为「没数据」就报错/空白**。

**AC:**
- [ ] 无任务/日程/消费的日期,接口返回 200 + 零值结构(完成率 0%、事件数 0、¥0.00)
- [ ] 各指标卡渲染「无数据」友好提示而非崩溃
- [ ] 饮食卡仍为 disabled(与有数据日一致)

### US-6:跨用户越权不可能

> 作为 **平台方**, 我需要 **用户 A 永远无法访问用户 B 的日报数据**, 以便于 **保证数据隔离底线**。

**AC:**
- [ ] 日报接口无 `{id}` 路径,只能查询 `UserContext.current()` 对应的本人数据
- [ ] 即使手动构造 `?userId=B` 参数也被忽略(代码层防御性注释 + Provider SQL 全部 `WHERE user_id = #{userId}`)
- [ ] Testcontainers 集成测试断言:用户 B 调用 `/api/daily` 永远只看 B 自己的数据

### US-7:鉴权与自动续期

> 作为 **小李**, 我需要 **access token 过期后自动续期不打断查看日报**, 以便于 **不会出现「请重新登录」的尴尬**。

**AC:**
- [ ] 401 时前端拦截器用 refresh token 自动续期并重放原请求
- [ ] 续期失败才跳转登录页(用户无感)
- [ ] refresh token 不可用于访问日报接口(`JwtAuthFilter` 校验 `typ=access`)

### US-8:饮食模块扩展预留

> 作为 **平台方**, 我需要 **饮食模块接口形状稳定,上线时前端无需改动**, 以便于 **饮食模块可以独立排期上线**。

**AC:**
- [ ] `DietMetrics` 形状冻结:`{ enabled: boolean, value: DietValue | null, reason: string | null }`
- [ ] `DietMetricProvider.aggregate()` 永远返回 `enabled=false, value=null`(单测锁死)
- [ ] 饮食模块上线只需在 Provider 内填 `enabled=true` 并实现 `value`,前端组件零改动

---

## 5. 功能范围与边界

### 5.1 In Scope(MVP v1.2.3 必须交付)

| 模块 | 功能 |
|---|---|
| **后端 API** | `GET /api/daily?date=YYYY-MM-DD`、`GET /api/daily/week?week=YYYY-Www` |
| **数据源** | `t_task`、`t_plan`、`t_expense` 实时聚合;**不新增任何表**(零 Flyway 业务表迁移) |
| **饮食接口** | `DietMetrics` 接口签名固定,实现永远 disabled,饮食模块上线时前端零改动 |
| **鉴权** | 沿用 `JwtAuthFilter` + access token,401 自动续期 |
| **错误码** | 沿用 1004(参数错/未来日)/ 401 / 500,**不新增错误码** |
| **前端视图** | `/daily` 单页 + 日期选择器,日/周同 payload shape 复用组件 |
| **前端组件** | 1 个 Header + 4 个指标卡(Task/Plan/Expense/Diet)+ 1 个周报对比条 |
| **测试** | 后端 Service ≥ 80% / Controller 100% / IT 关键路径 100% + 前端 store ≥ 70% / 关键组件 100% + E2E 8 用例 |
| **首页激活** | 占位卡 → 模块卡,跳转 `/daily` |
| **文档** | 设计 spec + RELEASES v1.2.3 + issue 关闭 |

### 5.2 Out of Scope(本期明确不做)

| 不做的内容 | 原因 |
|---|---|
| 月报视图 | RICE 分数仅 240;Reach 仅 60%;实现复杂度高;留 E1 |
| AI 摘要 | 依赖 AI 分析模块;Confidence 0.5(数据沉淀不足,空泛结论伤害品牌);留 E8 |
| 报告导出(PDF / Markdown) | Reach 仅 40%,RICE 32;留 E6 |
| 趋势图标(↑↓→) | 短期增强,留 E2 |
| 自定义指标 | 涉及配置层设计,留 E7 |
| 时区设置 | 单开发者节奏不引入复杂度,留 E4 |
| 推送通知 | CLAUDE.md §1 显式不做(推送通知);日报亦不例外 |
| 报告对比(本月 vs 上月) | 留 E9 |
| 报告订阅(邮件 / 浏览器通知) | 涉及订阅中心,留 E10 |
| 多用户协作 / 分享 | CLAUDE.md §1 显式不做(团队/共享);日报亦不例外 |
| 图片识别(截图上传消费小票) | CLAUDE.md §1 显式不做(图片识别);日报亦不例外 |
| 多语言(i18n) | CLAUDE.md §1 显式不做(多语言);日报亦不例外 |
| 离线缓存 / PWA | CLAUDE.md §1 显式不做(离线缓存);日报亦不例外 |
| 支付订阅 | CLAUDE.md §1 显式不做(支付订阅);日报亦不例外 |

---

## 6. 成功指标(KPIs)

| 维度 | 指标 | 目标值 | 衡量方式 |
|---|---|---|---|
| **交付** | 全部 6 节设计 + 8.5d 任务清单完成 | 100% | 任务清单 `- [x]` 全勾 |
| **功能完整度** | 8 项 E2E 用例 + 5 类指标卡全部上线 | 100% | Playwright 全绿 |
| **代码质量(后端)** | Service 行覆盖 | ≥ 80% | JaCoCo(`mvn verify` 闸门) |
| **代码质量(后端)** | Controller 关键路径 | 100% | JaCoCo |
| **代码质量(后端)** | 集成测试关键流 | 100% | Testcontainers |
| **代码质量(前端)** | store 行覆盖 | ≥ 70% | Vitest |
| **代码质量(前端)** | 关键组件(DailyReportView + 4 Card + WeeklyComparisonBar) | 100% | Vitest |
| **类型安全** | `vue-tsc --noEmit` | 0 错 | CI |
| **接口性能** | 日报接口 P95 响应时间(本地) | ≤ 200ms | Testcontainers 1w 行种子 + JFR 断言 |
| **接口性能** | 周报接口 P95 响应时间(本地) | ≤ 300ms | 同上 |
| **安全** | 跨用户越权拦截 | 100%(天然不可达,设计审阅 PR 卡住回归) | 代码 + IT |
| **安全** | 401 自动续期成功率 | 100% | E2E |
| **可维护** | 饮食 disabled 锁死测试 | 100%(diet 上线前任何人改 `enabled` 必须改测试) | 单测 + IT |
| **用户感知** | 首页日报卡激活 | 占位 → 模块 | 首页 E2E |
| **可发布** | `mvn verify` + `pnpm test` + `pnpm exec playwright test` | 全绿 | CI |
| **可发布** | `vue-tsc --noEmit` | 0 错 | CI |

---

## 7. 风险与应对

| 类别 | 风险 | 等级 | 应对 |
|---|---|---|---|
| **技术** | 周报聚合 SQL 在大表(>10w 行)慢 | 中 | M0 索引必须命中;集成测试种 1w 行做 P95 断言;EXPLAIN 检查 |
| **技术** | 时区边界 bug(`Asia/Shanghai` 跨 UTC) | 中 | Provider 单测固定 `00:00 / 23:59` 两端断言;IT 用 `Asia/Shanghai` 种子 |
| **技术** | 索引变更锁表 | 低 | MySQL 8 INPLACE DDL;小表秒级;RELEASES 文档记录回滚 SQL |
| **技术** | 饮食 Provider 误改 `enabled=true` | 低 | `DietMetricProviderTest` 锁死 enabled=false;PR 审阅卡 |
| **范围** | 用户要求加趋势图 / AI 摘要 / 自定义指标 | 中 | spec §5.2 明列「暂不做」;PR 卡 review 提醒 |
| **范围** | 周报边界被改成「滚动 7 天」 | 低 | 设计 §2.2 锁定 ISO 8601;US-3 AC 锁死周一为首 |
| **数据** | 大用户(10w+ 行 `t_task`)首次加载日报超时 | 低 | 索引 + 分页(若需要);P95 监控;后续可加 Redis 缓存(留 E11) |
| **资源** | 单开发者 8.5d 节奏不可预期 | 高 | 拆小任务到 ≤ 2d;每个可暂停点 commit;M0/M3/M5 为天然 checkpoint |
| **资源** | M4 前端组件工作量超预期 | 中 | M3 完成后即开始 M4;若 spec 锁的组件细节过多,优先交付核心 3 个(任务/日程/消费),饮食/对比条可后置 |
| **市场** | 与第三方工具(Notion Daily Note / Obsidian Daily)同质 | 中 | 差异化:与已有任务/日程/消费模块天然耦合;不需切换工具即可回顾 |
| **合规** | 用户数据隐私担忧 | 低 | 全部数据本地存储;不引入第三方服务;饮食 disabled 避免引入外部 API |

---

## 8. 关联文档

| 文档 | 路径 |
|---|---|
| 设计规格(6 节详细设计) | `docs/specs/08-daily-report-design.md`(落档中) |
| 实施计划 | `docs/superpowers/plans/2026-07-21-daily-v1-2-3.md`(待 writing-plans) |
| 项目总览 PRD | `docs/prd/00-index.md` |
| Phase 2 扩展模块愿景 | `docs/prd/04-future-prd.md` §2.1 |
| MVP2 占位模块 issue | `docs/issues/2026-07-18-mvp2-placeholder-modules.md` |
| 已有 PRD(同模板对照) | `docs/prd/01-auth-prd.md` / `docs/prd/02-task-prd.md` / `docs/prd/03-plan-prd.md` |
| v1.2.1 expense 实施参考 | `docs/superpowers/plans/2026-07-20-expense-v1-2-1.md` |
| 既有 spec 拆分规范 | `docs/superpowers/specs/2026-07-15-lifepulse-mvp1-design.md` 索引 |

---

*生成时间: 2026-07-21*
*状态: DRAFT — 待设计 spec 落档后转入待评审*
*版本: v1.0 草案 — 配套 v1.2.3 release*