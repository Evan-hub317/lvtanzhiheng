<template>
  <div class="dashboard">
    <!-- 欢迎条 + KPI + 操作 -->
    <div class="welcome-bar">
      <div>
        <h2>欢迎回来，{{ displayName }} 👋</h2>
        <p>{{ today }} · 区域碳排放「监测—核算—预警—仿真—决策」一体化平台</p>
      </div>
      <div class="welcome-right">
        <div v-if="kpi" class="kpi-inline">
          <span class="kpi-year">{{ kpi.year }} 年排放总量</span>
          <span class="kpi-value">{{ formatEmission(kpi.totalEmission) }}</span>
          <span v-if="kpi.yoyRate !== null && kpi.yoyRate !== undefined" class="kpi-yoy" :class="kpi.yoyRate >= 0 ? 'up' : 'down'">
            {{ kpi.yoyRate >= 0 ? '▲' : '▼' }} {{ Math.abs(kpi.yoyRate) }}%
          </span>
          <span v-if="kpi.intensity !== null && kpi.intensity !== undefined" class="kpi-intensity">
            碳强度 {{ kpi.intensity }} tCO₂/万元
          </span>
        </div>
        <template v-if="userStore.isAdmin">
          <el-button class="action-btn" size="small" :loading="generating" @click="handleGenerate">生成模拟数据</el-button>
          <el-button class="action-btn" size="small" :loading="calculating" @click="handleCalc">执行核算</el-button>
        </template>
      </div>
    </div>

    <!-- 统计卡片（真实数据来自数据字典） -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.title">
        <div class="tech-stat-card" :style="{ background: card.bg }">
          <div class="stat-icon">
            <el-icon :size="24"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.title }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <div class="tech-panel">
          <div class="tech-panel-title">
            年度碳排放总量趋势
            <el-tag v-if="!hasEmission" size="small" type="info" effect="plain">暂无数据</el-tag>
            <el-button v-if="hasEmission" link type="primary" class="detail-btn" @click="goAnalysis()">
              查看详情<el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div ref="trendRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="tech-panel">
          <div class="tech-panel-title">
            {{ maxYear || '-' }} 年行业排放结构
            <el-tag v-if="!hasEmission" size="small" type="info" effect="plain">暂无数据</el-tag>
            <el-button v-if="hasEmission" link type="primary" class="detail-btn" @click="goAnalysis()">
              查看详情<el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div ref="pieRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { regionListApi, industryListApi, energyListApi, factorListApi } from '@/api/dict'
import { dataStatusApi, generateDataApi } from '@/api/data'
import { executeCalcApi } from '@/api/calc'
import { trendApi, structureApi, kpiApi } from '@/api/analysis'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const router = useRouter()
const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户'
)

// 跳转数据分析页（支持行业联动参数）
function goAnalysis(query = {}) {
  const q = { year: maxYear.value, ...query }
  router.push({ path: '/analysis', query: q })
}

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

// ===== 数据状态 =====
const status = ref({})
const kpi = ref(null)
const hasEmission = computed(() => (status.value.emissionCount || 0) > 0)
const hasData = computed(() => (status.value.totalCount || 0) > 0 && status.value.maxYear != null)
const maxYear = computed(() => status.value.maxYear ?? null)

// ===== 统计卡片 =====
const statCards = reactive([
  { title: '覆盖城市', value: '-', icon: 'Location', bg: 'linear-gradient(135deg, #0ea5e9, #0369a1)' },
  { title: '监测行业', value: '-', icon: 'OfficeBuilding', bg: 'linear-gradient(135deg, #06b6d4, #0e7490)' },
  { title: '能源品种', value: '-', icon: 'Lightning', bg: 'linear-gradient(135deg, #8b5cf6, #6d28d9)' },
  { title: '排放因子', value: '-', icon: 'DataAnalysis', bg: 'linear-gradient(135deg, #10b981, #047857)' }
])

async function loadStats() {
  const [regions, industries, energies, factors] = await Promise.all([
    regionListApi(),
    industryListApi(),
    energyListApi(),
    factorListApi()
  ])
  statCards[0].value = regions.data.filter(r => r.level === 2).length
  statCards[1].value = industries.data.length
  statCards[2].value = energies.data.length
  statCards[3].value = factors.data.length
}

// ===== 图表 =====
const trendRef = ref()
const pieRef = ref()
let trendChart = null
let pieChart = null

function renderTrendChart(rows) {
  trendChart = trendChart || echarts.init(trendRef.value)
  if (!rows?.length) {
    trendChart.setOption({}, true)
    trendChart.setOption({
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无数据，请先生成模拟数据并执行核算', fill: '#98a4b3', fontSize: 14 }
      }
    })
    return
  }
  trendChart.setOption({
    graphic: null,
    tooltip: { trigger: 'axis', valueFormatter: v => (v / 1e8).toFixed(2) + ' 亿吨' },
    grid: { left: 84, right: 24, top: 30, bottom: 36 },
    xAxis: {
      type: 'category',
      data: rows.map(r => r.year),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#c4d0dd' } },
      axisLabel: { color: '#51606e' }
    },
    yAxis: {
      type: 'value',
      name: '亿吨 CO₂',
      nameTextStyle: { color: '#51606e', padding: [0, 0, 0, 4] },
      nameGap: 12,
      axisLabel: { color: '#51606e', formatter: v => (v / 1e8).toFixed(1) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      {
        name: '碳排放总量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: rows.map(r => r.emission),
        itemStyle: { color: '#0ea5e9' },
        lineStyle: { width: 3, color: '#0ea5e9' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(14, 165, 233, 0.35)' },
            { offset: 1, color: 'rgba(14, 165, 233, 0.02)' }
          ])
        }
      }
    ]
  })
}

function renderPieChart(rows) {
  pieChart = pieChart || echarts.init(pieRef.value)
  if (!rows?.length) {
    pieChart.setOption({}, true)
    pieChart.setOption({
      graphic: {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: '暂无数据', fill: '#98a4b3', fontSize: 14 }
      }
    })
    return
  }
  pieChart.setOption({
    graphic: null,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 万吨（{d}%）' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#51606e' } },
    color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b'],
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%', color: '#51606e' },
        data: rows.map(r => ({ name: r.industryName, value: Number((r.emission / 10000).toFixed(1)), industryId: r.industryId }))
      }
    ]
  })
  // 点击扇区 → 跳分析页并联动该行业筛选
  pieChart.off('click')
  pieChart.on('click', params => {
    const item = rows.find(r => r.industryName === params.name)
    if (item) {
      goAnalysis({ industryId: item.industryId })
    }
  })
}

async function loadDashboardData() {
  const res = await dataStatusApi()
  status.value = res.data
  if (!hasData.value) {
    renderTrendChart([])
    renderPieChart([])
    return
  }
  const startYear = status.value.minYear
  // 年度分析一律使用最后一个完整年（当年未过完不参与趋势/KPI）
  const endYear = status.value.maxFullYear ?? status.value.maxYear
  if (hasEmission.value) {
    const [trendRes, structureRes, kpiRes] = await Promise.all([
      trendApi({ regionId: 1, startYear, endYear }),
      structureApi({ regionId: 1, year: endYear }),
      kpiApi({ regionId: 1, year: endYear })
    ])
    renderTrendChart(trendRes.data)
    renderPieChart(structureRes.data)
    kpi.value = kpiRes.data
  } else {
    renderTrendChart([])
    renderPieChart([])
    ElMessage.info('已有活动数据，请点击「执行核算」生成核算结果')
  }
}

// ===== 生成 / 核算操作 =====
const generating = ref(false)
const calculating = ref(false)

async function handleGenerate() {
  await ElMessageBox.confirm(
    '将清空旧模拟数据并重新生成（约 20 万条，耗时约 1 分钟）。是否继续？',
    '生成模拟数据',
    { type: 'warning' }
  )
  generating.value = true
  try {
    const res = await generateDataApi({ mode: 'all', startYear: 2021, injectAnomaly: true })
    const d = res.data
    ElMessage.success(`生成完成：${d.totalCount} 条 / 区县 ${d.countyCount} 个 / 注入异常 ${d.anomalyCount} 条 / 耗时 ${d.seconds}s`)
    await loadDashboardData()
  } finally {
    generating.value = false
  }
}

async function handleCalc() {
  await ElMessageBox.confirm('执行核算：月度聚合 → 碳强度 → 年度聚合 → 同比。是否继续？', '执行核算', {
    type: 'warning'
  })
  calculating.value = true
  try {
    const res = await executeCalcApi({ startYear: 0, endYear: 0 })
    const d = res.data
    ElMessage.success(`核算完成：月度 ${d.monthRows} 行 / 年度 ${d.yearRows} 行 / 耗时 ${d.seconds}s`)
    await loadDashboardData()
  } finally {
    calculating.value = false
  }
}

function formatEmission(value) {
  if (value === null || value === undefined) return '-'
  return (value / 100000000).toFixed(2) + ' 亿吨'
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  loadStats()
  loadDashboardData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 20px 28px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(100deg, #0b3354 0%, #0a6f8c 55%, #0f7a5c 100%);
  box-shadow: 0 6px 20px rgba(9, 55, 86, 0.25);
}
.welcome-bar h2 {
  margin: 0 0 8px;
  font-size: 20px;
}
.welcome-bar p {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}
.welcome-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.kpi-inline {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}
.kpi-value {
  font-size: 22px;
  font-weight: 700;
}
.kpi-yoy.up {
  color: #ffd54a;
}
.kpi-yoy.down {
  color: #7ef0c8;
}
.kpi-intensity {
  color: rgba(255, 255, 255, 0.7);
}
.action-btn {
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.35);
  color: #fff;
}
.action-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  border-color: #fff;
  color: #fff;
}

.stat-row {
  row-gap: 16px;
}
.tech-stat-card {
  padding: 20px;
  min-height: 108px;
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  margin-bottom: 12px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}
.stat-label {
  margin-top: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.chart-box {
  height: 320px;
}

.detail-btn {
  margin-left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
</style>
