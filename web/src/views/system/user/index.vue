<template>
  <div class="tech-panel">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="query.username"
        placeholder="按用户名搜索"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="query.status" placeholder="账号状态" clearable style="width: 140px" @change="handleSearch">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <div class="search-right">
        <el-button type="primary" @click="openDialog()">新增用户</el-button>
      </div>
    </div>

    <!-- 用户表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="roleName" label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.roleName === '系统管理员' ? 'warning' : 'primary'" effect="light" round>
            {{ row.roleName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button link type="warning" size="small" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="handleToggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="460px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码">
          <el-input v-model="form.password" placeholder="不填默认 admin123" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.id" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageUsersApi,
  createUserApi,
  updateUserApi,
  deleteUserApi,
  resetPasswordApi
} from '@/api/user'
import { roleListApi } from '@/api/dict'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])
const total = ref(0)
const roles = ref([])
const dialogVisible = ref(false)
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 10, username: '', status: null })
const form = reactive({ id: null, username: '', password: '', realName: '', roleId: null, status: 1 })

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUsersApi(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.username = ''
  query.status = null
  handleSearch()
}

function openDialog(row) {
  Object.assign(form, { id: null, username: '', password: '', realName: '', roleId: null, status: 1 })
  if (row) {
    Object.assign(form, {
      id: row.id,
      username: row.username,
      realName: row.realName,
      roleId: row.roleId,
      status: row.status
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateUserApi(form)
    } else {
      await createUserApi(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function handleResetPassword(row) {
  await ElMessageBox.confirm(`确定将用户「${row.username}」的密码重置为 admin123 吗？`, '重置密码', {
    type: 'warning'
  })
  await resetPasswordApi(row.id)
  ElMessage.success('已重置为 admin123')
}

async function handleToggleStatus(row) {
  const action = row.status === 1 ? '停用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  await updateUserApi({ id: row.id, username: row.username, realName: row.realName, roleId: row.roleId, status: row.status === 1 ? 0 : 1 })
  ElMessage.success(`${action}成功`)
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？此操作不可恢复。`, '删除确认', {
    type: 'error'
  })
  await deleteUserApi(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  const res = await roleListApi()
  roles.value = res.data
  loadData()
})
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.search-right {
  margin-left: auto;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
