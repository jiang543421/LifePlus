<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { PASSWORD_RULES } from '@/types';

const router = useRouter();
const auth = useAuthStore();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive({ email: '', password: '', nickname: '' });

// 实时校验：computed 遍历 PASSWORD_RULES，每条返回 ok 状态。
const ruleResults = computed(() =>
  PASSWORD_RULES.map((r) => ({ key: r.key, label: r.label, ok: r.test(form.password) })),
);
const passwordOk = computed(() => ruleResults.value.every((r) => r.ok));

const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, cb) => {
        if (!value || !passwordOk.value) {
          cb(new Error('密码需满足全部规则'));
        } else {
          cb();
        }
      },
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 32, message: '昵称最多 32 字符', trigger: 'blur' }],
};

async function submit(): Promise<void> {
  if (!formRef.value) return;
  // 防御性预校验：ElForm.validate 在 jsdom 下时序不可靠；这里独立校验
  // email + 密码规则，校验失败直接早退，不发请求。ElForm.validate 仍会执行以渲染错误提示。
  const emailOk = !!form.email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email);
  const passwordOk = PASSWORD_RULES.every((r) => r.test(form.password));
  if (!emailOk || !passwordOk) {
    await formRef.value.validate().catch(() => undefined);
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    await auth.register(form.email, form.password, form.nickname || undefined);
    await router.push('/');
  } catch (e) {
    if (e instanceof ApiError) {
      showAuthError(e.code);
    } else {
      ElMessage({ message: '系统繁忙，请稍后再试', type: 'error' });
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <header class="brand">
        <h1>LifePulse</h1>
        <p>数字生活 · 注册</p>
      </header>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" type="email" placeholder="you@example.com" autocomplete="email" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="••••••••" autocomplete="new-password" />
        </el-form-item>
        <ul class="password-rules">
          <li v-for="r in ruleResults" :key="r.key" :class="{ ok: r.ok }">
            <span class="mark">{{ r.ok ? '✓' : '✗' }}</span>{{ r.label }}
          </li>
        </ul>
        <el-form-item label="昵称（可选）" prop="nickname">
          <el-input v-model="form.nickname" maxlength="32" show-word-limit placeholder="选填，方便打招呼" />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="submit-btn" native-type="submit" @click="submit">
          注 册
        </el-button>
      </el-form>
      <p class="footer-link">
        已有账号？<router-link to="/login">立即登录 →</router-link>
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 440px;
}
.brand {
  text-align: center;
  margin-bottom: 24px;
}
.brand h1 {
  margin: 0;
  font-size: 28px;
  letter-spacing: 0.04em;
}
.brand p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}
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
.submit-btn {
  width: 100%;
}
.footer-link {
  text-align: center;
  margin: 16px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
