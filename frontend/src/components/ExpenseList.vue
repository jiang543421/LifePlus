<!--
  ExpenseList — 消费列表容器（spec §06-expense section 5）。

  <p>按发生日（YYYY-MM-DD）分组；组内保持入参顺序，避免视觉跳跃。
  跨用户越权由 store 层 fetchList 过滤；本组件只做展示 + 事件转发。
-->
<script setup lang="ts">
import { computed } from 'vue';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import ExpenseListItem from './ExpenseListItem.vue';
import TriStateEmpty from './TriStateEmpty.vue';
import type { ExpenseListItem as ExpenseListItemT } from '@/types';

dayjs.locale('zh-cn');

const props = defineProps<{ items: ExpenseListItemT[] }>();
const emit = defineEmits<{
  (e: 'edit', id: number): void;
  (e: 'delete', id: number): void;
}>();

/** 按 YYYY-MM-DD 分组，组内保持入参顺序（Map 保留插入序）。 */
const grouped = computed<Array<{ day: string; items: ExpenseListItemT[] }>>(() => {
  const map = new Map<string, ExpenseListItemT[]>();
  for (const it of props.items) {
    const k = dayjs(it.occurredAt).format('YYYY-MM-DD');
    if (!map.has(k)) map.set(k, []);
    map.get(k)!.push(it);
  }
  return Array.from(map.entries()).map(([day, items]) => ({ day, items }));
});
</script>

<template>
  <div class="expense-list" data-testid="expense-list">
    <TriStateEmpty
      v-if="items.length === 0"
      test-id="expense-list-empty"
      description="暂无消费记录"
    />
    <template v-else>
      <div
        v-for="g in grouped"
        :key="g.day"
        class="day-group"
        :data-testid="`day-group-${g.day}`"
      >
        <div class="day-header" :data-testid="`day-header-${g.day}`">
          ▾ {{ dayjs(g.day).format('YYYY-MM-DD ddd') }}
        </div>
        <ExpenseListItem
          v-for="it in g.items"
          :key="it.id"
          :item="it"
          @edit="(id) => emit('edit', id)"
          @delete="(id) => emit('delete', id)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.expense-list {
  display: flex;
  flex-direction: column;
}
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
.expense-item:last-child {
  border-bottom: none;
}
</style>