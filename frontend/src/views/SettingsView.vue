<!--
  SettingsView — 设置页 MVP1 空态（spec §04 §2 + ui-prototype 03 §2）。

  MVP1 不实现个人设置 / 修改昵称 / 修改密码 / 注销账号（MVP1 不做账号注销，
  PRD §6 Out of Scope）。本视图提供：
  - 顶栏（与 HomeView 一致）
  - 空态文案（"设置 / 敬请期待"）
  - 返回首页入口

  /settings 路由声明在 router/index.ts（需登录）。
-->
<template>
  <div class="settings-view">
    <TopBar />

    <main class="settings-view__main">
      <section class="settings-empty" data-testid="settings-empty">
        <h1 class="settings-empty__title">设置</h1>
        <p class="settings-empty__hint">敬请期待</p>
        <p class="settings-empty__sub">v1.1 即将推出</p>
        <router-link
          to="/"
          class="settings-empty__back"
          data-testid="settings-back-home"
        >
          ← 返回首页
        </router-link>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import TopBar from '@/components/TopBar.vue';
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
  padding: 64px 20px;
}

.settings-empty {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.10);
  padding: 56px 32px;
  text-align: center;
}

.settings-empty__title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.settings-empty__hint {
  font-size: 18px;
  color: #909399;
  margin: 0 0 4px;
}

.settings-empty__sub {
  font-size: 13px;
  color: #c0c4cc;
  margin: 0 0 32px;
}

.settings-empty__back {
  display: inline-block;
  font-size: 14px;
  color: #409eff;
  text-decoration: none;
  padding: 8px 16px;
  border-radius: 8px;
  transition: background-color 0.15s ease;
}

.settings-empty__back:hover {
  background: #ecf5ff;
}
</style>
