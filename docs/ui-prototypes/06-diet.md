# UI 原型 · 06 — DietView

> 版本：v0.1 · 日期：2026-07-20 · 视图：`/diets` + `/diets/:id`（v1.2.2）
> 输入：[06-diet-prd.md §3.1 + §4](../prd/06-diet-prd.md) · [../specs/07-diet-design.md](../specs/07-diet-design.md) · 姊妹模块：[05-expense.md](./05-expense.md)

---

## 1. 故事 → UI 要素映射

| PRD 出处 | UI 必须呈现 |
|---|---|
| DIET-1 AC1：7 字段录入 + 默认时间 - 5min + 营养可填 0 | DietDialog 含 7 字段；`mounted` 时 `occurredAt = dayjs().subtract(5, 'm')`；4 项营养 `:min="0"` |
| DIET-1 AC2：「一键复用」下拉显示 top 10 高频名称 + 纯前端缓存 ≤100ms | Dialog 顶部「从常用项选择 ▾」下拉；点击 → 前端填值（不调接口） |
| DIET-1 AC3：成功后立即关弹窗 + 列表新增 + 营养卡刷新 | `submit()` 成功 → `dialogVisible=false` + `fetchList()` + `fetchSummary()` |
| DIET-2 AC1：4 项数字（实际/推荐）+ 横向柱图 | DietNutritionCard 含 4 行「实际 / 推荐」 + ECharts 横向柱图 |
| DIET-2 AC2：超 100% 红 / <60% 橙 / 60-100% 绿 + ▲▼ 文本（无障碍） | 柱图 + 文字色同步；右侧 ▲/▼ 文本指示方向；不仅依赖颜色 |
| DIET-2 AC3：vs 昨日 / vs 上周同日 + 无对比数据文案 | Card 底部 2 行 delta；null → "无对比数据"（与消费一致） |
| DIET-3 AC1：录入弹窗顶部下拉数据源 `/diets/frequent?from=&to=&limit=10` | Dialog `mounted` 即 `store.fetchFrequent()`；缓存进 `store.frequent` |
| DIET-3 AC2：点击填入名称 + 4 项平均营养 + 用户可微调 | `handleReuse(item)` 仅填 5 字段（name + 4 项营养）；mealType / note / occurredAt 由用户重选 |
| DIET-3 AC3：复用基于近 30 天均值；无历史不出现 | 数据源 SQL 聚合；前端只显示 hitCount > 0 项 |
| DIET-4 AC1：编辑/删除按钮 + dialog 回显 | ListItem 右侧 `[编辑][删除]` |
| DIET-4 AC2：删除二次确认 + 列表移除 + 营养卡同步 | ElMessageBox；成功后 `fetchList()` + `fetchSummary()` 双刷 |
| DIET-4 AC3：越权 1003 → 跳回列表 | 拦截器 → Toast「资源不存在」+ router.replace('/diets') |
| 首页集成 §3.1：饮食卡 placeholder=false | HomeView 第 5 卡激活，跳 `/diets` |
| 安全 §3.1：限流 10/min/userId | 1006 → Toast「操作过于频繁，请稍后再试」 |
| 安全 §3.1：跨用户 1003 | 见 DIET-4 AC3 |
| 模块独立 §3.1：不与消费共享 | 路由 `/diets`；不 import `expense/*`；`shared/EmptyState` / `shared/LoadingState` 在 v1.2.2 启动时统一抽取 |

## 2. 桌面 ASCII（≥1024px）

### 2.1 DietView（两栏：左 2/3 按天分组列表 + 右 1/3 营养卡）

```
┌─────────────────────────────────────────────────────┬──────────────────────────┐
│ [2026-07-15 ▾]  [全餐别 ▾]  [+ 新增饮食]            │ ┌──────────────────────┐ │
│                                                     │ │   今日营养            │ │
│ ┌─────────────────────────────────────────────────┐ │ │                      │ │
│ │ ▾ 2026-07-15 周三                  合计 1680kcal│ │ │  1680 / 2000 kcal   ▲│ │  ← 红/绿/橙三态色
│ │                                                 │ │ │  ▓▓▓▓▓▓▓▓░░  84%   │ │  ← 横向柱图
│ │   ▸ 早餐                                        │ │ │                      │ │
│ │     燕麦 100g          380kcal    [编辑][删除]  │ │ │   55 / 60 g 蛋白   ▼│ │
│ │     牛奶 200ml         120kcal    [编辑][删除]  │ │ │   ▓▓▓▓▓▓▓░░░  92%   │ │
│ │   ▸ 午餐                                        │ │ │                      │ │
│ │     米饭 1 碗          230kcal    [编辑][删除]  │ │ │  220 / 300 g 碳水  ▼│ │
│ │     西兰花 100g        55kcal     [编辑][删除]  │ │ │   ▓▓▓▓▓▓░░░░  73%   │ │
│ │     鸡胸肉 150g        250kcal    [编辑][删除]  │ │ │                      │ │
│ │   ▸ 晚餐                                        │ │ │   50 / 65 g 脂肪   ▼│ │
│ │     ...                                         │ │ │   ▓▓▓▓▓▓▓░░░  77%   │ │
│ │   ▸ 加餐                                        │ │ │                      │ │
│ │     酸奶 1 杯         150kcal    [编辑][删除]  │ │ │  vs 昨日  +120 kcal │ │
│ │                                                 │ │ │  vs 上周同日 -80   │ │
│ └─────────────────────────────────────────────────┘ │ │                      │ │
│                                                     │ │   ◀ 2026-07-15 ▶    │ │
│ ┌─────────────────────────────────────────────────┐ │ │                      │ │
│ │ ▾ 2026-07-14 周二                  合计 1560kcal│ │ └──────────────────────┘ │
│ │   ▸ 早餐                                        │ │                          │
│ │     ...                                         │ │                          │
│ └─────────────────────────────────────────────────┘ │                          │
│                                                     │                          │
│            [上一页] 1 / 5 [下一页]                  │                          │
└─────────────────────────────────────────────────────┴──────────────────────────┘
```

布局规则：
- 桌面 `el-row :gutter="16"` + `el-col :xs=24 :md=16 / :xs=24 :md=8`
- 列表按天折叠（默认今日展开 / 历史折叠）；每天头部显示日期 + 当日合计 kcal
- 每餐别（早/午/晚/加餐）是小折叠组；展开显示该餐所有条目
- 每行 `display: flex` 左 = 名称 + kcal，右 = 操作按钮组
- 右侧营养卡 sticky top=80（TopBar 下方）

### 2.2 DietDialog（新增 / 编辑 / 复用）

```
┌────────────────────────────────────────────────────┐
│  新增饮食                            ✕              │
├────────────────────────────────────────────────────┤
│                                                    │
│  ┌─ 一键复用 ──────────────────────────────────┐   │  ← 仅「新增」模式显示
│  │ 从常用项选择 ▾                               │   │
│  │   ┌──────────────────────────────────────┐   │   │
│  │   │ 燕麦 100g  380kcal/55P/65C/8F (×12) │   │   │
│  │   │ 鸡胸肉 150g  250kcal/45P/0C/5F  (×8) │   │   │
│  │   │ 米饭 1 碗  230kcal/5P/50C/0F    (×7)│   │   │
│  │   │ ...                                    │   │   │
│  │   └──────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────┘   │
│                                                    │
│  餐别 *  [ 午餐       ▾]                          │  ← el-select, 源 constants/diet
│                                                    │
│  名称 *  [                  ]                     │  ← el-input :maxlength="64"
│                                                    │
│  热量 kcal   [  230.00 ]                          │
│  蛋白 g      [    5.00 ]                          │  ← el-input-number :min="0" :precision="2"
│  碳水 g      [   50.00 ]                          │
│  脂肪 g      [    0.00 ]                          │
│                                                    │
│  备注    [                          ]              │  ← el-input :maxlength="200"
│                                                    │
│  发生时间 [ 2026-07-15 12:35 ] 📅                 │  ← el-date-picker type=datetime
│           （默认 = 现在 - 5 分钟）                 │
│                                                    │
│              [ 取 消 ]   [ 保 存 ]                 │
└────────────────────────────────────────────────────┘
```

复用行为细节：
- 下拉数据：进 Dialog 即调 `store.fetchFrequent()`（已缓存则不重调）
- 点击项 → 仅填 `name / kcal / proteinG / carbG / fatG` 5 个字段；`mealType / note / occurredAt` 由用户重填
- 「编辑」模式不显示一键复用下拉（避免误操作）
- 复用后的记录是新的一条（不修改原历史记录）；用户可任意修改后提交

### 2.3 DietDetailView（/diets/:id，只读）

```
┌────────────────────────────────────┐
│  饮食详情  ‹ 返回列表              │
├────────────────────────────────────┤
│                                    │
│  餐别      午餐                    │  ← 禁用态
│  名称      米饭 1 碗               │  ← 禁用态
│  热量      230.00 kcal            │  ← 禁用态
│  蛋白      5.00 g                 │  ← 禁用态
│  碳水      50.00 g                │  ← 禁用态
│  脂肪      0.00 g                 │  ← 禁用态
│  备注      -                       │  ← 禁用态
│  发生时间  2026-07-15 12:35       │  ← 禁用态
│  创建于    2026-07-15 12:36       │
│                                    │
│  [ 进入编辑 ]    [ 删除 ]           │  ← 删除二次确认
└────────────────────────────────────┘
```

### 2.4 Home 卡激活后 ASCII（更新 04-home §1.1）

```
┌──────────────┐
│ 今日摄入      │
│              │
│  1680 kcal   │  ← 大数字 + 单位
│              │
│ 早 ● 午 ●    │  ← 4 餐状态点
│ 晚 ○ 加 ●    │     ● = 已记录；○ = 未记录
│              │
│ 查看全部 →   │
└──────────────┘
```

> 04-home.md §1.1 表格同步：「饮食记录」行 → placeholder=false，真实数据源 = `/api/v1/diets/summary?date=今日` + `/api/v1/diets?date=今日&size=0`（仅取 4 餐是否有记录的状态）。

## 3. 移动 ASCII（<768px）

```
┌──────────────────────┐
│ TopBar（见 03）       │
├──────────────────────┤
│ [今日▾][全餐别▾][+]  │
│                      │
│ ┌──────────────────┐ │
│ │  今日营养         │ │
│ │  1680 / 2000 kcal│ │  ← 营养卡置顶（信息密度高）
│ │  ▓▓▓▓▓▓▓▓░░  84% │ │
│ │   55 / 60 g 蛋白 │ │
│ │  220 / 300 g 碳水 │ │
│ │   50 / 65 g 脂肪  │ │
│ └──────────────────┘ │
│                      │
│ ▾ 2026-07-15 周三    │
│  早餐                │
│   燕麦 380kcal       │
│   牛奶 120kcal       │
│  午餐                │
│   米饭 230kcal       │
│   ...                │
│                      │
│   [上一页] 1/5 [下一页]│
└──────────────────────┘
```

> 移动端汇总卡置顶（数字密度高，先看数字）；按餐别折叠保留；操作按钮简化为 ✏️🗑️ 图标。完整桌面版两栏在 <1024px 自动变单列堆叠（沿用 SPEC §6.3）。

## 4. 状态机（List 视角）

```
        ┌──────────┐
   →    │ LOADING  │ ← 进入页面 / 切换日期 / 翻页
        └────┬─────┘
             │ store.fetchList() + fetchSummary() 完成
   ┌─────────┼──────────┐
   ▼         ▼          ▼
   空数据    有数据     接口失败
   │         │          │
   ▼         ▼          ▼
  EmptyState 列表+营养  ElMessage
  +「+新增」  正常渲染   "加载失败，稍后重试"
                          │
                          ▼
                     ┌──────────┐
                     │  EMPTY   │ ← 「重试」按钮触发
                     └──────────┘
```

Dialog 状态机（嵌入 List 视角）：

```
        ┌──────────┐
   →    │  CLOSED  │ ← 默认
        └────┬─────┘
             │ 点 [新增] / [编辑]
        ┌────▼─────┐
        │  EDITING │ ← Dialog 打开；校验失败 → 字段红字
        └────┬─────┘
             │ submit() 成功 → store.create/update → fetchList + fetchSummary 双刷
        ┌────▼─────┐
        │ SUBMITTING│ ← 提交中，按钮 disabled + spinner
        └────┬─────┘
             │ 完成
             ▼
           CLOSED
```

DetailView（路由 /diets/:id）：

```
        ┌──────────┐
   →    │ LOADING  │ ← 进入路由
        └────┬─────┘
             │ store.fetchById() 完成
   ┌─────────┼──────────┐
   ▼         ▼          ▼
  找到    找到(他人)   未找到
   │         │          │
   ▼         ▼          ▼
  只读     1003 拦截  1004 拦截
  Dialog   跳回列表   跳回列表
  +删除    +Toast     +Toast
```

一键复用子状态（嵌入 EDITING）：

```
        ┌──────────┐
   →    │  EMPTY   │ ← 初始未选 / 用户清空
        └────┬─────┘
             │ 点下拉某项 → handleReuse(item)
        ┌────▼─────┐
        │ REUSED   │ ← 5 字段被填；用户可微调；mealType/note/occurredAt 不变
        └──────────┘
```

## 5. 字段清单

| 展示 | 来源 | 备注 |
|---|---|---|
| 列表名称 | `GET /api/v1/diets?size=20` → `items[].name` | 截断显示 ≤ 20 字符 + `…` |
| 列表 kcal | `items[].kcal` | 服务端 BigDecimal → 字符串 |
| 餐别中文 | `constants/diet.ts` `MEAL_TYPE_LABEL[mealType]` | 后端 enum 双侧对齐 |
| 当日 4 项合计 | `GET /api/v1/diets/summary?date=` → `kcal / proteinG / carbG / fatG` | 默认今日 |
| 推荐常量 | `constants/diet.ts` 静态值 | 2000/60/300/65 |
| 柱图比例 | `actual / recommended` | 三态色（>100 红 / <60 橙 / 60-100 绿） |
| ▲▼ 文本 | `actual > recommended ? ▲ : ▼` | 始终展示；不仅依赖颜色 |
| vs 昨日 | `summary.kcalDeltaYesterday` | null → "无对比数据" |
| vs 上周同日 | `summary.kcalDeltaLastWeek` | null → "无对比数据" |
| 高频项 | `GET /api/v1/diets/frequent?from=&to=&limit=10` | 近 30 天；hitCount > 0 |
| 复用填值 | frequent item 的 `name / avgKcal / avgProteinG / avgCarbG / avgFatG` | 不含 mealType / note / occurredAt |

## 6. 错误码 → 文案映射

| code | 触发场景 | UI 文案 | 行为 |
|---|---|---|---|
| 1001 | 名称空 / 餐别不在枚举 / 名称 > 64 / 备注 > 200 / 营养 < 0 | 字段下红字 | Dialog 字段定位 |
| 1002 | 未登录 / token 失效 | "请重新登录" | 跳 `/login?redirect=` |
| 1003 | 跨用户访问 `{id}` | "资源不存在" | 跳回 `/diets` + Toast |
| 1004 | 资源不存在 / 已软删 | "该笔饮食已被删除" | 跳回 `/diets` + Toast |
| 1006 | 写端点 10/min/userId 超限 | "操作过于频繁，请稍后再试" | Dialog 关闭 + 不更新列表 |

> frequent 接口为只读，不走限流；读端点 1004 → 空下拉（不报错）。

## 7. 与编码落地关联

- 路由：`router/index.ts` 加 `/diets` + `/diets/:id`（沿用 04-frontend §4）
- Store：`stores/diet.ts` state = `{list, filter: {date, mealType}, summary, frequent, page, loading, dialogVisible, dialogMode, currentItem}`；getters = `groupedByDay(list)`；actions = `fetchList / fetchSummary / fetchFrequent / create / update / remove / openDialog / closeDialog`
- 共享组件：`components/shared/EmptyState.vue` + `LoadingState.vue` 在 **v1.2.2 启动时**抽取；v1.2.1 消费独立开发，临时本地副本即可（沿用 SPEC 06 §6.2 + 07 §6.2 决策）
- 组件映射：
  - 列表组 = `el-collapse`（外层按天）+ `el-collapse-item`（内层按餐别）
  - 筛选条 = `el-date-picker date` + `el-select mealType` + `el-button primary`
  - Dialog = `el-dialog` + `el-form` + `el-select mealType` + `el-input name` + 4× `el-input-number` + `el-date-picker datetime`
  - 营养卡 = `<div class="nutrition-card">` + 4× 横向柱（ECharts `import('echarts/core')` 按需）
  - 一键复用下拉 = `el-popover` 或 `el-dropdown`（trigger=click）；点击项触发 `handleReuse`
- 文件拆分：`DietView.vue`（壳）/`DietDayGroup.vue` / `DietNutritionCard.vue` / `DietDialog.vue` / `DietDetailView.vue`
- ECharts 按需：与消费共享同一 chunk；`DietNutritionCard.vue` 内 `await import('echarts/core').then(...)`
- 一键复用缓存：进 Dialog 时若 `store.frequent` 为空 → 调接口；否则用缓存（≤100ms 命中）

## 8. 不在本原型范围

- 内置食物库（PRD §3.2）
- 拍照识别 / OCR（PRD §3.2 + CLAUDE.md §6.2 显式不做）
- 个性化推荐常量（PRD §3.2）
- 周 / 月营养趋势图（PRD §3.2）
- 智能饮食建议 / AI 分析（PRD §3.2 → 04-future §4）
- 食物过敏 / 忌口标签（PRD §3.2）
- 自定义餐别（PRD §3.2；SNACK 可复用）
- 与消费模块互通（PRD §3.2 → 04-future §2.3 决策 v2.x 评估）
- 营养目标设定（PRD §3.2）

## 9. 反查 spec 章节

| 本文章节 | spec 章节 |
|---|---|
| §1 | `07-diet-design.md §5` API + §5.1 DTO + §7 安全 |
| §2.1 / §3 | `07 §6.3` DietView 布局 |
| §2.2 | `07 §6.3` DietDialog（含一键复用） |
| §2.3 | `07 §6.3` DietDetailView |
| §4 | `07 §6.4` Pinia store actions + groupedByDay getter |
| §5 | `07 §5.1` DietResponse / DietSummary / DietFrequentItem |
| §6 | `07 §5.2` 错误码沿用 |
| §7 | `07 §6.2` 文件拆分 + shared/ 复用 06 决策 |