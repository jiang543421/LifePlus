// 首页仪表盘（spec §04 §4）卡片定义。
//
// 与 PASSWORD_RULES / TaskFilter 等保持同样的不可变模式：Object.freeze +
// ReadonlyArray。HomeView / ModuleCard / PlaceholderCard 共用这一份源。

/**
 * 卡片渲染类型。
 *
 * - `module`：跳转路由（to 必填）。
 * - `placeholder`：无路由，点击触发「即将上线」toast（to 留空）。
 */
export type HomeCardKind = 'module' | 'placeholder';

/**
 * Element Plus 内置 icon 组件名。
 *
 * 注意：保留为字符串字面量可以让卡片数据保持可序列化
 * （便于未来做 i18n / 后端拉取），渲染端用 `<component :is="...">` 动态加载。
 */
export type HomeCardIcon =
  | 'List'
  | 'Calendar'
  | 'EditPen'
  | 'Wallet'
  | 'KnifeFork'
  | 'DataAnalysis';

/** 单张首页卡片（不可变）。 */
export interface HomeCard {
  readonly key: string;
  readonly title: string;
  readonly icon: HomeCardIcon;
  readonly kind: HomeCardKind;
  /** 当 kind === 'module' 时为跳转路由；placeholder 卡无此字段。 */
  readonly to?: string;
}

/**
 * 6 张固定卡片（spec §04 §4）：
 * 任务 / 计划 → 真实模块入口；
 * 日报 / 消费 / 饮食 / AI 分析 → 占位（Phase 5+ 才实现）。
 *
 * `as const` + `Object.freeze` 双重锁定，避免任何模块 mutation（CLAUDE.md §4.1）。
 * 顺序锁定：模块卡在前、占位卡在后，便于响应式网格断点变化时视觉稳定。
 */
export const HOME_CARDS: ReadonlyArray<HomeCard> = Object.freeze([
  { key: 'task', title: '任务', icon: 'List', kind: 'module', to: '/tasks' },
  { key: 'plan', title: '计划', icon: 'Calendar', kind: 'module', to: '/plans' },
  { key: 'daily', title: '日报', icon: 'EditPen', kind: 'placeholder' },
  { key: 'expense', title: '消费', icon: 'Wallet', kind: 'placeholder' },
  { key: 'diet', title: '饮食', icon: 'KnifeFork', kind: 'placeholder' },
  { key: 'ai', title: 'AI 分析', icon: 'DataAnalysis', kind: 'placeholder' },
] as const);
