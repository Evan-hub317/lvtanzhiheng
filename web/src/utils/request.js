import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * Axios 统一封装
 * - 请求头自动携带 satoken
 * - 业务码非 200 统一提示；401 跳转登录
 */
// 部署时可在 public/config.js 中直接填写后端地址（不做 Nginx 反代）；默认走相对路径（Vite 代理）
const API_BASE = (window.APP_CONFIG && window.APP_CONFIG.API_BASE) || '/api'

const request = axios.create({
  baseURL: API_BASE,
  timeout: 15000
})

/** 401 处理去重：并发多个接口同时 401 时只弹一次提示、只跳一次登录页 */
let handling401 = false

request.interceptors.request.use(config => {
  const token = localStorage.getItem('satoken')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

request.interceptors.response.use(
  res => {
    const data = res.data
    if (data.code === 200) {
      return data
    }
    if (data.code === 401) {
      const hadToken = !!localStorage.getItem('satoken')
      localStorage.removeItem('satoken')
      // 本地已无 token（刚退出登录）时，401 是登出前在途请求被吊销后的必然结果，
      // 静默拒绝、不弹提示不跳转（避免退出登录瞬间弹「未登录或登录已过期」）
      if (!hadToken) {
        return Promise.reject(new Error(data.msg))
      }
      if (!handling401 && router.currentRoute.value.path !== '/login') {
        handling401 = true
        ElMessage.error(data.msg || '登录已过期，请重新登录')
        router.push('/login').finally(() => {
          handling401 = false
        })
      }
      return Promise.reject(new Error(data.msg))
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(new Error(data.msg))
  },
  err => {
    const msg = err.response?.data?.msg || err.message || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default request
