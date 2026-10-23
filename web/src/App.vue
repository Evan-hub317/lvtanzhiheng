<template>
  <router-view v-slot="{ Component }">
    <!-- 仅缓存 Layout（跳大屏时不销毁布局及其内部页面缓存）；
         大屏（碳排放热力图）不缓存，每次进入重新加载；登录页等其余路由也不缓存 -->
    <keep-alive include="LayoutPage" :key="authKey">
      <component :is="Component" />
    </keep-alive>
  </router-view>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
// 登录态变化（登出/换账号）时 keep-alive 整体重建 → 缓存清空，
// 避免上一个账号的页面数据（图表/筛选/聊天记录）残留给下一个登录者。
// 注意 key 必须挂在 keep-alive 上而非组件上：挂在组件上会让不同页面共用同一缓存键，导致路由切换页面不更新
const authKey = computed(() => userStore.token || 'guest')
</script>
