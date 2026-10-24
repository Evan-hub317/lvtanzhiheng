import { defineStore } from 'pinia'
import { loginApi, logoutApi, getUserInfoApi } from '@/api/auth'

/**
 * 用户状态：token + 用户信息
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('satoken') || '',
    userInfo: null
  }),
  getters: {
    isAdmin: state => state.userInfo?.roleCode === 'ADMIN'
  },
  actions: {
    async login(form) {
      const res = await loginApi(form)
      this.token = res.data.token
      localStorage.setItem('satoken', this.token)
      // 保存后端返回的完整用户信息（含邮箱/手机号/头像/注册时间），
      // 避免登录后个人中心缺字段、需刷新才补齐
      this.userInfo = res.data
      return res
    },
    async fetchUserInfo() {
      const res = await getUserInfoApi()
      this.userInfo = res.data
      return res
    },
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 后端登出失败不阻塞本地清理
      }
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('satoken')
    }
  }
})
