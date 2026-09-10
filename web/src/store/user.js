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
      this.userInfo = {
        username: res.data.username,
        realName: res.data.realName,
        roleCode: res.data.roleCode
      }
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
