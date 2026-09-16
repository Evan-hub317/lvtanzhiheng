<template>
  <div class="alert-page">
    <!-- 统计与操作 -->
    <el-row :gutter="16">
      <el-col :xs="8" :md="6">
        <div class="tech-stat-card" style="background: linear-gradient(135deg, #ef4444, #b91c1c)">
          <div class="stat-value">{{ summary.pending }}</div>
          <div class="stat-label">待确认预警</div>
        </div>
      </el-col>
      <el-col :xs="8" :md="6">
        <div class="tech-stat-card" style="background: linear-gradient(135deg, #f59e0b, #b45309)">
          <div class="stat-value">{{ summary.ruleCount }}</div>
          <div class="stat-label">阈值规则预警</div>
        </div>
      </el-col>
      <el-col :xs="8" :md="6">
        <div class="tech-stat-card" style="background: linear-gradient(135deg, #8b5cf6, #6d28d9)">
          <div class="stat-value">{{ summary.aiCount }}</div>
          <div class="stat-label">AI 异常检测</div>
        </div>
      </el-col>
      <el-col :xs="24" :md="6">
        <div class="tech-panel action-panel">
          <template v-if="userStore.isAdmin">
            <div class="action-btns">
              <el-button type="warning" :loading="scanning" @click="handleScan">执行阈值扫描</el-button>
              <el-button type="primary" :loading="detecting" @click="handleAnomaly">AI 异常检测</el-button>
            </div>
          </template>
          <p class="action-tip">阈值扫描每天 02:00 自动执行；AI 检测对全量数据建模（约 10~30 秒）</p>
        </div>
      </el-col>
    </el-row>

    <!-- 预警列表 -->
    <div class="tech-panel">
      <div class="tech-panel-title">预警列表</div>

      <div class="filter-bar">
        <el-select v-model="query.detectType" placeholder="检测方式" clearable style="width: 140px" @change="handleSearch">
          <el-option label="阈值规则" :value="1" />
          <el-option label="AI 异常检测" :value="2" />
        </el-select>
        <el-select v-model="query.status" placeholder="处理状态" clearable style="width: 130px" @change="handleSearch">
          <el-option label="待确认" :value="0" />
          <el-option label="已确认" :value="1" />
        </el-select>
        <RegionSelect v-model="query.regionId" :width="140" @change="handleSearch" />
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="检测方式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.detectType === 2 ? 'primary' : 'warning'" effect="light" round>
              {{ row.detectType === 2 ? '🤖 AI 检测' : '📏 阈值规则' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则 / 能源品种" min-width="170" />
        <el-table-column prop="regionName" label="区域" width="110" />
        <el-table-column prop="industryName" label="行业" width="100">
          <template #default="{ row }">{{ row.industryName || '全省' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="100">
          <template #default="{ row }">{{ row.year }}{{ row.month ? '-' + row.month : '' }}</template>
        </el-table-column>
        <el-table-column label="实际值" min-width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.detectType === 1 && row.ruleName.includes('环比')">{{ row.actualValue }}%</span>
            <span v-else-if="row.detectType === 1">{{ (row.actualValue / 1e8).toFixed(2) }} 亿吨</span>
            <span v-else>{{ (row.actualValue / 10000).toFixed(1) }} 万吨</span>
          </template>
        </el-table-column>
        <el-table-column label="异常分数" width="150">
          <template #default="{ row }">
            <el-progress
              v-if="row.anomalyScore !== null && row.anomalyScore !== undefined"
              :percentage="Math.round(row.anomalyScore * 100)"
              :stroke-width="10"
              :color="row.anomalyScore > 0.6 ? '#ef4444' : '#f59e0b'"
            />
            <span v-else class="no-score">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="light" round>
              {{ row.status === 1 ? '已确认' : '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="触发时间" width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="primary" size="small" @click="handleConfirm(row)">确认</el-button>
            <span v-else class="handled-by">{{ row.handler || '' }}</span>
          </template>
        </el-table-column>
      </el-table>

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
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertScanApi, alertAnomalyApi, alertPageApi, alertHandleApi, alertSummaryApi } from '@/api/alert'
import RegionSelect from '@/components/RegionSelect.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const summary = reactive({ pending: 0, ruleCount: 0, aiCount: 0 })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const scanning = ref(false)
const detecting = ref(false)

const query = reactive({ pageNum: 1, pageSize: 10, detectType: null, status: null, regionId: null })

async function loadSummary() {
  const res = await alertSummaryApi()
  Object.assign(summary, res.data)
}

async function loadData() {
  loading.value = true
  try {
    const res = await alertPageApi(query)
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

async function handleScan() {
  await ElMessageBox.confirm('执行阈值规则扫描（重扫自动去重）？', '阈值扫描', { type: 'warning' })
  scanning.value = true
  try {
    const res = await alertScanApi()
    ElMessage.success(`扫描完成，新增预警 ${res.data.newAlerts} 条`)
    await Promise.all([loadSummary(), loadData()])
  } finally {
    scanning.value = false
  }
}

async function handleAnomaly() {
  await ElMessageBox.confirm(
    '对全量核算数据执行孤立森林异常检测（约 10~30 秒），检测异常排放点。是否继续？',
    'AI 异常检测',
    { type: 'warning' }
  )
  detecting.value = true
  try {
    const res = await alertAnomalyApi()
    const d = res.data
    ElMessage.success(`检测完成：${d.series} 个序列，检出 ${d.anomalies} 个异常点，入库 Top ${d.saved}，耗时 ${d.seconds}s`)
    await Promise.all([loadSummary(), loadData()])
  } finally {
    detecting.value = false
  }
}

async function handleConfirm(row) {
  await alertHandleApi(row.id)
  ElMessage.success('已确认')
  await Promise.all([loadSummary(), loadData()])
}

onMounted(async () => {
  await Promise.all([loadSummary(), loadData()])
})
</script>

<style scoped>
.alert-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tech-stat-card {
  padding: 20px;
  border-radius: 12px;
  color: #fff;
  /* 固定等高：与右侧操作卡严格对齐 */
  height: 128px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-label {
  margin-top: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.action-panel {
  height: 128px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}
.action-btns {
  display: flex;
  gap: 10px;
}
/* 两个按钮等宽自适应，随卡片宽度伸缩，不贴边溢出 */
.action-btns .el-button {
  flex: 1;
  min-width: 0;
  margin-left: 0;
}
.action-tip {
  margin: 0;
  font-size: 12px;
  color: #98a4b3;
}

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.no-score {
  color: #98a4b3;
}
.handled-by {
  font-size: 12px;
  color: #98a4b3;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
