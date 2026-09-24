<template>
  <div class="agent-page">
    <el-row :gutter="16">
      <!-- 最左：历史会话 -->
      <el-col :xs="24" :md="4">
        <div class="tech-panel sessions-panel">
          <div class="tech-panel-title">历史会话</div>
          <div v-for="s in sessions" :key="s.id" class="session-item" :class="{ active: s.id === currentSessionId }" @click="loadSession(s)">
            <div class="session-main">
              <div class="session-title" :title="s.title">{{ s.title }}</div>
              <div class="session-time">{{ s.updateTime }}</div>
            </div>
            <el-icon class="session-delete" title="删除会话" @click.stop="handleDeleteSession(s)">
              <Delete />
            </el-icon>
          </div>
          <el-empty v-if="!sessions.length" description="暂无会话" :image-size="50" />
        </div>
      </el-col>

      <!-- 中间：对话 -->
      <el-col :xs="24" :md="panelCollapsed ? 19 : 11">
        <div class="tech-panel chat-panel">
          <div class="chat-header">
            <span class="tech-panel-title">💬 对话式分析</span>
            <el-button size="small" @click="newChat">新会话</el-button>
          </div>

          <div ref="msgBox" class="msg-box">
            <el-empty v-if="!messages.length" description="用自然语言提问，例如：江苏明年会超标吗？" :image-size="80" />
            <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
              <div class="msg-col">
                <div v-if="m.role === 'assistant' && m.steps && m.steps.length" class="msg-link-row">
                  <el-button link size="small" type="primary" @click="viewSteps(i)">
                    ⚙ 查看执行链路
                  </el-button>
                </div>
                <div class="msg-bubble" :class="m.role">
                  <template v-if="m.role === 'assistant'">
                    <!-- Markdown 渲染（流式时逐块实时解析） -->
                    <div class="md-body" v-html="renderMd(m.content)"></div>
                    <span v-if="m.streaming" class="cursor">▌</span>
                  </template>
                  <template v-else>{{ m.content }}</template>
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <el-input
              v-model="question"
              type="textarea"
              :rows="2"
              placeholder="输入问题，按 Enter 发送（Shift+Enter 换行）"
              :disabled="streaming"
              @keydown.enter.exact.prevent="send"
            />
            <el-button type="primary" :loading="streaming" @click="send">发送</el-button>
          </div>
        </div>
      </el-col>

      <!-- 右侧：执行链路（整栏可向右收起） -->
      <el-col :xs="24" :md="panelCollapsed ? 1 : 9">
        <div class="tech-panel steps-panel" :class="{ collapsed: panelCollapsed }">
          <div class="steps-header">
            <template v-if="!panelCollapsed">
              <span class="tech-panel-title" style="margin-bottom: 0">⚙ 执行链路</span>
              <el-icon class="panel-toggle" title="向右收起" @click="togglePanel"><DArrowRight /></el-icon>
            </template>
            <el-icon v-else class="panel-toggle" title="展开执行链路" @click="togglePanel"><DArrowLeft /></el-icon>
          </div>
          <template v-if="!panelCollapsed">
            <div v-if="currentSteps.length" class="steps-question">
              关联问题：<span class="steps-question-text">{{ currentQuestion }}</span>
            </div>
            <el-empty v-if="!currentSteps.length" description="点击回答气泡上的「查看执行链路」" :image-size="80" />
            <div v-for="(s, i) in currentSteps" :key="i" class="step-item" :class="{ expanded: s.expanded }">
              <div class="step-head" @click="toggleStep(s)">
                <span class="step-index">{{ i + 1 }}</span>
                <span class="step-icon" :class="s.status">
                  {{ stepIcon(s) }}
                </span>
                <span class="step-name">{{ stepMeta(s).label }}</span>
                <span v-if="s.type !== 'plan' && s.type !== 'conclusion'" class="step-tool">{{ s.name }}</span>
                <span v-if="s.durationMs" class="step-cost">{{ s.durationMs }}ms</span>
                <el-icon class="step-toggle"><ArrowDown /></el-icon>
              </div>
              <div v-show="s.expanded" class="step-body">
                <!-- 分析规划：LLM 输出的分析路径 -->
                <div v-if="s.type === 'plan'" class="md-body" v-html="renderMd(s.content)"></div>
                <!-- 综合结论 -->
                <div v-else-if="s.type === 'conclusion'" class="step-summary">{{ s.summary }}</div>
                <!-- 工具执行 -->
                <template v-else>
                  <div class="step-desc">{{ stepMeta(s).desc }}</div>
                  <div class="step-args">{{ formatArgs(s.args) }}</div>
                  <div v-if="s.summary" class="step-summary">{{ s.summary }}</div>
                  <div v-if="s.chart" class="step-chart">
                    <div ref="chartRefs" class="chart-inner"></div>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import { agentSessionListApi, agentSessionDetailApi, agentDeleteSessionApi } from '@/api/agent'

/** Markdown 渲染助手回复 */
function renderMd(content) {
  return content ? marked.parse(content) : ''
}

const messages = ref([])
const sessions = ref([])
const question = ref('')
const streaming = ref(false)
const currentSessionId = ref(null)
const msgBox = ref()
/** 右侧执行链路展示的问答序号（默认最新一个有链路的回答） */
const selectedMsgIndex = ref(-1)

// ===== 新会话 =====
function newChat() {
  currentSessionId.value = null
  messages.value = []
  selectedMsgIndex.value = -1
  disposeCharts()
}

// ===== 右侧链路：默认最新一个带链路的回答 =====
const currentSteps = computed(() => {
  if (selectedMsgIndex.value >= 0 && messages.value[selectedMsgIndex.value]) {
    return messages.value[selectedMsgIndex.value].steps || []
  }
  // 默认取最后一条带 steps 的 assistant 消息
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'assistant' && m.steps && m.steps.length) {
      return m.steps
    }
  }
  return []
})
const currentQuestion = computed(() => {
  if (selectedMsgIndex.value >= 0) {
    for (let i = selectedMsgIndex.value - 1; i >= 0; i--) {
      if (messages.value[i].role === 'user') return messages.value[i].content
    }
    return ''
  }
  // 默认：最新一个带执行链路的回答对应的用户问题（与 currentSteps 的 fallback 对称）
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'assistant' && m.steps && m.steps.length) {
      for (let j = i - 1; j >= 0; j--) {
        if (messages.value[j].role === 'user') return messages.value[j].content
      }
      break
    }
  }
  return ''
})

function viewSteps(i) {
  selectedMsgIndex.value = i
  nextTick(() => rebuildCharts())
}

/** 重建全部图表：v-if 销毁 DOM 后旧实例已失效，需重新 init 绑定新节点 */
function rebuildCharts() {
  disposeCharts()
  currentSteps.value.forEach(step => {
    if (!step.chart) return
    step.chartRendered = false
    if (step.expanded && renderStepChart(step)) {
      step.chartRendered = true
    }
  })
}

// ===== 工具 → 分析步骤语义映射（链路以"分析问题的每一步"呈现） =====
const TOOL_META = {
  query_kpi: { label: '了解现状', desc: '查询核心指标，掌握当前排放水平' },
  query_trend: { label: '回顾趋势', desc: '查询历史年度序列，观察演变规律' },
  query_structure: { label: '剖析结构', desc: '查询行业排放构成' },
  run_calc: { label: '核算数据', desc: '重新执行碳核算，校准数据基础' },
  predict_emission: { label: '预测未来', desc: 'LSTM 外推未来趋势，判断达峰' },
  detect_anomaly: { label: '筛查异常', desc: '孤立森林检测异常排放点' },
  simulate_policy: { label: '推演政策', desc: '情景仿真，测算政策减排效果' },
  generate_report: { label: '生成报告', desc: '一键生成 AIGC 监测报告' },
  check_threshold: { label: '对照判断', desc: '与阈值对比，判断是否超标' },
  query_energy_structure: { label: '剖析能源结构', desc: '查询各能源品种排放构成与占比' },
  query_region_ranking: { label: '区域排行对比', desc: '查询各市排放排行' },
  query_monthly_trend: { label: '分析月度规律', desc: '查询各月排放，观察季节特征' },
  query_industry_trend: { label: '追踪行业趋势', desc: '查询某行业年度排放演变' },
  query_alerts: { label: '查看预警概况', desc: '查询待确认预警与异常统计' }
}

function stepMeta(step) {
  if (step.type === 'plan') return { label: '分析问题', desc: '理解用户意图并规划分析路径' }
  if (step.type === 'conclusion') return { label: '综合结论', desc: '汇总分析结果，给出判断与建议' }
  return TOOL_META[step.name] || { label: step.name || '分析步骤', desc: '' }
}

function stepIcon(step) {
  if (step.type === 'plan') return '💭'
  if (step.type === 'conclusion') return '🏁'
  return step.status === 'running' ? '⏳' : step.status === 'error' ? '❌' : '✅'
}

// ===== 执行链路整栏收起/展开 =====
const panelCollapsed = ref(false)

function togglePanel() {
  panelCollapsed.value = !panelCollapsed.value
  if (!panelCollapsed.value) {
    // 面板收起期间 DOM 被 v-if 销毁，展开后需重建图表实例
    nextTick(() => rebuildCharts())
  }
}

// ===== 步骤展开/收起 =====
function toggleStep(step) {
  step.expanded = !step.expanded
  if (step.expanded && step.chart && !step.chartRendered) {
    nextTick(() => {
      if (renderStepChart(step)) step.chartRendered = true
    })
  }
}

// ===== 发送与 SSE 消费（按 SSE 规范分块解析 event:/data:） =====
async function send() {
  const q = question.value.trim()
  if (!q || streaming.value) return
  question.value = ''
  messages.value.push({ role: 'user', content: q })
  messages.value.push({ role: 'assistant', content: '', streaming: true, steps: [] })
  // 关键：取回 Vue 响应式代理引用——闭包内直接改原始对象不会触发视图刷新
  const liveMsg = messages.value[messages.value.length - 1]
  selectedMsgIndex.value = -1
  streaming.value = true
  // 发送后立即滚动到底部（用户消息与空回答气泡可见）
  scrollBottom()
  try {
    const token = localStorage.getItem('satoken')
    const resp = await fetch('/api/agent/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', satoken: token },
      body: JSON.stringify({ sessionId: currentSessionId.value, question: q })
    })
    if (!resp.ok) {
      const err = await resp.json().catch(() => null)
      throw new Error(err?.msg || '请求失败')
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // SSE 事件以空行分隔
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop()
      for (const block of blocks) {
        let eventName = 'message'
        let dataStr = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataStr += line.slice(5).trim()
          }
        }
        if (!dataStr) continue
        try {
          const parsed = JSON.parse(dataStr)
          // 兼容两种来源：data 内的 event 字段 或 SSE event 行
          handleEvent(parsed.event || eventName, parsed, liveMsg)
        } catch (e) { /* 忽略解析失败 */ }
      }
    }
    // 处理流结束后残留在 buffer 的最后一个事件块
    if (buffer.trim()) {
      try {
        const block = buffer.trim()
        let eventName = 'message'
        let dataStr = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim()
          else if (line.startsWith('data:')) dataStr += line.slice(5).trim()
        }
        if (dataStr) {
          const parsed = JSON.parse(dataStr)
          handleEvent(parsed.event || eventName, parsed, liveMsg)
        }
      } catch (e) { /* 忽略 */ }
    }
  } catch (e) {
    liveMsg.content += '（分析失败：' + e.message + '）'
    ElMessage.error(e.message)
  } finally {
    liveMsg.streaming = false
    streaming.value = false
    scrollBottom()
    loadSessions()
  }
}

function handleEvent(eventName, data, assistantMsg) {
  switch (eventName) {
    case 'step': {
      // 分析规划 / 综合结论（非工具步骤）
      if (data.type === 'plan') {
        assistantMsg.steps.push({
          type: 'plan', label: '分析问题', content: data.content,
          status: 'done', expanded: true, chartRendered: true
        })
      } else if (data.type === 'conclusion') {
        assistantMsg.steps.push({
          type: 'conclusion', label: '综合结论', summary: data.summary,
          status: 'done', expanded: false, chartRendered: true
        })
      }
      break
    }
    case 'tool_call': {
      assistantMsg.steps.push({
        type: 'tool', name: data.name, args: data.args,
        status: 'running', summary: '', durationMs: null,
        chart: null, expanded: true, chartRendered: false
      })
      break
    }
    case 'tool_result': {
      const step = assistantMsg.steps[assistantMsg.steps.length - 1]
      if (step) {
        const failed = data.summary && String(data.summary).includes('失败')
        Object.assign(step, data, { status: failed ? 'error' : 'done' })
        if (step.chart && step.expanded) {
          nextTick(() => {
            // 渲染成功才标记；容器不可用（如面板收起）时保持 false，展开时补渲染
            if (renderStepChart(step)) step.chartRendered = true
          })
        }
      }
      break
    }
    case 'answer':
      assistantMsg.content += data.content
      scrollBottom()
      break
    case 'error':
      ElMessage.error(data.message || '分析失败')
      break
    case 'done':
      if (data.sessionId && data.sessionId !== -1) currentSessionId.value = data.sessionId
      break
  }
}

// ===== 执行链路图表渲染 =====
const chartInstances = []

function renderStepChart(step) {
  const el = findChartEl(step)
  if (!el) return false
  const existing = echarts.getInstanceByDom(el)
  if (existing) {
    existing.resize()
    return true
  }
  const chart = echarts.init(el)
  chartInstances.push(chart)
  const { type, title, x, series } = step.chart
  let option = {}
  if (type === 'pie') {
    option = {
      title: { text: title, left: 'center', top: 2, textStyle: { fontSize: 12, color: '#51606e' } },
      tooltip: { trigger: 'item', formatter: '{b}：{c} 亿吨（{d}%）' },
      legend: { bottom: 0, type: 'scroll', icon: 'circle', textStyle: { color: '#51606e', fontSize: 10 } },
      color: ['#0ea5e9', '#06b6d4', '#10b981', '#8b5cf6', '#f59e0b', '#f87171', '#94a3b8'],
      series: [{
        // 饼图居中偏上：顶部让位标题，底部让位单行滚动图例
        type: 'pie', radius: ['32%', '50%'], center: ['50%', '52%'],
        label: { fontSize: 10, color: '#51606e', formatter: '{b}\n{d}%' },
        data: x.map((name, i) => ({ name, value: series[0].data[i] }))
      }]
    }
  } else {
    option = {
      title: { text: title, left: 'center', textStyle: { fontSize: 12, color: '#51606e' } },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 16, top: 34, bottom: 26 },
      xAxis: { type: 'category', data: x, axisLabel: { color: '#51606e', fontSize: 10 } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { color: '#51606e', fontSize: 10 } },
      series: series.map(s => ({
        name: s.name, type: type === 'bar' ? 'bar' : 'line', smooth: type === 'line',
        barMaxWidth: 14, data: s.data,
        itemStyle: type === 'bar'
          ? { borderRadius: [4, 4, 0, 0], color: '#0ea5e9' }
          : { color: '#0ea5e9' },
        lineStyle: type === 'line' ? { width: 2, color: '#0ea5e9' } : undefined
      }))
    }
  }
  chart.setOption(option)
  chart.resize()
  return true
}

function findChartEl(step) {
  const chartSteps = currentSteps.value.filter(s => s.chart)
  const idx = chartSteps.indexOf(step)
  const refs = document.querySelectorAll('.step-chart .chart-inner')
  return idx >= 0 && refs[idx] ? refs[idx] : null
}

function disposeCharts() {
  chartInstances.forEach(c => c.dispose())
  chartInstances.length = 0
}

// ===== 会话管理 =====
async function loadSessions() {
  const res = await agentSessionListApi()
  sessions.value = res.data
}

async function handleDeleteSession(s) {
  await ElMessageBox.confirm(`删除会话「${s.title}」？`, '提示', { type: 'warning' })
  await agentDeleteSessionApi(s.id)
  ElMessage.success('已删除')
  if (currentSessionId.value === s.id) {
    newChat()
  }
  await loadSessions()
}

async function loadSession(s) {
  const res = await agentSessionDetailApi(s.id)
  currentSessionId.value = s.id
  disposeCharts()
  const saved = res.data.messagesJson ? JSON.parse(res.data.messagesJson) : []
  messages.value = saved.map(m => ({
    role: m.role,
    content: m.content,
    steps: (m.steps || []).map(step => ({ ...step, expanded: false, chartRendered: false }))
  }))
  selectedMsgIndex.value = -1
  scrollBottom()
}

function formatArgs(args) {
  if (!args || !Object.keys(args).length) return '无参数'
  return Object.entries(args).map(([k, v]) => `${k}=${v}`).join('，')
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

onMounted(() => loadSessions())
onBeforeUnmount(() => disposeCharts())
</script>

<style scoped>
.agent-page {
  height: calc(100vh - 92px);
  display: flex;
  flex-direction: column;
}
.agent-page :deep(.el-row),
.agent-page :deep(.el-col) {
  height: 100%;
}

/* 左栏：历史会话 */
.sessions-panel {
  height: 100%;
  overflow-y: auto;
}
.session-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.session-main {
  flex: 1;
  min-width: 0;
}
.session-delete {
  color: #c4d0dd;
  font-size: 14px;
  flex-shrink: 0;
  visibility: hidden;
}
.session-item:hover .session-delete {
  visibility: visible;
  color: #f56c6c;
}
.session-item:hover {
  background: #f0f7fd;
}
.session-item.active {
  background: #e7f7fd;
}
.session-title {
  font-size: 13px;
  color: #1f2d3d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-time {
  margin-top: 3px;
  font-size: 12px;
  color: #98a4b3;
}

/* 中栏：对话 */
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 380px;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.chat-header .tech-panel-title {
  margin-bottom: 0;
}
.msg-box {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-col {
  max-width: 88%;
}
.msg-link-row {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 4px;
}
.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-bubble.user {
  background: linear-gradient(135deg, #0ea5e9, #0b84bb);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-bubble.assistant {
  background: #f0f7fd;
  color: #1f2d3d;
  border-bottom-left-radius: 4px;
}

/* Markdown 渲染样式 */
.md-body :deep(p) {
  margin: 4px 0;
}
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  font-size: 15px;
  margin: 10px 0 6px;
  color: #0b84bb;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}
.md-body :deep(li) {
  margin: 2px 0;
}
.md-body :deep(strong) {
  color: #0b84bb;
}
.md-body :deep(hr) {
  border: none;
  border-top: 1px solid #d5e6f3;
  margin: 10px 0;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #cfe3f0;
  padding: 4px 10px;
  font-size: 13px;
}
.cursor {
  animation: blink 0.9s infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.chat-input {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  align-items: flex-end;
}

/* 右栏：执行链路 */
.steps-panel {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.steps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.panel-toggle {
  cursor: pointer;
  color: #51606e;
  font-size: 15px;
}
.panel-toggle:hover {
  color: #0ea5e9;
}
.steps-panel.collapsed {
  align-items: center;
  justify-content: flex-start;
  padding: 14px 0;
  overflow: hidden;
}
.steps-panel.collapsed .steps-header {
  margin-bottom: 0;
}
.steps-question {
  font-size: 12px;
  color: #98a4b3;
  margin-bottom: 8px;
  line-height: 1.6;
}
.steps-question-text {
  color: #51606e;
}
.step-item {
  border: 1px solid #e8eef5;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fafcfe;
}
.step-item.expanded {
  border-color: #b6e5f9;
}
.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
.step-index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0ea5e9, #10b981);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-icon.running {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.step-name {
  font-weight: 600;
  font-size: 13px;
  color: #0b84bb;
}
.step-tool {
  font-size: 11px;
  color: #98a4b3;
  background: #eef2f7;
  border-radius: 4px;
  padding: 1px 6px;
}
.step-desc {
  font-size: 12px;
  color: #51606e;
  margin-bottom: 4px;
}
.step-cost {
  margin-left: auto;
  font-size: 12px;
  color: #98a4b3;
}
.step-toggle {
  color: #98a4b3;
  font-size: 12px;
  transition: transform 0.2s;
}
.step-item.expanded .step-toggle {
  transform: rotate(180deg);
}
.step-body {
  margin-top: 8px;
}
.step-args {
  font-size: 12px;
  color: #7d93b0;
}
.step-summary {
  margin-top: 6px;
  font-size: 13px;
  color: #34455a;
  line-height: 1.6;
}
.step-chart {
  margin-top: 8px;
}
.chart-inner {
  height: 200px;
}
</style>
