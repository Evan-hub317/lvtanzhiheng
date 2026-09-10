import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * Axios 统一封装
 * - 请求头自动携带 satoken
 * - 业务码非 200 统一提示；401 跳转登录
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

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
      localStorage.removeItem('satoken')
      ElMessage.error(data.msg || '登录已过期，请重新登录')
      router.push('/login')
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
