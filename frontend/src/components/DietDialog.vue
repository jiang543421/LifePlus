<!--
  DietDialog — 饮食创建/编辑弹窗（spec §07-diet-design §5 + §6.3）。

  <p>展示层组件：
  <ul>
    <li>modelValue 双向绑定显隐（父视图通过 store.dialogVisible 持有）</li>
    <li>mode: 'create' | 'edit' 决定表单初值（默认 vs 从 item 预填）</li>
    <li>item: 编辑模式下的初始 DietResponse；create 模式为 null</li>
  </ul>

  <p>4 个用户可见输入字段（spec §6.3 基础版）：
  <ul>
    <li>name — 食物名称</li>
    <li>mealType — 餐别（早 / 午 / 晚 / 加餐）</li>
    <li>kcal — 热量</li>
    <li>note — 备注（可选）</li>
  </ul>

  <p>后端必填但前端简化的字段（自动填默认）：
  <ul>
    <li>proteinG / carbG / fatG — 营养字段 v1.2.2 MVP2 暂固定为 0；后续 phase 在 UI 加 3 个营养字段</li>
    <li>occurredAt — 提交瞬间取 "now - 5 分钟"（与 ExpenseDialog 同款）</li>
  </ul>

  <p>校验策略与 ExpenseDialog 同款：手工校验 + ElMessage toast，
  避开 el-form + el-input-number 在 jsdom 下的兼容性坑。
-->
<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';
import { MAX_NAME_LEN, MAX_NOTE_LEN, MEAL_LABEL, MEAL_TYPES } from '@/constants/diet';
import { useDietStore } from '@/stores/diet';
import type { CreateDietRequest, DietResponse, MealType, UpdateDietRequest } from '@/types';

const props = defineProps<{
  modelValue: boolean;
  mode: 'create' | 'edit';
  item: DietResponse | null;
  /** 父视图提交中标记（禁用提交按钮避免重复点击）。 */
  submitting?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', open: boolean): void;
  /** 提交成功（create/update 都触发），父视图负责 refresh + toast。 */
  (e: 'success'): void;
}>();

const store = useDietStore();

/** 表单本地副本。 */
interface DietFormState {
  name: string;
  mealType: MealType;
  kcal: number | undefined;
  note: string;
}

function emptyForm(): DietFormState {
  return {
    name: '',
    mealType: 'LUNCH',
    kcal: undefined,
    note: '',
  };
}

const form = reactive<DietFormState>(emptyForm());

const dialogTitle = computed(() => (props.mode === 'create' ? '新增饮食' : '编辑饮食'));

function prefillFromItem(item: DietResponse): void {
  form.name = item.name;
  form.mealType = item.mealType;
  form.kcal = item.kcal;
  form.note = item.note ?? '';
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
  const name = form.name.trim();
  if (!name) {
    ElMessage.warning('请输入食物名称');
    return;
  }
  if (name.length > MAX_NAME_LEN) {
    ElMessage.warning(`食物名称不能超过 ${MAX_NAME_LEN} 字符`);
    return;
  }
  if (!MEAL_TYPES.includes(form.mealType)) {
    ElMessage.warning('请选择合法餐别');
    return;
  }
  if (
    typeof form.kcal !== 'number' ||
    !Number.isFinite(form.kcal) ||
    form.kcal < 0
  ) {
    ElMessage.warning('热量必须为不小于 0 的有限数');
    return;
  }
  const note = form.note.trim();
  if (note.length > MAX_NOTE_LEN) {
    ElMessage.warning(`备注不能超过 ${MAX_NOTE_LEN} 字符`);
    return;
  }

  // 前端简化的固定字段（spec §6.3 基础版，营养字段留待 phase 扩展）
  const occurredAt = dayjs().subtract(5, 'minute').toISOString();

  if (props.mode === 'create') {
    const req: CreateDietRequest = {
      name,
      mealType: form.mealType,
      kcal: form.kcal,
      proteinG: 0,
      carbG: 0,
      fatG: 0,
      note: note || null,
      occurredAt,
    };
    await store.create(req);
  } else if (props.item) {
    const req: UpdateDietRequest = {
      name,
      mealType: form.mealType,
      kcal: form.kcal,
      note: note || null,
      occurredAt,
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
    data-testid="diet-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <ElForm label-position="top" data-testid="diet-dialog-form" @submit.prevent="onSubmit">
      <ElFormItem label="食物名称">
        <div data-testid="diet-dialog-name">
          <ElInput
            v-model="form.name"
            :maxlength="MAX_NAME_LEN"
            show-word-limit
            placeholder="例：米饭"
            class="diet-dialog__name"
          />
        </div>
      </ElFormItem>

      <ElFormItem label="餐别">
        <div data-testid="diet-dialog-meal-type">
          <ElSelect v-model="form.mealType" class="diet-dialog__meal-type">
            <ElOption
              v-for="m in MEAL_TYPES"
              :key="m"
              :label="MEAL_LABEL[m]"
              :value="m"
            />
          </ElSelect>
        </div>
      </ElFormItem>

      <ElFormItem label="热量（kcal）">
        <div data-testid="diet-dialog-kcal">
          <ElInputNumber
            v-model="form.kcal"
            :min="0"
            :precision="2"
            :step="10"
            class="diet-dialog__kcal"
          />
        </div>
      </ElFormItem>

      <ElFormItem label="备注">
        <div data-testid="diet-dialog-note">
          <ElInput
            v-model="form.note"
            type="textarea"
            :rows="2"
            :maxlength="MAX_NOTE_LEN"
            show-word-limit
            placeholder="可选"
            class="diet-dialog__note"
          />
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton data-testid="diet-dialog-cancel" @click="close">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitting"
        data-testid="diet-dialog-submit"
        @click="onSubmit"
      >
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.diet-dialog__name,
.diet-dialog__meal-type,
.diet-dialog__kcal,
.diet-dialog__note {
  width: 100%;
}
</style>