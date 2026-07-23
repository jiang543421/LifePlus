## 6. 设计令牌

| 元素 | Token | 用途 |
|---|---|---|
| 卡片背景 | `var(--el-bg-color)` | 默认卡片 |
| 卡片边框 | `var(--el-border-color-light)` | 默认边框 |
| 主文字 | `var(--el-text-color-primary)` | headline / 标题 |
| 副文字 | `var(--el-text-color-regular)` | chip label |
| 三级文字 | `var(--el-text-color-secondary)` | 来源说明 / 底部 |
| 主色 | `var(--el-color-primary)` | "查看完整分析 →" 按钮 |
| 成功色 | `var(--el-color-success)` | "AI 生成" 标签 + UP 箭头 |
| 警告色 | `var(--el-color-warning)` | "CAUTIOUS" mood（本期不展示）|
| 危险色 | `var(--el-color-danger)` | DOWN 箭头 |
| 信息色 | `var(--el-color-info)` | "模板" 标签 + 骨架屏 |

---

## 7. 可访问性（WCAG 2.2 AA）

| 元素 | 要求 |
|---|---|
| 智能卡 headline | `role="region"` + `aria-label="AI 洞察卡片"` |
| source 标签 | `aria-label="数据来源：{source 文本}"`（屏幕阅读器友好）|
| 刷新按钮 | `aria-label="刷新 AI 洞察"`（icon-only 时）|
| 抽屉 | `role="dialog"` + `aria-modal="true"` + `aria-labelledby` 指向标题 |
| 独立分析页 headline | `<h1>` 语义标签 |
| 关键指标 | 每个 chip `<div role="group" aria-label="{label} {value} {delta}">` |
| 颜色对比 | 主文字 ≥ 4.5:1；副文字 ≥ 3:1（已用 Element Plus 默认）|
| 键盘导航 | 智能卡 → Tab 聚焦 ⓘ → Enter 触发；抽屉 Esc 关闭（el-drawer 默认）|
| 屏幕阅读器 | source 标签朗读为 "AI 生成" / "模板"（无视觉字符）|

---

## 8. 与 07-ai.md（v2.0）的关系

| 改动类型 | 内容 |
|---|---|
| 升级 | 智能卡：source 标签 + headline 可 2 句 |
| 升级 | 抽屉：source 标签 + 底部"查看完整分析 →"链接 |
| 新增 | 独立分析页 `/ai-analysis`（4 段内容）|
| 新增 | 移动端适配（底部弹出抽屉 + 2×2 关键指标）|
| 新增 | 加载 / 空 / 降级 / 错误 4 种状态完整覆盖 |
| 沿用 | 6 卡首页布局（AI 卡插首位） |
| 沿用 | `AiInsightStore`（仅加 `refresh()` 方法）|

> **不破坏 v2.0**：所有 v2.0 组件代码仍可用，仅做属性扩展与新组件引入。

---
