import { ref, watch } from 'vue'

/**
 * 头像加载：通过后端接口流式获取（与知识库文件链路一致，带 token 请求），
 * 返回 blob objectURL——部署环境不依赖 /uploads/** 静态资源映射。
 * getAvatar：返回 userInfo.avatar（相对路径，仅作"是否已上传"标记）的取值函数。
 */
export function useAvatar(getAvatar) {
  const url = ref('')
  watch(
    getAvatar,
    async a => {
      if (url.value) {
        URL.revokeObjectURL(url.value)
        url.value = ''
      }
      if (!a) return
      try {
        const API_BASE = window.APP_CONFIG?.API_BASE || '/api'
        const token = localStorage.getItem('satoken')
        const resp = await fetch(`${API_BASE}/user/avatar`, { headers: { satoken: token } })
        if (!resp.ok) throw new Error('avatar load failed')
        url.value = URL.createObjectURL(await resp.blob())
      } catch {
        // 加载失败回退姓名首字
      }
    },
    { immediate: true }
  )
  return url
}
