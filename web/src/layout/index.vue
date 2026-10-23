<template>
  <el-container class="layout">
    <!-- 深色科技风侧栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <img class="logo-icon" src="/favicon.ico" alt="logo" />
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
          <!-- 标签页导航：与面包屑同行；点击切换，× 关闭释放缓存（仅剩一个标签时不可关闭） -->
          <div class="tabs-bar">
            <div
              v-for="tab in tabs"
              :key="tab.path"
              class="tab-item"
              :class="{ active: route.path === tab.path }"
              :style="{ flexShrink: tab.title.length > 4 ? 2 : 1 }"
              @click="router.push(tab.path)"
            >
              <span class="tab-title">{{ tab.title }}</span>
              <el-icon v-if="tabs.length > 1" class="tab-close" @click.stop="closeTab(tab)"><Close /></el-icon>
            </div>
          </div>
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
            <!-- 页面缓存：仅缓存标签页打开的页面（include 按组件名匹配）；
                 关闭标签即从 include 移除 → 对应缓存被释放 -->
            <keep-alive :include="cachedNames">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

// App 级 keep-alive 缓存名：跳转大屏（布局外顶级路由）时 Layout 仅休眠不销毁，
// 内部各页面的 keep-alive 缓存得以保留，返回时不整页重载
defineOptions({ name: 'LayoutPage' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

// ===== 标签页导航（已打开页面） =====
/** 路径 → 页面组件名（keep-alive include 匹配；与各页面 defineOptions 的 name 对应） */
const keepNameMap = {
  '/dashboard': 'DashboardPage',
  '/analysis': 'AnalysisPage',
  '/simulation': 'SimulationPage',
  '/alert': 'AlertPage',
  '/report': 'ReportPage',
  '/ai-chat': 'AiChatPage',
  '/agent': 'AgentPage',
  '/system/user': 'UserPage',
  '/profile': 'ProfilePage'
}
const tabs = ref([])
const cachedNames = ref([])

watch(
  () => route.path,
  path => {
    const name = keepNameMap[path]
    if (!name) return   // 非内页（如跳转大屏），不记录标签
    if (!tabs.value.some(t => t.path === path)) {
      tabs.value.push({ path, title: route.meta.title || path })
      cachedNames.value.push(name)
    }
  },
  { immediate: true }
)

/** 关闭标签：从列表与缓存 include 中移除（keep-alive 随即释放该页内存）；
 *  仅剩一个标签时不允许关闭（保持始终有一个页面打开） */
function closeTab(tab) {
  if (tabs.value.length <= 1) return
  const idx = tabs.value.findIndex(t => t.path === tab.path)
  if (idx === -1) return
  tabs.value.splice(idx, 1)
  cachedNames.value = cachedNames.value.filter(n => n !== keepNameMap[tab.path])
  // 关闭的是当前页：跳到相邻标签
  if (route.path === tab.path) {
    router.push(tabs.value[Math.max(0, idx - 1)].path)
  }
}

// 侧栏菜单（与路由 meta 一致，roles 控制可见性）
const allMenus = [
  { path: '/dashboard', title: '数据总览', icon: 'Odometer' },
  { path: '/screen', title: '碳排放热力图', icon: 'Monitor' },
  { path: '/analysis', title: '数据分析', icon: 'TrendCharts' },
  { path: '/simulation', title: '情景仿真', icon: 'MagicStick' },
  { path: '/ai-chat', title: 'AI 知识库', icon: 'ChatDotRound' },
  { path: '/agent', title: 'AI 分析助手', icon: 'Cpu' },
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
  width: 26px;
  height: 26px;
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
  flex: 1; /* 占满剩余空间 */
  min-width: 0; /* 允许收缩到内容以下：标签溢出只裁标签，绝不挤右侧用户区 */
}
.collapse-btn {
  flex-shrink: 0; /* 折叠按钮与面包屑不参与收缩，只收缩标签栏 */
  font-size: 18px;
  color: #51606e;
  cursor: pointer;
}
.header-left :deep(.el-breadcrumb) {
  flex-shrink: 0;
  width: 140px; /* 固定宽度：面包屑文字长短变化不带动标签栏位移 */
  overflow: hidden;
  white-space: nowrap;
}
.header-left :deep(.el-breadcrumb__item) {
  min-width: 0;
  overflow: hidden;
}
.header-left :deep(.el-breadcrumb__inner) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.layout-header :deep(.el-dropdown) {
  flex-shrink: 0; /* 用户区固定，不被任何情况挤压 */
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

/* ===== 标签页导航（与面包屑同行的页签，可点击切换、可关闭释放缓存） =====
   标签总宽以不挤压右侧用户区为上限（flex:1 + min-width:0 已限制容器），
   空间不足时标签按原宽度比例收缩，长标签的文字先被省略（…），不产生滚动 */
.tabs-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 14px;
  margin-right: 16px; /* 与右侧用户区保持间隔，标签再多也不贴头像 */
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}
.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 12px;
  border: 1px solid #e3ebf3;
  background: #f0f4f8;
  color: #51606e;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 1; /* 允许收缩：配合行内 flexShrink，较长的标题承担更多省略 */
  min-width: 70px; /* 收缩下限：保证关闭按钮和约 2-3 个字可见 */
  max-width: 160px; /* 正常宽度上限（防超长标题） */
}
.tab-close {
  flex-shrink: 0; /* 关闭按钮不参与收缩 */
}
.tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tab-item.active {
  background: #e8f4fd;
  border-color: #0ea5e9;
  color: #0b84bb;
  font-weight: 600;
}
.tab-item:hover {
  color: #0b84bb;
}
.tab-close {
  font-size: 12px;
  padding: 2px;
  border-radius: 50%;
  color: #98a4b3;
}
.tab-close:hover {
  background: #e3ebf3;
  color: #f56c6c;
}
</style>
