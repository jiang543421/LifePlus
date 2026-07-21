<!--
  DietDayGroup — 单日 4 餐分组容器（spec §07-diet-design §6.3）。

  <p>props.items 单日所有 diet；按 mealType 拆成 4 餐（早 / 午 / 晚 / 加餐），
  每餐内保持入参顺序；每餐底部子合计（kcal）。餐别标题可点击折叠 / 展开。

  <p>DietView（T17）按 groupedByDay 结果逐日渲染本组件；
  本组件只做展示 + 事件转发，不做数据拉取。
-->
<script setup lang="ts">
import { computed, ref } from 'vue';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { ElButton } from 'element-plus';
import { MEAL_LABEL, MEAL_TYPES } from '@/constants/diet';
import { formatAmount } from '@/utils/number';
import type { DietListItem, MealType } from '@/types';

dayjs.locale('zh-cn');

const props = defineProps<{
  /** YYYY-MM-DD（来自 store.groupedByDay 的 day 字段）。 */
  day: string;
  /** 单日内所有 diet 项。 */
  items: DietListItem[];
}>();

const emit = defineEmits<{
  (e: 'edit', id: number): void;
  (e: 'delete', id: number): void;
}>();

/** 单餐视图状态：collapsed = true 时折叠；默认全部展开。 */
const collapsed = ref<Record<MealType, boolean>>({
  BREAKFAST: false,
  LUNCH: false,
  DINNER: false,
  SNACK: false,
});

/** 单餐所有项 + 子合计（kcal）。 */
interface MealGroup {
  mealType: MealType;
  label: string;
  items: DietListItem[];
  totalKcal: number;
}

const mealGroups = computed<MealGroup[]>(() => {
  const map = new Map<MealType, DietListItem[]>();
  for (const it of props.items) {
    const bucket = map.get(it.mealType);
    if (bucket) bucket.push(it);
    else map.set(it.mealType, [it]);
  }
  return MEAL_TYPES.map((mealType) => {
    const items = map.get(mealType) ?? [];
    const totalKcal = items.reduce((s, x) => s + (Number.isFinite(x.kcal) ? x.kcal : 0), 0);
    return { mealType, label: MEAL_LABEL[mealType], items, totalKcal };
  });
});

/** 单餐项数（用于组标题角标）。 */
function itemCount(items: DietListItem[]): number {
  return items.length;
}

/** 折叠切换。 */
function toggle(mealType: MealType): void {
  collapsed.value = { ...collapsed.value, [mealType]: !collapsed.value[mealType] };
}

/** 格式化时间：HH:mm（取 occurredAt 11..16 位）。 */
function formatTime(s: string): string {
  // occurredAt 形如 "2026-07-15T12:00:00+08:00" → 取 11..16 即 "12:00"
  return s.length >= 16 ? s.substring(11, 16) : s;
}
</script>

<template>
  <div class="day-group" :data-testid="`diet-day-group-${day}`">
    <div class="day-header" :data-testid="`diet-day-header-${day}`">
      ▾ {{ dayjs(day).format('YYYY-MM-DD ddd') }}
    </div>
    <div
      v-for="g in mealGroups"
      :key="g.mealType"
      class="meal-group"
      :data-testid="`diet-meal-group-${day}-${g.mealType}`"
    >
      <div
        class="meal-header"
        :data-testid="`diet-meal-header-${day}-${g.mealType}`"
        @click="toggle(g.mealType)"
      >
        <span class="meal-header__caret" :data-testid="`diet-meal-caret-${day}-${g.mealType}`">
          {{ collapsed[g.mealType] ? '▸' : '▾' }}
        </span>
        <span class="meal-header__label">{{ g.label }}</span>
        <span class="meal-header__count" :data-testid="`diet-meal-count-${day}-${g.mealType}`">
          {{ itemCount(g.items) }} 笔
        </span>
        <span
          v-if="!collapsed[g.mealType] && g.items.length > 0"
          class="meal-header__total"
          :data-testid="`diet-meal-total-${day}-${g.mealType}`"
        >
          小计 {{ formatAmount(g.totalKcal) }} kcal
        </span>
      </div>
      <ul v-if="!collapsed[g.mealType]" class="meal-items">
        <li
          v-for="it in g.items"
          :key="it.id"
          class="meal-item"
          :data-testid="`diet-item-${it.id}`"
        >
          <div class="meal-item__main">
            <span class="meal-item__time">{{ formatTime(it.occurredAt) }}</span>
            <span class="meal-item__name">{{ it.name }}</span>
            <span class="meal-item__kcal">{{ formatAmount(it.kcal) }} kcal</span>
          </div>
          <div class="meal-item__actions">
            <ElButton
              size="small"
              link
              :data-testid="`diet-edit-${it.id}`"
              @click="emit('edit', it.id)"
            >
              编辑
            </ElButton>
            <ElButton
              size="small"
              link
              type="danger"
              :data-testid="`diet-delete-${it.id}`"
              @click="emit('delete', it.id)"
            >
              删除
            </ElButton>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.day-group {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}
.day-header {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.meal-group + .meal-group {
  border-top: 1px solid var(--el-border-color-lighter);
}
.meal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  background: var(--el-fill-color-blank);
  transition: background 0.15s ease;
}
.meal-header:hover {
  background: var(--el-fill-color-light);
}
.meal-header__caret {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.meal-header__label {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.meal-header__count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.meal-header__total {
  margin-left: auto;
  color: var(--el-color-primary);
  font-weight: 500;
}
.meal-items {
  list-style: none;
  margin: 0;
  padding: 0;
}
.meal-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 13px;
}
.meal-item:first-child {
  border-top: none;
}
.meal-item__main {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.meal-item__time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  min-width: 40px;
}
.meal-item__name {
  flex: 1;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meal-item__kcal {
  color: var(--el-color-primary);
  font-weight: 500;
  font-size: 12px;
}
.meal-item__actions {
  display: flex;
  gap: 4px;
}
</style>