<!--
  PlaceholderCard — 首页仪表盘的占位卡（spec §04 §4）。

  与 ModuleCard 的区别（独立组件，不复用）：
  - 仅承担 placeholder 模式：点击 → emit `placeholder-click`，由父组件统一弹「即将上线」。
  - 不带 `to` props（无路由跳转诉求）。
  - 视觉与 ModuleCard 的 placeholder 变体一致：白底 / 圆角 12px / 淡蓝阴影 /
    hover 抬起；占位卡灰度略浅以暗示「未上线」。

  设计选择：保持组件正交，不复用 ModuleCard，避免后者 placeholder 双分支膨胀。
-->
<template>
  <button
    type="button"
    class="placeholder-card placeholder-card--placeholder"
    data-testid="placeholder-card-button"
    @click="onClick"
  >
    <span class="placeholder-card__icon">
      <ElIcon :size="40" aria-hidden="true">
        <component :is="iconComponent" />
      </ElIcon>
    </span>
    <span class="placeholder-card__title">{{ title }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

/**
 * 公共 props。
 *
 * - `title`：卡片中文标签（如「日报」）。
 * - `icon`：Element Plus icon 名（与 HomeCardIcon 联合一致）。
 */
interface Props {
  title: string;
  icon: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  /** 占位卡点击触发；父组件统一弹「即将上线」toast。 */
  (e: 'placeholder-click'): void;
}>();

// 将 icon 名映射到 @element-plus/icons-vue 的组件；找不到则降级为 More。
const iconComponent = computed(
  () =>
    (ElementPlusIconsVue as Record<string, unknown>)[props.icon] ??
    ElementPlusIconsVue.More,
);

function onClick(): void {
  emit('placeholder-click');
}
</script>

<style scoped>
.placeholder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 28px 16px;
  background: #f8fafc;
  border-radius: 12px;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.10);
  color: #909399;
  transition:
    box-shadow 0.18s ease,
    transform 0.18s ease;
  cursor: pointer;
  border: 0;
  font: inherit;
}

.placeholder-card:hover,
.placeholder-card:focus-visible {
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.18);
  transform: translateY(-2px);
  outline: none;
}

.placeholder-card__icon {
  display: inline-flex;
  color: #c0c4cc;
}

.placeholder-card__title {
  font-size: 16px;
  font-weight: 500;
  color: #909399;
}
</style>
