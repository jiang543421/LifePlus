<!--
  TopBar — 首页仪表盘顶栏（spec §04 §4 + ui-prototype 03）。

  三栏布局：
  - 左：头像（昵称首字母）+ ElDropdown（账号信息 + 退出登录）
  - 中：品牌名 "LifePulse" + 副标 "数字生活"
  - 右：设置图标 → /settings（router-link）

  响应式（@vueuse/core useBreakpoints）：
  - ≥768px：完整三栏；ElDropdown 触发器可见
  - <768px：仅 logo + 标题 + 汉堡按钮；点击展开 ElDrawer

  ⚠️ /settings 路由当前未在 router 声明，运行时未匹配会走 catch-all 重定向 `/`。
  待 SettingsView 落地后此 TopBar 自动生效，无需改动本组件。
-->
<template>
  <header class="topbar">
    <!-- 左：桌面态头像 dropdown 触发器；移动态隐藏（仅汉堡） -->
    <div class="topbar__left">
      <ElDropdown
        v-if="!isMobile"
        trigger="click"
        data-testid="topbar-avatar-trigger"
      >
        <span class="topbar__avatar" data-testid="topbar-avatar">
          {{ initial }}
        </span>
        <template #dropdown>
          <ElDropdownMenu>
            <div class="topbar__account" data-testid="topbar-account-info">
              <div class="topbar__account-name">{{ displayName }}</div>
              <div class="topbar__account-email">{{ maskedEmail }}</div>
            </div>
            <ElDropdownItem
              divided
              data-testid="topbar-logout"
              @click="onLogout"
            >
              退出登录
            </ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
    </div>

    <!-- 中：品牌名 + 副标 -->
    <div class="topbar__center" data-testid="topbar-brand">
      <span class="topbar__brand-name">LifePulse</span>
      <span class="topbar__brand-sub">数字生活</span>
    </div>

    <!-- 右：设置图标 -->
    <div class="topbar__right">
      <router-link
        v-if="!isMobile"
        to="/settings"
        class="topbar__settings"
        data-testid="topbar-settings-link"
        aria-label="设置"
      >
        <ElIcon :size="20" aria-hidden="true">
          <Setting />
        </ElIcon>
      </router-link>
      <ElButton
        v-else
        link
        class="topbar__hamburger"
        data-testid="topbar-hamburger"
        aria-label="打开菜单"
        @click="drawerOpen = true"
      >
        <ElIcon :size="22" aria-hidden="true">
          <Menu />
        </ElIcon>
      </ElButton>
    </div>

    <!-- 移动态 Drawer：用纯 div + CSS slide 实现，避开 Element Plus ElDrawer
         Teleport 在测试中不绑定 @click 的坑。视觉等效（遮罩 + 右侧滑入）。 -->
    <Teleport to="body">
      <div
        v-if="drawerOpen"
        class="topbar__drawer-root"
        data-testid="topbar-drawer"
        role="dialog"
        aria-modal="true"
      >
        <div
          class="topbar__drawer-mask"
          data-testid="topbar-drawer-mask"
          @click="drawerOpen = false"
        ></div>
        <div class="topbar__drawer-panel">
          <div class="topbar__drawer-account">
            <span class="topbar__avatar topbar__avatar--lg">{{ initial }}</span>
            <div class="topbar__account-name">{{ displayName }}</div>
            <div class="topbar__account-email">{{ maskedEmail }}</div>
          </div>
          <div class="topbar__drawer-divider"></div>
          <router-link
            to="/settings"
            class="topbar__drawer-link"
            data-testid="topbar-settings-link"
            @click="drawerOpen = false"
          >
            <ElIcon :size="18" aria-hidden="true"><Setting /></ElIcon>
            <span>设置</span>
          </router-link>
          <div class="topbar__drawer-divider"></div>
          <button
            type="button"
            class="topbar__drawer-link topbar__drawer-link--button"
            data-testid="topbar-logout"
            @click="onLogout"
          >
            <ElIcon :size="18" aria-hidden="true"><SwitchButton /></ElIcon>
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </Teleport>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useBreakpoints } from '@vueuse/core';
import {
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElIcon,
  ElButton,
} from 'element-plus';
import { Setting, Menu, SwitchButton } from '@element-plus/icons-vue';
import { useAuthStore } from '@/stores/auth';

// 768px 断点（spec §04 §4）：<768 视为移动。
const breakpoints = useBreakpoints({ sm: 768 });
const isMobile = breakpoints.smaller('sm');

const auth = useAuthStore();
const router = useRouter();
const drawerOpen = ref(false);

// 首字母：nickname > email > '?'
const initial = computed<string>(() => {
  const n = auth.user?.nickname;
  if (n && n.length > 0) return n.charAt(0);
  const e = auth.user?.email;
  if (e && e.length > 0) return e.charAt(0).toUpperCase();
  return '?';
});

// 展示名：nickname > email 前缀 > '用户'
const displayName = computed<string>(
  () => auth.user?.nickname ?? auth.user?.email?.split('@')[0] ?? '用户',
);

// 邮箱掩码：保留 local 前 2 位 + ***@domain（CLAUDE.md §7.3 不打完整 email）。
const maskedEmail = computed<string>(() => {
  const e = auth.user?.email;
  if (!e) return '';
  const at = e.indexOf('@');
  if (at <= 0) return '';
  const local = e.slice(0, at);
  const domain = e.slice(at + 1);
  const head = local.slice(0, 2);
  return `${head}***@${domain}`;
});

async function onLogout(): Promise<void> {
  drawerOpen.value = false;
  await auth.logout();
  router.push('/login');
}
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.topbar__left,
.topbar__right {
  flex: 1;
  display: flex;
  align-items: center;
}

.topbar__right {
  justify-content: flex-end;
}

.topbar__center {
  flex: 0 0 auto;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.topbar__brand-name {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0.5px;
}

.topbar__brand-sub {
  font-size: 13px;
  color: #909399;
}

.topbar__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}

.topbar__avatar--lg {
  width: 56px;
  height: 56px;
  font-size: 22px;
}

.topbar__settings {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #606266;
  text-decoration: none;
  transition: background-color 0.15s ease;
}

.topbar__settings:hover {
  background: #f2f3f5;
}

.topbar__hamburger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
}

.topbar__account {
  padding: 8px 12px;
  min-width: 200px;
}

.topbar__account-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.topbar__account-email {
  font-size: 12px;
  color: #909399;
}

.topbar__drawer-account {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
}

.topbar__drawer-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  color: #303133;
  text-decoration: none;
  font-size: 15px;
  border: 0;
  background: transparent;
  width: 100%;
  text-align: left;
  cursor: pointer;
}

.topbar__drawer-link:hover {
  background: #f2f3f5;
}

.topbar__drawer-link--button {
  font: inherit;
}

.topbar__drawer-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  justify-content: flex-end;
}

.topbar__drawer-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
}

.topbar__drawer-panel {
  position: relative;
  width: 280px;
  max-width: 80vw;
  height: 100%;
  background: #fff;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  animation: topbar-drawer-slide-in 0.2s ease-out;
}

@keyframes topbar-drawer-slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.topbar__drawer-divider {
  height: 1px;
  background: #ebeef5;
  margin: 4px 0;
}
</style>
