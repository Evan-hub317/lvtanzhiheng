<template>
  <div class="analysis-page">
    <!-- 筛选栏 -->
    <div class="tech-panel filter-bar">
      <div class="filter-item">
        <span class="filter-label">区域</span>
        <RegionSelect v-model="filters.regionId" :width="150" @change="onRegionChange" />
      </div>
      <div class="filter-item">
        <span class="filter-label">年份</span>
        <el-select v-model="filters.year" style="width: 110px" @change="onMainChange">
          <el-option v-for="y in yearOptions" :key="y" :label="`${y} 年`" :value="y" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">行业</span>
        <el-select v-model="filters.industryId" style="width: 140px" clearable placeholder="全部行业" @change="onIndustryChange">
          <el-option v-for="i in industries" :key="i.id" :label="i.industryName" :value="i.id" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">能源</span>
        <el-select v-model="filters.energyId" style="width: 140px" clearable placeholder="全部能源" @change="onEnergyChange">
          <el-option v-for="e in energies" :key="e.id" :label="e.energyName" :value="e.id" />
        </el-select>
      </div>
      <span class="filter-tip">提示：点击图表可联动下钻（结构图→行业筛选、排行图→切换城市）</span>
    </div>

    <!-- 行1：年度趋势 + 结构图 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <div class="tech-panel">
          <div class="tech-panel-title">{{ currentRegionName }} · 年度碳排放趋势{{ energyName ? '（' + energyName + '）' : '' }}</div>
          <div ref="trendRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="8">
        <div class="tech-panel">
          <div class="tech-panel-title">
            {{ filters.year }} 年排放结构
            <el-radio-group v-model="structType" size="small" class="struct-switch" @change="onStructTypeChange">
              <el-radio-button value="industry">行业</el-radio-button>
              <el-radio-button value="energy">能源</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="pieRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 行2：月度趋势 + 城市排行 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="tech-panel">
          <div class="tech-panel-title">
            {{ filters.year }} 年月度排放（季节规律{{ filters.industryId ? ' · ' + industryName : '' }}{{ filters.energyId ? ' · ' + energyName : '' }}）
          </div>
          <div ref="monthRef" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="tech-panel">
          <div class="tech-panel-title">{{ filters.year }} 年各市排放排行</div>
          <div ref="rankRef" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 行3：明细表 -->
    <div class="tech-panel">
      <div class="tech-panel-title">
        排放明细（区域 × 行业 × 能源 × 年度）
        <el-tag size="small" type="info" effect="plain">{{ detailTotal }} 条</el-tag>
      </div>
      <el-table :data="detailRows" v-loading="detailLoading" stripe size="small">
        <el-table-column prop="regionName" label="区县" min-width="110" />
        <el-table-column prop="industryName" label="行业" width="100" />
        <el-table-column prop="energyName" label="能源品种" width="100" />
        <el-table-column prop="year" label="年份" width="80" />
        <el-table-column prop="emission" label="排放量（tCO₂）" min-width="130" align="right">
          <template #default="{ row }">{{ Number(row.emission).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="yoyRate" label="同比（%）" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.yoyRate !== null && row.yoyRate !== undefined" :style="{ color: row.yoyRate >= 0 ? '#e6a23c' : '#10b981' }">
              {{ row.yoyRate >= 0 ? '+' : '' }}{{ row.yoyRate }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="detailQuery.pageNum"
          v-model:page-size="detailQuery.pageSize"
          :total="detailTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadDetail"
          @current-change="loadDetail"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { regionListApi, industryListApi, energyListApi } from '@/api/dict'
import RegionSelect from '@/components/RegionSelect.vue'
import { dataStatusApi } from '@/api/data'
import {
  trendApi, structureApi, energyStructureApi,
  monthlyTrendApi, regionRankingApi, detailPageApi
} from '@/api/analysis'

const route = useRoute()

// ===== 维度数据 =====
const regions = ref([])
const industries = ref([])
const energies = ref([])
const yearOptions = ref([])

const filters = reactive({ regionId: 1, year: null, industryId: null, energyId: null })
const structType = ref('industry')

const currentRegionName = computed(
  () => regions.value.find(r => r.id === filters.regionId)?.regionName || '全国'
)
const industryName = computed(
  () => industries.value.find(i => i.id === filters.industryId)?.industryName || ''
)
const energyName = computed(
  () => energies.value.find(e => e.id === filters.energyId)?.energyName || ''
)

// ===== 图表 =====
const trendRef = ref()
const pieRef = ref()
const monthRef = ref()
const rankRef = ref()
let trendChart, pieChart, monthChart, rankChart

function renderTrend(rows) {
  trendChart = trendChart || echarts.init(trendRef.value)
  if (!rows?.length) {
    trendChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  trendChart.setOption({
    graphic: null,
    tooltip: { trigger: 'axis', valueFormatter: v => (v / 10000).toFixed(1) + ' 万吨' },
    grid: { left: 60, right: 24, top: 30, bottom: 36 },
    xAxis: {
      type: 'category', data: rows.map(r => r.year), boundaryGap: false,
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    yAxis: {
      type: 'value', name: '万吨 CO₂',
      nameTextStyle: { color: '#51606e' }, axisLabel: { color: '#51606e', formatter: v => (v / 10000).toFixed(1) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [{
      name: '碳排放总量', type: 'line', smooth: true, symbol: 'circle', symbolSize: 7,
      data: rows.map(r => r.emission),
      itemStyle: { color: '#0ea5e9' }, lineStyle: { width: 3, color: '#0ea5e9' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(14, 165, 233, 0.35)' },
          { offset: 1, color: 'rgba(14, 165, 233, 0.02)' }
        ])
      }
    }]
  })
}

function renderStructure(rows) {
  pieChart = pieChart || echarts.init(pieRef.value)
  if (!rows?.length) {
    pieChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  pieChart.setOption({
    graphic: null,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 万吨（{d}%）' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#51606e' } },
    color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444', '#64748b', '#f97316'],
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', color: '#51606e' },
      data: rows.map(r => ({
        name: r.industryName || r.energyName,
        value: Number((r.emission / 10000).toFixed(1))
      }))
    }]
  })
  pieChart.off('click')
  pieChart.on('click', params => {
    if (structType.value === 'industry') {
      const target = industries.value.find(i => i.industryName === params.name)
      if (target) filters.industryId = target.id
    } else {
      const target = energies.value.find(e => e.energyName === params.name)
      if (target) filters.energyId = target.id
    }
    onIndustryChange()
  })
}

function renderMonthly(rows) {
  monthChart = monthChart || echarts.init(monthRef.value)
  if (!rows?.length) {
    monthChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  monthChart.setOption({
    graphic: null,
    tooltip: { trigger: 'axis', valueFormatter: v => (v / 10000).toFixed(1) + ' 万吨' },
    grid: { left: 60, right: 24, top: 30, bottom: 36 },
    xAxis: {
      type: 'category', data: rows.map(r => r.month + '月'),
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    yAxis: {
      type: 'value', name: '万吨 CO₂',
      nameTextStyle: { color: '#51606e' }, axisLabel: { color: '#51606e', formatter: v => (v / 10000).toFixed(1) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [{
      name: '月度排放', type: 'bar', barMaxWidth: 26,
      data: rows.map(r => r.emission),
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#0ea5e9' }, { offset: 1, color: '#10b981' }
        ])
      }
    }]
  })
}

function renderRanking(rows) {
  rankChart = rankChart || echarts.init(rankRef.value)
  if (!rows?.length) {
    rankChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  const sorted = [...rows].reverse() // 条形图自下而上
  rankChart.setOption({
    graphic: null,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: v => (v / 10000).toFixed(1) + ' 万吨' },
    grid: { left: 70, right: 40, top: 10, bottom: 30 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#51606e', formatter: v => (v / 10000).toFixed(1) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    yAxis: {
      type: 'category', data: sorted.map(r => r.regionName),
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    series: [{
      type: 'bar', barMaxWidth: 20, data: sorted.map(r => r.emission),
      itemStyle: {
        borderRadius: [0, 5, 5, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#06b6d4' }, { offset: 1, color: '#0ea5e9' }
        ])
      }
    }]
  })
}

function emptyGraphic(text) {
  return { type: 'text', left: 'center', top: 'middle', style: { text, fill: '#98a4b3', fontSize: 14 } }
}

// ===== 明细表 =====
const detailRows = ref([])
const detailTotal = ref(0)
const detailLoading = ref(false)
const detailQuery = reactive({ pageNum: 1, pageSize: 10 })

// ===== 数据加载与联动 =====
// seq 防竞态：仅最新一轮筛选的响应可渲染（慢的旧请求不会覆盖新选择的结果）
let mainSeq = 0

async function loadDetail(seq) {
  detailLoading.value = true
  try {
    const res = await detailPageApi({
      ...detailQuery,
      regionId: filters.regionId,
      year: filters.year,
      industryId: filters.industryId,
      energyId: filters.energyId
    })
    if (seq === mainSeq) {
      detailRows.value = res.data.records
      detailTotal.value = res.data.total
    }
  } finally {
    if (seq === mainSeq) {
      detailLoading.value = false
    }
  }
}

async function loadTrend(seq) {
  const res = await trendApi({
    regionId: filters.regionId,
    startYear: yearOptions.value[0],
    endYear: yearOptions.value[yearOptions.value.length - 1],
    energyId: filters.energyId
  })
  if (seq === mainSeq) {
    renderTrend(res.data)
  }
}

async function loadStructure(seq) {
  const params = { regionId: filters.regionId, year: filters.year }
  const res = structType.value === 'industry'
    ? await structureApi(params)
    : await energyStructureApi(params)
  if (seq === mainSeq) {
    renderStructure(res.data)
  }
}

async function loadMonthly(seq) {
  const res = await monthlyTrendApi({
    regionId: filters.regionId,
    year: filters.year,
    industryId: filters.industryId,
    energyId: filters.energyId
  })
  if (seq === mainSeq) {
    renderMonthly(res.data)
  }
}

async function loadRanking(seq) {
  const res = await regionRankingApi({ year: filters.year })
  if (seq === mainSeq) {
    renderRanking(res.data)
  }
}

function onRegionChange() {
  filters.industryId = null
  filters.energyId = null
  onMainChange()
}

function onMainChange() {
  const seq = ++mainSeq
  loadTrend(seq)
  loadStructure(seq)
  loadMonthly(seq)
  loadRanking(seq)
  detailQuery.pageNum = 1
  loadDetail(seq)
}

function onIndustryChange() {
  const seq = ++mainSeq
  loadMonthly(seq)
  detailQuery.pageNum = 1
  loadDetail(seq)
}

function onStructTypeChange() {
  const seq = ++mainSeq
  loadStructure(seq)
}

function onEnergyChange() {
  // 能源筛选联动：年度趋势 + 月度趋势 + 明细表
  const seq = ++mainSeq
  loadTrend(seq)
  loadMonthly(seq)
  detailQuery.pageNum = 1
  loadDetail(seq)
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
  monthChart?.resize()
  rankChart?.resize()
}

onMounted(async () => {
  const [regionRes, industryRes, energyRes, statusRes] = await Promise.all([
    regionListApi(), industryListApi(), energyListApi(), dataStatusApi()
  ])
  regions.value = regionRes.data.filter(r => r.level <= 2)
  industries.value = industryRes.data
  energies.value = energyRes.data

  const { minYear, maxYear, maxFullYear } = statusRes.data
  // 年度分析使用完整年范围（当年未过完不参与）
  const endYear = maxFullYear || maxYear
  if (minYear && endYear) {
    yearOptions.value = Array.from({ length: endYear - minYear + 1 }, (_, i) => minYear + i)
    filters.year = Number(route.query.year) && route.query.year <= endYear ? Number(route.query.year) : endYear
  } else {
    filters.year = null
  }
  if (route.query.industryId) {
    filters.industryId = Number(route.query.industryId)
  }
  if (filters.year) {
    onMainChange()
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
  monthChart?.dispose()
  rankChart?.dispose()
})
</script>

<style scoped>
.analysis-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding: 14px 20px;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-label {
  font-size: 13px;
  color: #51606e;
}
.filter-tip {
  margin-left: auto;
  font-size: 12px;
  color: #98a4b3;
}
.struct-switch {
  margin-left: 12px;
}
.chart-box {
  height: 300px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
