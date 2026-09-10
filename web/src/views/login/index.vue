<template>
  <div class="login-page">
    <!-- 背景装饰：网格 + 光斑 + 漂浮粒子 -->
    <div class="bg-grid"></div>
    <div class="bg-glow bg-glow-1"></div>
    <div class="bg-glow bg-glow-2"></div>
    <span v-for="p in particles" :key="p.id" class="particle" :style="p.style"></span>

    <!-- 玻璃拟态登录卡片 -->
    <div class="login-card">
      <div class="brand">
        <div class="brand-icon">🌿</div>
        <h1 class="brand-title tech-gradient-text">绿碳智衡</h1>
        <p class="brand-sub">区域碳排放大数据监测与仿真决策平台</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <p class="login-tip">演示账号 admin / admin123</p>
    </div>

    <p class="copyright">© 2026 绿碳智衡 · Regional Carbon Intelligence Platform</p>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 背景漂浮粒子
const particles = Array.from({ length: 18 }, (_, i) => ({
  id: i,
  style: {
    left: `${Math.random() * 100}%`,
    bottom: `${Math.random() * 50}%`,
    width: `${3 + Math.random() * 5}px`,
    height: `${3 + Math.random() * 5}px`,
    animationDelay: `${Math.random() * 8}s`,
    animationDuration: `${6 + Math.random() * 10}s`
  }
}))

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功，欢迎回来')
    const redirect = route.query.redirect
    router.push(redirect ? decodeURIComponent(redirect) : '/dashboard')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(ellipse at 20% 0%, #123057 0%, #0b1526 55%, #081020 100%);
}

/* 网格背景 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 212, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 212, 255, 0.06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 75%);
}

/* 光斑 */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.35;
}
.bg-glow-1 {
  width: 420px;
  height: 420px;
  left: -120px;
  top: -120px;
  background: #0ea5e9;
}
.bg-glow-2 {
  width: 380px;
  height: 380px;
  right: -100px;
  bottom: -100px;
  background: #10b981;
}

/* 漂浮粒子 */
.particle {
  position: absolute;
  border-radius: 50%;
  background: #00d4ff;
  box-shadow: 0 0 8px #00d4ff;
  animation: rise linear infinite;
}
@keyframes rise {
  0% {
    transform: translateY(0);
    opacity: 0;
  }
  12% {
    opacity: 0.85;
  }
  85% {
    opacity: 0.4;
  }
  100% {
    transform: translateY(-100vh);
    opacity: 0;
  }
}

/* 登录卡片 */
.login-card {
  position: relative;
  z-index: 2;
  width: 420px;
  padding: 44px 40px 32px;
  border-radius: 16px;
  background: rgba(13, 27, 48, 0.55);
  border: 1px solid rgba(0, 212, 255, 0.22);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.brand {
  text-align: center;
  margin-bottom: 32px;
}
.brand-icon {
  font-size: 44px;
  filter: drop-shadow(0 0 14px rgba(16, 185, 129, 0.6));
}
.brand-title {
  margin: 10px 0 8px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 6px;
}
.brand-sub {
  margin: 0;
  font-size: 13px;
  color: #7d93b0;
  letter-spacing: 1px;
}

.login-card :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(0, 212, 255, 0.18) inset;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #00d4ff inset, 0 0 12px rgba(0, 212, 255, 0.35);
}
.login-card :deep(.el-input__inner) {
  color: #e6f1ff;
}
.login-card :deep(.el-input__inner::placeholder) {
  color: #5b7393;
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  letter-spacing: 8px;
  border: none;
  background: linear-gradient(90deg, #0ea5e9 0%, #10b981 100%);
  transition: all 0.3s;
}
.login-btn:hover {
  opacity: 0.88;
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(14, 165, 233, 0.4);
}

.login-tip {
  margin: 18px 0 0;
  text-align: center;
  font-size: 12px;
  color: #4d668a;
}

.copyright {
  position: absolute;
  bottom: 22px;
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: #3d5273;
  z-index: 2;
}
</style>
