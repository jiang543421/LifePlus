<!--
  TriStateEmpty — 统一空态组件（v1.2.6 #4.1）。

  <p>设计动机：v1.2.5 之前各视图（TaskListView / PlanCalendarView / ExpenseView /
  DietView）用裸 `<div>` 写空态，DailyView / AiAnalysisView 用 Element Plus
  ElEmpty。视觉不一致 + 复用困难。

  <p>统一行为：
  <ul>
    <li>ElEmpty 渲染图标 + 描述文案</li>
    <li>外层 testId 命名由父组件提供，避免命名空间冲突</li>
    <li>默认 slot 可塞 action 按钮（暂时保留扩展点；当前所有视图都只在空态下
        提示，无 action；未来若需要"立即新建"按钮可直接复用）</li>
  </ul>

  <p>约束：0 新依赖；复用 Element Plus ElEmpty；不引入额外 CSS 变量。
-->
<template>
  <div :data-testid="testId" class="tri-state-empty">
    <ElEmpty :description="description">
      <slot />
    </ElEmpty>
  </div>
</template>

<script setup lang="ts">
import { ElEmpty } from 'element-plus';

/**
 * 公共 props。
 *
 * - `description`：空态文案；默认「暂无数据」与 v1.2.5 AiDrawer 的友好基调一致。
 * - `testId`：父组件传入的命名空间（如 `task-list-empty` / `day-empty`），保证
 *   各视图测试钩子不冲突。
 */
interface Props {
  description?: string;
  testId?: string;
}

withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  testId: 'tristate-empty',
});
</script>

<style scoped>
/* ElEmpty 自带 padding 与居中样式，不重复添加；外层容器只承担 testId 命名空间职责。 */
.tri-state-empty {
  width: 100%;
}
</style>