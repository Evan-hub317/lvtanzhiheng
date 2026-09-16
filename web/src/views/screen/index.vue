<template>
  <div class="screen-page">
    <!-- 顶栏 -->
    <header class="screen-header">
      <h1 class="screen-title tech-gradient-text">绿碳智衡 · 全国碳排放监测大屏</h1>
      <div class="header-right">
        <span class="header-time">{{ nowText }} · 数据截至 {{ fullYear }} 年</span>
        <el-button size="small" class="fullscreen-btn" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '⛶ 全屏' }}
        </el-button>
      </div>
    </header>

    <div class="screen-body">
      <!-- 左侧：KPI + 趋势 -->
      <div class="side-panel">
        <div class="panel-title">核心指标</div>
        <div class="kpi-grid">
          <div class="kpi-item">
            <div class="kpi-label">全国排放总量</div>
            <div class="kpi-value">{{ nationalTotal }} <span>亿吨</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">同比增速</div>
            <div class="kpi-value" :class="yoy >= 0 ? 'up' : 'down'">{{ yoy ?? '-' }}<span v-if="yoy !== null">%</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">碳强度</div>
            <div class="kpi-value">{{ intensity ?? '-' }}<span v-if="intensity !== null"> tCO₂/万元</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label">监测城市</div>
            <div class="kpi-value">{{ cityCount }} <span>个</span></div>
          </div>
        </div>
        <div class="panel-title sub">近五年排放趋势</div>
        <div ref="trendRef" class="mini-chart"></div>
      </div>

      <!-- 中间：地图 -->
      <div class="map-panel">
        <div ref="mapRef" class="map-box" v-loading="loading" element-loading-text="加载中…"></div>
      </div>

      <!-- 右侧：排行 + 结构 -->
      <div class="side-panel">
        <div class="panel-title">省份排放 TOP10</div>
        <div ref="rankRef" class="rank-chart"></div>
        <div class="panel-title sub">能源结构（{{ fullYear }} 年）</div>
        <div ref="pieRef" class="structure-chart"></div>
      </div>
    </div>

    <!-- 省份下钻抽屉（不用 destroy-on-close：DOM 常驻保证 ECharts 实例跨多次打开有效） -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="40%" class="screen-drawer">
      <div v-loading="drawerLoading" class="drawer-body">
        <div class="drawer-kpis">
          <div class="dkpi">
            <div class="dkpi-label">年度总量</div>
            <div class="dkpi-value">{{ provinceKpi?.totalEmission ? (provinceKpi.totalEmission / 1e8).toFixed(2) : '-' }} 亿吨</div>
          </div>
          <div class="dkpi">
            <div class="dkpi-label">同比</div>
            <div class="dkpi-value">{{ provinceKpi?.yoyRate ?? '-' }}%</div>
          </div>
          <div class="dkpi">
            <div class="dkpi-label">碳强度</div>
            <div class="dkpi-value">{{ provinceKpi?.intensity ?? '-' }} tCO₂/万元</div>
          </div>
        </div>
        <div class="drawer-chart-title">排放趋势</div>
        <div ref="provinceTrendRef" class="drawer-chart"></div>
        <div class="drawer-chart-title">行业结构（{{ fullYear }} 年）</div>
        <div ref="provincePieRef" class="drawer-chart"></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { dataStatusApi } from '@/api/data'
import { mapDataApi, trendApi, structureApi, energyStructureApi, kpiApi } from '@/api/analysis'
import { regionListApi } from '@/api/dict'
import chinaJson from '@/assets/china.json'

const loading = ref(false)
const fullYear = ref(2025)
const cityCount = ref(0)
const nationalTotal = ref('-')
const yoy = ref(null)
const intensity = ref(null)
const nowText = ref('')
let clockTimer = null

const mapRef = ref()
const trendRef = ref()
const rankRef = ref()
const pieRef = ref()
let mapChart, trendChart, rankChart, pieChart

// ===== 时钟 =====
function tickClock() {
  nowText.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

// ===== 数据加载 =====
async function loadScreenData() {
  loading.value = true
  try {
    const status = (await dataStatusApi()).data
    fullYear.value = status.maxFullYear ?? status.maxYear ?? 2025
    cityCount.value = status.cityCount ?? 0

    const [mapRes, trendRes, rankRes, structRes, energyRes, kpiRes] = await Promise.all([
      mapDataApi({ year: fullYear.value }),
      trendApi({ regionId: 1, startYear: (status.minYear ?? 2021), endYear: fullYear.value }),
      mapDataApi({ year: fullYear.value }),
      structureApi({ regionId: 1, year: fullYear.value }),
      energyStructureApi({ regionId: 1, year: fullYear.value }),
      kpiApi({ regionId: 1, year: fullYear.value })
    ])
    nationalTotal.value = (mapRes.data.reduce((s, p) => s + Number(p.emission), 0) / 1e8).toFixed(2)
    yoy.value = kpiRes.data?.yoyRate ?? null
    intensity.value = kpiRes.data?.intensity ?? null

    renderMap(mapRes.data)
    renderTrend(trendRes.data)
    renderRank(rankRes.data)
    renderPie(energyRes.data)
  } finally {
    loading.value = false
  }
}

// ===== 地图 =====
function renderMap(provinces) {
  mapChart = mapChart || echarts.init(mapRef.value)
  echarts.registerMap('china', chinaJson)
  const data = provinces.map(p => ({ name: p.regionName, value: Number((p.emission / 1e8).toFixed(2)) }))
  const max = Math.max(...data.map(d => d.value))
  mapChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: p => `${p.name}<br>排放量：<b>${p.value}</b> 亿吨`
    },
    visualMap: {
      min: 0,
      max: Math.ceil(max),
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
function renderTrend(rows) {
  trendChart = trendChart || echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: v => (v / 1e8).toFixed(2) + ' 亿吨' },
    grid: { left: 8, right: 10, top: 16, bottom: 4 },
    xAxis: {
      type: 'category', data: rows.map(r => r.year), boundaryGap: false,
      axisLine: { lineStyle: { color: '#23486b' } }, axisLabel: { color: '#7ea6c8', fontSize: 10 }
    },
    yAxis: {
      type: 'value', splitLine: { lineStyle: { color: '#16324f' } },
      axisLabel: { color: '#7ea6c8', fontSize: 10, formatter: v => (v / 1e8).toFixed(1) }
    },
    series: [{
      type: 'line', smooth: true, symbol: 'none', data: rows.map(r => r.emission),
      lineStyle: { width: 2, color: '#22d3ee' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(34, 211, 238, 0.4)' },
          { offset: 1, color: 'rgba(34, 211, 238, 0.02)' }
        ])
      }
    }]
  })
}

function renderRank(rows) {
  rankChart = rankChart || echarts.init(rankRef.value)
  const top = [...rows].sort((a, b) => a.emission - b.emission).slice(-10)
  rankChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => (v / 1e8).toFixed(2) + ' 亿吨' },
    grid: { left: 4, right: 16, top: 6, bottom: 4 },
    xAxis: {
      type: 'value', splitLine: { lineStyle: { color: '#16324f' } },
      axisLabel: { color: '#7ea6c8', fontSize: 10, formatter: v => (v / 1e8).toFixed(0) }
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

function renderPie(rows) {
  pieChart = pieChart || echarts.init(pieRef.value)
  if (!rows?.length) {
    pieChart.setOption({ graphic: emptyScreenGraphic('暂无数据') }, true)
    return
  }
  pieChart.setOption(buildPieOption(rows))
}

function emptyScreenGraphic(text) {
  return { type: 'text', left: 'center', top: 'middle', style: { text, fill: '#7ea6c8', fontSize: 13 } }
}

function buildPieOption(rows) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 亿吨（{d}%）' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#7ea6c8', fontSize: 10 } },
    color: ['#22d3ee', '#4ade80', '#fbbf24', '#a78bfa', '#f87171', '#f472b6', '#94a3b8', '#34d399'],
    series: [{
      type: 'pie', radius: ['40%', '62%'], center: ['50%', '44%'],
      label: { color: '#c9e2f5', fontSize: 10, formatter: '{b}\n{d}%' },
      itemStyle: { borderColor: '#0a1428', borderWidth: 1 },
      data: rows.map(r => ({ name: r.energyName, value: Number((r.emission / 1e8).toFixed(2)) }))
    }]
  }
}

// ===== 省份下钻 =====
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerTitle = ref('')
const provinceKpi = ref(null)
const provinceTrendRef = ref()
const provincePieRef = ref()
let provinceTrendChart, provincePieChart

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
    const regionId = province.id
    const [kpiRes, trendRes, structRes] = await Promise.all([
      kpiApi({ regionId, year: fullYear.value }),
      trendApi({ regionId, startYear: 2021, endYear: fullYear.value }),
      structureApi({ regionId, year: fullYear.value })
    ])
    provinceKpi.value = kpiRes.data
    await nextTick()
    // getInstanceByDom 保证重复打开复用同一实例（destroy-on-close 已移除，DOM 常驻）
    provinceTrendChart = echarts.getInstanceByDom(provinceTrendRef.value)
      || echarts.init(provinceTrendRef.value)
    provinceTrendChart.setOption({
      tooltip: { trigger: 'axis', valueFormatter: v => (v / 1e8).toFixed(2) + ' 亿吨' },
      grid: { left: 60, right: 16, top: 20, bottom: 30 },
      xAxis: {
        type: 'category', data: trendRes.data.map(r => r.year), boundaryGap: false,
        axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
      },
      yAxis: {
        type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } },
        axisLabel: { color: '#51606e', formatter: v => (v / 1e8).toFixed(1) }
      },
      series: [{
        type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: trendRes.data.map(r => r.emission),
        lineStyle: { width: 2.5, color: '#0ea5e9' }, itemStyle: { color: '#0ea5e9' }
      }]
    })
    provincePieChart = echarts.getInstanceByDom(provincePieRef.value)
      || echarts.init(provincePieRef.value)
    provincePieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 万吨（{d}%）' },
      legend: { bottom: 0, icon: 'circle', textStyle: { color: '#51606e', fontSize: 11 } },
      color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b'],
      series: [{
        type: 'pie', radius: ['40%', '64%'], center: ['50%', '44%'],
        label: { color: '#51606e', fontSize: 11, formatter: '{b}\n{d}%' },
        data: structRes.data.map(r => ({ name: r.industryName, value: Number((r.emission / 10000).toFixed(1)) }))
      }]
    })
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

let refreshTimer = null

function handleResize() {
  mapChart?.resize()
  trendChart?.resize()
  rankChart?.resize()
  pieChart?.resize()
  provinceTrendChart?.resize()
  provincePieChart?.resize()
}

onMounted(() => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  loadScreenData()
  refreshTimer = setInterval(loadScreenData, 30000)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  clearInterval(clockTimer)
  clearInterval(refreshTimer)
  window.removeEventListener('resize', handleResize)
  mapChart?.dispose()
  trendChart?.dispose()
  rankChart?.dispose()
  pieChart?.dispose()
  provinceTrendChart?.dispose()
  provincePieChart?.dispose()
})
</script>

<style scoped>
.screen-page {
  min-height: 100vh;
  background: radial-gradient(ellipse at 50% -10%, #10305a 0%, #0a1428 55%, #060d1c 100%);
  color: #e2f1ff;
  display: flex;
  flex-direction: column;
  padding: 0 20px 20px;
}

.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
.kpi-value.up {
  color: #fbbf24;
}
.kpi-value.down {
  color: #4ade80;
}

.mini-chart {
  flex: 1;
  min-height: 160px;
}
.rank-chart {
  flex: 1;
  min-height: 200px;
}
.structure-chart {
  flex: 1;
  min-height: 180px;
}

.map-panel {
  background: rgba(13, 43, 69, 0.35);
  border: 1px solid rgba(34, 211, 238, 0.15);
  border-radius: 12px;
  overflow: hidden;
}
.map-box {
  width: 100%;
  height: 100%;
  min-height: 480px;
}

/* 下钻抽屉 */
.drawer-body {
  min-height: 200px;
}
.drawer-kpis {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  margin-bottom: 18px;
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
  height: 240px;
}
</style>
