// 消费模块常量（spec §06-expense section 2）。
//
// 与后端 ExpenseCategory 枚举字面值 + 中文 label 严格对齐：
// 后端 Jackson 默认按 enum.name() 序列化（见 T8 IT 输出 "category":"MEAL"），
// 所以前端 English name 就是传输值；label 仅用于 UI 展示。
//
// 与已有 TaskStatus / PlanAllDay 等"类型 + 常量同文件"模式不同：
// 消费模块把 UI 维度（label）也带进来了，需要单独文件否则类型 barrel 会膨胀。
// 与 auth.ts 的 PASSWORD_RULES / AuthErrorCode 同位置选择：放进 constants/。

/** 后端 ExpenseCategory 5 个枚举值的字面量联合。 */
export const EXPENSE_CATEGORIES = [
  'MEAL',
  'SHOPPING',
  'TRANSPORT',
  'SUBSCRIPTION',
  'OTHER',
] as const;

export type ExpenseCategory = (typeof EXPENSE_CATEGORIES)[number];

/** UI 展示用中文 label；与后端 ExpenseCategory.getLabel() 一致。 */
export const CATEGORY_LABEL: Readonly<Record<ExpenseCategory, string>> = {
  MEAL: '餐饮',
  SHOPPING: '购物',
  TRANSPORT: '交通',
  SUBSCRIPTION: '订阅',
  OTHER: '其他',
};

/** 列表默认 page size；与 ExpenseConstants.DEFAULT_PAGE_SIZE 对齐。 */
export const EXPENSE_PAGE_SIZE = 20;

/** 列表 page size 上限；与 ExpenseConstants.MAX_PAGE_SIZE 对齐，
 * 防止前端误传超大 size 一次拉爆 DB。 */
export const EXPENSE_MAX_PAGE_SIZE = 100;