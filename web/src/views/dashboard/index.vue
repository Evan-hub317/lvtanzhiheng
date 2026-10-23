<template>
  <div class="dashboard">
    <!-- 欢迎条 + KPI + 操作 -->
    <div class="welcome-bar">
      <div>
        <h2>欢迎回来，{{ displayName }}</h2>
        <p>{{ today }} · 区域碳排放「监测—核算—预警—仿真—决策」一体化平台</p>
      </div>
      <div class="welcome-right">
        <div class="kpi-inline">
          <span class="kpi-year">{{ kpi?.year ?? '-' }} 年排放总量</span>
          <span class="kpi-value">{{ formatEmission(kpi?.totalEmission) }}</span>
          <span v-if="kpi?.yoyRate !== null && kpi?.yoyRate !== undefined" class="kpi-yoy" :class="kpi.yoyRate >= 0 ? 'up' : 'down'">
            {{ kpi.yoyRate >= 0 ? '▲' : '▼' }} {{ Math.abs(kpi.yoyRate) }}%
          </span>
          <span v-else class="kpi-yoy up">▲ -%</span>
          <span v-if="kpi?.intensity !== null && kpi?.intensity !== undefined" class="kpi-intensity">
            碳强度 {{ kpi.intensity }} tCO₂/万元
          </span>
          <span v-else class="kpi-intensity">碳强度 - tCO₂/万元</span>
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

    <!-- 图表区（弹性占满剩余高度） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="16">
        <div class="tech-panel">
          <div class="tech-panel-title">
            全国年度碳排放总量趋势
            <el-tag v-if="!hasEmission" size="small" type="info" effect="plain">暂无数据</el-tag>
            <el-button v-if="hasEmission" link type="primary" class="detail-btn" @click="goAnalysis()">
              查看详情<el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div ref="trendRef" class="chart-box" v-loading="loading" element-loading-text="加载中…"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="tech-panel">
          <div class="struct-switch-row">
            <el-radio-group v-model="structType" size="small" @change="onStructTypeChange">
              <el-radio-button value="industry">行业</el-radio-button>
              <el-radio-button value="energy">能源（直接）</el-radio-button>
              <el-radio-button value="indirect">能源（间接）</el-radio-button>
            </el-radio-group>
          </div>
          <div class="tech-panel-title">
            {{ structTitle }}
            <el-tag v-if="!hasEmission" size="small" type="info" effect="plain">暂无数据</el-tag>
            <el-button v-if="hasEmission" link type="primary" class="detail-btn" @click="goAnalysis()">
              查看详情<el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div ref="pieRef" class="chart-box" v-loading="loading" element-loading-text="加载中…"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

// keep-alive 标签页缓存名（布局 include 列表按此匹配）
defineOptions({ name: 'DashboardPage' })
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { regionListApi, industryListApi, energyListApi, factorListApi } from '@/api/dict'
import { dataStatusApi, generateDataApi } from '@/api/data'
import { executeCalcApi } from '@/api/calc'
import { trendApi, structureApi, energyStructureApi, indirectApi, kpiApi } from '@/api/analysis'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const router = useRouter()
const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户'
)

// 跳转数据分析页（支持行业联动参数）
function goAnalysis(query = {}) {
  const q = { year: chartYear.value, ...query }
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
// 图表展示年：年度图表只用最后一个完整年（当年未过完不参与），标题与数据同源
const chartYear = computed(() => status.value.maxFullYear ?? status.value.maxYear ?? null)

// ===== 结构图（行业 / 能源直接 / 能源间接 卡片切换，全国口径） =====
const structType = ref('industry')
const structTitle = computed(() => {
  const y = chartYear.value ?? '-'
  if (structType.value === 'industry') return `${y} 年全国行业排放结构`
  if (structType.value === 'energy') return `${y} 年全国能源结构（直接排放）`
  return `${y} 年全国间接排放结构`
})

/** 结构图请求序号：只有最新一次请求的响应才渲染，避免首载与切换的响应竞态互相覆盖 */
let structSeq = 0

async function loadStructureData() {
  // 状态未就绪（年份未知）时不发请求：等首载完成后按「当前所选类型」加载，
  // 避免 year 缺失触发 400 报错提示
  if (chartYear.value == null) return
  const seq = ++structSeq
  const type = structType.value   // 类型快照：渲染与点击行为均以本次请求的类型为准
  const params = { regionId: 1, year: chartYear.value }
  let res = null
  if (type === 'industry') {
    res = await structureApi(params)
  } else if (type === 'energy') {
    res = await energyStructureApi(params)
  } else {
    res = await indirectApi(params)
  }
  if (seq === structSeq) {
    renderPieChart(res.data, type)
  }
}

async function onStructTypeChange() {
  // 状态未就绪：不动转圈，等首载完成后按当前所选类型加载
  if (chartYear.value == null) return
  beginLoad()
  try {
    await loadStructureData()
  } finally {
    endLoad()
  }
}

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
/** 图表数据加载中（转圈遮罩） */
const loading = ref(false)
/** 并发加载计数：首载与切换的请求交错时，全部完成后才关闭转圈 */
let pendingLoads = 0
function beginLoad() {
  pendingLoads++
  loading.value = true
}
function endLoad() {
  pendingLoads = Math.max(0, pendingLoads - 1)
  if (pendingLoads === 0) {
    loading.value = false
  }
}

function renderTrendChart(rows) {
  trendChart = echarts.getInstanceByDom(trendRef.value) || echarts.init(trendRef.value)
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
      axisLabel: {
        color: '#51606e',
        // 按量级自适应小数位，避免小区域（万吨级）刻度标签四舍五入后重叠
        formatter: v => {
          const y = v / 1e8
          if (y >= 10) return y.toFixed(0)
          if (y >= 1) return y.toFixed(1)
          return y.toFixed(3)
        }
      },
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

function renderPieChart(rows, type) {
  pieChart = echarts.getInstanceByDom(pieRef.value) || echarts.init(pieRef.value)
  if (!rows?.length) {
    pieChart.setOption({ graphic: null }, true)
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
    tooltip: { trigger: 'item', formatter: '{b}：{c} 亿吨（{d}%）' },
    legend: { bottom: 0, type: 'scroll', icon: 'circle', itemGap: 10, textStyle: { color: '#51606e', fontSize: 11 } },
    color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444', '#64748b', '#f97316'],
    series: [
      {
        type: 'pie',
        // 半径与中心同分析页：饼体收缩留出标签空间，避免块标签被容器裁切
        radius: ['38%', '58%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%', color: '#51606e', fontSize: 11 },
        labelLine: { length: 10, length2: 6 },
        data: rows.map(r => ({
          name: r.industryName || r.energyName,
          value: Number((r.emission / 1e8).toFixed(2))
        }))
      }
    ]
  }, true)  // notMerge：切换结构类型时全量替换，避免旧图残留
  // 点击扇区 → 跳分析页（行业类型联动行业筛选；能源类型仅跳转）
  pieChart.off('click')
  pieChart.on('click', params => {
    if (type === 'industry') {
      const item = rows.find(r => r.industryName === params.name)
      if (item) {
        goAnalysis({ industryId: item.industryId })
      }
    } else {
      goAnalysis()
    }
  })
}

async function loadDashboardData() {
  beginLoad()
  try {
    const res = await dataStatusApi()
    status.value = res.data
    if (!hasData.value) {
      renderTrendChart([])
      renderPieChart([])
      return
    }
    const startYear = status.value.minYear
    // 年度分析一律使用最后一个完整年（当年未过完不参与趋势/KPI）
    const endYear = chartYear.value
    if (hasEmission.value) {
      const [trendRes, kpiRes] = await Promise.all([
        trendApi({ regionId: 1, startYear, endYear }),
        kpiApi({ regionId: 1, year: endYear }),
        loadStructureData()
      ])
      renderTrendChart(trendRes.data)
      kpi.value = kpiRes.data
    } else {
      renderTrendChart([])
      renderPieChart([])
      ElMessage.info('已有活动数据，请点击「执行核算」生成核算结果')
    }
  } finally {
    endLoad()
  }
}

// ===== 生成 / 核算操作 =====
const generating = ref(false)
const calculating = ref(false)

async function handleGenerate() {
  await ElMessageBox.confirm(
    '将清空旧模拟数据并重新生成（2016 年至今，约 130 万条，耗时约 5 分钟）。是否继续？',
    '生成模拟数据',
    { type: 'warning' }
  )
  generating.value = true
  try {
    const res = await generateDataApi({ mode: 'all', startYear: 2016, injectAnomaly: true })
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

// keep-alive 恢复页面时按当前尺寸重绘图表：数据若在页面离屏（deactivated）期间
// 返回并渲染，图表容器尺寸为 0，必须激活后重绘，否则切回来是空白图表
onActivated(handleResize)

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
/* 占满视口可用高度：100vh - 顶栏 60px - 内容区上下内边距 32px；页面自身不滚动 */
.dashboard {
  /* 占满视口可用高度：100vh - 顶栏 60 - 内容区边距 32 */
  height: calc(100vh - 92px);
  min-height: 560px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 图表行：吸收剩余高度，图表随之撑到页面底部 */
.chart-row {
  flex: 1;
  min-height: 0;
}

.welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 24px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(100deg, #0b3354 0%, #0a6f8c 55%, #0f7a5c 100%);
  box-shadow: 0 6px 20px rgba(9, 55, 86, 0.25);
}
.welcome-bar h2 {
  margin: 0 0 4px;
  font-size: 18px;
}
.welcome-bar p {
  margin: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
}
.welcome-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  /* 占满整行：KPI 与操作按钮始终在问候语下方，加载前后布局一致（不随内容宽度跳行） */
  flex-basis: 100%;
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
  row-gap: 12px;
}
.tech-stat-card {
  padding: 14px 18px;
  min-height: 84px;
}
.stat-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.18);
  margin-bottom: 10px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

/* 图表卡片等高：左侧趋势卡拉伸到与右侧结构卡（含切换行）一致，两图底部对齐 */
.tech-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.chart-box {
  flex: 1;
  min-height: 200px;
}

.detail-btn {
  margin-left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.struct-switch-row {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 8px;
}
</style>
