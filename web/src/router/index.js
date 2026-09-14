import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/screen',
    name: 'Screen',
    component: () => import('@/views/screen/index.vue'),
    meta: { title: '全国碳排放监测大屏' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据总览', icon: 'Odometer' }
      },
      {
        path: 'analysis',
        name: 'Analysis',
        component: () => import('@/views/analysis/index.vue'),
        meta: { title: '数据分析', icon: 'TrendCharts' }
      },
      {
        path: 'simulation',
        name: 'Simulation',
        component: () => import('@/views/simulation/index.vue'),
        meta: { title: '情景仿真', icon: 'MagicStick' }
      },
      {
        path: 'alert',
        name: 'Alert',
        component: () => import('@/views/alert/index.vue'),
        meta: { title: '预警中心', icon: 'Bell' }
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '监测报告', icon: 'Document' }
      },
      {
        path: 'ai-chat',
        name: 'AiChat',
        component: () => import('@/views/ai-chat/index.vue'),
        meta: { title: 'AI 碳管家', icon: 'ChatDotRound' }
      },
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', roles: ['ADMIN'] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'UserFilled' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局守卫：登录校验 + 角色权限 + 动态标题
router.beforeEach(async (to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 绿碳智衡` : '绿碳智衡'

  if (to.path === '/login') {
    next()
    return
  }

  const userStore = useUserStore()
  if (!userStore.token) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (e) {
      localStorage.removeItem('satoken')
      next('/login')
      return
    }
  }

  if (to.meta.roles && !to.meta.roles.includes(userStore.userInfo.roleCode)) {
    ElMessage.warning('无权限访问该页面')
    next('/dashboard')
    return
  }
  next()
})

export default router
