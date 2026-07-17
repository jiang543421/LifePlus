<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/api/http';
import { showAuthError } from '@/utils/error';
import { safeRedirect } from '@/utils/safeRedirect';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive({ email: '', password: '' });

// 规则不使用 trigger:'blur'，确保 ElForm.validate() 立即对所有字段生效
// （trigger 在未触发时会让 validate() 误报 invalid）。
const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email', message: '邮箱格式不正确' },
  ],
  password: [{ required: true, message: '请输入密码' }],
};

async function submit(): Promise<void> {
  if (!formRef.value) return;
  // 防御性预校验（独立于 ElForm.validate）：表单输入合法才调 API。
  const emailOk = !!form.email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email);
  if (!emailOk || !form.password) {
    // 让 ElForm.validate 渲染错误消息（on invalid），不影响早退
    await formRef.value.validate().catch(() => undefined);
    return;
  }
  submitting.value = true;
  try {
    await auth.login(form.email, form.password);
    // safeRedirect: 防止 ?redirect=https://evil.com 等 open redirect 攻击（CLAUDE.md §7 + Review C-4）
    const redirect = safeRedirect(route.query.redirect);
    await router.push(redirect);
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
        <p>数字生活 · 登录</p>
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
          <el-input v-model="form.password" type="password" show-password placeholder="••••••••" autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="submit-btn" native-type="submit" @click="submit">
          登 录
        </el-button>
      </el-form>
      <p class="footer-link">
        还没账号？<router-link to="/register">立即注册 →</router-link>
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
  max-width: 400px;
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
