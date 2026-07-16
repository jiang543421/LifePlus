<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import { greeting, todayDateLine } from '@/utils/time';

const auth = useAuthStore();
</script>

<template>
  <main class="home">
    <p class="greet">{{ greeting() }}</p>
    <h1>欢迎回来，{{ auth.greetingName }}</h1>
    <p class="date">{{ todayDateLine() }}</p>
    <p class="hint">数字生活仪表盘将在 Phase 4 上线。</p>
  </main>
</template>

<style scoped>
.home {
  max-width: 720px;
  margin: 0 auto;
  padding: 48px 24px;
}
.greet {
  color: var(--el-text-color-secondary);
  margin: 0;
}
h1 {
  margin: 4px 0 8px;
}
.date {
  color: var(--el-text-color-secondary);
  margin: 0 0 24px;
}
.hint {
  color: var(--el-text-color-regular);
}
</style>
