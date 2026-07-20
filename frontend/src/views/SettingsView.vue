<!--
  SettingsView — 设置页 v1.1（issue 2026-07-18-settings-v1-1）。
  三段：资料（改昵称）/ 安全（改密码）/ 危险操作（注销账号）。
-->
<template>
  <div class="settings-view">
    <TopBar />

    <main class="settings-view__main">
      <header class="settings-header">
        <h1 class="settings-header__title">设置</h1>
        <router-link
          to="/"
          class="settings-header__back"
          data-testid="settings-back-home"
        >
          ← 返回首页
        </router-link>
      </header>

      <!-- ① 资料 -->
      <el-card class="settings-card" shadow="hover" data-testid="settings-profile-card">
        <template #header>
          <span class="settings-card__title">资料</span>
        </template>
        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-position="top"
          @submit.prevent="onSaveProfile"
        >
          <el-form-item label="昵称" prop="nickname">
            <div data-testid="settings-nickname-input">
              <el-input
                v-model="profileForm.nickname"
                placeholder="留空则使用邮箱前缀"
                maxlength="32"
                show-word-limit
                clearable
              />
            </div>
          </el-form-item>
          <p class="settings-hint">仅你自己可见；空字符串将清空昵称。</p>
          <el-button
            type="primary"
            :loading="profileSubmitting"
            native-type="submit"
            data-testid="settings-profile-submit"
            @click="onSaveProfile"
          >
            保存
          </el-button>
        </el-form>
      </el-card>

      <!-- ② 安全 -->
      <el-card class="settings-card" shadow="hover" data-testid="settings-password-card">
        <template #header>
          <span class="settings-card__title">安全</span>
        </template>
        <el-form
          ref="pwFormRef"
          :model="pwForm"
          :rules="pwRules"
          label-position="top"
          @submit.prevent="onChangePassword"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <div data-testid="settings-old-password-input">
              <el-input
                v-model="pwForm.oldPassword"
                type="password"
                show-password
                autocomplete="current-password"
              />
            </div>
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <div data-testid="settings-new-password-input">
              <el-input
                v-model="pwForm.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
              />
            </div>
            <PasswordRules :value="pwForm.newPassword" />
          </el-form-item>
          <el-button
            type="primary"
            :loading="pwSubmitting"
            native-type="submit"
            data-testid="settings-password-submit"
            @click="onChangePassword"
          >
            修改密码
          </el-button>
        </el-form>
      </el-card>

      <!-- ③ 危险操作 -->
      <el-card class="settings-card settings-card--danger" shadow="hover" data-testid="settings-danger-card">
        <template #header>
          <span class="settings-card__title">危险操作</span>
        </template>
        <p class="danger-hint">
          注销账号后所有数据将被永久归档，无法恢复；需要输入当前密码二次确认。
        </p>
        <el-button
          type="danger"
          plain
          data-testid="settings-delete-account-btn"
          @click="onDeleteAccount"
        >
          注销账号
        </el-button>
      </el-card>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import TopBar from '@/components/TopBar.vue';
import PasswordRules from '@/components/PasswordRules.vue';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { PASSWORD_RULES } from '@/types';

const router = useRouter();
const auth = useAuthStore();

/* -------------------- ① 资料 -------------------- */
const profileFormRef = ref<FormInstance>();
const profileSubmitting = ref(false);
const profileForm = reactive({ nickname: auth.user?.nickname ?? '' });

// store.user 变化时（如刚登录）同步表单初值；提交期间由 submitting 保护。
watch(
  () => auth.user?.nickname,
  (n) => {
    if (!profileSubmitting.value) profileForm.nickname = n ?? '';
  },
);

const profileRules: FormRules<typeof profileForm> = {
  nickname: [{ max: 32, message: '昵称最多 32 字符' }],
};

async function onSaveProfile(): Promise<void> {
  if (!profileFormRef.value) return;
  // 防御性预校验（ElForm 行为受 trigger 默认值影响，独立兜底更稳）。
  try {
    await profileFormRef.value.validate();
  } catch {
    return;
  }
  const trimmed = profileForm.nickname.trim();
  const next: string | null = trimmed.length === 0 ? null : trimmed;
  // 与当前值相同则跳过请求，避免无意义往返。
  if ((auth.user?.nickname ?? null) === next) {
    ElMessage({ message: '没有变更', type: 'info' });
    return;
  }
  profileSubmitting.value = true;
  try {
    await auth.updateProfile({ nickname: next });
    ElMessage({ message: '昵称已保存', type: 'success' });
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
    else ElMessage({ message: '系统繁忙，请稍后再试', type: 'error' });
  } finally {
    profileSubmitting.value = false;
  }
}

/* -------------------- ② 安全 -------------------- */
const pwFormRef = ref<FormInstance>();
const pwSubmitting = ref(false);
const pwForm = reactive({ oldPassword: '', newPassword: '' });

const pwRules: FormRules<typeof pwForm> = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    {
      validator: (_rule, value, cb) => {
        if (!value || !PASSWORD_RULES.every((r) => r.test(value))) {
          cb(new Error('新密码需满足全部规则'));
        } else {
          cb();
        }
      },
    },
  ],
};

async function onChangePassword(): Promise<void> {
  if (!pwFormRef.value) return;
  try {
    await pwFormRef.value.validate();
  } catch {
    return;
  }
  pwSubmitting.value = true;
  try {
    await auth.changePassword({ oldPassword: pwForm.oldPassword, newPassword: pwForm.newPassword });
    ElMessage({ message: '密码已修改，请重新登录', type: 'success' });
    await router.push({ name: 'login', query: { reason: 'password-changed' } });
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
    else ElMessage({ message: '系统繁忙，请稍后再试', type: 'error' });
  } finally {
    pwSubmitting.value = false;
  }
}

/* -------------------- ③ 注销账号 -------------------- */
// 双 confirm：先 prompt 输入当前密码，再 confirm 不可恢复，最后调 API。
// 任何一步取消都静默退出。
async function onDeleteAccount(): Promise<void> {
  let password: string;
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入当前密码以确认注销',
      '注销账号',
      {
        type: 'warning',
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '当前密码',
        inputValidator: (v: string) => (v && v.length >= 1 ? true : '请输入密码'),
      },
    );
    password = value;
  } catch {
    return;
  }
  try {
    await ElMessageBox.confirm(
      '此操作不可恢复，确定要永久注销账号吗？',
      '最后确认',
      {
        type: 'warning',
        confirmButtonText: '永久注销',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  try {
    await auth.deleteAccount({ password });
    ElMessage({ message: '账号已注销', type: 'success' });
    await router.push({ name: 'login', query: { reason: 'account-deleted' } });
  } catch (e) {
    if (e instanceof ApiError) showAuthError(e.code);
    else ElMessage({ message: '系统繁忙，请稍后再试', type: 'error' });
  }
}
</script>

<style scoped>
.settings-view {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.settings-view__main {
  flex: 1;
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 20px 64px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 8px 4px 0;
}
.settings-header__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
.settings-header__back {
  font-size: 14px;
  color: #409eff;
  text-decoration: none;
}
.settings-header__back:hover {
  text-decoration: underline;
}

.settings-card {
  border-radius: 12px;
}
.settings-card__title {
  font-weight: 600;
  color: #303133;
}
.settings-card--danger {
  border-color: #fbc4c4;
}
.settings-card--danger :deep(.el-card__header) {
  color: #c45656;
}

.settings-hint {
  margin: -8px 0 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.danger-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
</style>