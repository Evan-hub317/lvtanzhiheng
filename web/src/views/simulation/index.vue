<template>
  <div class="sim-page">
    <el-row :gutter="16">
      <!-- 左侧控制面板 -->
      <el-col :xs="24" :md="7">
        <div class="tech-panel control-panel">
          <div class="tech-panel-title">仿真控制台</div>

          <div class="ctrl-item">
            <span class="ctrl-label">区域</span>
            <el-select v-model="regionId" size="small" style="width: 150px" @change="onRegionChange">
              <el-option v-for="r in regions" :key="r.id" :label="r.regionName" :value="r.id" />
            </el-select>
          </div>

          <div class="ctrl-item">
            <span class="ctrl-label">情景预设</span>
            <el-select v-model="preset" size="small" style="width: 150px" @change="onPresetChange">
              <el-option v-for="p in presets" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </div>

          <div class="slider-item">
            <div class="slider-head">
              <span>煤炭占比</span>
              <b class="slider-val">{{ params.coalRatio.toFixed(1) }}%</b>
            </div>
            <el-slider v-model="params.coalRatio" :min="10" :max="80" :step="0.5" @change="onParamChange" />
          </div>

          <div class="slider-item">
            <div class="slider-head">
              <span>工业占 GDP 比重</span>
              <b class="slider-val">{{ params.industryRatio.toFixed(1) }}%</b>
            </div>
            <el-slider v-model="params.industryRatio" :min="10" :max="60" :step="0.5" @change="onParamChange" />
          </div>

          <div class="slider-item">
            <div class="slider-head">
              <span>单位能耗年均下降率</span>
              <b class="slider-val">{{ params.techEfficiency.toFixed(1) }}%</b>
            </div>
            <el-slider v-model="params.techEfficiency" :min="0" :max="8" :step="0.1" @change="onParamChange" />
          </div>

          <div class="slider-item">
            <div class="slider-head">
              <span>GDP 年均增速</span>
              <b class="slider-val">{{ params.gdpGrowth.toFixed(1) }}%</b>
            </div>
            <el-slider v-model="params.gdpGrowth" :min="2" :max="8" :step="0.5" @change="onParamChange" />
          </div>

          <div class="ctrl-actions">
            <el-button type="primary" :loading="simulating" @click="runSimulate">
              立即仿真
            </el-button>
            <el-button :loading="saving" @click="handleSave">保存情景</el-button>
          </div>

          <p class="ctrl-tip">
            调整滑杆后自动重新计算；绿色阴影带为蒙特卡洛 95% 置信区间。
            达峰年份 = 曲线最高点所在年份。
          </p>
        </div>

        <!-- 已保存情景 -->
        <div class="tech-panel scenario-panel">
          <div class="tech-panel-title">已保存情景（勾选叠加对比）</div>
          <el-checkbox-group v-model="checkedScenarios" @change="onCheckedChange">
            <div v-for="s in scenarios" :key="s.id" class="scenario-row">
              <el-checkbox :value="s.id">
                <span class="scenario-name">{{ s.scenarioName }}</span>
                <el-tag size="small" :type="s.peakYear ? 'success' : 'warning'" effect="light" round>
                  {{ s.peakYear ? s.peakYear + ' 达峰' : '未达峰' }}
                </el-tag>
              </el-checkbox>
            </div>
          </el-checkbox-group>
          <el-empty v-if="!scenarios.length" description="暂无保存的情景" :image-size="60" />
        </div>
      </el-col>

      <!-- 右侧图表 -->
      <el-col :xs="24" :md="17">
        <div class="tech-panel">
          <div class="tech-panel-title">
            {{ regionName }} · 碳排放预测与仿真推演
            <el-tag v-if="method" size="small" type="info" effect="plain">
              预测模型：{{ method === 'lstm' ? 'LSTM 神经网络' : '线性回归（降级）' }}
            </el-tag>
            <el-tag v-if="simPeakYear" size="small" type="success" effect="dark" round class="peak-tag">
              {{ simPeakYear }} 年达峰 · 峰值 {{ formatYi(simPeakValue) }}
            </el-tag>
            <el-tag v-else-if="simResult" size="small" type="warning" effect="dark" round class="peak-tag">
              仿真期内未达峰
            </el-tag>
          </div>
          <div
            ref="chartRef"
            v-loading="pageLoading"
            element-loading-text="正在训练预测模型，请稍候…"
            element-loading-background="rgba(255, 255, 255, 0.85)"
            class="sim-chart"
          ></div>
        </div>

        <!-- 情景对比表 -->
        <div class="tech-panel" style="margin-top: 16px">
          <div class="tech-panel-title">情景对比</div>
          <el-table :data="compareRows" size="small" stripe>
            <el-table-column prop="name" label="情景" min-width="130" />
            <el-table-column label="煤炭占比" width="100" align="right">
              <template #default="{ row }">{{ row.coalRatio }}%</template>
            </el-table-column>
            <el-table-column label="工业占比" width="100" align="right">
              <template #default="{ row }">{{ row.industryRatio }}%</template>
            </el-table-column>
            <el-table-column label="能效下降率" width="110" align="right">
              <template #default="{ row }">{{ row.techEfficiency }}%</template>
            </el-table-column>
            <el-table-column label="达峰年份" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.peakYear" size="small" type="success" effect="light">{{ row.peakYear }}</el-tag>
                <span v-else class="no-peak">未达峰</span>
              </template>
            </el-table-column>
            <el-table-column label="峰值（亿吨）" width="120" align="right">
              <template #default="{ row }">{{ row.peakValue ? (row.peakValue / 1e8).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="末期排放（亿吨）" width="140" align="right">
              <template #default="{ row }">{{ (row.endValue / 1e8).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { regionListApi } from '@/api/dict'
import { dataStatusApi } from '@/api/data'
import { predictApi, simulateApi, saveScenarioApi, scenarioListApi, scenarioDetailApi } from '@/api/sim'

// ===== 基础数据 =====
const regions = ref([])
const regionId = ref(1)
const regionName = computed(() => regions.value.find(r => r.id === regionId.value)?.regionName || '全省')

const presets = [
  { value: 1, label: '基准情景（现趋势延续）', coal: 58, industry: 42, tech: 1.5 },
  { value: 2, label: '低碳情景（能源转型）', coal: 48, industry: 36, tech: 2.5 },
  { value: 3, label: '强化低碳情景（深度减排）', coal: 38, industry: 30, tech: 3.5 },
  { value: 0, label: '自定义情景', coal: 50, industry: 38, tech: 2.0 }
]
const preset = ref(1)
const params = reactive({ coalRatio: 58, industryRatio: 42, techEfficiency: 1.5, gdpGrowth: 5.0 })

function onPresetChange() {
  const p = presets.find(x => x.value === preset.value)
  if (p) {
    params.coalRatio = p.coal
    params.industryRatio = p.industry
    params.techEfficiency = p.tech
  }
  runSimulate()
}

// ===== 预测与仿真结果 =====
const predictData = ref(null)
const simResult = ref(null)
const method = ref('')
const simPeakYear = computed(() => simResult.value?.peakYear ?? null)
const simPeakValue = computed(() => simResult.value?.peakValue ?? null)

const simulating = ref(false)
let simTimer = null

function onParamChange() {
  // 滑杆防抖 400ms 自动重算
  clearTimeout(simTimer)
  simTimer = setTimeout(runSimulate, 400)
}

async function runSimulate() {
  if (!simResult.value && !predictData.value) return
  simulating.value = true
  try {
    const res = await simulateApi({ regionId: regionId.value, ...params, years: 6 })
    simResult.value = res.data
    renderChart()
  } catch (e) {
    // 拦截器已提示
  } finally {
    simulating.value = false
  }
}

// ===== 情景管理 =====
const scenarios = ref([])
const checkedScenarios = ref([])
const scenarioCache = new Map()
const saving = ref(false)

async function loadScenarios() {
  const res = await scenarioListApi({ regionId: regionId.value })
  scenarios.value = res.data
  checkedScenarios.value = checkedScenarios.value.filter(id => scenarios.value.some(s => s.id === id))
}

async function handleSave() {
  saving.value = true
  try {
    await saveScenarioApi({ scenarioName: `情景-${Date.now().toString().slice(-6)}`, regionId: regionId.value, presetType: preset.value, ...params, years: 6 })
    ElMessage.success('情景已保存')
    await loadScenarios()
  } finally {
    saving.value = false
  }
}

async function onCheckedChange(ids) {
  // 新勾选的场景拉取轨迹
  for (const id of ids) {
    if (!scenarioCache.has(id)) {
      const res = await scenarioDetailApi(id)
      scenarioCache.set(id, res.data)
    }
  }
  renderChart()
}

const compareRows = computed(() => {
  const rows = []
  if (simResult.value) {
    const v = simResult.value.values
    rows.push({
      name: `当前参数（${presets.find(p => p.value === preset.value)?.label || '自定义'}）`,
      coalRatio: params.coalRatio.toFixed(1),
      industryRatio: params.industryRatio.toFixed(1),
      techEfficiency: params.techEfficiency.toFixed(1),
      peakYear: simResult.value.peakYear,
      peakValue: simResult.value.peakValue,
      endValue: v[v.length - 1]
    })
  }
  for (const s of scenarios.value) {
    rows.push({
      name: s.scenarioName,
      coalRatio: s.coalRatio,
      industryRatio: s.industryRatio,
      techEfficiency: s.techEfficiency,
      peakYear: s.peakYear,
      peakValue: s.peakEmission,
      endValue: s.values ? s.values[s.values.length - 1] : null
    })
  }
  return rows.filter(r => r.endValue != null)
})

// ===== 图表 =====
const chartRef = ref()
let chart = null

const SCENARIO_COLORS = ['#f59e0b', '#8b5cf6', '#ef4444', '#64748b', '#06b6d4']

function formatYi(v) {
  return v == null ? '-' : (v / 1e8).toFixed(2) + ' 亿吨'
}

function renderChart() {
  chart = chart || echarts.init(chartRef.value)
  const series = []
  const legend = []

  // 历史（深灰实线，标记已知数据）
  let lastHistY = null
  let lastHistV = null
  if (predictData.value) {
    const p = predictData.value
    lastHistY = p.historyYears[p.historyYears.length - 1]
    lastHistV = p.historyValues[p.historyValues.length - 1]
    series.push({
      name: '历史排放（已知）', type: 'line', smooth: true, symbol: 'circle', symbolSize: 7,
      data: p.historyYears.map((y, i) => [y, p.historyValues[i]]),
      itemStyle: { color: '#1f2d3d' }, lineStyle: { width: 3, color: '#1f2d3d' },
      z: 5
    })
    legend.push('历史排放（已知）')
  }

  // LSTM 预测（虚线 + 置信区间，拼接历史末点保持连续；拼接点标记 isBridge 由 tooltip formatter 过滤）
  if (predictData.value && predictData.value.years.length) {
    const p = predictData.value
    const bridge = { value: [lastHistY, lastHistV], isBridge: true, symbol: 'none' }
    const lower = [bridge, ...p.lower.map((v, i) => [p.years[i], v])]
    const upper = [...[bridge, ...p.upper.map((v, i) => [p.years[i], v])].reverse()]
    series.push({
      name: '预测区间', type: 'line', data: [...lower, ...upper],
      lineStyle: { opacity: 0 }, stack: 'confidence', silent: true, symbol: 'none',
      areaStyle: { color: 'rgba(16, 185, 129, 0.14)' }, z: 1
    })
    series.push({
      name: '基线预测（LSTM）', type: 'line', smooth: true, symbol: 'none',
      data: [bridge, ...p.years.map((y, i) => [y, p.values[i]])],
      lineStyle: { width: 2, type: 'dashed', color: '#10b981' }, itemStyle: { color: '#10b981' }, z: 4,
      markPoint: p.peakYear ? {
        symbol: 'pin', symbolSize: 46, itemStyle: { color: '#f59e0b' },
        label: { formatter: `${p.peakYear} 达峰\n${(p.peakValue / 1e8).toFixed(2)} 亿吨`, fontSize: 10, color: '#fff' },
        data: [{ coord: [p.peakYear, p.peakValue] }]
      } : undefined
    })
    legend.push('预测区间', '基线预测（LSTM）')
  }

  // 当前仿真轨迹（从最新历史年开始，拼接点同样标记 isBridge）
  if (simResult.value) {
    const s = simResult.value
    const bridge = s.baseYear != null && s.baseEmission != null
      ? { value: [s.baseYear, s.baseEmission], isBridge: true, symbol: 'none' }
      : null
    series.push({
      name: '当前仿真', type: 'line', smooth: true, symbol: 'circle', symbolSize: 7,
      data: bridge ? [bridge, ...s.years.map((y, i) => [y, s.values[i]])] : s.years.map((y, i) => [y, s.values[i]]),
      lineStyle: { width: 3.5, color: '#0ea5e9' }, itemStyle: { color: '#0ea5e9' }, z: 6,
      markPoint: s.peakYear ? {
        symbol: 'pin', symbolSize: 46, itemStyle: { color: '#ef4444' },
        label: { formatter: `${s.peakYear} 达峰\n${(s.peakValue / 1e8).toFixed(2)} 亿吨`, fontSize: 10, color: '#fff' },
        data: [{ coord: [s.peakYear, s.peakValue] }]
      } : undefined
    })
    legend.push('当前仿真')
  }

  // 已勾选情景叠加
  for (const id of checkedScenarios.value) {
    const s = scenarioCache.get(id)
    if (!s?.values?.length) continue
    series.push({
      name: s.scenarioName, type: 'line', smooth: true, symbol: 'none',
      data: s.years.map((y, i) => [y, s.values[i]]),
      lineStyle: { width: 2, type: 'dashed', color: SCENARIO_COLORS[(id - 1) % SCENARIO_COLORS.length] }
    })
    legend.push(s.scenarioName)
  }

  // X 轴范围 = 数据实际年份区间（历史起始年 ~ 仿真末年），避免自动扩展
  const allYears = []
  if (predictData.value) {
    allYears.push(...predictData.value.historyYears, ...predictData.value.years)
  }
  if (simResult.value) {
    allYears.push(simResult.value.baseYear, ...simResult.value.years)
  }
  const axisMin = allYears.length ? Math.min(...allYears) : 2021
  const axisMax = allYears.length ? Math.max(...allYears) : 2031

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      // 自定义渲染：过滤拼接点（isBridge）与置信区间阴影带，避免交界年份重复显示
      formatter: params => {
        if (!Array.isArray(params)) params = [params]
        const rows = params.filter(p => {
          if (p.seriesName === '预测区间') return false
          if (p.data && p.data.isBridge) return false
          return true
        })
        if (!rows.length) return ''
        const title = Array.isArray(rows[0].data) ? rows[0].data[0] : rows[0].axisValue
        let html = `<div style="font-weight:600;margin-bottom:4px">${title} 年</div>`
        for (const p of rows) {
          let val
          if (p.componentType === 'markPoint') {
            val = p.data?.coord?.[1]
          } else if (Array.isArray(p.data)) {
            val = p.data[1]
          } else if (p.data?.value) {
            val = p.data.value[1]
          }
          const name = p.seriesName + (p.componentType === 'markPoint' ? ' · 达峰' : '')
          html += `<div style="display:flex;align-items:center;margin:2px 0">` +
            `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px"></span>` +
            `<span>${name}</span>` +
            `<span style="margin-left:auto;font-weight:600;padding-left:16px">${val != null ? formatYi(val) : '-'}</span>` +
            `</div>`
        }
        return html
      }
    },
    legend: { top: 6, data: legend, textStyle: { color: '#51606e', fontSize: 12 } },
    grid: { left: 70, right: 30, top: 46, bottom: 40 },
    xAxis: {
      type: 'value',
      min: axisMin,
      max: axisMax,
      minInterval: 1,
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e', formatter: '{value} 年' },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value', name: '亿吨 CO₂',
      nameTextStyle: { color: '#51606e' },
      axisLabel: { color: '#51606e', formatter: v => (v / 1e8).toFixed(1) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series
  })
}

// ===== 初始化 =====
const pageLoading = ref(true)

async function init() {
  try {
    const [regionRes, statusRes] = await Promise.all([regionListApi(), dataStatusApi()])
    regions.value = regionRes.data.filter(r => r.level !== 3)
    const { minYear, maxYear } = statusRes.data
    if (!minYear || !maxYear) {
      ElMessage.warning('暂无数据，请先在工作台生成模拟数据并执行核算')
      return
    }
    await loadPredict()
    await loadScenarios()
  } finally {
    pageLoading.value = false
  }
}

async function loadPredict() {
  const res = await predictApi({ regionId: regionId.value })
  predictData.value = res.data
  method.value = res.data.method
  renderChart()
  // 预测就绪后按默认参数跑一次仿真
  await runSimulate()
}

async function onRegionChange() {
  checkedScenarios.value = []
  await loadPredict()
  await loadScenarios()
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  init()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  clearTimeout(simTimer)
  chart?.dispose()
})
</script>

<style scoped>
.sim-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.control-panel,
.scenario-panel {
  margin-bottom: 16px;
}

.ctrl-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.ctrl-label {
  font-size: 13px;
  color: #51606e;
  width: 60px;
}

.slider-item {
  margin-bottom: 6px;
}
.slider-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #51606e;
}
.slider-val {
  color: #0ea5e9;
}

.ctrl-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.ctrl-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: #98a4b3;
  line-height: 1.7;
}

.scenario-row {
  padding: 6px 0;
}
.scenario-name {
  margin-right: 8px;
  font-size: 13px;
}

.sim-chart {
  height: 460px;
}

.peak-tag {
  margin-left: 12px;
}
.no-peak {
  color: #e6a23c;
  font-size: 12px;
}
</style>
