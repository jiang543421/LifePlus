<!--
  ExpenseListItem — 消费列表单行（spec §06-expense section 5）。

  <p>展示层组件：纯 props 输入 + emit 事件，**不**直接调 API；
  路由跳转 / dialog 打开 / 删除二次确认都交给父视图（ExpenseList）。
-->
<script setup lang="ts">
import { computed } from 'vue';
import { ElTag } from 'element-plus';
import { CATEGORY_LABEL } from '@/constants/expense';
import { formatAmountWithSymbol } from '@/utils/number';
import dayjs from 'dayjs';
import type { ExpenseListItem } from '@/types';

const props = defineProps<{ item: ExpenseListItem }>();
const emit = defineEmits<{
  (e: 'edit', id: number): void;
  (e: 'delete', id: number): void;
}>();

/** 分类中文 label（与 CATEGORY_LABEL 对齐）。 */
const categoryLabel = computed(() => CATEGORY_LABEL[props.item.category]);
/** "¥ 12.50" 格式金额（保留 2 位小数）。 */
const amountStr = computed(() => formatAmountWithSymbol(props.item.amount));
/** "MM-DD HH:mm" 时间串（发生在同一天无需年份；HH:mm 与消费时段习惯一致）。 */
const time = computed(() => dayjs(props.item.occurredAt).format('MM-DD HH:mm'));
/** note 超长截断 + 「」 引号包裹（无 note 时不渲染引号）。 */
const noteText = computed(() => {
  const n = props.item.note;
  if (!n) return '';
  return n.length > 20 ? n.slice(0, 20) + '…' : n;
});
</script>

<template>
  <div class="expense-item" :data-testid="`expense-item-${item.id}`">
    <ElTag
      size="small"
      class="expense-item__category"
      data-testid="expense-item-category"
    >
      {{ categoryLabel }}
    </ElTag>
    <span class="expense-item__amount" data-testid="expense-item-amount">{{ amountStr }}</span>
    <span class="expense-item__note" data-testid="expense-item-note">「{{ noteText }}」</span>
    <span class="expense-item__time" data-testid="expense-item-time">{{ time }}</span>
    <div class="expense-item__actions">
      <button class="link-btn" data-testid="expense-item-edit" @click="emit('edit', item.id)">
        编辑
      </button>
      <button
        class="link-btn danger"
        data-testid="expense-item-delete"
        @click="emit('delete', item.id)"
      >
        删除
      </button>
    </div>
  </div>
</template>

<style scoped>
.expense-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.expense-item:hover {
  background: var(--el-fill-color-light);
}
.expense-item__category {
  min-width: 56px;
  justify-content: center;
}
.expense-item__amount {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 80px;
  text-align: right;
}
.expense-item__note {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.expense-item__time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 96px;
  text-align: right;
}
.expense-item__actions {
  display: flex;
  gap: 8px;
}
.link-btn {
  background: none;
  border: none;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 13px;
}
.link-btn:hover {
  text-decoration: underline;
}
.link-btn.danger {
  color: var(--el-color-danger);
}
</style>