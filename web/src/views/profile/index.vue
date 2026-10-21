<template>
  <div class="profile-page">
    <el-row :gutter="16">
      <!-- 左侧：个人卡片（底部含平台模块导航区） -->
      <el-col :xs="24" :md="8">
        <div class="profile-card">
          <div class="card-banner"></div>
          <div class="avatar-ring">
            <div class="avatar-big">{{ avatarText }}</div>
          </div>
          <div class="profile-name">{{ displayName }}</div>
          <div class="profile-username">@{{ userStore.userInfo?.username }}</div>
          <div class="role-pill" :class="userStore.isAdmin ? 'admin' : 'user'">
            <el-icon><component :is="userStore.isAdmin ? 'Cpu' : 'User'" /></el-icon>
            {{ userStore.isAdmin ? '系统管理员' : '业务用户' }}
          </div>
          <p class="profile-desc">绿碳智衡 · 碳排放大数据监测与仿真决策平台</p>
          <div class="module-section">
            <div class="module-title">
              <el-icon class="title-icon"><Compass /></el-icon>平台模块
            </div>
            <div class="module-grid">
              <div v-for="m in modules" :key="m.path" class="module-item" @click="router.push(m.path)">
                <span class="module-icon" :style="{ background: m.bg }">
                  <el-icon><component :is="m.icon" /></el-icon>
                </span>
                <span class="module-name">{{ m.name }}</span>
              </div>
            </div>
          </div>
          
        </div>
      </el-col>

      <!-- 右侧：资料与密码两卡上下排列，等高占满 -->
      <el-col :xs="24" :md="16">
        <div class="right-stack">
          <div class="tech-panel form-panel form-basic">
            <div class="tech-panel-title">
              <el-icon class="title-icon"><EditPen /></el-icon>基本资料
            </div>
            <el-form label-position="top" class="profile-form">
              <el-form-item label="用户名">
                <el-input :model-value="userStore.userInfo?.username" disabled>
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item label="姓名">
                <el-input v-model="profileForm.realName" placeholder="请输入姓名" maxlength="20" show-word-limit>
                  <template #prefix><el-icon><EditPen /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-button type="primary" class="block-btn" :loading="savingProfile" @click="handleSaveProfile">
                <el-icon style="margin-right: 4px"><Check /></el-icon>保存资料
              </el-button>
            </el-form>
          </div>
          <div class="tech-panel form-panel form-pwd">
            <div class="tech-panel-title">
              <el-icon class="title-icon"><Lock /></el-icon>修改密码
            </div>
            <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-position="top" class="profile-form">
              <el-form-item label="旧密码" prop="oldPassword">
                <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码">
                  <template #prefix><el-icon><Key /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位">
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码">
                  <template #prefix><el-icon><CircleCheck /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-button type="primary" class="block-btn" :loading="savingPwd" @click="handleChangePassword">
                <el-icon style="margin-right: 4px"><Key /></el-icon>修改密码
              </el-button>
            </el-form>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePasswordApi, updateProfileApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

/** 平台模块导航（与侧栏菜单一致，个人中心快捷入口） */
const modules = [
  { name: '数据总览', path: '/dashboard', icon: 'Odometer', bg: 'linear-gradient(135deg, #0ea5e9, #0369a1)' },
  { name: '碳排放热力图', path: '/screen', icon: 'Monitor', bg: 'linear-gradient(135deg, #06b6d4, #0e7490)' },
  { name: '数据分析', path: '/analysis', icon: 'TrendCharts', bg: 'linear-gradient(135deg, #8b5cf6, #6d28d9)' },
  { name: '情景仿真', path: '/simulation', icon: 'MagicStick', bg: 'linear-gradient(135deg, #10b981, #047857)' },
  { name: '预警中心', path: '/alert', icon: 'Bell', bg: 'linear-gradient(135deg, #f59e0b, #d97706)' },
  { name: 'AI 知识库', path: '/ai-chat', icon: 'ChatDotRound', bg: 'linear-gradient(135deg, #0ea5e9, #0284c7)' },
  { name: 'AI 分析助手', path: '/agent', icon: 'Cpu', bg: 'linear-gradient(135deg, #ec4899, #db2777)' },
  { name: '监测报告', path: '/report', icon: 'Document', bg: 'linear-gradient(135deg, #64748b, #475569)' }
]

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户'
)
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase())

const profileForm = reactive({ realName: '' })
watch(
  () => userStore.userInfo,
  info => {
    if (info) profileForm.realName = info.realName || ''
  },
  { immediate: true }
)

const savingProfile = ref(false)
async function handleSaveProfile() {
  if (!profileForm.realName.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  savingProfile.value = true
  try {
    await updateProfileApi(profileForm.realName.trim())
    await userStore.fetchUserInfo()
    ElMessage.success('资料已更新')
  } finally {
    savingProfile.value = false
  }
}

const pwdFormRef = ref()
const savingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度需为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        value === pwdForm.newPassword ? callback() : callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur'
    }
  ]
}

async function handleChangePassword() {
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  savingPwd.value = true
  try {
    await changePasswordApi({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    await userStore.logout()
    router.push('/login')
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingPwd.value = false
  }
}
</script>

<style scoped>
/* 占满视口可用高度：100vh - 顶栏 60px - 内容区上下内边距 32px */
.profile-page {
  height: calc(100vh - 92px);
  min-height: 620px;
}
.profile-page :deep(.el-row),
.profile-page :deep(.el-col) {
  height: 100%;
}


/* ===== 左侧个人卡片（含底部模块导航区） ===== */
.profile-card {
  position: relative;
  display: flex;
  flex-direction: column;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 20px rgba(9, 55, 86, 0.1);
  padding: 0 24px 20px;
  overflow: hidden;
  height: 100%;
  min-height: 0;
}
.card-banner {
  height: 96px;
  flex-shrink: 0;
  margin: 0 -24px;
  background:
    radial-gradient(circle at 18% 30%, rgba(255, 255, 255, 0.18) 0, transparent 40%),
    radial-gradient(circle at 85% 70%, rgba(255, 255, 255, 0.12) 0, transparent 45%),
    linear-gradient(100deg, #0b3354 0%, #0a6f8c 55%, #0f7a5c 100%);
}
.avatar-ring {
  width: 96px;
  height: 96px;
  margin: -48px auto 0;
  flex-shrink: 0;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 18px rgba(9, 55, 86, 0.25);
}
.avatar-big {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #0ea5e9, #10b981);
}
.profile-name {
  margin-top: 14px;
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}
.profile-username {
  margin-top: 4px;
  font-size: 13px;
  color: #98a4b3;
}
.role-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 12px;
  padding: 4px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  align-self: center;
  border: 1px solid transparent;
}
/* 浅色身份标签（同 Element Plus warning/primary 浅色风格） */
.role-pill.admin {
  color: #e6a23c;
  background: #fdf6ec;
  border-color: #f5dab1;
}
.role-pill.user {
  color: #409eff;
  background: #ecf5ff;
  border-color: #d9ecff;
}
.profile-desc {
  margin: 14px 0 0;
  font-size: 12px;
  color: #98a4b3;
}

/* ===== 平台模块导航区（卡片内嵌，浅灰底；占满剩余高度） ===== */
.module-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 14px;
  padding: 12px 14px 14px;
  background: #f6f8fa;
  border-radius: 10px;
  text-align: left;
}
.module-title {
  display: flex;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #51606e;
}
.module-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: repeat(4, 1fr);
  gap: 8px;
  margin-top: 10px;
}
.module-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.module-item:hover {
  background: #fff;
  border-color: rgba(14, 165, 233, 0.3);
  box-shadow: 0 2px 8px rgba(9, 55, 86, 0.08);
}
.module-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
}
.module-name {
  font-size: 13px;
  color: #33475e;
  font-weight: 500;
}

/* ===== 右侧表单卡（上下排列；修改密码字段多，占比更高） ===== */
.right-stack {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.form-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 20px 24px;
}
.form-basic {
  flex: 3;
}
.form-pwd {
  flex: 4;
}
.title-icon {
  margin-right: 6px;
  color: #0ea5e9;
  vertical-align: -2px;
}
.profile-form {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 8px;
}
.profile-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.block-btn {
  width: 100%;
  margin-top: auto;
}
</style>
