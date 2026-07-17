<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import dayjs from 'dayjs';
import {
  ElMessage,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSwitch,
  ElDatePicker,
  ElSelect,
  ElOption,
  ElButton,
} from 'element-plus';
import type { PlanAllDay, PlanCreateRequest, PlanResponse, PlanUpdateRequest } from '@/types';
import { PlanAllDayValue } from '@/types';

/**
 * 事件创建/编辑弹窗（spec §04 §5 + PRD PLAN-2/PLAN-3）。
 *
 * <p>展示层组件：内部维护表单副本，校验通过后 emit `submit(payload)`，由父视图落 API。
 * 全天切换时隐藏时刻（date picker），提交时补齐边界秒（00:00:00 / 23:59:59）。
 *
 * <p>el-date-picker type=datetime 在真浏览器下 E2E 交互不稳（见 MEMORY），
 * 交互路径以单测覆盖为主；E2E 走 store 直连。
 */
const props = defineProps<{
  /** v-model 显隐。 */
  modelValue: boolean;
  /** 弹窗模式：新建 / 编辑。 */
  mode: 'create' | 'edit';
  /** 编辑模式下的初始事件；create 时忽略。 */
  initial?: PlanResponse | null;
  /** 新建时的默认日期（YYYY-MM-DD），来自日历选中格。 */
  defaultDate?: string | null;
  /** 父视图提交中标记（禁用按钮 / loading）。 */
  submitting?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', open: boolean): void;
  (e: 'submit', payload: PlanCreateRequest | PlanUpdateRequest): void;
}>();

const TITLE_MAX = 200;
const LOCATION_MAX = 200;
const NOTE_MAX = 500;
const DATETIME_FMT = 'YYYY-MM-DD[T]HH:mm:ss';
const DATE_FMT = 'YYYY-MM-DD';
/** 新建默认时段：09:00 ~ 10:00。 */
const DEFAULT_START_HM = 'T09:00:00';
const DEFAULT_END_HM = 'T10:00:00';

/** el-option value 不接受 null，用哨兵值代表「不提醒」，提交时映射回 null。 */
const REMINDER_NONE = -1;
const REMINDER_OPTIONS: ReadonlyArray<{ value: number; label: string }> = [
  { value: REMINDER_NONE, label: '不提醒' },
  { value: 5, label: '提前 5 分钟' },
  { value: 15, label: '提前 15 分钟' },
  { value: 30, label: '提前 30 分钟' },
  { value: 60, label: '提前 1 小时' },
] as const;

interface EventForm {
  title: string;
  allDay: PlanAllDay;
  /** 全天时为 YYYY-MM-DD；否则为 YYYY-MM-DDTHH:mm:ss。 */
  start: string;
  end: string;
  location: string;
  note: string;
  reminderMin: number | null;
}

const form = reactive<EventForm>({
  title: '',
  allDay: PlanAllDayValue.TIMED,
  start: '',
  end: '',
  location: '',
  note: '',
  reminderMin: null,
});

const dialogTitle = ref('新建事件');

/** el-select 代理：null <-> REMINDER_NONE 哨兵。 */
const reminderModel = computed<number>({
  get: () => form.reminderMin ?? REMINDER_NONE,
  set: (v) => {
    form.reminderMin = v === REMINDER_NONE ? null : v;
  },
});

/** date/datetime 之间转换：保留日期部分，补/去时刻。 */
function toAllDay(value: string): string {
  return value ? value.slice(0, 10) : '';
}
function toTimed(value: string, hm: string): string {
  if (!value) return '';
  return value.length <= 10 ? `${value.slice(0, 10)}${hm}` : value;
}

function resetForm(): void {
  if (props.mode === 'edit' && props.initial) {
    const p = props.initial;
    form.title = p.title;
    form.allDay = p.allDay;
    form.location = p.location ?? '';
    form.note = p.note ?? '';
    form.reminderMin = p.reminderMin;
    form.start = p.allDay === PlanAllDayValue.ALL_DAY ? p.startTime.slice(0, 10) : p.startTime.slice(0, 19);
    form.end = p.allDay === PlanAllDayValue.ALL_DAY ? p.endTime.slice(0, 10) : p.endTime.slice(0, 19);
    dialogTitle.value = '编辑事件';
    return;
  }
  const base = props.defaultDate ?? dayjs().format(DATE_FMT);
  form.title = '';
  form.allDay = PlanAllDayValue.TIMED;
  form.start = `${base}${DEFAULT_START_HM}`;
  form.end = `${base}${DEFAULT_END_HM}`;
  form.location = '';
  form.note = '';
  form.reminderMin = null;
  dialogTitle.value = '新建事件';
}

// 打开时（modelValue false→true）重置表单；关闭不清空避免闪烁
watch(
  () => props.modelValue,
  (open, prev) => {
    if (open && !prev) resetForm();
  },
);

// 全天切换 → 转换已有起止值的粒度
watch(
  () => form.allDay,
  (allDay) => {
    if (allDay === PlanAllDayValue.ALL_DAY) {
      form.start = toAllDay(form.start);
      form.end = toAllDay(form.end);
    } else {
      form.start = toTimed(form.start, DEFAULT_START_HM);
      form.end = toTimed(form.end, DEFAULT_END_HM);
    }
  },
);

function close(): void {
  emit('update:modelValue', false);
}

function buildPayload(): PlanCreateRequest | null {
  const title = form.title.trim();
  if (!title) {
    ElMessage({ message: '请输入事件标题', type: 'warning' });
    return null;
  }
  if (!form.start || !form.end) {
    ElMessage({ message: '请选择起止时间', type: 'warning' });
    return null;
  }
  const isAllDay = form.allDay === PlanAllDayValue.ALL_DAY;
  const startTime = isAllDay ? `${form.start.slice(0, 10)}T00:00:00` : form.start;
  const endTime = isAllDay ? `${form.end.slice(0, 10)}T23:59:59` : form.end;
  if (!dayjs(endTime).isAfter(dayjs(startTime))) {
    ElMessage({ message: '结束时间必须晚于开始时间', type: 'warning' });
    return null;
  }
  return {
    title,
    startTime,
    endTime,
    allDay: form.allDay,
    location: form.location.trim() || null,
    note: form.note.trim() || null,
    reminderMin: form.reminderMin,
  };
}

function onSubmit(): void {
  const payload = buildPayload();
  if (payload) emit('submit', payload);
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="520px"
    data-testid="event-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-form label-position="top">
      <div data-testid="event-title">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="如 团队周会" :maxlength="TITLE_MAX" show-word-limit />
        </el-form-item>
      </div>

      <el-form-item label="全天">
        <el-switch
          v-model="form.allDay"
          :active-value="PlanAllDayValue.ALL_DAY"
          :inactive-value="PlanAllDayValue.TIMED"
          data-testid="event-allday"
        />
      </el-form-item>

      <el-form-item label="开始">
        <el-date-picker
          v-if="form.allDay === PlanAllDayValue.TIMED"
          v-model="form.start"
          type="datetime"
          placeholder="开始时间"
          :value-format="DATETIME_FMT"
          data-testid="event-start"
          style="width: 100%"
        />
        <el-date-picker
          v-else
          v-model="form.start"
          type="date"
          placeholder="开始日期"
          :value-format="DATE_FMT"
          data-testid="event-start-date"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="结束">
        <el-date-picker
          v-if="form.allDay === PlanAllDayValue.TIMED"
          v-model="form.end"
          type="datetime"
          placeholder="结束时间"
          :value-format="DATETIME_FMT"
          data-testid="event-end"
          style="width: 100%"
        />
        <el-date-picker
          v-else
          v-model="form.end"
          type="date"
          placeholder="结束日期"
          :value-format="DATE_FMT"
          data-testid="event-end-date"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="地点">
        <el-input v-model="form.location" placeholder="如 会议室 A" :maxlength="LOCATION_MAX" />
      </el-form-item>

      <el-form-item label="提醒">
        <el-select v-model="reminderModel" placeholder="不提醒" data-testid="event-reminder" style="width: 180px">
          <el-option v-for="o in REMINDER_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
        </el-select>
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.note"
          type="textarea"
          :rows="3"
          placeholder="可选"
          :maxlength="NOTE_MAX"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button data-testid="event-cancel" @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" data-testid="event-submit" @click="onSubmit">
        {{ mode === 'edit' ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>
