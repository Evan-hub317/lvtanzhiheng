<template>
  <div class="screen-page">
    <!-- 顶栏 -->
    <header class="screen-header">
      <h1 class="screen-title tech-gradient-text">绿碳智衡 · 碳排放热力图</h1>
      <div class="header-right">
        <span class="header-time">{{ nowText }}</span>
        <el-button size="small" class="fullscreen-btn" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '⛶ 全屏' }}
        </el-button>
      </div>
    </header>

    <div class="screen-body">
      <!-- 左侧：时段选择 + 核心指标 -->
      <div class="side-panel" v-loading="loading" element-loading-text="加载中…" element-loading-background="rgba(10, 20, 40, 0.6)">
        <div class="panel-title">时段选择</div>
        <div class="period-presets">
          <div
            v-for="p in presets"
            :key="p.key"
            class="preset-chip"
            :class="{ active: presetKey === p.key }"
            @click="applyPreset(p.key)"
          >
            <span class="chip-dot"></span>
            <span>{{ p.label }}</span>
          </div>
        </div>
        <!-- 起止年月下拉：年份选今年时月份只列已过完的月；起止倒挂时自动跟随校正 -->
        <div class="period-range-row">
          <span class="range-label">起</span>
          <el-select v-model="startYear" class="period-select period-year" @change="onStartYearChange">
            <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
          </el-select>
          <el-select v-model="startMonth" class="period-select period-month" @change="onStartMonthChange">
            <el-option v-for="m in monthOptionsOf(startYear)" :key="m" :label="`${m}月`" :value="m" />
          </el-select>
        </div>
        <div class="period-range-row">
          <span class="range-label">止</span>
          <el-select v-model="endYear" class="period-select period-year" @change="onEndYearChange">
            <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
          </el-select>
          <el-select v-model="endMonth" class="period-select period-month" @change="onEndMonthChange">
            <el-option v-for="m in monthOptionsOf(endYear)" :key="m" :label="`${m}月`" :value="m" />
          </el-select>
        </div>
        <!-- 所选时段摘要 -->
        <div class="period-summary">
          <el-icon class="sum-icon"><Calendar /></el-icon>
          <div>
            <div class="sum-range">{{ periodText }}</div>
            <div class="sum-sub">共 {{ periodMonths }} 个月</div>
          </div>
        </div>
        <div class="panel-title sub">核心指标</div>
        <div class="kpi-grid">
          <div class="kpi-item full">
            <div class="kpi-label">全国排放总量</div>
            <div class="kpi-value">{{ nationalTotal }} <span>亿吨</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">碳强度</div>
            <div class="kpi-value">{{ intensity ?? '-' }}<span v-if="intensity !== null"> tCO₂/万元</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">监测地级行政区</div>
            <div class="kpi-value">{{ cityCount }} <span>个</span></div>
          </div>
        </div>
      </div>

      <!-- 中间：地图 -->
      <div class="map-panel">
        <div ref="mapRef" class="map-box" v-loading="loading" element-loading-text="加载中…"></div>
        <div class="map-attribution">
          地图数据来源：阿里云 DataV.GeoAtlas（datav.aliyun.com/portal/school/atlas/area_selector）
        </div>
      </div>

      <!-- 右侧：排行 + 行业/能源结构（面板高度不变，超出内部滚动） -->
      <div class="side-panel" v-loading="loading" element-loading-text="加载中…" element-loading-background="rgba(10, 20, 40, 0.6)">
        <div class="panel-title">省级行政区排放 TOP10</div>
        <div ref="rankRef" class="rank-chart"></div>
        <div class="panel-title sub">行业结构</div>
        <div ref="industryRef" class="structure-chart"></div>
        <div class="panel-title sub">能源结构（直接）</div>
        <div ref="directRef" class="structure-chart"></div>
        <div class="panel-title sub">能源结构（间接）</div>
        <div ref="indirectRef" class="structure-chart"></div>
      </div>
    </div>

    <!-- 省份下钻抽屉（不用 destroy-on-close：DOM 常驻保证 ECharts 实例跨多次打开有效） -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="40%" class="screen-drawer">
      <div v-loading="drawerLoading" class="drawer-body">
        <div class="drawer-period">统计时段：{{ periodText }}</div>
        <div class="drawer-kpis">
          <div class="dkpi">
            <div class="dkpi-label">时段总量</div>
            <div class="dkpi-value">{{ provinceTotal }} 亿吨</div>
          </div>
          <div class="dkpi">
            <div class="dkpi-label">碳强度</div>
            <div class="dkpi-value">{{ provinceIntensity ?? '-' }} tCO₂/万元</div>
          </div>
        </div>
        <div class="drawer-chart-title">行业结构</div>
        <div ref="provincePieRef" class="drawer-chart"></div>
        <div class="drawer-chart-title">能源结构（直接）</div>
        <div ref="provinceDirectRef" class="drawer-chart"></div>
        <div class="drawer-chart-title">能源结构（间接）</div>
        <div ref="provinceIndirectRef" class="drawer-chart"></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { dataStatusApi } from '@/api/data'
import { screenPeriodApi } from '@/api/analysis'
import { regionListApi } from '@/api/dict'
import chinaJson from '@/assets/china.json'

// keep-alive 缓存匹配名（App.vue 的 include 列表）
defineOptions({ name: 'ScreenPage' })

const loading = ref(false)
const status = ref(null)
const periodRange = ref([])
/** 当前生效的快捷项（自定义选择时为空串） */
const presetKey = ref('year')
const cityCount = ref(0)
const nationalTotal = ref('-')
const intensity = ref(null)
const nowText = ref('')
let clockTimer = null

const mapRef = ref()
const rankRef = ref()
const industryRef = ref()
const directRef = ref()
const indirectRef = ref()
let mapChart, rankChart, industryChart, directChart, indirectChart

/** 时段文案：2025年1月 - 2025年12月 */
const periodText = computed(() => {
  const [a, b] = periodRange.value || []
  if (!a || !b) return '-'
  const fmt = x => {
    const [y, m] = x.split('-')
    return `${y}年${Number(m)}月`
  }
  return `${fmt(a)} - ${fmt(b)}`
})
/** 时段月数（摘要展示） */
const periodMonths = computed(() => {
  const [a, b] = periodRange.value || []
  if (!a || !b) return 0
  const [sy, sm] = a.split('-').map(Number)
  const [ey, em] = b.split('-').map(Number)
  return (ey - sy) * 12 + (em - sm) + 1
})

// ===== 时钟 =====
function tickClock() {
  nowText.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

// ===== 时段选择 =====
const presets = [
  { key: 'year', label: '全年' },
  { key: 'ytd', label: '今年至今' },
  { key: 'recent', label: '近一年' }
]
/** 可选年份（数据范围 minYear ~ maxYear） */
const yearOptions = computed(() => {
  const s = status.value
  if (!s || !s.minYear) return []
  const arr = []
  for (let y = s.minYear; y <= s.maxYear; y++) arr.push(y)
  return arr
})
/** 某年可选月份：今年只列已过完的月（1 ~ maxMonth），其余年份 1 ~ 12 */
function monthOptionsOf(year) {
  const s = status.value
  if (!s || !year) return []
  const max = year === s.maxYear ? (s.maxMonth || 12) : 12
  const arr = []
  for (let m = 1; m <= max; m++) arr.push(m)
  return arr
}
/** periodRange 起止解析（"YYYY-MM" → {y, m}） */
function ymParts(idx) {
  const v = periodRange.value[idx]
  if (!v) return null
  const [y, m] = v.split('-')
  return { y: Number(y), m: Number(m) }
}
function ymToStr(y, m) {
  return `${y}-${String(m).padStart(2, '0')}`
}
/** 起止读写代理（四个下拉框各自绑定；写入由 @change 处理器统一 commit，setter 留空） */
const startYear = computed({ get: () => ymParts(0)?.y ?? '', set: () => {} })
const startMonth = computed({ get: () => ymParts(0)?.m ?? '', set: () => {} })
const endYear = computed({ get: () => ymParts(1)?.y ?? '', set: () => {} })
const endMonth = computed({ get: () => ymParts(1)?.m ?? '', set: () => {} })
/** 更新区间后统一收尾：去快捷高亮 + 刷新 */
function commitRange(range) {
  periodRange.value = range
  presetKey.value = ''
  loadScreenData()
}
function onStartYearChange(y) {
  const s = status.value
  let m = ymParts(0)?.m ?? 1
  if (y === s.maxYear) m = Math.min(m, s.maxMonth || 12)
  const start = ymToStr(y, m)
  const end = periodRange.value[1]
  commitRange(end && start > end ? [start, start] : [start, end])
}
function onStartMonthChange(m) {
  const y = ymParts(0)?.y
  if (y == null) return
  const start = ymToStr(y, m)
  const end = periodRange.value[1]
  commitRange(end && start > end ? [start, start] : [start, end])
}
function onEndYearChange(y) {
  const s = status.value
  let m = ymParts(1)?.m ?? (s.maxMonth || 12)
  if (y === s.maxYear) m = Math.min(m, s.maxMonth || 12)
  const end = ymToStr(y, m)
  const start = periodRange.value[0]
  commitRange(start && end < start ? [end, end] : [start, end])
}
function onEndMonthChange(m) {
  const y = ymParts(1)?.y
  if (y == null) return
  const end = ymToStr(y, m)
  const start = periodRange.value[0]
  commitRange(start && end < start ? [end, end] : [start, end])
}

/** 月份序号（year*12 + month-1）与 "YYYY-MM" 互转 */
function monthIndex(y, m) {
  return y * 12 + m - 1
}
function fmtMonth(idx) {
  return `${Math.floor(idx / 12)}-${String((idx % 12) + 1).padStart(2, '0')}`
}

function rangeOf(key) {
  const s = status.value
  if (!s || !s.minYear) return null
  const maxIdx = monthIndex(s.maxYear, s.maxMonth || 12)
  if (key === 'year') {
    const y = s.maxFullYear || s.maxYear
    return [fmtMonth(monthIndex(y, 1)), fmtMonth(monthIndex(y, 12))]
  }
  if (key === 'ytd') {
    return [fmtMonth(monthIndex(s.maxYear, 1)), fmtMonth(maxIdx)]
  }
  if (key === 'recent') {
    return [fmtMonth(maxIdx - 11), fmtMonth(maxIdx)]
  }
  return null
}

function applyPreset(key) {
  const range = rangeOf(key)
  if (!range) return
  presetKey.value = key
  periodRange.value = range
  loadScreenData()
}

// ===== 数据加载 =====
async function loadScreenData() {
  const [startMonth, endMonth] = periodRange.value || []
  if (!startMonth || !endMonth) return
  loading.value = true
  try {
    const res = await screenPeriodApi({ regionId: 1, startMonth, endMonth })
    const d = res.data
    nationalTotal.value = d.total != null ? (Number(d.total) / 1e8).toFixed(2) : '-'
    intensity.value = d.intensity != null ? Number(d.intensity).toFixed(2) : null
    renderMap(d.regions || [])
    renderRank(d.regions || [])
    renderIndustry(d.industries || [])
    renderEnergyDirect(d.direct || [])
    renderEnergyIndirect(d.indirect || [])
  } finally {
    loading.value = false
  }
}

// ===== 地图 =====
function renderMap(provinces) {
  mapChart = echarts.getInstanceByDom(mapRef.value) || echarts.init(mapRef.value)
  echarts.registerMap('china', chinaJson)
  const data = provinces.map(p => ({ name: p.regionName, value: Number((p.emission / 1e8).toFixed(2)) }))
  const max = data.length ? Math.max(...data.map(d => d.value)) : 0
  mapChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: p => `${p.name}<br>${periodText.value}排放量：<b>${p.value}</b> 亿吨`
    },
    visualMap: {
      min: 0,
      max: Math.ceil(max) || 1,
      calculable: true,
      orient: 'vertical',
      right: 16,
      top: 'center',
      text: ['高', '低'],
      textStyle: { color: '#7ea6c8' },
      inRange: {
        color: ['#0d2b45', '#0b5d8a', '#0ea5e9', '#22d3ee', '#4ade80']
      }
    },
    series: [{
      type: 'map',
      map: 'china',
      roam: true,
      scaleLimit: { min: 0.9, max: 4 },
      data,
      label: { show: true, fontSize: 9, color: '#c9e2f5' },
      itemStyle: {
        borderColor: '#1e3d5f',
        borderWidth: 0.8,
        areaColor: '#0d2b45'
      },
      emphasis: {
        label: { show: true, color: '#fff', fontSize: 12 },
        itemStyle: { areaColor: '#ffb300' }
      }
    }]
  })
  mapChart.off('click')
  mapChart.on('click', params => {
    if (params.name) {
      openProvince(params.name)
    }
  })
}

// ===== 侧栏图表 =====
function renderRank(rows) {
  rankChart = echarts.getInstanceByDom(rankRef.value) || echarts.init(rankRef.value)
  const top = [...rows].sort((a, b) => a.emission - b.emission).slice(-10)
  rankChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => (v / 1e8).toFixed(2) + ' 亿吨' },
    grid: { left: 4, right: 16, top: 6, bottom: 4 },
    xAxis: {
      type: 'value', splitLine: { lineStyle: { color: '#16324f' } },
      axisLabel: {
        color: '#7ea6c8', fontSize: 10,
        // 按量级自适应小数位，避免小区域（万吨级）刻度标签四舍五入后重叠
        formatter: v => {
          const y = v / 1e8
          if (y >= 10) return y.toFixed(0)
          if (y >= 1) return y.toFixed(1)
          return y.toFixed(3)
        }
      }
    },
    yAxis: {
      type: 'category', data: top.map(r => r.regionName.replace(/省|市|自治区|壮族|回族|维吾尔/g, '')),
      axisLine: { lineStyle: { color: '#23486b' } }, axisLabel: { color: '#c9e2f5', fontSize: 11 }
    },
    series: [{
      type: 'bar', barMaxWidth: 12, data: top.map(r => r.emission),
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#0b5d8a' }, { offset: 1, color: '#22d3ee' }
        ])
      }
    }]
  })
}

function renderIndustry(rows) {
  industryChart = echarts.getInstanceByDom(industryRef.value) || echarts.init(industryRef.value)
  if (!rows?.length) {
    industryChart.setOption({ graphic: emptyScreenGraphic('暂无数据') }, true)
    return
  }
  industryChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 亿吨（{d}%）' },
    legend: { bottom: 0, type: 'scroll', icon: 'circle', textStyle: { color: '#7ea6c8', fontSize: 10 } },
    color: ['#22d3ee', '#4ade80', '#fbbf24', '#a78bfa', '#f87171', '#f472b6', '#94a3b8', '#34d399'],
    series: [{
      type: 'pie', radius: ['40%', '62%'], center: ['50%', '44%'],
      label: { color: '#c9e2f5', fontSize: 10, formatter: '{b}\n{d}%' },
      itemStyle: { borderColor: '#0a1428', borderWidth: 1 },
      data: rows.map(r => ({ name: r.industryName, value: Number((r.emission / 1e8).toFixed(2)) }))
    }]
  })
  industryChart.resize()
}

/** 右栏能源结构饼图（深色主题，亿吨单位） */
function renderEnergyDirect(rows) {
  renderEnergyPie(directRef.value, 'direct', rows, ['#22d3ee', '#4ade80', '#fbbf24', '#a78bfa', '#f87171', '#f472b6', '#94a3b8', '#34d399'])
}

function renderEnergyIndirect(rows) {
  renderEnergyPie(indirectRef.value, 'indirect', rows, ['#fbbf24', '#f87171', '#fb923c'])
}

function renderEnergyPie(el, key, rows, colors) {
  const chart = echarts.getInstanceByDom(el) || echarts.init(el)
  if (key === 'direct') directChart = chart
  else indirectChart = chart
  if (!rows?.length) {
    chart.setOption({ graphic: emptyScreenGraphic('暂无数据') }, true)
    return
  }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 亿吨（{d}%）' },
    legend: { bottom: 0, type: 'scroll', icon: 'circle', textStyle: { color: '#7ea6c8', fontSize: 10 } },
    color: colors,
    series: [{
      type: 'pie', radius: ['40%', '62%'], center: ['50%', '44%'],
      label: { color: '#c9e2f5', fontSize: 10, formatter: '{b}\n{d}%' },
      itemStyle: { borderColor: '#0a1428', borderWidth: 1 },
      data: rows.map(r => ({ name: r.energyName, value: Number((r.emission / 1e8).toFixed(2)) }))
    }]
  })
  chart.resize()
}

function emptyScreenGraphic(text) {
  return { type: 'text', left: 'center', top: 'middle', style: { text, fill: '#7ea6c8', fontSize: 13 } }
}

// ===== 省份下钻 =====
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerTitle = ref('')
const provinceTotal = ref('-')
const provinceIntensity = ref(null)
const provinceDirectRef = ref()
const provinceIndirectRef = ref()
const provincePieRef = ref()
let provinceDirectChart, provinceIndirectChart, provincePieChart

/** 抽屉饼图（浅色主题，万吨单位） */
function buildDrawerPieOption(rows, colors) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 万吨（{d}%）' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#51606e', fontSize: 11 } },
    color: colors || ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b', '#f87171', '#94a3b8', '#34d399'],
    series: [{
      type: 'pie', radius: ['40%', '64%'], center: ['50%', '44%'],
      label: { color: '#51606e', fontSize: 11, formatter: '{b}\n{d}%' },
      itemStyle: { borderColor: '#fff', borderWidth: 1 },
      data: rows.map(r => ({ name: r.energyName || r.industryName, value: Number((r.emission / 10000).toFixed(1)) }))
    }]
  }
}

async function openProvince(name) {
  drawerTitle.value = name + ' · 碳排放详情'
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    // 由省名定位区域ID
    const regions = (await regionListApi()).data
    const province = regions.find(r => r.level === 1 && r.regionName === name)
    if (!province) {
      return
    }
    const [startMonth, endMonth] = periodRange.value
    const res = await screenPeriodApi({ regionId: province.id, startMonth, endMonth })
    const d = res.data
    provinceTotal.value = d.total != null ? (Number(d.total) / 1e8).toFixed(2) : '-'
    provinceIntensity.value = d.intensity != null ? Number(d.intensity).toFixed(2) : null
    await nextTick()
    // getInstanceByDom 保证重复打开复用同一实例（destroy-on-close 已移除，DOM 常驻）
    provinceDirectChart = echarts.getInstanceByDom(provinceDirectRef.value)
      || echarts.init(provinceDirectRef.value)
    provinceDirectChart.setOption(buildDrawerPieOption(d.direct || []))
    provinceIndirectChart = echarts.getInstanceByDom(provinceIndirectRef.value)
      || echarts.init(provinceIndirectRef.value)
    provinceIndirectChart.setOption(buildDrawerPieOption(d.indirect || [], ['#fbbf24', '#f87171']))
    provincePieChart = echarts.getInstanceByDom(provincePieRef.value)
      || echarts.init(provincePieRef.value)
    provincePieChart.setOption(buildDrawerPieOption(d.industries || []))
  } finally {
    drawerLoading.value = false
  }
}

// ===== 全屏与刷新 =====
const isFullscreen = ref(false)
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}
document.addEventListener('fullscreenchange', () => {
  isFullscreen.value = !!document.fullscreenElement
})

function handleResize() {
  mapChart?.resize()
  rankChart?.resize()
  industryChart?.resize()
  directChart?.resize()
  indirectChart?.resize()
  provinceDirectChart?.resize()
  provinceIndirectChart?.resize()
  provincePieChart?.resize()
}

onMounted(async () => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  window.addEventListener('resize', handleResize)
  // 进入页面立即转圈：状态接口（明细表 COUNT 较慢）同样耗时，不能空窗等待
  loading.value = true
  try {
    // 先取数据范围（决定可选月份与快捷项），再默认加载「全年」
    const statusRes = await dataStatusApi()
    status.value = statusRes.data
    cityCount.value = statusRes.data.cityCount ?? 0
    if (status.value.minYear) {
      applyPreset('year')
    }
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  clearInterval(clockTimer)
  window.removeEventListener('resize', handleResize)
  mapChart?.dispose()
  rankChart?.dispose()
  industryChart?.dispose()
  directChart?.dispose()
  indirectChart?.dispose()
  provinceDirectChart?.dispose()
  provinceIndirectChart?.dispose()
  provincePieChart?.dispose()
})
</script>

<style scoped>
.screen-page {
  /* 固定整屏高度：页面自身不滚动，面板内容超出时仅在面板内部滚动 */
  height: 100vh;
  overflow: hidden;
  background: radial-gradient(ellipse at 50% -10%, #10305a 0%, #0a1428 55%, #060d1c 100%);
  color: #e2f1ff;
  display: flex;
  flex-direction: column;
  padding: 0 20px 16px;
}

.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 8px 10px;
}
.screen-title {
  margin: 0;
  font-size: 24px;
  letter-spacing: 4px;
  font-weight: 700;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.period-presets {
  display: flex;
  gap: 10px;
}
.preset-chip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 11px 4px;
  font-size: 13px;
  color: #9be6f5;
  background: rgba(34, 211, 238, 0.07);
  border: 1px solid rgba(34, 211, 238, 0.25);
  border-radius: 20px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
}
.preset-chip:hover {
  border-color: rgba(34, 211, 238, 0.6);
  background: rgba(34, 211, 238, 0.14);
}
.preset-chip.active {
  color: #fff;
  font-weight: 600;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.85), rgba(34, 211, 238, 0.7));
  border-color: rgba(34, 211, 238, 0.9);
  box-shadow: 0 0 10px rgba(34, 211, 238, 0.35);
}
.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7ea6c8;
  transition: background 0.2s;
}
.preset-chip.active .chip-dot {
  background: #4ade80;
}

.period-range-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
.range-label {
  width: 18px;
  font-size: 13px;
  color: #7ea6c8;
  text-align: center;
}
.period-select {
  flex: 1;
}
.period-year {
  flex: 1.3;
}
.period-month {
  flex: 1;
}

.period-summary {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.16), rgba(34, 211, 238, 0.07));
  border: 1px solid rgba(34, 211, 238, 0.35);
  border-radius: 12px;
}
.sum-icon {
  color: #22d3ee;
  font-size: 22px;
  flex-shrink: 0;
}
.sum-range {
  font-size: 14px;
  font-weight: 600;
  color: #e2f1ff;
  letter-spacing: 0.5px;
}
.sum-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #7ea6c8;
}
.period-select :deep(.el-select__wrapper) {
  background: rgba(34, 211, 238, 0.07);
  border: 1px solid rgba(34, 211, 238, 0.3);
  border-radius: 8px;
  box-shadow: none;
  transition: all 0.2s;
}
.period-select :deep(.el-select__wrapper:hover) {
  border-color: rgba(34, 211, 238, 0.6);
}
.period-select :deep(.el-select__wrapper.is-focused) {
  border-color: rgba(34, 211, 238, 0.8);
  box-shadow: 0 0 8px rgba(34, 211, 238, 0.25);
}
.period-select :deep(.el-select__selected-item),
.period-select :deep(.el-select__placeholder) {
  color: #e2f1ff;
}
.period-select :deep(.el-select__caret) {
  color: #7ea6c8;
}
.header-time {
  font-size: 13px;
  color: #7ea6c8;
}
.fullscreen-btn {
  background: rgba(34, 211, 238, 0.12);
  border: 1px solid rgba(34, 211, 238, 0.4);
  color: #9be6f5;
}
.fullscreen-btn:hover {
  background: rgba(34, 211, 238, 0.25);
  color: #fff;
}

.screen-body {
  flex: 1;
  display: grid;
  grid-template-columns: 300px 1fr 320px;
  gap: 16px;
  min-height: 0;
}

.side-panel {
  position: relative;   /* v-loading 遮罩定位锚点 */
  background: rgba(13, 43, 69, 0.5);
  border: 1px solid rgba(34, 211, 238, 0.15);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #9be6f5;
  letter-spacing: 1px;
}
.panel-title.sub {
  margin-top: 8px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.kpi-item {
  background: rgba(34, 211, 238, 0.07);
  border: 1px solid rgba(34, 211, 238, 0.15);
  border-radius: 10px;
  padding: 12px;
}
.kpi-item.full {
  grid-column: span 2;
}
.kpi-label {
  font-size: 12px;
  color: #7ea6c8;
}
.kpi-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: #4ade80;
}
.kpi-value span {
  font-size: 12px;
  font-weight: 400;
  color: #7ea6c8;
}

.rank-chart {
  flex: 0 0 240px; /* 固定高度：排行图不再占满剩余空间，多余空间留在面板底部 */
}
.structure-chart {
  height: 220px;
  flex-shrink: 0;
}
/* 高度不足时面板内部滚动，避免图表互相挤压重叠 */
.side-panel {
  overflow-y: auto;
}

.map-panel {
  position: relative;
  background: rgba(13, 43, 69, 0.35);
  border: 1px solid rgba(34, 211, 238, 0.15);
  border-radius: 12px;
  overflow: hidden;
}
/* 地图数据来源标注（右下角，不挡地图交互与 visualMap） */
.map-attribution {
  position: absolute;
  right: 14px;
  bottom: 8px;
  z-index: 5;
  font-size: 11px;
  color: rgba(126, 166, 200, 0.65);
  pointer-events: none;
}
.map-box {
  width: 100%;
  height: 100%;
  min-height: 0;
}

/* 下钻抽屉 */
.drawer-body {
  min-height: 200px;
}
.drawer-period {
  font-size: 13px;
  font-weight: 600;
  color: #0b84bb;
  margin-bottom: 10px;
}
.drawer-kpis {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 10px;
}
.dkpi {
  background: #f0f7fd;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
}
.dkpi-label {
  font-size: 12px;
  color: #7d93b0;
}
.dkpi-value {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 700;
  color: #0b84bb;
}
.drawer-chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #51606e;
  margin: 14px 0 6px;
}
.drawer-chart {
  height: 230px;
}
</style>
