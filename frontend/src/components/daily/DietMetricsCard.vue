<!--
  DietMetricsCard — 日报饮食指标卡（spec §08-daily-report-design §3.1 + §7.2）。

  <p><b>v1.2.4 冻结契约</b>：DietMetricProvider 永远返回
  {@code DietMetrics(enabled=false, value=null, reason=...)}。
  本组件按 enabled=false 渲染占位卡：标题「饮食」+ 大字「暂未启用」+ reason 副文案。
  饮食模块解冻后（DietMetricProvider 改 enabled=true），本组件新增 enabled=true
  分支渲染 kcal / 蛋白质 / 碳水 / 脂肪（与 DietNutritionCard 同款进度条）。
  record 形状在 spec §3.1 永久冻结，组件无需修改 props。

  <p>Pure presentational。
-->
<script setup lang="ts">
import { computed } from 'vue';
import type { DietMetrics } from '@/types';

const props = defineProps<{
  diet: DietMetrics;
}>();

/** 解冻前的占位文案兜底（防止后端 reason 为空字符串）。 */
const placeholderReason = computed<string>(() => props.diet.reason || 'v1.2.4+ 启用');
</script>

<template>
  <div class="diet-card" data-testid="daily-diet-card">
    <header class="diet-card__header">
      <h3 class="diet-card__title">饮食</h3>
    </header>

    <div
      v-if="!diet.enabled"
      class="diet-card__placeholder"
      data-testid="daily-diet-placeholder"
    >
      <div class="diet-card__placeholder-headline">暂未启用</div>
      <div class="diet-card__placeholder-reason">{{ placeholderReason }}</div>
    </div>
  </div>
</template>

<style scoped>
.diet-card {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.diet-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.diet-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.diet-card__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 12px;
  background: #fafbfc;
  border-radius: 6px;
  text-align: center;
}
.diet-card__placeholder-headline {
  font-size: 20px;
  font-weight: 600;
  color: #909399;
}
.diet-card__placeholder-reason {
  font-size: 13px;
  color: #909399;
}
</style>
