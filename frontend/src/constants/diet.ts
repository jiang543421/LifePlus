// 饮食模块常量（spec §07-diet-design section 2 + §5 + §6.3）。
//
// 与后端 MealType 枚举字面值 + 中文 label 严格对齐：
// 后端 Jackson 默认按 enum.name() 序列化（MyBatis-Plus IEnum<String>），
// 所以前端 English name 就是传输值；label 仅用于 UI 展示。
//
// 与 expense.ts 同位置选择：放进 constants/，避免 types barrel 膨胀。
// 推荐摄入常量为人群体均值（PRD §5.1），spec §6.3 DietNutritionCard 横向柱图直接引用。

/** 后端 MealType 4 个枚举值的字面量联合。 */
export const MEAL_TYPES = [
  'BREAKFAST',
  'LUNCH',
  'DINNER',
  'SNACK',
] as const;

/** UI 展示用中文 label；与后端 MealType.getLabel() 一致（spec §2 MVP2 硬编码文案）。 */
export const MEAL_LABEL: Readonly<Record<(typeof MEAL_TYPES)[number], string>> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐',
};

/** 列表默认 page size；与后端 DietConstants.DEFAULT_PAGE_SIZE 对齐。 */
export const DIET_PAGE_SIZE = 20;

/** 列表 page size 上限；与后端 DietConstants.MAX_PAGE_SIZE 对齐，
 * 防止前端误传超大 size 一次拉爆 DB。 */
export const DIET_MAX_PAGE_SIZE = 100;

/** 一键复用（frequent）默认窗口（天）；与后端 DietConstants.DEFAULT_FREQUENT_DAYS 对齐。 */
export const DEFAULT_FREQUENT_DAYS = 30;

/** frequent 默认 top N；与后端 DietConstants.DEFAULT_FREQUENT_LIMIT 对齐。 */
export const DEFAULT_FREQUENT_LIMIT = 10;

/** frequent 上限（防御性）；与后端 DietConstants.MAX_FREQUENT_LIMIT 对齐。 */
export const MAX_FREQUENT_LIMIT = 50;

/** 字段长度上限；与后端 DietConstants.MAX_NAME_LEN / MAX_NOTE_LEN 对齐，
 * UI el-input maxlength 用。 */
export const MAX_NAME_LEN = 64;
export const MAX_NOTE_LEN = 200;

// ---- 推荐摄入常量（spec §6.3 / PRD §5.1，人群体均值；DietNutritionCard 柱图用）----

/** 每日推荐热量 kcal；与后端 DietConstants.REC_DAILY_KCAL 对齐。 */
export const REC_DAILY_KCAL = 2000;

/** 每日推荐蛋白质 g；与后端 DietConstants.REC_DAILY_PROTEIN_G 对齐。 */
export const REC_DAILY_PROTEIN_G = 60;

/** 每日推荐碳水 g；与后端 DietConstants.REC_DAILY_CARB_G 对齐。 */
export const REC_DAILY_CARB_G = 300;

/** 每日推荐脂肪 g；与后端 DietConstants.REC_DAILY_FAT_G 对齐。 */
export const REC_DAILY_FAT_G = 65;