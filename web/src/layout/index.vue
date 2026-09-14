<template>
  <el-container class="layout">
    <!-- 深色科技风侧栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span class="logo-icon">🌿</span>
        <span v-show="!collapsed" class="logo-text tech-gradient-text">绿碳智衡</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="layout-body">
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <el-dropdown @command="handleCommand">
          <span class="header-user">
            <el-avatar :size="32" class="user-avatar">{{ avatarText }}</el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-icon class="user-arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><UserFilled /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

// 侧栏菜单（与路由 meta 一致，roles 控制可见性）
const allMenus = [
  { path: '/dashboard', title: '数据总览', icon: 'Odometer' },
  { path: '/analysis', title: '数据分析', icon: 'TrendCharts' },
  { path: '/simulation', title: '情景仿真', icon: 'MagicStick' },
  { path: '/ai-chat', title: 'AI 碳管家', icon: 'ChatDotRound' },
  { path: '/alert', title: '预警中心', icon: 'Bell' },
  { path: '/report', title: '监测报告', icon: 'Document' },
  { path: '/system/user', title: '用户管理', icon: 'User', roles: ['ADMIN'] },
  { path: '/profile', title: '个人中心', icon: 'UserFilled' }
]
const menuItems = computed(() =>
  allMenus.filter(m => !m.roles || m.roles.includes(userStore.userInfo?.roleCode))
)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')
const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户'
)
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase())

async function handleCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* ===== 侧栏 ===== */
.layout-aside {
  background: linear-gradient(180deg, #0d1a30 0%, #0b1526 60%, #091120 100%);
  border-right: 1px solid rgba(0, 212, 255, 0.08);
  transition: width 0.25s ease;
  overflow-x: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-icon {
  font-size: 22px;
}
.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 3px;
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
  padding-top: 8px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #8fa3bd;
  --el-menu-hover-bg-color: rgba(0, 212, 255, 0.08);
  --el-menu-hover-text-color: #e6f1ff;
  --el-menu-active-color: #00d4ff;
}
.sidebar-menu :deep(.el-menu-item) {
  position: relative;
  margin: 4px 10px;
  border-radius: 8px;
}
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(0, 212, 255, 0.16), rgba(16, 185, 129, 0.08));
}
.sidebar-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  border-radius: 2px;
  background: linear-gradient(180deg, #00d4ff, #10b981);
}

/* ===== 顶栏 ===== */
.layout-body {
  min-width: 0;
}
.layout-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8eef5;
  box-shadow: 0 1px 6px rgba(16, 42, 67, 0.05);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.collapse-btn {
  font-size: 18px;
  color: #51606e;
  cursor: pointer;
}
.collapse-btn:hover {
  color: #0ea5e9;
}
.header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.user-avatar {
  background: linear-gradient(135deg, #0ea5e9, #10b981);
  color: #fff;
  font-weight: 600;
}
.user-name {
  font-size: 14px;
  color: #1f2d3d;
}
.user-arrow {
  color: #98a4b3;
  font-size: 12px;
}

/* ===== 内容区 ===== */
.layout-main {
  background: var(--tech-bg);
  padding: 16px;
  overflow-y: auto;
}
</style>
