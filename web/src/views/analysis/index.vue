<template>
  <div class="analysis-page">
    <!-- 筛选栏：固定两行，第一行 粒度/区域/年份/月份，第二行 行业/能源 -->
    <div class="tech-panel filter-bar">
      <div class="filter-line">
        <div class="filter-item">
          <span class="filter-label">粒度</span>
          <el-radio-group v-model="granularity" @change="onGranularityChange">
            <el-radio-button value="year">年</el-radio-button>
            <el-radio-button value="month">月</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-item">
          <span class="filter-label">区域</span>
          <RegionSelect v-model="filters.regionId" :width="150" @change="onRegionChange" />
        </div>
        <span class="filter-tip">提示：点击图表可联动下钻（结构图→行业筛选、排行图→切换城市）</span>
      </div>
      <div class="filter-line">
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
        <div class="filter-item">
          <span class="filter-label">年份</span>
          <el-select v-model="filters.year" style="width: 110px" placeholder="-" @change="onMainChange">
            <el-option v-for="y in yearOptions" :key="y" :label="`${y} 年`" :value="y" />
          </el-select>
        </div>
        <div class="filter-item" v-if="granularity === 'month'">
          <span class="filter-label">月份</span>
          <el-select v-model="selectedMonth" style="width: 110px" @change="onMonthChange">
            <el-option v-for="m in monthOptions" :key="m" :label="`${m} 月`" :value="m" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 行1：年度趋势（仅年粒度） + 结构图（月粒度时占满整行） -->
    <el-row :gutter="16">
      <el-col v-if="granularity === 'year'" :xs="24" :md="16">
        <div class="tech-panel row-fill">
          <div class="chart-title-row">
            <div class="tech-panel-title">
              {{ currentRegionName }} · 年度碳排放趋势{{ trendFilterText }}
              <span v-if="isIncompleteYear" class="chart-tip-inline">（注：因{{ yearLabel }}年尚未结束，故不含此年数据）</span>
            </div>
            <el-radio-group v-model="trendMetric" size="small" @change="onTrendMetricChange">
              <el-radio-button value="total">总量</el-radio-button>
              <el-radio-button value="perCapita">人均</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="trendRef" class="chart-box" v-loading="loading" element-loading-text="加载中…"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="granularity === 'year' ? 8 : 24">
        <div class="tech-panel">
          <div class="struct-switch-row">
            <el-radio-group v-model="structType" size="small" @change="onStructTypeChange">
              <el-radio-button value="industry">行业</el-radio-button>
              <el-radio-button value="energy">能源（直接）</el-radio-button>
              <el-radio-button value="indirect">能源（间接）</el-radio-button>
            </el-radio-group>
          </div>
          <div class="tech-panel-title">{{ structTitle }}</div>
          <div ref="pieRef" class="chart-box" v-loading="loading" element-loading-text="加载中…"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 行2：月度趋势 + 城市排行（两卡等高） -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12" class="row2-col">
        <div class="tech-panel row2-panel">
          <div class="chart-title-row">
            <div class="tech-panel-title">
              {{ yearLabel }} 年月度排放（季节规律{{ filters.industryId ? ' · ' + industryName : '' }}{{ filters.energyId ? ' · ' + energyName : '' }}）
              <span v-if="showMonthGapTip" class="chart-tip-inline">（注：因{{ yearLabel }}年尚未结束，故不含{{ monthGapText }}数据）</span>
            </div>
            <el-radio-group v-model="monthMetric" size="small" @change="onMonthMetricChange">
              <el-radio-button value="total">总量</el-radio-button>
              <el-radio-button value="perCapita">人均</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="monthRef" class="chart-box month-chart-box" v-loading="loading" element-loading-text="加载中…"></div>
        </div>
      </el-col>
      <el-col :xs="24" :md="12" class="row2-col">
        <div class="tech-panel row2-panel">
          <div class="struct-switch-row">
            <el-radio-group v-model="rankType" size="small" @change="onRankTypeChange">
              <el-radio-button value="province">{{ provinceRankLabel }}</el-radio-button>
              <!-- 直辖市无下辖地级行政区，不提供该选项 -->
              <el-radio-button v-if="!isMunicipalitySelected" value="city">{{ cityRankLabel }}</el-radio-button>
            </el-radio-group>
            <el-radio-group v-model="rankMetric" size="small" @change="onRankMetricChange" style="margin-left: 12px">
              <el-radio-button value="total">总量</el-radio-button>
              <el-radio-button value="perCapita">人均</el-radio-button>
            </el-radio-group>
          </div>
          <div class="tech-panel-title">{{ rankTitle }}</div>
          <div class="rank-scroll" v-loading="loading" element-loading-text="加载中…">
            <div ref="rankRef" class="chart-box rank-chart"></div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 行3：明细表 -->
    <div class="tech-panel">
      <div class="chart-title-row">
        <div class="tech-panel-title">
          排放明细（区域 × 行业 × 能源 · {{ yearLabel }} 年）
          <el-tag size="small" type="info" effect="plain">{{ detailTotal }} 条</el-tag>
        </div>
      </div>
      <!-- 明细筛选行：粒度/月份跟随顶部全局选项，此处仅等级与排序（选择即生效） -->
      <div class="detail-filters">
        <span class="filter-caption">总排放量等级</span>
        <el-select v-model="emissionPreset" size="small" placeholder="未选择" clearable style="width: 125px" @change="onEmissionPresetChange">
          <el-option v-for="l in emissionLevels" :key="l.value" :label="l.label" :value="l.value" />
        </el-select>
        <span class="filter-caption">人均排放量等级</span>
        <el-select v-model="perCapitaPreset" size="small" placeholder="未选择" clearable style="width: 125px" @change="onPerCapitaPresetChange">
          <el-option v-for="l in perCapitaLevels" :key="l.value" :label="l.label" :value="l.value" />
        </el-select>
        <!-- 纯文字排序条：靠右；选中项带 ↑/↓ 箭头 -->
        <span class="sort-bar sort-right">
          <span class="sort-label">排序：</span>
          <template v-for="opt in sortOptions" :key="opt.value">
            <span
              class="sort-option"
              :class="{ active: detailQuery.sortField === opt.value, disabled: opt.value === 'yoyRate' && yoyDisabled }"
              @click="onSortOptionClick(opt.value)"
            >{{ opt.label }}</span>
            <span
              v-if="detailQuery.sortField === opt.value"
              class="sort-arrow"
              @click="toggleSortOrder"
            >{{ detailQuery.sortOrder === 'asc' ? '↑' : '↓' }}</span>
          </template>
        </span>
      </div>
      <el-table :data="detailRows" v-loading="detailLoading" stripe size="small">
        <el-table-column prop="regionName" label="区域" width="130" align="center" />
        <el-table-column prop="industryName" label="行业" width="110" align="center" />
        <el-table-column prop="energyName" label="能源品种" width="110" align="center" />
        <el-table-column label="时间" width="110" align="center">
          <template #default="{ row }">{{ row.month != null ? `${row.year}-${String(row.month).padStart(2, '0')}` : row.year }}</template>
        </el-table-column>
        <el-table-column prop="emission" label="总排放量（tCO₂）" min-width="120" align="center">
          <template #default="{ row }">{{ Number(row.emission).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="perCapita" label="人均排放量（tCO₂/人）" width="150" align="center">
          <template #default="{ row }">{{ row.perCapita != null ? Number(row.perCapita).toFixed(4) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="yoyRate" label="同比（%）" width="110" align="center">
          <template #default="{ row }">
            <!-- 年粒度的未过完当年与上年全年比较无意义，显示为空；月粒度同比与去年同月比，正常显示 -->
            <span v-if="!yoyDisabled && row.yoyRate !== null && row.yoyRate !== undefined" :style="{ color: row.yoyRate >= 0 ? '#e6a23c' : '#10b981' }">
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
          @size-change="onDetailPageChange"
          @current-change="onDetailPageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

// keep-alive 标签页缓存名（布局 include 列表按此匹配）
defineOptions({ name: 'AnalysisPage' })
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { regionListApi, industryListApi, energyListApi } from '@/api/dict'
import RegionSelect from '@/components/RegionSelect.vue'
import { dataStatusApi } from '@/api/data'
import {
  trendApi, structureApi, energyStructureApi, indirectApi,
  monthlyTrendApi, regionRankingApi, provinceRankingApi, cityRankingApi, monthRankingApi,
  detailPageApi, screenPeriodApi
} from '@/api/analysis'

const route = useRoute()
/** 图表数据加载中（转圈遮罩） */
const loading = ref(false)

// ===== 维度数据 =====
const regions = ref([])
const industries = ref([])
const energies = ref([])
const yearOptions = ref([])

const filters = reactive({ regionId: 1, year: null, industryId: null, energyId: null })
const structType = ref('industry')

// ===== 全局粒度（年/月） =====
const granularity = ref('year')
const selectedMonth = ref(1)
const maxYear = ref(null)
const maxFullYear = ref(null)
const maxMonthOfYear = ref(null) // 最新年份已生成的最大月

/** 所选年为「尚未过完的当年」（年度趋势图加提示） */
const isIncompleteYear = computed(() =>
  granularity.value === 'year' && filters.year != null && maxYear.value != null &&
  filters.year === maxYear.value && maxFullYear.value !== maxYear.value
)
/** 所选年已生成的最大月（完整年为 12） */
const maxGenMonth = computed(() =>
  filters.year != null && filters.year === maxYear.value ? (maxMonthOfYear.value || 12) : 12
)
const showMonthGapTip = computed(() => maxGenMonth.value < 12)
/** 月份下拉选项：未过完的当年只到已生成的最大月，其余年份 1-12 月 */
const monthOptions = computed(() => {
  const max = (filters.year != null && filters.year === maxYear.value) ? maxGenMonth.value : 12
  return Array.from({ length: max }, (_, i) => i + 1)
})
/** 所选年为尚未过完的当年：同比与上年全年比较无意义，明细显示为空、排序不可用 */
const isIncompleteYearSelected = computed(() =>
  filters.year != null && maxYear.value != null &&
  filters.year === maxYear.value && maxFullYear.value !== maxYear.value
)
/** 同比置空仅限「年粒度 + 未过完当年」；月粒度同比与去年同月比较，仍有意义 */
const yoyDisabled = computed(() => granularity.value === 'year' && isIncompleteYearSelected.value)
/** 缺失月份范围文案（如「10月-12月」） */
const monthGapText = computed(() => {
  const start = maxGenMonth.value + 1
  return start === 12 ? '12月' : `${start}月-12月`
})

/** 粒度切换：清空等级（档位随粒度变化）并全量重载 */
function onGranularityChange() {
  emissionPreset.value = null
  perCapitaPreset.value = null
  detailQuery.minEmission = null
  detailQuery.maxEmission = null
  detailQuery.minPerCapita = null
  detailQuery.maxPerCapita = null
  onMainChange()
  // 结构图卡片宽度随粒度变化（8 列 ↔ 24 列），布局完成后按新尺寸重绘各图
  nextTick(() => handleResize())
}
function onMonthChange() {
  onMainChange()
}

// 年份切换后校正月份：未过完的当年超出已生成范围的月份回落到最大已生成月
watch(
  () => filters.year,
  () => {
    const max = monthOptions.value[monthOptions.value.length - 1] || 1
    if (selectedMonth.value > max) {
      selectedMonth.value = max
    }
  }
)
const rankType = ref('province') // 排行范围：省级行政区 / 地级行政区（默认省级）
const rankMetric = ref('total') // 排行口径：总量 / 人均
const rankRows = ref([]) // 最近一次排行数据（口径切换时本地重排序，不重新请求）

// 范围标签随所选区域口径变化：
// 全国时「省级/地级行政区」；选省时「各省级/此省内各地级行政区」；
// 选到具体地级行政区时「全国/所在省级行政区内」（两种范围都展示地级排行，高亮该市）
const isCitySelected = computed(() => {
  const r = regions.value.find(x => x.id === filters.regionId)
  return !!r && r.level === 2
})
const provinceRankLabel = computed(() => {
  if (isCitySelected.value) return '全国'
  return filters.regionId === 1 ? '省级行政区' : '各省级行政区'
})
const cityRankLabel = computed(() => {
  if (isCitySelected.value) return '所在省级行政区内'
  return filters.regionId === 1 ? '地级行政区' : '此省内各地级行政区'
})
const rankTitle = computed(() => {
  const scope = rankType.value === 'city' ? cityRankLabel.value : provinceRankLabel.value
  const metric = rankMetric.value === 'total' ? '总量' : '人均'
  const period = granularity.value === 'month'
    ? `${yearLabel.value} 年 ${selectedMonth.value} 月`
    : `${yearLabel.value} 年`
  return `${period}${scope}${metric}排行`
})

// 所选区域为直辖市：无下辖地级行政区，隐藏「此省内各地级行政区」选项并复位范围
const isMunicipalitySelected = computed(() => {
  const r = regions.value.find(x => x.id === filters.regionId)
  return !!r?.isMunicipality
})
watch(
  () => filters.regionId,
  () => {
    if (isMunicipalitySelected.value && rankType.value === 'city') {
      rankType.value = 'province'
    }
  }
)

/** 年份显示：数据未加载完成（filters.year 为 null）时以“-”占位 */
const yearLabel = computed(() => filters.year ?? '-')

const structTitle = computed(() => {
  const period = granularity.value === 'month'
    ? `${yearLabel.value} 年 ${selectedMonth.value} 月`
    : `${yearLabel.value} 年`
  if (structType.value === 'industry') return `${period}行业排放结构`
  if (structType.value === 'energy') return `${period}能源结构（直接排放）`
  return `${period}间接排放结构`
})

const currentRegionName = computed(
  () => regions.value.find(r => r.id === filters.regionId)?.regionName || '全国'
)
const industryName = computed(
  () => industries.value.find(i => i.id === filters.industryId)?.industryName || ''
)
const energyName = computed(
  () => energies.value.find(e => e.id === filters.energyId)?.energyName || ''
)
// 年度趋势标题的筛选标注：行业与能源合并为「（工业 · 原油）」形式
const trendFilterText = computed(() => {
  const parts = [industryName.value, energyName.value].filter(Boolean)
  return parts.length ? '（' + parts.join(' · ') + '）' : ''
})

// ===== 年度/月度趋势口径（总量 / 人均） =====
const trendMetric = ref('total')
const monthMetric = ref('total')
const trendRows = ref([])
const monthRows = ref([])

// 当前所选区域人口（万人）：全国 = 各省级行政区之和；人均换算用
const regionPopulation = computed(() => {
  if (filters.regionId === 1) {
    return regions.value.filter(r => r.level === 1).reduce((s, r) => s + Number(r.population || 0), 0)
  }
  const r = regions.value.find(x => x.id === filters.regionId)
  return r ? Number(r.population || 0) : 0
})

/** 总量/人均切换只改变数值口径，用缓存数据本地重渲染 */
function onTrendMetricChange() {
  renderTrend(trendRows.value)
}
function onMonthMetricChange() {
  renderMonthly(monthRows.value)
}

// ===== 图表 =====
const trendRef = ref()
const pieRef = ref()
const monthRef = ref()
const rankRef = ref()
let trendChart, pieChart, monthChart, rankChart

function renderTrend(rows) {
  trendChart = echarts.getInstanceByDom(trendRef.value) || echarts.init(trendRef.value)
  if (!rows?.length) {
    trendChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  const isPer = trendMetric.value === 'perCapita'
  const pop = regionPopulation.value
  // 所选年份的数据点高亮为橙色（年粒度）
  const highlightYear = granularity.value === 'year' ? filters.year : null
  const data = rows.map(r => {
    const v = isPer && pop > 0 ? Number((r.emission / (pop * 10000)).toFixed(4)) : r.emission
    return r.year === highlightYear
      ? { value: v, symbolSize: 11, itemStyle: { color: '#f59e0b', borderColor: '#f59e0b', borderWidth: 2 } }
      : v
  })
  trendChart.setOption({
    graphic: null,
    tooltip: { trigger: 'axis', valueFormatter: v => (isPer ? Number(v).toFixed(2) + ' tCO₂/人' : (v / 1e8).toFixed(2) + ' 亿吨') },
    grid: { left: 84, right: 24, top: 30, bottom: 36 },
    xAxis: {
      type: 'category', data: rows.map(r => r.year), boundaryGap: false,
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    yAxis: {
      type: 'value', name: isPer ? 'tCO₂/人' : '亿吨 CO₂',
      nameTextStyle: { color: '#51606e', padding: [0, 0, 0, 4] },
      nameGap: 12,
      axisLabel: {
        color: '#51606e',
        // 按量级自适应小数位，避免小区域刻度标签四舍五入后重叠
        formatter: v => {
          if (isPer) return Number(v).toFixed(2)
          const y = v / 1e8
          if (y >= 10) return y.toFixed(0)
          if (y >= 1) return y.toFixed(1)
          return y.toFixed(3)
        }
      },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [{
      name: isPer ? '人均碳排放' : '碳排放总量', type: 'line', smooth: true, symbol: 'circle', symbolSize: 7,
      data,
      itemStyle: { color: '#0ea5e9' }, lineStyle: { width: 3, color: '#0ea5e9' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(14, 165, 233, 0.35)' },
          { offset: 1, color: 'rgba(14, 165, 233, 0.02)' }
        ])
      }
    }]
  }, true) // notMerge：全量替换，清除上次“暂无数据”的 graphic 残留
}

function renderStructure(rows) {
  pieChart = echarts.getInstanceByDom(pieRef.value) || echarts.init(pieRef.value)
  if (!rows?.length) {
    pieChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  pieChart.setOption({
    graphic: null,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 亿吨（{d}%）' },
    legend: { bottom: 0, type: 'scroll', icon: 'circle', itemGap: 10, textStyle: { color: '#51606e', fontSize: 11 } },
    color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444', '#64748b', '#f97316'],
    series: [{
      type: 'pie', radius: ['38%', '58%'], center: ['50%', '42%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', color: '#51606e', fontSize: 11 },
      labelLine: { length: 10, length2: 6 },
      data: rows.map(r => ({
        name: r.industryName || r.energyName,
        value: Number((r.emission / 1e8).toFixed(2))
      }))
    }]
  }, true)  // notMerge：切换 Tab 时全量替换，避免旧图残留
  pieChart.resize() // 粒度切换会改变卡片宽度（8 列 ↔ 24 列），必须按新尺寸重绘
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
  monthChart = echarts.getInstanceByDom(monthRef.value) || echarts.init(monthRef.value)
  if (!rows?.length) {
    monthChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  const isPer = monthMetric.value === 'perCapita'
  const pop = regionPopulation.value
  // 补全 12 个月：未生成月份为空（提示见标题下）；月粒度时选中月高亮为橙色
  const highlightMonth = granularity.value === 'month' ? selectedMonth.value : null
  const data = []
  for (let m = 1; m <= 12; m++) {
    const row = rows.find(r => r.month === m)
    const v = row
      ? (isPer && pop > 0 ? Number((row.emission / (pop * 10000)).toFixed(4)) : row.emission)
      : null
    data.push(m === highlightMonth ? { value: v, itemStyle: { color: '#f59e0b' } } : v)
  }
  monthChart.setOption({
    graphic: null,
    tooltip: { trigger: 'axis', valueFormatter: v => (isPer ? Number(v).toFixed(2) + ' tCO₂/人' : (v / 10000).toFixed(1) + ' 万吨') },
    grid: { left: 60, right: 24, top: 30, bottom: 36 },
    xAxis: {
      type: 'category', data: Array.from({ length: 12 }, (_, i) => (i + 1) + '月'),
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    yAxis: {
      type: 'value', name: isPer ? 'tCO₂/人' : '万吨 CO₂',
      nameTextStyle: { color: '#51606e' },
      axisLabel: { color: '#51606e', formatter: v => (isPer ? Number(v).toFixed(2) : (v / 10000).toFixed(1)) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [{
      name: isPer ? '人均排放' : '月度排放', type: 'bar', barMaxWidth: 26,
      data,
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#0ea5e9' }, { offset: 1, color: '#10b981' }
        ])
      }
    }]
  }, true) // notMerge：全量替换，清除上次“暂无数据”的 graphic 残留
}

function renderRanking(rows) {
  // 区域多时按固定行高撑高图表，卡片高度不变，容器内滚动
  const rowHeight = 36
  const count = rows?.length || 0
  rankRef.value.style.height = Math.max(300, count * rowHeight + 24) + 'px'
  // 切换列表时先重置滚动：长列表滚到底后切到短列表，
  // 旧滚动位置会把内容顶出可视区，留下大片空白
  if (rankRef.value.parentElement) {
    rankRef.value.parentElement.scrollTop = 0
  }
  rankChart = echarts.getInstanceByDom(rankRef.value) || echarts.init(rankRef.value)
  if (!rows?.length) {
    rankChart.setOption({ graphic: emptyGraphic('暂无数据') }, true)
    return
  }
  const isPer = rankMetric.value === 'perCapita'
  // 升序排列（条形图自下而上，最大值在最上）；名次 = 总数 - 索引
  const sorted = [...rows].sort((a, b) => {
    const va = isPer ? Number(a.perCapita || 0) : Number(a.emission || 0)
    const vb = isPer ? Number(b.perCapita || 0) : Number(b.emission || 0)
    return va - vb
  })
  // 名次前缀 + 直辖市标注
  const names = sorted.map((r, i) => {
    const rank = sorted.length - i
    return `${rank}. ${r.isMunicipality ? r.regionName + '（直辖市）' : r.regionName}`
  })
  // 高亮与滚动定位的目标：
  // 选到地级行政区时两种范围（全国地级/省内各地级）都高亮该市；
  // 选省且范围=各省级行政区时高亮该省；全国口径不亮
  let highlight = null
  if (isCitySelected.value) {
    highlight = filters.regionId
  } else if (filters.regionId !== 1 && rankType.value === 'province') {
    highlight = filters.regionId
  }
  const barData = sorted.map(r => {
    const item = {
      value: isPer ? Number(r.perCapita || 0) : Number(r.emission),
      borderRadius: [0, 5, 5, 0]
    }
    if (r.regionId === highlight) {
      item.itemStyle = { color: '#f59e0b' }
    } else {
      item.itemStyle = {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#06b6d4' }, { offset: 1, color: '#0ea5e9' }
        ])
      }
    }
    return item
  })
  rankChart.setOption({
    animation: false, // 列表行数变化大，关闭过渡动画避免旧布局残留
    graphic: null,
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      valueFormatter: v => (isPer ? Number(v).toFixed(2) + ' tCO₂/人' : (v / 10000).toFixed(1) + ' 万吨')
    },
    grid: { left: 130, right: 50, top: 10, bottom: 30 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#51606e', formatter: v => (isPer ? Number(v).toFixed(1) : (v / 10000).toFixed(1)) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    yAxis: {
      type: 'category', data: names,
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    series: [{
      type: 'bar', barMaxWidth: 14, barCategoryGap: '55%', data: barData
    }]
  }, true) // notMerge：全量替换，避免旧长列表的图形残留
  rankChart.resize()
  // 选中省自动滚动到卡片中间：升序数组索引 0 是最底部行，
  // 需反转为「从图表顶部数」的位置，再按实际网格行高定位行中心
  if (highlight != null) {
    const idx = sorted.findIndex(r => r.regionId === highlight)
    if (idx >= 0) {
      const scroller = rankRef.value.parentElement // rank-scroll 容器
      const gridTop = 10
      const gridBottom = 30
      const band = (rankRef.value.clientHeight - gridTop - gridBottom) / count
      const rowCenter = gridTop + (count - 1 - idx) * band + band / 2
      scroller.scrollTop = Math.max(0, rowCenter - scroller.clientHeight / 2)
    }
  }
  // 点击排行条目下钻：按 dataIndex 定位原数据行；
  // 点省 → 切换区域联动其余图表，并切到「此省内各地级行政区」视图
  rankChart.off('click')
  rankChart.on('click', params => {
    const row = sorted[params.dataIndex]
    if (!row) return
    const region = regions.value.find(r => r.id === row.regionId)
    if (region) {
      filters.regionId = region.id
      if (rankType.value === 'province') rankType.value = 'city'
      onRegionChange()
    }
  })
}

function emptyGraphic(text) {
  return { type: 'text', left: 'center', top: 'middle', style: { text, fill: '#98a4b3', fontSize: 14 } }
}

// ===== 明细表 =====
const detailRows = ref([])
const detailTotal = ref(0)
const detailLoading = ref(false)
const detailQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  minEmission: null, // 总排放量区间（tCO2）
  maxEmission: null,
  minPerCapita: null, // 人均排放量区间（tCO2/人）
  maxPerCapita: null,
  sortField: 'time', // 排序字段
  sortOrder: 'desc' // 排序方向
})
// 等级选择（按粒度分档：月度量级约为年度的 1/12，档位整体降一级）
const emissionPreset = ref(null)
const perCapitaPreset = ref(null)
const emissionLevels = computed(() => (granularity.value === 'month'
  ? [
      { value: 'e1', label: '微型（<1000 吨）', min: null, max: 1e3 },
      { value: 'e2', label: '小型（1000 吨~1 万吨）', min: 1e3, max: 1e4 },
      { value: 'e3', label: '中型（1~10 万吨）', min: 1e4, max: 1e5 },
      { value: 'e4', label: '大型（10~100 万吨）', min: 1e5, max: 1e6 },
      { value: 'e5', label: '超大型（>100 万吨）', min: 1e6, max: null }
    ]
  : [
      { value: 'e1', label: '微型（<1 万吨）', min: null, max: 1e4 },
      { value: 'e2', label: '小型（1~10 万吨）', min: 1e4, max: 1e5 },
      { value: 'e3', label: '中型（10~100 万吨）', min: 1e5, max: 1e6 },
      { value: 'e4', label: '大型（100~1000 万吨）', min: 1e6, max: 1e7 },
      { value: 'e5', label: '超大型（>1000 万吨）', min: 1e7, max: null }
    ]))
const perCapitaLevels = computed(() => (granularity.value === 'month'
  ? [
      { value: 'p1', label: '低（<0.1）', min: null, max: 0.1 },
      { value: 'p2', label: '中低（0.1~0.3）', min: 0.1, max: 0.3 },
      { value: 'p3', label: '中（0.3~0.5）', min: 0.3, max: 0.5 },
      { value: 'p4', label: '中高（0.5~1）', min: 0.5, max: 1 },
      { value: 'p5', label: '高（>1）', min: 1, max: null }
    ]
  : [
      { value: 'p1', label: '低（<1）', min: null, max: 1 },
      { value: 'p2', label: '中低（1~3）', min: 1, max: 3 },
      { value: 'p3', label: '中（3~6）', min: 3, max: 6 },
      { value: 'p4', label: '中高（6~10）', min: 6, max: 10 },
      { value: 'p5', label: '高（>10）', min: 10, max: null }
    ]))
function onEmissionPresetChange(v) {
  const l = emissionLevels.value.find(x => x.value === v)
  detailQuery.minEmission = l ? l.min : null
  detailQuery.maxEmission = l ? l.max : null
  onDetailQueryChange()
}
function onPerCapitaPresetChange(v) {
  const l = perCapitaLevels.value.find(x => x.value === v)
  detailQuery.minPerCapita = l ? l.min : null
  detailQuery.maxPerCapita = l ? l.max : null
  onDetailQueryChange()
}
// ===== 纯文字排序条 =====
const sortOptions = [
  { value: 'time', label: '时间' },
  { value: 'emission', label: '总排放量' },
  { value: 'perCapita', label: '人均排放量' },
  { value: 'yoyRate', label: '同比' }
]
// 年粒度切到未过完的当年时，若当前排序为同比，自动回退为按时间排序
watch(yoyDisabled, v => {
  if (v && detailQuery.sortField === 'yoyRate') {
    detailQuery.sortField = 'time'
    onDetailQueryChange()
  }
})

function onSortOptionClick(v) {
  // 年粒度的未过完当年同比无意义，「同比」排序不可用
  if (v === 'yoyRate' && yoyDisabled.value) return
  if (detailQuery.sortField === v) {
    // 已选中：点击切换升/降序
    toggleSortOrder()
    return
  }
  // 未选中：选中该项，默认降序
  detailQuery.sortField = v
  detailQuery.sortOrder = 'desc'
  onDetailQueryChange()
}
function toggleSortOrder() {
  detailQuery.sortOrder = detailQuery.sortOrder === 'asc' ? 'desc' : 'asc'
  onDetailQueryChange()
}
/** 明细查询条件变化：回到第一页重新加载 */
function onDetailQueryChange() {
  detailQuery.pageNum = 1
  loadDetail(++mainSeq)
}

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
      energyId: filters.energyId,
      // 明细粒度/月份跟随顶部全局选项
      granularity: granularity.value,
      month: granularity.value === 'month' ? selectedMonth.value : null
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
    // 年度趋势只取完整年度（当年未过完不纳入），2026 年不接
    endYear: maxFullYear.value || yearOptions.value[yearOptions.value.length - 1],
    industryId: filters.industryId,
    energyId: filters.energyId
  })
  if (seq === mainSeq) {
    trendRows.value = res.data
    renderTrend(res.data)
  }
}

async function loadStructure(seq) {
  let res = null
  if (granularity.value === 'month') {
    // 月粒度：复用大屏时段聚合接口，按所选单月统计结构（行业/能源直接/能源间接）
    const ym = `${filters.year}-${String(selectedMonth.value).padStart(2, '0')}`
    const r = await screenPeriodApi({ regionId: filters.regionId, startMonth: ym, endMonth: ym })
    if (seq === mainSeq && r) {
      const d = r.data
      if (structType.value === 'industry') renderStructure(d.industries || [])
      if (structType.value === 'energy') renderStructure(d.direct || [])
      if (structType.value === 'indirect') renderStructure(d.indirect || [])
    }
    return
  }
  const params = { regionId: filters.regionId, year: filters.year }
  if (structType.value === 'industry') {
    res = await structureApi(params)
  }
  if (structType.value === 'energy') {
    res = await energyStructureApi(params)
  }
  if (structType.value === 'indirect') {
    res = await indirectApi(params)
  }
  if (seq === mainSeq && res) {
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
    monthRows.value = res.data
    renderMonthly(res.data)
  }
}

async function loadRanking(seq) {
  // 排行跟随行业/能源筛选：参数统一带上（后端无筛选时按直接排放口径）
  const filterParams = { year: filters.year, industryId: filters.industryId, energyId: filters.energyId }
  let res
  if (granularity.value === 'month') {
    // 月粒度：按月聚合排行（省级/地级与年粒度口径一致）
    const monthParams = { month: selectedMonth.value, ...filterParams }
    if (rankType.value === 'province') {
      res = await monthRankingApi({ ...monthParams, scope: 'province' })
    } else if (isCitySelected.value) {
      const city = regions.value.find(x => x.id === filters.regionId)
      res = await monthRankingApi({ ...monthParams, scope: 'city', provinceId: city?.parentId })
    } else if (filters.regionId === 1) {
      res = await monthRankingApi({ ...monthParams, scope: 'city' })
    } else {
      res = await monthRankingApi({ ...monthParams, scope: 'city', provinceId: filters.regionId })
    }
  } else if (isCitySelected.value) {
    // 选到具体地级行政区：「全国」= 全国地级排行，「所在省级行政区内」= 该市所属省的地级排行
    if (rankType.value === 'province') {
      res = await regionRankingApi(filterParams)
    } else {
      const city = regions.value.find(x => x.id === filters.regionId)
      res = await cityRankingApi({ provinceId: city?.parentId, ...filterParams })
    }
  } else if (rankType.value === 'province') {
    // 省级行政区排行：全国口径（选省时同一份数据，前端高亮该省）
    res = await provinceRankingApi(filterParams)
  } else if (filters.regionId === 1) {
    // 全国口径的地级行政区排行
    res = await regionRankingApi(filterParams)
  } else {
    // 选省时：此省级行政区内各地级行政区排行
    res = await cityRankingApi({ provinceId: filters.regionId, ...filterParams })
  }
  if (seq === mainSeq) {
    rankRows.value = res.data
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
  detailQuery.pageNum = 1
  loading.value = true
  // 月粒度下年度趋势卡隐藏（容器不存在），不加载不渲染，避免 echarts.init(null) 抛错
  const tasks = granularity.value === 'year'
    ? [loadTrend(seq), loadStructure(seq), loadMonthly(seq), loadRanking(seq), loadDetail(seq)]
    : [loadStructure(seq), loadMonthly(seq), loadRanking(seq), loadDetail(seq)]
  Promise.all(tasks)
    .catch(() => {}) // 单个请求失败不悬挂 loading，错误由拦截器提示
    .finally(() => { if (seq === mainSeq) loading.value = false })
}

function onIndustryChange() {
  const seq = ++mainSeq
  detailQuery.pageNum = 1
  loading.value = true
  const tasks = granularity.value === 'year'
    ? [loadTrend(seq), loadMonthly(seq), loadRanking(seq), loadDetail(seq)]
    : [loadMonthly(seq), loadRanking(seq), loadDetail(seq)]
  Promise.all(tasks)
    .catch(() => {})
    .finally(() => { if (seq === mainSeq) loading.value = false })
}

function onStructTypeChange() {
  // 状态未就绪（年份未知）时不发请求：等首载完成后按当前所选类型渲染，
  // 避免 year 缺失触发 400 报错提示
  if (filters.year == null) return
  const seq = ++mainSeq
  loading.value = true
  Promise.all([loadStructure(seq)])
    .finally(() => { if (seq === mainSeq) loading.value = false })
}

function onRankTypeChange() {
  if (filters.year == null) return
  const seq = ++mainSeq
  loading.value = true
  Promise.all([loadRanking(seq)])
    .finally(() => { if (seq === mainSeq) loading.value = false })
}

function onRankMetricChange() {
  // 口径切换只影响排序与单位，用缓存数据本地重渲染，不重新请求
  renderRanking(rankRows.value)
}

function onDetailPageChange() {
  const seq = ++mainSeq
  loading.value = true
  Promise.all([loadDetail(seq)])
    .finally(() => { if (seq === mainSeq) loading.value = false })
}

function onEnergyChange() {
  // 能源筛选联动：年度趋势 + 月度趋势 + 排行 + 明细表
  const seq = ++mainSeq
  detailQuery.pageNum = 1
  loading.value = true
  const tasks = granularity.value === 'year'
    ? [loadTrend(seq), loadMonthly(seq), loadRanking(seq), loadDetail(seq)]
    : [loadMonthly(seq), loadRanking(seq), loadDetail(seq)]
  Promise.all(tasks)
    .catch(() => {})
    .finally(() => { if (seq === mainSeq) loading.value = false })
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
  monthChart?.resize()
  rankChart?.resize()
}

// keep-alive 恢复页面时按当前尺寸重绘图表：数据若在页面离屏（deactivated）期间
// 返回并渲染，图表容器尺寸为 0，必须激活后重绘，否则切回来是空白图表
onActivated(handleResize)

onMounted(async () => {
  // 进入页面立即转圈：首屏的字典/状态查询同样耗时，不能空窗等待
  loading.value = true
  const [regionRes, industryRes, energyRes, statusRes] = await Promise.all([
    regionListApi(), industryListApi(), energyListApi(), dataStatusApi()
  ])
  regions.value = regionRes.data.filter(r => r.level <= 2)
  industries.value = industryRes.data
  energies.value = energyRes.data

  const { minYear, maxYear: mx, maxFullYear: mfy, maxMonth } = statusRes.data
  maxYear.value = mx
  maxFullYear.value = mfy
  maxMonthOfYear.value = maxMonth
  // 年份可选至最新年（含尚未过完的当年，趋势图会提示数据不全）
  const endYear = mx
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
    onMainChange()   // 内部会重新置位并在全部请求完成后关闭转圈
  } else {
    loading.value = false
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
  flex-direction: column;
  gap: 10px;
  padding: 12px 20px;
}
.filter-line {
  display: flex;
  align-items: center;
  gap: 18px;
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
.struct-switch-row {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 8px;
}
.tech-panel-title {
  flex-wrap: wrap;
  row-gap: 4px;
}
/* 标题行：左侧标题 + 右上角总量/人均切换（切换组不换行，标题过长时自行折行） */
.chart-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.chart-title-row .tech-panel-title {
  flex: 1;
  min-width: 0;
}
.chart-title-row :deep(.el-radio-group) {
  flex-shrink: 0;
}
/* 行2 两卡等高：列撑满、面板纵向布局、图表区吸收剩余高度 */
.row2-col {
  display: flex;
}
.row2-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}
/* 排行图容器固定 300px（图形不被拉伸，超出内部滚动）；
   月度图弹性吸收剩余高度，使两卡等高 */
.row2-panel .rank-scroll {
  height: 300px;
  flex: none;
}
.row2-panel .month-chart-box {
  flex: 1;
  height: auto;
  min-height: 300px;
}
.row2-panel .struct-switch-row {
  flex-shrink: 0;
}
/* 数据不完整提示（未过完的年度/未生成的月份） */
.chart-tip {
  font-size: 12px;
  color: #b8860b;
  margin-bottom: 6px;
}
/* 标题后的简短括号标注 */
.chart-tip-inline {
  font-size: 12px;
  color: #b8860b;
  font-weight: normal;
}
/* 明细筛选行：区间/等级/排序 */
.detail-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.filter-caption {
  font-size: 12px;
  color: #51606e;
  margin-left: 4px;
}
.range-sep {
  color: #98a4b3;
}
/* 纯文字排序条：无边框无背景，选中蓝色加粗并带方向箭头 */
.sort-bar {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
}
.sort-right {
  margin-left: auto; /* 排序条靠右 */
}
.sort-label {
  color: #606266;
  margin-right: 4px;
}
.sort-option {
  color: #606266;
  cursor: pointer;
  padding: 0 6px;
  user-select: none;
}
.sort-option:hover {
  color: #409eff;
}
.sort-option.active {
  color: #409eff;
  font-weight: 600;
}
.sort-option.disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}
.sort-arrow {
  color: #409eff;
  cursor: pointer;
  font-weight: 600;
  padding: 0 2px;
  user-select: none;
}
.chart-box {
  height: 300px;
}
/* 城市排行：卡片高度固定，图表按行数撑高后容器内滚动 */
.rank-scroll {
  height: 300px;
  overflow-y: auto;
}
/* 月度排放图表补高，抵消排行卡顶部的标签切换行，使两卡高度对齐 */
.month-chart-box {
  height: 332px;
}
.rank-chart {
  height: auto;
}
/* 趋势卡片：高度跟随同行右侧饼图卡片（el-row 拉伸等高），图表撑满剩余空间 */
.row-fill {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.row-fill .chart-box {
  flex: 1;
  height: auto;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
