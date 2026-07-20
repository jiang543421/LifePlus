# 06 — Expense（消费模块设计 v1.2.1）

> 本文件为 LifePulse v1.2 设计规格第 1 部分（消费模块）。对应 issue：2026-07-18-mvp2-placeholder-modules.md。
> 编码消费模块时单独加载本文，加载本文件时无需同时加载其他 sub-spec。
>
> **索引**：[00-overview.md](./00-overview.md) · [01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md) · [07-diet-design](./07-diet-design.md)

---

## 1. 设计原则

- **范围 v1.2.1**：单笔 + 预置分类；不做预算、不做循环账单、不做多账户、不做导入导出（PRD §6.2 / `docs/prd/04-future-prd.md` §2.2）
- **数据隔离**：每条 `t_expense` 强制带 `user_id`，所有查询按 `userId` 过滤（CLAUDE.md §7.2）
- **金额精度**：`DECIMAL(12,2)`，应用层 `BigDecimal`；前端 `el-input-number` 限定 2 位小数；禁止负数
- **时区**：DB `DATETIME(3)`，应用层 UTC 存储 / `Asia/Shanghai` 展示（沿用 02-database §1）
- **软删**：`@TableLogic deleted=0`（沿用 02-database §1）
- **Flyway**：消费迁移编号 V4（V1-V3 已用于 user/refresh_token/task/plan）
- **首页入口**：发布时 `HomeView` 中 `expense` 卡 `placeholder: false`，跳 `/expenses`

## 2. 表结构

### 2.1 `t_expense`

| 字段 | 类型 | 约束 | 索引 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL, FK 语义 → t_user.id | 联合 (user_id, occurred_at) + (user_id, category, occurred_at) |
| amount | DECIMAL(12,2) | NOT NULL, CHECK (amount > 0) | |
| category | VARCHAR(16) | NOT NULL, CHECK (IN enum) | |
| note | VARCHAR(200) | NULL | |
| occurred_at | DATETIME(3) | NOT NULL | 联合索引见上 |
| created_at | DATETIME(3) | NOT NULL | |
| updated_at | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | NOT NULL DEFAULT 0 | @TableLogic |

枚举字面值（应用层 Java enum + TS 字面量双侧对齐）：

```java
enum ExpenseCategory { MEAL, SHOPPING, TRANSPORT, SUBSCRIPTION, OTHER }
```

- 后端 `ExpenseCategory` Java enum + `MyBatis-Plus IEnum` 自动 String 转换
- SQL CHECK：`category IN ('MEAL','SHOPPING','TRANSPORT','SUBSCRIPTION','OTHER')`
- 前端常量类 `frontend/src/constants/expense.ts` 与后端同构
- MVP2 暂硬编码中文文案（`餐饮 / 购物 / 交通 / 订阅 / 其他`），i18n 字典后期接入

> **设计决策**：分类用 enum 而非字典表。预置清单不会变；后期要支持用户自定义分类时再迁移到 `t_dict_category`（与 MVP1 `t_task.tag` 同思路，02-database §2.2）。

## 3. 索引清单

| 索引 | 表 | 列 | 类型 | 用途 |
|---|---|---|---|---|
| `PRIMARY` | t_expense | (id) | 主键 | 主键 |
| `idx_user_occurred` | t_expense | (user_id, occurred_at DESC) | 普通 | 列表按时间倒序 |
| `idx_user_category` | t_expense | (user_id, category, occurred_at) | 普通 | 月分类汇总 |

## 4. Flyway 迁移

```
V4__init_expense.sql   -- 本模块
```

```sql
CREATE TABLE t_expense (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  user_id      BIGINT        NOT NULL,
  amount       DECIMAL(12,2) NOT NULL,
  category     VARCHAR(16)   NOT NULL,
  note         VARCHAR(200)  NULL,
  occurred_at  DATETIME(3)   NOT NULL,
  created_at   DATETIME(3)   NOT NULL,
  updated_at   DATETIME(3)   NOT NULL,
  deleted      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_occurred (user_id, occurred_at),
  KEY idx_user_category (user_id, category, occurred_at),
  CONSTRAINT chk_expense_amount_pos CHECK (amount > 0),
  CONSTRAINT chk_expense_category CHECK (category IN ('MEAL','SHOPPING','TRANSPORT','SUBSCRIPTION','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

## 5. API 端点

> 全局约定沿用 03-api-auth：前缀 `/api/v1`，返回信封 `{code, message, data, traceId}`，错误码 1001/1002/1003/1004/1006 沿用。

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/v1/expenses` | 列表 + 筛选：`category, from, to, page, size`（默认 size=20，max=100） |
| GET | `/api/v1/expenses/{id}` | 单个；越权 1003；不存在 1004 |
| POST | `/api/v1/expenses` | 新增；Body：`{amount, category, note?, occurredAt}` |
| PATCH | `/api/v1/expenses/{id}` | 修改任意字段；空字段不更新（与 MVP1 task PATCH 一致） |
| DELETE | `/api/v1/expenses/{id}` | 软删 |
| GET | `/api/v1/expenses/summary?year&month` | 当月汇总：总支出 + 分类占比 + 同比上月 / 同比去年同月 |
| GET | `/api/v1/expenses/categories` | 预置分类清单（前端表单下拉数据源） |

### 5.1 DTO 字段

```java
record ExpenseResponse(Long id, BigDecimal amount, String category,
                        String note, OffsetDateTime occurredAt,
                        OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

record CreateExpenseRequest(
    @NotNull @DecimalMin(value="0.01") BigDecimal amount,
    @NotBlank String category,
    @Size(max=200) String note,
    @NotNull OffsetDateTime occurredAt) {}

record UpdateExpenseRequest(
    @DecimalMin("0.01") BigDecimal amount,
    String category,
    @Size(max=200) String note,
    OffsetDateTime occurredAt) {}

record ExpenseSummary(BigDecimal totalAmount,
                       Map<String, BigDecimal> categoryBreakdown,
                       BigDecimal monthOverMonthDelta,    // null 表示上月无数据
                       BigDecimal yearOverYearDelta) {}    // null 表示去年同月无数据

record CategoryItem(String code, String label) {}
```

### 5.2 错误码沿用

| 场景 | code |
|---|---|
| DTO 校验失败（amount ≤ 0、category 不在枚举、note 超长） | 1001 |
| 未登录 / token 失效 | 1002 |
| 跨用户访问 `{id}` | 1003 |
| 资源不存在 / 已软删 | 1004 |
| 写端点 10/min/userId 超限 | 1006 |

## 6. 前端结构

### 6.1 新增路由

| 路径 | 视图 | 守卫 |
|---|---|---|
| `/expenses` | ExpenseView | 需登录 |
| `/expenses/:id` | ExpenseDetailView | 需登录 |

### 6.2 新增 / 修改文件

```
frontend/src/
├─ api/
│  └─ expense.ts                        (新)
├─ stores/
│  └─ expense.ts                        (新)
├─ views/
│  ├─ ExpenseView.vue                   (新)
│  └─ ExpenseDetailView.vue             (新)
├─ components/
│  ├─ ExpenseSummaryCard.vue            (新)
│  ├─ ExpenseList.vue                   (新)
│  ├─ ExpenseDialog.vue                 (新 — 新增 / 编辑共用)
│  └─ shared/
│     ├─ EmptyState.vue                 (新 — 抽出复用)
│     └─ LoadingState.vue               (新 — 抽出复用)
├─ constants/
│  └─ expense.ts                        (新 — 分类清单 + 中文文案)
├─ types/
│  └─ expense.ts                        (新)
└─ utils/
   └─ number.ts                         (新 — 金额格式化)
```

### 6.3 视图布局

**ExpenseView**：桌面 ≥1024px 两栏（左侧主列表 2/3，右侧月汇总卡 1/3）；<1024px 单列堆叠。

```
┌─────────────────────────────────────┬───────────────────┐
│  [筛选条：分类 | 月份范围 | +新增]    │   当月总支出 ¥XXX │
│                                     │   ┌─ 分类环图 ─┐  │
│  ▾ 2026-07-15 周三                  │   │ MEAL 45%   │  │
│   餐饮  ¥35  「午餐便当」[编辑][删] │   │ SHOPP 25%  │  │
│   交通  ¥12  「地铁」[编辑][删]      │   │ ...        │  │
│                                     │   └────────────┘  │
│  ▾ 2026-07-14 周二                  │   同比上月 +12%   │
│   ...                               │   同比去年 +5%    │
│                                     │                   │
│  [分页]                             │                   │
└─────────────────────────────────────┴───────────────────┘
```

**ExpenseDialog**：单 `el-dialog` 同时承载"新增"和"编辑"两种模式（mode prop）；含金额（el-input-number）/分类（el-select，源 `/api/v1/expenses/categories`）/备注/发生时间（el-date-picker type=datetime）。

**ExpenseDetailView**：复用 Dialog；只读模式切换；底部「删除」按钮二次确认。

### 6.4 Pinia store

```ts
state: {
  list: ExpenseResponse[]
  filter: { category: string | null; from: string | null; to: string | null }
  summary: ExpenseSummary | null
  page: { current: number; size: number; total: number }
  loading: boolean
}
getters: { hasData: (s) => s.list.length > 0 }
actions: {
  fetchList()           // GET /expenses，叠加 filter + page
  fetchSummary(year, month)
  create(req)
  update(id, req)
  remove(id)
  resetFilter()
}
```

> **设计决策**：store 不缓存；进入页面即拉（与 MVP1 task / plan store 一致，04-frontend §5）。

## 7. 安全细节

- **越权**：`ExpenseController` 每个 `{id}` 端点先 `WHERE user_id = ?` 再 `LIMIT 1`；越权抛 `BusinessException(1003)`
- **写限流**：`POST / PATCH / DELETE` 走 `RateLimiter.hit("lp:rl:expense:write:" + userId, 10, Duration.ofMinutes(1))`；阈值 10/min 比 settings 略宽（日常使用频次高）
- **金额校验**：DTO `@DecimalMin("0.01")`；前端 `el-input-number :min="0.01" :precision="2"`
- **SQL**：MyBatis-Plus 参数绑定，禁字符串拼接（沿用 03-api-auth §6）
- **输入校验**：所有 DTO `@Valid`，错误统一 1001（沿用 03-api-auth §6）
- **CORS**：沿用 dev `http://localhost:5173`，prod 白名单（沿用 03-api-auth §6）
- **错误日志**：打 `userId` + `expenseId` + `category`；**禁止**打 `note` 全文（CLAUDE.md §7.3）

## 8. 测试策略

### 8.1 覆盖率门槛（沿用 05-nfr-testing §5.1）

| 层 | 工具 | 阈值 |
|---|---|---|
| ExpenseService 单测 | JUnit 5 + Mockito | 行覆盖 ≥ 80% |
| ExpenseController 切片 | @WebMvcTest | 鉴权 / 校验 / 越权 路径 100% |
| ExpenseIT | @SpringBootTest + Testcontainers (MySQL) | 关键流 |
| expense store 单测 | Vitest | 行覆盖 ≥ 70% |
| ExpenseView 组件 | Vitest | 关键组件 ≥ 80% |
| 端到端 | Playwright | 关键流 |

### 8.2 必备测试用例

**Service 单测（≥15 cases）**：
- happy path：list / get / create / update / delete / summary / categories 全部正常路径
- amount ≤ 0 / category 不在 enum / note 超长 → 1001（×3）
- 跨用户访问 → 1003（×3：get / update / delete）
- 不存在 → 1004（×3）
- 限流超 10/min → 1006（×1）
- 月汇总聚合正确性：构造 5 笔不同分类，断言 totalAmount + 占比 + 同比（×1）

**Controller 切片（≥10 cases）**：
- 未带 token / token 失效 → 1002（×2）
- DTO 校验失败 → 1001（×2）
- 越权 → 1003（×2）
- 写端点限流 → 1006（×2）
- GET categories 返回 5 项（×1）
- summary 参数缺 month → 1001（×1）

**IT（≥6 cases）**：
- register → login → create-expense → list → summary → delete → list 闭环
- 跨用户：A 创建 → B token GET → 1003
- 软删幂等：delete 后再 GET → 1004
- 月汇总 SQL 聚合正确性（vs 手算）

**前端单测**：
- store ≥ 6 cases（fetchList / filter / summary 合并 / 错误处理）
- ExpenseView ≥ 8 cases（初始加载 / 筛选 / 切换月份 / 空态 / 加载态 / 错误态 / 录入弹窗 / 删除二次确认）
- ExpenseDialog ≥ 4 cases（新增校验 / 编辑回显 / 取消不提交 / 提交成功）

**E2E（≥3 cases）**：
- 新增一笔餐饮 → 列表出现 → 月汇总数字变化
- 编辑金额 → 列表更新 → 月汇总重算
- 注销后访问 `/expenses` → 跳 /login

### 8.3 流水线闸门

- `mvn verify` 通过；JaCoCo 行覆盖 < 80% 失败
- `pnpm run lint && pnpm run test` 通过
- `pnpm exec playwright test expenses.spec.ts` 通过

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| ECharts 包体（~280KB gzip）致首页加载 P95 超 2.0s | 中 | 首页不直接引入 ECharts；按需在 `ExpenseView` 内 `import()` 动态加载；路由级 code-split |
| DECIMAL(12,2) 在前端 JS Number 失精 | 低 | 前端展示用 `decimal.js` 或字符串回显，服务端始终用 `BigDecimal` 计算 |
| 月汇总 SQL 性能 | 低 | `idx_user_category` 覆盖；LIMIT 5 行分组 |
| 限流误伤（用户高频使用） | 低 | 阈值 10/min 较 settings 宽；前端友好提示"操作过于频繁，请稍后再试" |
| 分类枚举后期想扩展 | 低 | 迁移路径明确（enum → 字典表），不在本模块范围 |

---

> **下一步**：本文 + 07-diet-design.md 一起通过后，由 writing-plans 技能生成 v1.2.1 实施计划。