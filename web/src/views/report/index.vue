<template>
  <div class="report-page">
    <!-- 生成面板 -->
    <div class="tech-panel gen-bar">
      <span class="gen-label">生成监测报告</span>
      <el-select v-model="genForm.regionId" size="small" style="width: 140px">
        <el-option v-for="r in regions" :key="r.id" :label="r.regionName" :value="r.id" />
      </el-select>
      <el-radio-group v-model="genForm.periodType" size="small">
        <el-radio-button :value="2">年报</el-radio-button>
        <el-radio-button :value="1">月报</el-radio-button>
      </el-radio-group>
      <el-select v-model="genForm.year" size="small" style="width: 100px">
        <el-option v-for="y in yearOptions" :key="y" :label="y + ' 年'" :value="y" />
      </el-select>
      <el-select v-if="genForm.periodType === 1" v-model="genForm.month" size="small" style="width: 90px">
        <el-option v-for="m in 12" :key="m" :label="m + ' 月'" :value="m" />
      </el-select>
      <el-button type="primary" size="small" :loading="generating" @click="handleGenerate">
        ✨ 一键生成
      </el-button>
      <el-tag v-if="currentReport" size="small" type="info" effect="plain" class="gen-status">
        {{ currentReport.status === 1 ? '生成成功' : '生成中' }}
      </el-tag>
    </div>

    <el-row :gutter="16">
      <!-- 历史列表 -->
      <el-col :xs="24" :md="6">
        <div class="tech-panel history-panel">
          <div class="tech-panel-title">报告历史</div>
          <div v-for="r in reports" :key="r.id" class="report-item" :class="{ active: r.id === currentId }" @click="loadReport(r)">
            <div class="report-item-title">{{ r.title }}</div>
            <div class="report-item-meta">{{ r.createTime }} · {{ r.periodType === 1 ? '月报' : '年报' }}</div>
          </div>
          <el-empty v-if="!reports.length" description="暂无报告" :image-size="60" />
        </div>
      </el-col>

      <!-- 报告正文 -->
      <el-col :xs="24" :md="18">
        <div class="tech-panel report-paper" v-loading="loading">
          <template v-if="currentReport">
            <div class="report-toolbar no-print">
              <el-button size="small" @click="handleRegenerate" :loading="generating">重新生成</el-button>
              <el-button size="small" type="danger" @click="handleDelete">删除</el-button>
              <el-button size="small" type="primary" @click="handlePrint">🖨 导出 PDF</el-button>
            </div>
            <div class="report-doc">
              <!-- Markdown 正文（AI 生成） -->
              <div class="markdown-body" v-html="renderedContent"></div>
              <!-- 平台图表附件 -->
              <div class="report-charts no-print">
                <div class="chart-panel">
                  <div class="chart-title">年度排放趋势</div>
                  <div ref="trendRef" class="chart-box"></div>
                </div>
                <div class="chart-panel">
                  <div class="chart-title">行业排放结构（{{ currentReport.reportYear }} 年）</div>
                  <div ref="pieRef" class="chart-box"></div>
                </div>
              </div>
              <p class="report-foot">本报告由绿碳智衡平台自动生成 · 数据口径：能源活动类排放核算</p>
            </div>
          </template>
          <el-empty v-else description="选择生成参数后点击「一键生成」" :image-size="100" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import { regionListApi } from '@/api/dict'
import { dataStatusApi } from '@/api/data'
import { trendApi, structureApi } from '@/api/analysis'
import { generateReportApi, reportListApi, reportDetailApi, regenerateReportApi, deleteReportApi } from '@/api/report'

const regions = ref([])
const yearOptions = ref([])
const reports = ref([])
const currentReport = ref(null)
const currentId = ref(null)
const loading = ref(false)
const generating = ref(false)

const genForm = reactive({ regionId: 1, periodType: 2, year: 2025, month: 1 })
const renderedContent = computed(() =>
  currentReport.value?.content ? marked.parse(currentReport.value.content) : ''
)

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generateReportApi({ ...genForm })
    ElMessage.success('报告生成成功')
    currentId.value = res.data.id
    await Promise.all([loadReports(), loadReport(res.data)])
  } finally {
    generating.value = false
  }
}

async function loadReports() {
  const res = await reportListApi()
  reports.value = res.data
}

async function loadReport(r) {
  loading.value = true
  try {
    const res = await reportDetailApi(r.id)
    currentReport.value = res.data
    currentId.value = res.data.id
    await nextTick()
    renderCharts(res.data)
  } finally {
    loading.value = false
  }
}

async function handleRegenerate() {
  if (!currentReport.value) return
  await ElMessageBox.confirm('重新生成将替换当前报告内容，是否继续？', '提示', { type: 'warning' })
  generating.value = true
  try {
    const res = await regenerateReportApi(currentReport.value.id)
    ElMessage.success('已重新生成')
    currentId.value = res.data.id
    await Promise.all([loadReports(), loadReport(res.data)])
  } finally {
    generating.value = false
  }
}

async function handleDelete() {
  if (!currentReport.value) return
  await ElMessageBox.confirm('删除该报告？', '提示', { type: 'warning' })
  await deleteReportApi(currentReport.value.id)
  currentReport.value = null
  currentId.value = null
  await loadReports()
}

function handlePrint() {
  const report = currentReport.value
  if (!report) return
  // ECharts canvas 转图片，随报告附图（新窗口无法共享原 canvas）
  const trendImg = trendChart?.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' }) || ''
  const pieImg = pieChart?.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' }) || ''
  const printWin = window.open('', '_blank', 'width=1000,height=720')
  printWin.document.write(`<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>${report.title}</title>
<style>
  body { font-family: 'PingFang SC','Microsoft YaHei',sans-serif; color: #1f2d3d; max-width: 760px; margin: 40px auto; }
  h1 { text-align: center; font-size: 22px; margin: 8px 0 24px; }
  h2 { font-size: 16px; margin: 22px 0 10px; padding-left: 10px; border-left: 4px solid #0ea5e9; }
  p, li { font-size: 14px; line-height: 1.9; color: #34455a; }
  blockquote { margin: 14px 0; padding: 8px 14px; background: #f7fafc; border-left: 3px solid #c4d0dd; color: #7d93b0; font-size: 13px; }
  strong { color: #0b84bb; }
  .chart-figure { margin: 28px 0 0; page-break-inside: avoid; }
  .chart-title { font-size: 14px; font-weight: 600; color: #51606e; margin: 0 0 8px; }
  .chart-figure img { width: 100%; border: 1px solid #e8eef5; border-radius: 8px; }
  .foot { text-align: center; color: #98a4b3; font-size: 12px; margin-top: 32px; border-top: 1px solid #eee; padding-top: 12px; }
  @page { margin: 20mm; }
</style></head>
<body>
${renderedContent.value}
${trendImg ? `<div class="chart-figure"><div class="chart-title">附图一：年度排放趋势</div><img src="${trendImg}"></div>` : ''}
${pieImg ? `<div class="chart-figure"><div class="chart-title">附图二：行业排放结构（${report.reportYear} 年）</div><img src="${pieImg}"></div>` : ''}
<div class="foot">本报告由绿碳智衡平台自动生成 · 数据口径：能源活动类排放核算</div>
<script>window.onload = function () { setTimeout(function () { window.print(); }, 400); }<\/script>
</body></html>`)
  printWin.document.close()
}

// ===== 报告附图表 =====
const trendRef = ref()
const pieRef = ref()
let trendChart, pieChart

async function renderCharts(report) {
  const { minYear, maxYear } = (await dataStatusApi()).data
  const trendRes = await trendApi({ regionId: report.regionId, startYear: minYear, endYear: maxYear })
  const structureRes = await structureApi({ regionId: report.regionId, year: report.reportYear })

  trendChart = trendChart || echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: v => (v / 10000).toFixed(1) + ' 万吨' },
    grid: { left: 60, right: 20, top: 24, bottom: 32 },
    xAxis: {
      type: 'category', data: trendRes.data.map(r => r.year), boundaryGap: false,
      axisLine: { lineStyle: { color: '#c4d0dd' } }, axisLabel: { color: '#51606e' }
    },
    yAxis: {
      type: 'value', name: '万吨 CO₂',
      nameTextStyle: { color: '#51606e' }, axisLabel: { color: '#51606e', formatter: v => (v / 10000).toFixed(1) },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [{
      name: '排放总量', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      data: trendRes.data.map(r => r.emission),
      itemStyle: { color: '#0ea5e9' }, lineStyle: { width: 2.5, color: '#0ea5e9' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(14, 165, 233, 0.3)' },
          { offset: 1, color: 'rgba(14, 165, 233, 0.02)' }
        ])
      }
    }]
  })

  pieChart = pieChart || echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{d}%' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#51606e', fontSize: 11 } },
    color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b'],
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'],
      itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', color: '#51606e', fontSize: 11 },
      data: structureRes.data.map(r => ({ name: r.industryName, value: Number(r.emission) }))
    }]
  })
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  const [regionRes, statusRes] = await Promise.all([regionListApi(), dataStatusApi()])
  regions.value = regionRes.data.filter(r => r.level !== 3)
  const { minYear, maxYear } = statusRes.data
  if (minYear && maxYear) {
    yearOptions.value = Array.from({ length: maxYear - minYear + 1 }, (_, i) => minYear + i)
    genForm.year = maxYear
  }
  await loadReports()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.gen-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 20px;
}
.gen-label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}
.gen-status {
  margin-left: 4px;
}

.history-panel {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
.report-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.report-item:hover {
  background: #f0f7fd;
}
.report-item.active {
  background: #e7f7fd;
  border-color: #0ea5e9;
}
.report-item-title {
  font-size: 13px;
  color: #1f2d3d;
  font-weight: 500;
}
.report-item-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #98a4b3;
}

.report-paper {
  min-height: 400px;
}
.report-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 14px;
  border-bottom: 1px dashed #e8eef5;
  padding-bottom: 12px;
}
.report-doc {
  max-width: 780px;
  margin: 0 auto;
}
.report-foot {
  margin-top: 28px;
  padding-top: 12px;
  border-top: 1px solid #e8eef5;
  font-size: 12px;
  color: #98a4b3;
  text-align: center;
}

/* Markdown 渲染样式 */
.markdown-body :deep(h1) {
  font-size: 22px;
  text-align: center;
  margin: 8px 0 20px;
  color: #1f2d3d;
}
.markdown-body :deep(h2) {
  font-size: 16px;
  margin: 22px 0 10px;
  padding-left: 10px;
  border-left: 4px solid #0ea5e9;
  color: #1f2d3d;
}
.markdown-body :deep(p),
.markdown-body :deep(li) {
  font-size: 14px;
  line-height: 1.9;
  color: #34455a;
}
.markdown-body :deep(blockquote) {
  margin: 14px 0;
  padding: 8px 14px;
  background: #f7fafc;
  border-left: 3px solid #c4d0dd;
  color: #7d93b0;
  font-size: 13px;
}
.markdown-body :deep(strong) {
  color: #0b84bb;
}

.report-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 24px;
}
.chart-panel {
  border: 1px solid #e8eef5;
  border-radius: 10px;
  padding: 12px;
}
.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #51606e;
  margin-bottom: 8px;
}
.chart-box {
  height: 240px;
}

/* 打印样式：导出 PDF 时隐藏操作按钮 */
@media print {
  .no-print {
    display: none !important;
  }
  .report-paper {
    box-shadow: none;
    padding: 0;
  }
}
</style>
