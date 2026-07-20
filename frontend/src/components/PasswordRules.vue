<script setup lang="ts">
import { computed } from 'vue';
import { PASSWORD_RULES } from '@/types';

/**
 * 实时密码强度规则提示（Settings v1.1 提取自 RegisterView 复用）。
 *
 * <p>按 {@link PASSWORD_RULES} 顺序逐条展示 ✓/✗ + label；调用方无需关心样式
 * 与文案。仅展示，不参与 {@code ElForm} 校验（校验仍在父级 rules.validator 中
 * 复用同一份 {@code PASSWORD_RULES}）。
 */
const props = defineProps<{ value: string }>();

const ruleResults = computed(() =>
  PASSWORD_RULES.map((r) => ({ key: r.key, label: r.label, ok: r.test(props.value) })),
);
</script>

<template>
  <ul class="password-rules">
    <li v-for="r in ruleResults" :key="r.key" :class="{ ok: r.ok }">
      <span class="mark">{{ r.ok ? '✓' : '✗' }}</span>{{ r.label }}
    </li>
  </ul>
</template>

<style scoped>
.password-rules {
  list-style: none;
  padding: 0 0 16px;
  margin: -8px 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.password-rules li {
  padding: 2px 0;
}
.password-rules li.ok {
  color: var(--el-color-success);
}
.password-rules .mark {
  display: inline-block;
  width: 1.2em;
}
</style>