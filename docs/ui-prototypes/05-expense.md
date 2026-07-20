# UI 原型 · 05 — ExpenseView

> 版本：v0.1 · 日期：2026-07-20 · 视图：`/expenses` + `/expenses/:id`（v1.2.1）
> 输入：[05-expense-prd.md §3.1 + §4](../prd/05-expense-prd.md) · [../specs/06-expense-design.md](../specs/06-expense-design.md)

---

## 1. 故事 → UI 要素映射

| PRD 出处 | UI 必须呈现 |
|---|---|
| EXP-1 AC1：4 字段录入表单 + 实时校验 | ExpenseDialog 仅 4 字段（金额/分类/备注/发生时间）；`el-form` rules 实时校验 |
| EXP-1 AC2：默认时间 = 当前 - 5 分钟 | Dialog `mounted` 时 `occurredAt = dayjs().subtract(5, 'm')`；用户可改 |
| EXP-1 AC3：成功后立即关弹窗 + 列表新增 + Toast | `submit()` 成功 → `dialogVisible=false` + `fetchList()` 增量 + Toast「已记录 ¥XX」 |
| EXP-2 AC1：当月总支出 + 5 分类环图 + 空态 | ExpenseSummaryCard 大数字 + ECharts 环图；空态显示「暂无记录」 |
| EXP-2 AC2：同比上月 + 同比去年 + 空对比文案 | Card 底部 2 行 delta；上月/去年无数据时显「—」而非 NaN% |
| EXP-2 AC3：月份切换 + URL 同步 + 刷新保留 | Card 顶部 `◀ 2026-07 ▶` 翻月器；路由 query `?year=YYYY&month=MM` |
| EXP-3 AC1：6 选项分类筛选 | Filter 分类下拉「全部/餐饮/购物/交通/订阅/其他」 |
| EXP-3 AC2：选中后列表只显示该分类 | `category` 字段写到 URL query + store.filter |
| EXP-3 AC3：URL 同步 category | 路由 query → store.filter（双绑） |
| EXP-4 AC1：编辑/删除按钮 | ListItem 右侧 `[编辑][删除]` |
| EXP-4 AC2：编辑回显 + 删除二次确认 | 编辑走 Dialog `mode='edit'`；删除走 `ElMessageBox.confirm('确定删除这笔消费？')` |
| EXP-4 AC3：越权 → 1003 + 跳回列表 | 后端 1003 → 拦截器 → Toast「资源不存在」+ router.replace('/expenses') |
| 首页集成 §3.1：消费卡 placeholder=false | HomeView 第 4 卡激活，跳 `/expenses` |
| 安全 §3.1：限流 10/min/userId | 1006 → Toast「操作过于频繁，请稍后再试」 |
| 安全 §3.1：跨用户 1003 | 见 EXP-4 AC3 |

## 2. 桌面 ASCII（≥1024px）

### 2.1 ExpenseView（两栏：左 2/3 列表 + 右 1/3 汇总）

```
┌─────────────────────────────────────────────────────┬──────────────────────────┐
│ [全部分类 ▾] [2026-07-01 ~ 2026-07-31] [+ 新增消费]   │ ┌──────────────────────┐ │
│                                                     │ │   本月支出            │ │
│ ┌─────────────────────────────────────────────────┐ │ │                      │ │
│ │ ▾ 2026-07-15 周三                                │ │ │   ¥ 1,234.56         │ │
│ │   餐饮  ¥35.00  「午餐便当」       [编辑][删除]  │ │ │                      │ │
│ │   交通  ¥12.00  「地铁」           [编辑][删除]  │ │ │   ┌─ 分类环图 ──┐    │ │
│ │   订阅  ¥99.00  「Copilot 月费」   [编辑][删除]  │ │ │   │    MEAL 45%  │    │ │
│ └─────────────────────────────────────────────────┘ │ │   │   ╭─────╮    │    │ │
│                                                     │ │   │  ╱  45% ╲   │    │ │
│ ┌─────────────────────────────────────────────────┐ │ │   │ │  ¥555  │   │    │ │
│ │ ▾ 2026-07-14 周二                                │ │ │   │  ╲     ╱   │    │ │
│ │   购物  ¥128.00 「T恤」           [编辑][删除]  │ │ │   │   ╰───╯    │    │ │
│ │   其他  ¥5.00   「矿泉水」         [编辑][删除]  │ │ │   │  其他: 5%   │    │ │
│ └─────────────────────────────────────────────────┘ │ │   └─────────────┘    │ │
│                                                     │ │                      │ │
│ ┌─────────────────────────────────────────────────┐ │ │   同比上月  +12.3%   │ │
│ │ ▾ 2026-07-13 周一                                │ │ │   同比去年  +5.8%    │ │
│ │   ...                                             │ │ │                      │ │
│ └─────────────────────────────────────────────────┘ │ │   ◀ 2026-07 ▶         │ │
│                                                     │ └──────────────────────┘ │
│            [上一页] 1 / 5 [下一页]                   │                          │
└─────────────────────────────────────────────────────┴──────────────────────────┘
```

布局规则：
- 桌面 `el-row :gutter="16"` + `el-col :xs=24 :md=16 / :xs=24 :md=8`
- 列表按天折叠分组（默认当天展开、昨天折叠）
- 每行 `display: flex` 左 = 分类标签 + 金额 + 备注，右 = 操作按钮组
- 右侧汇总卡 sticky top=80（TopBar 下方）

### 2.2 ExpenseDialog（新增 / 编辑共用）

```
┌────────────────────────────────────┐
│  新增消费                       ✕   │
├────────────────────────────────────┤
│                                    │
│  金额 *  [    35.00    ] ¥         │  ← el-input-number :min="0.01" :precision="2"
│                                    │
│  分类 *  [ 餐饮        ▾]          │  ← el-select, 源 GET /categories
│                                    │
│  备注    [ 午餐便当           ]     │  ← el-input :maxlength="200" show-word-limit
│                                    │
│  发生时间 [ 2026-07-15 12:35 ] 📅  │  ← el-date-picker type=datetime
│           （默认 = 现在 - 5 分钟） │
│                                    │
│           [ 取 消 ]   [ 保 存 ]    │
└────────────────────────────────────┘
```

### 2.3 ExpenseDetailView（/expenses/:id，只读）

```
┌────────────────────────────────────┐
│  消费详情  ‹ 返回列表              │
├────────────────────────────────────┤
│                                    │
│  金额      ¥ 35.00                │  ← 禁用态
│  分类      餐饮                    │  ← 禁用态
│  备注      午餐便当                │  ← 禁用态
│  发生时间  2026-07-15 12:35       │  ← 禁用态
│  创建于    2026-07-15 12:36       │
│                                    │
│  [ 进入编辑 ]    [ 删除 ]           │  ← 删除二次确认
└────────────────────────────────────┘
```

### 2.4 Home 卡激活后 ASCII（更新 04-home §1.1）

```
┌──────────────┐
│ 本月支出      │
│              │
│  ¥1,234.56   │  ← 大数字
│              │
│ ▸ 餐饮 ¥555  │  ← 最近 3 笔
│ ▸ 购物 ¥128  │
│ ▸ 交通 ¥12   │
│              │
│ 查看全部 →   │
└──────────────┘
```

> 04-home.md §1.1 表格同步：「消费概览」行 → placeholder=false，真实数据源 = `/api/v1/expenses/summary?year&month` + `/api/v1/expenses?size=3&from=本月1日`。

## 3. 移动 ASCII（<768px）

```
┌──────────────────────┐
│ TopBar（见 03）       │
├──────────────────────┤
│ [全部分类▾][7月▾][+] │  ← 三按钮单行
│                      │
│ ┌──────────────────┐ │
│ │  本月支出         │ │
│ │  ¥1,234.56       │ │  ← 汇总卡置顶
│ │  同比上月 +12.3%  │ │
│ │  同比去年 +5.8%   │ │
│ └──────────────────┘ │
│                      │
│ ▾ 2026-07-15 周三    │
│  餐饮 ¥35  [编辑][删]│
│  交通 ¥12  [编辑][删]│
│ ▾ 2026-07-14 周二    │
│  购物 ¥128 [编辑][删]│
│ ...                  │
│                      │
│   [上一页] 1/5 [下一页]│
└──────────────────────┘
```

> 移动端把汇总卡放顶部（"先看数字"下意识）；操作按钮简化为图标（✏️🗑️）+ 文字隐藏。完整桌面版两栏在 <1024px 自动变单列堆叠（沿用 SPEC §6.3）。

## 4. 状态机（List 视角）

```
        ┌──────────┐
   →    │ LOADING  │ ← 进入页面 / 切换筛选 / 翻页
        └────┬─────┘
             │ store.fetchList() 完成
   ┌─────────┼──────────┐
   ▼         ▼          ▼
   空数据    有数据     接口失败
   │         │          │
   ▼         ▼          ▼
  EmptyState 列表+汇总  ElMessage
  +「+新增」  正常渲染   "加载失败，稍后重试"
                          │
                          ▼
                     ┌──────────┐
                     │  EMPTY   │ ← 「重试」按钮触发 fetchList()
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
             │ submit() 成功 → store.create/update → fetchList
        ┌────▼─────┐
        │ SUBMITTING│ ← 提交中，按钮 disabled + spinner
        └────┬─────┘
             │ 完成
             ▼
           CLOSED
```

DetailView（路由 /expenses/:id）：

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

## 5. 字段清单

| 展示 | 来源 | 备注 |
|---|---|---|
| 列表金额 | `GET /api/v1/expenses?size=20` → `items[].amount` | 服务端 BigDecimal → 字符串；前端 `formatAmount()` |
| 分类中文 | `constants/expense.ts` `CATEGORY_LABEL[category]` | 与后端 enum 双侧对齐 |
| 备注 | `items[].note` | 截断显示 ≤ 20 字符 + `…`；hover 完整 |
| 发生时间 | `items[].occurredAt` | dayjs `MM-DD HH:mm`；跨年才显示年份 |
| 当月总支出 | `GET /api/v1/expenses/summary?year&month` → `totalAmount` | 同上 |
| 环图 | `summary.categoryBreakdown` | 5 段；总和 0 时不渲染 |
| 同比上月 | `summary.monthOverMonthDelta` | null → "—" |
| 同比去年 | `summary.yearOverYearDelta` | null → "—" |
| 分类下拉 | `GET /api/v1/expenses/categories` | 进页面即拉，缓存到 store |

## 6. 错误码 → 文案映射

| code | 触发场景 | UI 文案 | 行为 |
|---|---|---|---|
| 1001 | 金额 ≤ 0 / 分类不在枚举 / 备注 > 200 | 字段下红字 | Dialog 字段定位 |
| 1002 | 未登录 / token 失效 | "请重新登录" | 跳 `/login?redirect=` |
| 1003 | 跨用户访问 `{id}` | "资源不存在" | 跳回 `/expenses` + Toast |
| 1004 | 资源不存在 / 已软删 | "该笔消费已被删除" | 跳回 `/expenses` + Toast |
| 1006 | 写端点 10/min/userId 超限 | "操作过于频繁，请稍后再试" | Dialog 关闭 + 不更新列表 |

## 7. 与编码落地关联

- 路由：`router/index.ts` 加 `/expenses` + `/expenses/:id`（沿用 04-frontend §4）
- Store：`stores/expense.ts` state = `{list, filter, summary, page, loading, dialogVisible, dialogMode, currentItem}`；actions = `fetchList / fetchSummary / create / update / remove / openDialog / closeDialog`
- 组件映射：列表组 = `el-collapse`；筛选条 = `el-select` + `el-date-picker daterange` + `el-button`；Dialog = `el-dialog` + `el-form` + `el-input-number` + `el-select` + `el-date-picker datetime`；汇总卡 = `<div class="summary-card">` + ECharts `import('echarts/core').then(...)`
- 文件拆分：`ExpenseView.vue`（壳）/`ExpenseList.vue` / `ExpenseListItem.vue` / `ExpenseSummaryCard.vue` / `ExpenseDialog.vue` / `ExpenseDetailView.vue`
- ECharts 按需：`ExpenseSummaryCard.vue` 内 `const echarts = await import('echarts/core')`，避免污染主 bundle

## 8. 不在本原型范围

- 预算 / 超预算提醒（PRD §3.2）
- 循环账单（PRD §3.2）
- 多账户 / 多币种（PRD §3.2）
- 自定义分类（PRD §3.2）
- 导入 / 导出（PRD §3.2）
- 跨年热力图 / 年度趋势（PRD §3.2）
- AI 趋势洞察（PRD §3.2 → 04-future §4）

## 9. 反查 spec 章节

| 本文章节 | spec 章节 |
|---|---|
| §1 | `06-expense-design.md §5` API + §5.1 DTO + §7 安全 |
| §2.1 / §3 | `06 §6.3` ExpenseView 布局 |
| §2.2 | `06 §6.3` ExpenseDialog |
| §2.3 | `06 §6.3` ExpenseDetailView |
| §4 | `06 §6.4` Pinia store actions |
| §5 | `06 §5.1` ExpenseResponse / ExpenseSummary / CategoryItem |
| §6 | `06 §5.2` 错误码沿用 |