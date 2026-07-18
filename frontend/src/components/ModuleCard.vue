<!--
  ModuleCard — 首页仪表盘单卡（spec §04 §4）。

  两类模式（用 `placeholder` boolean 区分，无 `kind` 字符串分支）：
  - 模块卡：渲染 <router-link :to="to">，点击走路由。
  - 占位卡：渲染 <button>，点击 emit `placeholder-click`，由父组件统一 Toast。

  视觉规格（CLAUDE.md §4 + spec §4）：
  - 白底（#fff）
  - 圆角 12px
  - 淡蓝阴影 0 6px 16px rgba(64,158,255,0.10)
  - hover 时阴影加深 + 轻微 translateY 抬起
-->
<template>
  <router-link
    v-if="!placeholder"
    class="module-card"
    :to="to ?? '/'"
    data-testid="module-card-link"
  >
    <span class="module-card__icon">
      <ElIcon :size="40" aria-hidden="true">
        <component :is="iconComponent" />
      </ElIcon>
    </span>
    <span class="module-card__title">{{ title }}</span>
  </router-link>

  <button
    v-else
    type="button"
    class="module-card module-card--placeholder"
    data-testid="module-card-placeholder"
    @click="onPlaceholderClick"
  >
    <span class="module-card__icon">
      <ElIcon :size="40" aria-hidden="true">
        <component :is="iconComponent" />
      </ElIcon>
    </span>
    <span class="module-card__title">{{ title }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

/**
 * 公共 props。
 *
 * - `title`：卡片中文标签（如「任务」）。
 * - `icon`：Element Plus icon 名（与 HomeCardIcon 联合一致，但 props 接受 string 以放宽约束）。
 * - `to`：模块卡的目标路由；占位卡模式下被忽略。
 * - `placeholder`：true → 占位卡，emit `placeholder-click` 而非跳转。
 */
interface Props {
  title: string;
  icon: string;
  to?: string;
  placeholder?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: false,
  to: undefined,
});

const emit = defineEmits<{
  /** 占位卡点击触发；父组件统一弹「即将上线」toast。 */
  (e: 'placeholder-click'): void;
}>();

// 模块模式下 to 可选；未传则 fallback 到 '/'，由 caller 保证传值。
// 占位模式下走 button 分支，与 to 无关。

// 将 icon 名映射到 @element-plus/icons-vue 的组件；找不到则降级为 ElementPlus 占位。
const iconComponent = computed(
  () =>
    (ElementPlusIconsVue as Record<string, unknown>)[props.icon] ??
    ElementPlusIconsVue.More,
);

function onPlaceholderClick(): void {
  emit('placeholder-click');
}
</script>

<style scoped>
.module-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 28px 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.10);
  text-decoration: none;
  color: inherit;
  transition:
    box-shadow 0.18s ease,
    transform 0.18s ease;
  cursor: pointer;
  border: 0;
  font: inherit;
}

.module-card:hover,
.module-card:focus-visible {
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.18);
  transform: translateY(-2px);
  outline: none;
}

.module-card__icon {
  display: inline-flex;
  color: #409eff;
}

.module-card__title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.module-card--placeholder {
  /* 占位卡视觉与模块卡一致；灰度略浅以暗示「未上线」。 */
  background: #f8fafc;
  color: #909399;
}

.module-card--placeholder .module-card__icon {
  color: #c0c4cc;
}
</style>
