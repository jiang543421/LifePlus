# 07 — Diet（饮食模块设计 v1.2.2）

> 本文件为 LifePulse v1.2 设计规格第 2 部分（饮食模块）。对应 issue：2026-07-18-mvp2-placeholder-modules.md。
> 编码饮食模块时单独加载本文，加载本文件时无需同时加载其他 sub-spec。
>
> **索引**：[00-overview.md](./00-overview.md) · [01-architecture](./01-architecture.md) · [02-database](./02-database.md) · [03-api-auth](./03-api-auth.md) · [04-frontend](./04-frontend.md) · [05-nfr-testing](./05-nfr-testing.md) · [06-expense-design](./06-expense-design.md)

---

## 1. 设计原则

- **范围 v1.2.2**：自由文本 + 手填营养；不引入食物库、不做 OCR、不与消费互通（PRD §6.2 / `docs/prd/04-future-prd.md` §2.3）
- **数据隔离**：每条 `t_diet` 强制带 `user_id`，所有查询按 `userId` 过滤（CLAUDE.md §7.2）
- **颗粒度**：单笔 = 一餐的一项（如午餐米饭 + 西兰花算两笔）；按餐别聚合
- **时区 / 软删**：沿用 02-database §1
- **Flyway**：饮食迁移编号 V5（V4 是消费）
- **与消费的关系**：完全独立——两表不共享外键、不共享 API、不共享前端组件（详见 `docs/issues/2026-07-18-mvp2-placeholder-modules.md` 决策）
- **首页入口**：发布时 `HomeView` 中 `diet` 卡 `placeholder: false`，跳 `/diets`

## 2. 表结构

### 2.1 `t_diet`

| 字段 | 类型 | 约束 | 索引 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL, FK 语义 → t_user.id | 联合 (user_id, occurred_at) + (user_id, meal_type, occurred_at) |
| meal_type | VARCHAR(8) | NOT NULL, CHECK (IN enum) | |
| name | VARCHAR(64) | NOT NULL | |
| kcal | DECIMAL(7,2) | NOT NULL DEFAULT 0, CHECK (kcal >= 0) | |
| protein_g | DECIMAL(6,2) | NOT NULL DEFAULT 0, CHECK (>= 0) | |
| carb_g | DECIMAL(6,2) | NOT NULL DEFAULT 0, CHECK (>= 0) | |
| fat_g | DECIMAL(6,2) | NOT NULL DEFAULT 0, CHECK (>= 0) | |
| occurred_at | DATETIME(3) | NOT NULL | 联合索引见上 |
| note | VARCHAR(200) | NULL | |
| created_at | DATETIME(3) | NOT NULL | |
| updated_at | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | NOT NULL DEFAULT 0 | @TableLogic |

枚举字面值（应用层 Java enum + TS 字面量双侧对齐）：

```java
enum MealType { BREAKFAST, LUNCH, DINNER, SNACK }
```

- 后端 `MealType` Java enum + `MyBatis-Plus IEnum` 自动 String 转换
- SQL CHECK：`meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')`
- 前端常量类 `frontend/src/constants/diet.ts` 与后端同构
- MVP2 暂硬编码中文文案（`早餐 / 午餐 / 晚餐 / 加餐`）

> **设计决策**：常用项不独立表（决策见 brainstorming §approach）。录入 UI 通过 `GET /api/v1/diets/frequent?from=&to=&limit=10`（近 30 天）聚合，按出现频次降序提示「点这里一键复用」。

## 3. 索引清单

| 索引 | 表 | 列 | 类型 | 用途 |
|---|---|---|---|---|
| `PRIMARY` | t_diet | (id) | 主键 | 主键 |
| `idx_user_occurred` | t_diet | (user_id, occurred_at DESC) | 普通 | 列表按时间倒序 |
| `idx_user_meal` | t_diet | (user_id, meal_type, occurred_at) | 普通 | 按餐别分组查询 |

## 4. Flyway 迁移

```
V5__init_diet.sql   -- 本模块
```

```sql
CREATE TABLE t_diet (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  meal_type    VARCHAR(8)   NOT NULL,
  name         VARCHAR(64)  NOT NULL,
  kcal         DECIMAL(7,2) NOT NULL DEFAULT 0,
  protein_g    DECIMAL(6,2) NOT NULL DEFAULT 0,
  carb_g       DECIMAL(6,2) NOT NULL DEFAULT 0,
  fat_g        DECIMAL(6,2) NOT NULL DEFAULT 0,
  occurred_at  DATETIME(3)  NOT NULL,
  note         VARCHAR(200) NULL,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_occurred (user_id, occurred_at),
  KEY idx_user_meal (user_id, meal_type, occurred_at),
  CONSTRAINT chk_diet_meal CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')),
  CONSTRAINT chk_diet_kcal_nonneg CHECK (kcal >= 0),
  CONSTRAINT chk_diet_protein_nonneg CHECK (protein_g >= 0),
  CONSTRAINT chk_diet_carb_nonneg CHECK (carb_g >= 0),
  CONSTRAINT chk_diet_fat_nonneg CHECK (fat_g >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

## 5. API 端点

> 全局约定沿用 03-api-auth；返回信封 / 错误码同 06-expense-design §5.2。

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/v1/diets` | 列表 + 筛选：`mealType, from, to, page, size` |
| GET | `/api/v1/diets/{id}` | 单个 |
| POST | `/api/v1/diets` | 新增；Body：`{mealType, name, kcal, proteinG, carbG, fatG, note?, occurredAt}` |
| PATCH | `/api/v1/diets/{id}` | 修改任意字段 |
| DELETE | `/api/v1/diets/{id}` | 软删 |
| GET | `/api/v1/diets/summary?date=YYYY-MM-DD` | 当日 4 项营养汇总（kcal/protein/carb/fat）+ 与昨日 / 上周同日差值 |
| GET | `/api/v1/diets/frequent?from=&to=&limit=10` | 高频名称聚合（默认近 30 天 / top 10），录入弹窗「一键复用」数据源 |

### 5.1 DTO 字段

```java
record DietResponse(Long id, String mealType, String name,
                     BigDecimal kcal, BigDecimal proteinG,
                     BigDecimal carbG, BigDecimal fatG,
                     String note, OffsetDateTime occurredAt,
                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

record CreateDietRequest(
    @NotBlank String mealType,
    @NotBlank @Size(max=64) String name,
    @NotNull @DecimalMin("0.0") BigDecimal kcal,
    @NotNull @DecimalMin("0.0") BigDecimal proteinG,
    @NotNull @DecimalMin("0.0") BigDecimal carbG,
    @NotNull @DecimalMin("0.0") BigDecimal fatG,
    @Size(max=200) String note,
    @NotNull OffsetDateTime occurredAt) {}

record UpdateDietRequest(
    String mealType,
    @Size(max=64) String name,
    @DecimalMin("0.0") BigDecimal kcal,
    @DecimalMin("0.0") BigDecimal proteinG,
    @DecimalMin("0.0") BigDecimal carbG,
    @DecimalMin("0.0") BigDecimal fatG,
    @Size(max=200) String note,
    OffsetDateTime occurredAt) {}

record DietSummary(BigDecimal kcal, BigDecimal proteinG,
                    BigDecimal carbG, BigDecimal fatG,
                    BigDecimal kcalDeltaYesterday,    // null 表示昨日无数据
                    BigDecimal kcalDeltaLastWeek) {}    // null 表示上周同日无数据

record DietFrequentItem(String name, BigDecimal avgKcal,
                         BigDecimal avgProteinG, BigDecimal avgCarbG,
                         BigDecimal avgFatG, Integer hitCount) {}
```

### 5.2 错误码沿用

| 场景 | code |
|---|---|
| DTO 校验失败 | 1001 |
| 未登录 / token 失效 | 1002 |
| 跨用户访问 `{id}` | 1003 |
| 资源不存在 / 已软删 | 1004 |
| 写端点 10/min/userId 超限 | 1006 |

## 6. 前端结构

### 6.1 新增路由

| 路径 | 视图 | 守卫 |
|---|---|---|
| `/diets` | DietView | 需登录 |
| `/diets/:id` | DietDetailView | 需登录 |

### 6.2 新增 / 修改文件

```
frontend/src/
├─ api/
│  └─ diet.ts                           (新)
├─ stores/
│  └─ diet.ts                           (新)
├─ views/
│  ├─ DietView.vue                      (新)
│  └─ DietDetailView.vue                (新)
├─ components/
│  ├─ DietDayGroup.vue                  (新 — 单日 4 餐分组)
│  ├─ DietNutritionCard.vue             (新 — 4 项营养 + 横向柱图)
│  ├─ DietDialog.vue                    (新 — 新增 / 编辑 / 含「一键复用」下拉)
│  └─ shared/                           (沿用 06)
├─ constants/
│  └─ diet.ts                           (新 — 餐别清单 + 中文文案 + 推荐摄入常量)
├─ types/
│  └─ diet.ts                           (新)
```

> **设计决策**：复用 06-expense-design §6.2 抽出的 `EmptyState.vue` / `LoadingState.vue`；不重复实现。

### 6.3 视图布局

**DietView**：桌面 ≥1024px 两栏（左侧按天分组列表 2/3，右侧当日营养卡 1/3）；<1024px 单列堆叠。

```
┌─────────────────────────────────────┬───────────────────┐
│  [筛选条：日期 | 餐别 | +新增]       │   当日营养        │
│                                     │   ┌────────────┐  │
│  ▾ 2026-07-15 周三                  │   │ 1680 / 2000│  │
│   ▸ 早餐                            │   │  kcal ▓▓▓▓░ │  │
│     燕麦 100g    380kcal            │   │ 55 / 60 g  │  │
│   ▸ 午餐                            │   │  protein ▓▓│  │
│     米饭 1碗     230kcal            │   │ 220 / 300  │  │
│     西兰花       55kcal             │   │  carb  ▓▓▓░ │  │
│   ▸ 晚餐                            │   │ 50 / 65    │  │
│     ...                             │   │  fat   ▓▓░░ │  │
│   ▸ 加餐                            │   └────────────┘  │
│     ...                             │   vs 昨日 -120    │
│                                     │   vs 上周同日 -80 │
│  [分页]                             │                   │
└─────────────────────────────────────┴───────────────────┘
```

**DietDayGroup**：单日 4 餐分组（早 / 午 / 晚 / 加餐）；每餐内卡片可折叠；每餐底部子合计（kcal）。

**DietDialog**：单 `el-dialog` 同时承载"新增 / 编辑 / 复用"；「一键复用」下拉源 `GET /diets/frequent`，点击填入 name + avgKcal + avgProtein + avgCarb + avgFat。

**DietNutritionCard**：4 项营养数字 + ECharts 横向柱图（vs 推荐摄入常量 `REC_DAILY_KCAL=2000` / `REC_DAILY_PROTEIN_G=60` / `REC_DAILY_CARB_G=300` / `REC_DAILY_FAT_G=65`，常量定义于 `constants/diet.ts`）。

### 6.4 Pinia store

```ts
state: {
  list: DietResponse[]
  filter: { date: string | null; mealType: string | null }
  summary: DietSummary | null
  frequent: DietFrequentItem[]
  page: { current: number; size: number; total: number }
  loading: boolean
}
getters: {
  groupedByDay: (s) => groupBy(s.list, d => d.occurredAt.slice(0, 10))
}
actions: {
  fetchList()
  fetchSummary(date)
  fetchFrequent(from, to)
  create(req)
  update(id, req)
  remove(id)
  resetFilter()
}
```

> 沿用 06-expense-design §6.4：store 不缓存。

## 7. 安全细节

- **越权**：`DietController` 每个 `{id}` 端点先 `WHERE user_id = ?`；越权抛 1003
- **写限流**：`POST / PATCH / DELETE` 走 `RateLimiter.hit("lp:rl:diet:write:" + userId, 10, Duration.ofMinutes(1))`；阈值与消费对齐
- **营养字段非负**：SQL CHECK + DTO `@DecimalMin("0.0")`；前端 `el-input-number :min="0"`
- **名称长度**：DTO `@Size(max=64)`；前端 el-input maxlength=64
- **SQL / 输入校验 / CORS / 错误日志**：沿用 06-expense-design §7

## 8. 测试策略

### 8.1 覆盖率门槛

沿用 05-nfr-testing §5.1（与消费模块同阈值）。

### 8.2 必备测试用例

**Service 单测（≥15 cases）**：
- happy path：list / get / create / update / delete / summary / frequent 全部正常路径
- mealType 不在 enum / name 空 / 营养字段负数 → 1001（×4）
- 跨用户访问 → 1003（×3）
- 不存在 → 1004（×3）
- 限流超 10/min → 1006（×1）
- 当日 summary 聚合：构造早午晚各一笔，断言总 kcal = sum（×1）
- frequent 聚合：构造 5 笔相同 name + 3 笔不同 name，断言 top 1 是高频 name（×1）

**Controller 切片（≥10 cases）**：同消费结构（鉴权 / 校验 / 越权 / 限流 / summary 参数 / frequent 参数）

**IT（≥6 cases）**：
- register → login → create-diet → list → summary → delete → list 闭环
- 跨用户拒绝
- 软删幂等
- summary 聚合正确性（vs 手算）
- frequent 时间窗口边界（from = to + 1 day → 空集）

**前端单测**：
- store ≥ 6 cases（fetchList / groupedByDay getter / summary 合并 / 错误处理）
- DietView ≥ 8 cases（初始加载 / 按天分组渲染 / 切换日期 / 餐别筛选 / 空态 / 加载态 / 一键复用 / 删除）
- DietDialog ≥ 4 cases（新增校验 / 编辑回显 / 一键复用填值 / 取消不提交）

**E2E（≥3 cases）**：
- 录入早餐 + 午餐 → 营养卡显示当日合计
- 复用功能：点同一名称 → 营养数字增加
- 切换日期 → 营养卡数字变化

### 8.3 流水线闸门

同 06-expense-design §8.3。

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 营养手填用户嫌麻烦 → 数据稀疏 | 中 | UI 提供「一键复用」+「近 30 天 top 10」快捷入口；summary 接受空集返回 0 不报错 |
| 推荐摄入常量（2000 / 60 / 300 / 65）非个性化 | 低 | MVP2 用人群均值；后期 v2.x 可加用户级 profile 字段 |
| 餐别枚举与一日多餐习惯冲突（如下午茶 + 宵夜） | 低 | SNACK 餐别可重复使用；不在本模块扩展 |
| frequent 接口 N+1（聚合全表） | 低 | `idx_user_occurred` 覆盖；默认 30 天窗口 + LIMIT 10 |
| ECharts 包体（与 06 共享） | 中 | 同 06 §9 缓解：按需 `import()` |

---

> **下一步**：本文 + 06-expense-design.md 一起通过后，由 writing-plans 技能生成 v1.2.2 实施计划（v1.2.1 = 消费 串行 先做）。