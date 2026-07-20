<!--
  ExpenseDialog — 消费创建/编辑弹窗（spec §06-expense section 5）。

  <p>展示层组件：
  <ul>
    <li>modelValue 双向绑定显隐（父视图通过 store.dialogVisible 持有）</li>
    <li>mode: 'create' | 'edit' 决定表单初值（默认 vs 从 item 预填）</li>
    <li>item: 编辑模式下的初始 ExpenseResponse；create 模式为 null</li>
  </ul>

  <p>校验策略：el-form + el-input-number 的 async-validator 在 jsdom 下兼容性差（el-input-number
  未失焦时 validate() 不触发 custom validator），v1.2.1 改用 onSubmit 内手工校验 + ElMessage toast。
  后续 Phase 5 评估是否引入 async-validator 替代方案。

  <p>提交流程：onSubmit 手工校验 → store.create / store.update → 关闭弹窗 + emit success。
  失败由 store 抛 ApiError，弹窗不感知具体错误（视图层 toast — T14）。
-->
<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';
import {
  CATEGORY_LABEL,
  EXPENSE_CATEGORIES,
  type ExpenseCategory,
} from '@/constants/expense';
import { useExpenseStore } from '@/stores/expense';
import type {
  CreateExpenseRequest,
  ExpenseResponse,
  UpdateExpenseRequest,
} from '@/types';

/** 备注最大长度（与后端 @Size(max=200) 对齐）。 */
const NOTE_MAX = 200;

const props = defineProps<{
  modelValue: boolean;
  mode: 'create' | 'edit';
  item: ExpenseResponse | null;
  /** 父视图提交中标记（禁用提交按钮避免重复点击）。 */
  submitting?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', open: boolean): void;
  /** 提交成功（create/update 都触发），父视图负责 refresh + toast。 */
  (e: 'success'): void;
}>();

const store = useExpenseStore();

/**
 * 表单本地副本。
 *
 * <p>{@code amount} 用 {@code number | undefined}：el-input-number 清空时模型为 undefined，
 * onSubmit 拒收 undefined 并提示「金额必须为大于 0 的有限数」。
 *
 * <p>{@code occurredAt} 默认 "now - 5 分钟"（用户进入页面即可提交"刚刚"发生的消费）。
 */
interface ExpenseFormState {
  amount: number | undefined;
  category: ExpenseCategory;
  note: string;
  occurredAt: string;
}

function defaultOccurredAt(): string {
  return dayjs().subtract(5, 'minute').toISOString();
}

function emptyForm(): ExpenseFormState {
  return {
    amount: undefined,
    category: 'MEAL',
    note: '',
    occurredAt: defaultOccurredAt(),
  };
}

const form = reactive<ExpenseFormState>(emptyForm());

const dialogTitle = computed(() => (props.mode === 'create' ? '新增消费' : '编辑消费'));

function prefillFromItem(item: ExpenseResponse): void {
  form.amount = item.amount;
  form.category = item.category;
  form.note = item.note ?? '';
  form.occurredAt = item.occurredAt;
}

// 打开弹窗（false→true）时按 mode 决定重置 / 预填；item 变化也同步预填。
watch(
  () => [props.modelValue, props.mode, props.item] as const,
  ([open, mode, item]) => {
    if (!open) return;
    if (mode === 'edit' && item) {
      prefillFromItem(item);
    } else {
      Object.assign(form, emptyForm());
    }
  },
);

function close(): void {
  emit('update:modelValue', false);
}

async function onSubmit(): Promise<void> {
  // 业务级校验（手工，避免 el-form + el-input-number 在 jsdom 下的兼容性坑）。
  if (typeof form.amount !== 'number' || !Number.isFinite(form.amount) || form.amount <= 0) {
    ElMessage.warning('金额必须为大于 0 的有限数');
    return;
  }
  if (!EXPENSE_CATEGORIES.includes(form.category)) {
    ElMessage.warning('请选择合法分类');
    return;
  }
  if (!form.occurredAt) {
    ElMessage.warning('请选择发生时间');
    return;
  }

  const note = form.note.trim() || null;

  if (props.mode === 'create') {
    const req: CreateExpenseRequest = {
      amount: form.amount,
      category: form.category,
      note,
      occurredAt: form.occurredAt,
    };
    await store.create(req);
  } else if (props.item) {
    const req: UpdateExpenseRequest = {
      amount: form.amount,
      category: form.category,
      note,
      occurredAt: form.occurredAt,
    };
    await store.update(props.item.id, req);
  }

  emit('update:modelValue', false);
  emit('success');
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="520px"
    data-testid="expense-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <ElForm
      label-position="top"
      data-testid="expense-dialog-form"
      @submit.prevent="onSubmit"
    >
      <ElFormItem label="金额">
        <div data-testid="expense-dialog-amount">
          <ElInputNumber
            v-model="form.amount"
            :min="0.01"
            :precision="2"
            :step="1"
            class="expense-dialog__amount"
          />
        </div>
      </ElFormItem>

      <ElFormItem label="分类">
        <div data-testid="expense-dialog-category">
          <ElSelect v-model="form.category" class="expense-dialog__category">
            <ElOption
              v-for="c in EXPENSE_CATEGORIES"
              :key="c"
              :label="CATEGORY_LABEL[c]"
              :value="c"
            />
          </ElSelect>
        </div>
      </ElFormItem>

      <ElFormItem label="备注">
        <div data-testid="expense-dialog-note">
          <ElInput
            v-model="form.note"
            type="textarea"
            :rows="2"
            :maxlength="NOTE_MAX"
            show-word-limit
          />
        </div>
      </ElFormItem>

      <ElFormItem label="发生时间">
        <div data-testid="expense-dialog-occurred-at">
          <ElDatePicker
            v-model="form.occurredAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            class="expense-dialog__occurred-at"
          />
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton data-testid="expense-dialog-cancel" @click="close">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitting"
        data-testid="expense-dialog-submit"
        @click="onSubmit"
      >
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.expense-dialog__amount,
.expense-dialog__category,
.expense-dialog__occurred-at {
  width: 100%;
}
</style>
