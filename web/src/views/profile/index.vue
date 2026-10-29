<template>
  <div class="profile-page">
    <div class="profile-layout">
      <!-- 左栏：用户概要 + 选项菜单（基本资料 / 账号密码） -->
      <aside class="profile-aside">
        <div class="aside-user">
          <div class="aside-avatar">
            <img v-if="avatarUrl" :src="avatarUrl" alt="avatar" />
            <span v-else>{{ avatarText }}</span>
          </div>
          <div class="aside-info">
            <div class="aside-name">{{ displayName }}</div>
            <div class="aside-meta">
              <span class="role-tag" :class="userStore.isAdmin ? 'admin' : 'user'">
                {{ userStore.isAdmin ? '系统管理员' : '业务用户' }}
              </span>
              <span class="aside-time">注册于 {{ createTimeText }}</span>
            </div>
          </div>
        </div>
        <div class="aside-menu">
          <div
            v-for="m in menuItems"
            :key="m.key"
            class="menu-item"
            :class="{ active: activeTab === m.key }"
            @click="activeTab = m.key"
          >
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.label }}</span>
          </div>
        </div>
      </aside>

      <!-- 右栏：当前选项的面板 -->
      <div class="profile-panel">
        <!-- 基本资料 -->
        <div v-show="activeTab === 'basic'" class="panel-card">
          <div class="card-title">
            <el-icon class="title-icon"><EditPen /></el-icon>基本资料
          </div>
          <!-- 头像：点击选择图片并框选裁剪 -->
          <div class="avatar-row">
            <div class="avatar-uploader" @click="fileInputRef?.click()">
              <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img" alt="avatar" />
              <span v-else class="avatar-fallback">{{ avatarText }}</span>
              <div class="avatar-mask">
                <el-icon><Camera /></el-icon>更换头像
              </div>
            </div>
            <div class="avatar-tip">点击头像上传图片，可框选区域裁剪</div>
          </div>
          <el-form label-position="top" class="profile-form">
            <el-form-item label="姓名">
              <el-input v-model="profileForm.realName" size="large" placeholder="请输入姓名" maxlength="20" show-word-limit>
                <template #prefix><el-icon><EditPen /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" size="large" placeholder="请输入邮箱">
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" size="large" placeholder="请输入手机号" maxlength="20">
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>
            <div class="btn-row">
              <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">
                <el-icon style="margin-right: 4px"><Check /></el-icon>保存资料
              </el-button>
              <el-button plain :disabled="savingProfile" @click="handleResetProfile">
                <el-icon style="margin-right: 4px"><RefreshLeft /></el-icon>重置
              </el-button>
            </div>
          </el-form>
        </div>

        <!-- 账号密码 -->
        <div v-show="activeTab === 'pwd'" class="panel-card">
          <div class="card-title">
            <el-icon class="title-icon"><Lock /></el-icon>账号密码
          </div>
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-position="top" class="profile-form">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.userInfo?.username" size="large" disabled>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" size="large" show-password placeholder="请输入旧密码">
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" size="large" show-password placeholder="6-20 位">
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" size="large" show-password placeholder="再次输入新密码">
                <template #prefix><el-icon><CircleCheck /></el-icon></template>
              </el-input>
            </el-form-item>
            <div class="btn-row">
              <el-button type="primary" :loading="savingPwd" @click="handleChangePassword">
                <el-icon style="margin-right: 4px"><Key /></el-icon>修改密码
              </el-button>
              <el-button plain :disabled="savingPwd" @click="handleResetPwd">
                <el-icon style="margin-right: 4px"><RefreshLeft /></el-icon>重置
              </el-button>
            </div>
          </el-form>
        </div>
      </div>
    </div>

    <!-- 头像裁剪对话框：选择图片后框选区域 -->
    <el-dialog v-model="cropVisible" title="裁剪头像" width="520px" :close-on-click-modal="false" destroy-on-close>
      <div class="crop-box">
        <VueCropper
          ref="cropperRef"
          :img="cropImg"
          output-type="png"
          :output-size="1"
          :auto-crop="true"
          :auto-crop-width="220"
          :auto-crop-height="220"
          :fixed-box="true"
          :center-box="true"
          :can-move-box="true"
          :info="true"
          :full="false"
        />
      </div>
      <template #footer>
        <el-button @click="cropVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="confirmCrop">确认上传</el-button>
      </template>
    </el-dialog>
    <!-- 隐藏的文件选择框 -->
    <input ref="fileInputRef" type="file" accept="image/*" style="display: none" @change="handleFileChange" />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

// keep-alive 标签页缓存名（布局 include 列表按此匹配）
defineOptions({ name: 'ProfilePage' })
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VueCropper } from 'vue-cropper'
import 'vue-cropper/dist/index.css'
import { changePasswordApi, updateProfileApi, uploadAvatarApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/utils/avatar'

const router = useRouter()
const userStore = useUserStore()

// ===== 左栏选项菜单 =====
const menuItems = [
  { key: 'basic', label: '基本资料', icon: 'User' },
  { key: 'pwd', label: '账号密码', icon: 'Lock' }
]
const activeTab = ref('basic')

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户'
)
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase())

// 头像：经后端接口流式获取（与知识库文件链路一致，不依赖 /uploads/** 静态映射）
const avatarUrl = useAvatar(() => userStore.userInfo?.avatar)
// 注册时间只显示到日
const createTimeText = computed(() => (userStore.userInfo?.createTime || '—').slice(0, 10))

// ===== 基本资料 =====
const profileForm = reactive({ realName: '', email: '', phone: '' })
watch(
  () => userStore.userInfo,
  info => {
    if (info) {
      profileForm.realName = info.realName || ''
      profileForm.email = info.email || ''
      profileForm.phone = info.phone || ''
    }
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
    await updateProfileApi({
      realName: profileForm.realName.trim(),
      email: profileForm.email.trim(),
      phone: profileForm.phone.trim()
    })
    await userStore.fetchUserInfo()
    ElMessage.success('资料已更新')
  } finally {
    savingProfile.value = false
  }
}

/** 重置基本资料：恢复为当前账号信息 */
function handleResetProfile() {
  profileForm.realName = userStore.userInfo?.realName || ''
  profileForm.email = userStore.userInfo?.email || ''
  profileForm.phone = userStore.userInfo?.phone || ''
}

// ===== 头像上传与裁剪 =====
const fileInputRef = ref()
const cropperRef = ref()
const cropVisible = ref(false)
const cropImg = ref('')
const uploading = ref(false)

function handleFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  cropImg.value = URL.createObjectURL(file)
  cropVisible.value = true
  e.target.value = '' // 允许再次选择同一文件
}

/** 确认裁剪：框选区域导出为 PNG 上传 */
function confirmCrop() {
  cropperRef.value?.getCropBlob(blob => {
    uploading.value = true
    const fd = new FormData()
    fd.append('file', blob, 'avatar.png')
    uploadAvatarApi(fd)
      .then(async () => {
        await userStore.fetchUserInfo() // 刷新 userInfo.avatar
        ElMessage.success('头像已更新')
        cropVisible.value = false
      })
      .finally(() => {
        uploading.value = false
      })
  })
}

// ===== 账号密码 =====
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

/** 重置修改密码表单：清空输入并清除全部校验提示 */
function handleResetPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}
</script>

<style scoped>
/* 占满视口可用高度，页面不滚动 */
.profile-page {
  height: calc(100vh - 92px);
  overflow: hidden;
}
.profile-layout {
  display: flex;
  gap: 16px;
  height: 100%;
}

/* ===== 左栏：用户概要 + 选项菜单 ===== */
.profile-aside {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  padding: 20px 14px;
}
.aside-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px 16px;
  border-bottom: 1px solid #eef2f7;
}
.aside-avatar {
  margin-left: 10px;
  width: 92px;
  height: 92px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #0ea5e9, #10b981);
}
.aside-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.aside-info {
  flex: 1; /* 占满头像右侧剩余宽度，文字在其内居中 */
  min-width: 0;
  height: 108px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.aside-name {
  font-size: 17px;
  font-weight: 600;
  color: #1f2d3d;
  max-width: 6em; /* 超过六个字省略 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.aside-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
}
.aside-time {
  font-size: 11px;
  color: #98a4b3;
}
.aside-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: #51606e;
  cursor: pointer;
  transition: all 0.2s;
}
.menu-item:hover {
  background: #f0f4f8;
  color: #0b84bb;
}
.menu-item.active {
  background: #e8f4fd;
  color: #0b84bb;
  font-weight: 600;
}

/* ===== 右栏：面板卡片 ===== */
.profile-panel {
  flex: 1;
  min-width: 0;
}
.panel-card {
  height: 100%;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  padding: 20px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.card-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 14px;
}
.title-icon {
  margin-right: 6px;
  color: #0ea5e9;
}

/* ===== 头像上传 ===== */
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}
.avatar-uploader {
  position: relative;
  width: 84px;
  height: 84px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 2px dashed #c9d4df;
}
.avatar-uploader:hover {
  border-color: #0ea5e9;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #0ea5e9, #10b981);
}
.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 12px;
  color: #fff;
  background: rgba(13, 26, 48, 0.5);
  opacity: 0;
  transition: opacity 0.2s;
}
.avatar-uploader:hover .avatar-mask {
  opacity: 1;
}
.avatar-tip {
  font-size: 12px;
  color: #98a4b3;
}

/* ===== 表单 ===== */
.profile-form {
  max-width: 480px;
}
.profile-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.profile-form :deep(.el-form-item__label) {
  color: #98a4b3;
  font-size: 13px;
  margin-bottom: 4px;
}
/* 输入框：可编辑为白底 + 淡边框，统一 40px 高；只读（禁用）为浅灰底 */
.profile-form :deep(.el-input__wrapper) {
  background-color: #fff;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}
.profile-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}
.profile-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0ea5e9 inset;
}
/* 禁用态：is-disabled 挂在 .el-input 根元素上（EP 实现），需从其下钻到 wrapper */
.profile-form :deep(.el-input.is-disabled .el-input__wrapper) {
  background-color: #f1f5f9;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}
.profile-form :deep(.el-input__inner) {
  height: 40px;
}

/* 角色标签（左栏概要区） */
.role-tag {
  padding: 2px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}
.role-tag.admin {
  color: #e6a23c;
  background: #fdf6ec;
  border-color: #f5dab1;
}
.role-tag.user {
  color: #409eff;
  background: #ecf5ff;
  border-color: #d9ecff;
}

/* 按钮：固定宽度并排靠左（间距 12px） */
.btn-row {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
}
.btn-row :deep(.el-button) {
  width: 120px;
  margin-left: 0;
}

/* ===== 裁剪对话框 ===== */
.crop-box {
  height: 360px;
}
</style>
