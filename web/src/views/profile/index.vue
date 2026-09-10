<template>
  <el-row :gutter="16">
    <!-- 左侧信息卡 -->
    <el-col :xs="24" :md="8">
      <div class="tech-panel profile-card">
        <div class="avatar-big">{{ avatarText }}</div>
        <div class="profile-name">{{ displayName }}</div>
        <div class="profile-username">@{{ userStore.userInfo?.username }}</div>
        <el-tag :type="userStore.isAdmin ? 'warning' : 'primary'" effect="light" round class="profile-role">
          {{ userStore.isAdmin ? '系统管理员' : '业务用户' }}
        </el-tag>
        <p class="profile-desc">区域碳排放大数据监测与仿真决策平台</p>
      </div>
    </el-col>

    <!-- 右侧表单 -->
    <el-col :xs="24" :md="16">
      <div class="tech-panel">
        <div class="tech-panel-title">基本资料</div>
        <el-form label-width="80px" style="max-width: 420px">
          <el-form-item label="用户名">
            <el-input :model-value="userStore.userInfo?.username" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="profileForm.realName" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存资料</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="tech-panel" style="margin-top: 16px">
        <div class="tech-panel-title">修改密码</div>
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px" style="max-width: 420px">
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20 位" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPwd" @click="handleChangePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePasswordApi, updateProfileApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

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
.profile-card {
  text-align: center;
  padding: 36px 20px;
}
.avatar-big {
  width: 84px;
  height: 84px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #0ea5e9, #10b981);
  box-shadow: 0 8px 20px rgba(14, 165, 233, 0.35);
}
.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}
.profile-username {
  margin-top: 6px;
  font-size: 13px;
  color: #98a4b3;
}
.profile-role {
  margin-top: 12px;
}
.profile-desc {
  margin-top: 18px;
  font-size: 12px;
  color: #98a4b3;
}
</style>
