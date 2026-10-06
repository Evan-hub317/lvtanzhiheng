<template>
  <div class="chat-page">
    <!-- 左侧：会话 + 知识库 -->
    <div class="chat-side">
      <el-tabs v-model="sideTab" class="side-tabs">
        <el-tab-pane label="会话" name="sessions">
          <div class="side-block">
            <el-button type="primary" class="new-chat-btn" @click="newChat">
              <el-icon><Plus /></el-icon>&nbsp;新建对话
            </el-button>
            <div v-if="sessions.length" class="session-list">
              <div
                v-for="s in sessions"
                :key="s.id"
                class="session-item"
                :class="{ active: s.id === sessionId }"
                @click="openSession(s)"
              >
                <span class="session-title">{{ s.title }}</span>
                <el-icon class="session-del" @click.stop="handleDeleteSession(s)">
                  <Delete />
                </el-icon>
              </div>
            </div>
            <el-empty v-else description="暂无会话" :image-size="60" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="知识库" name="kb">
          <div class="side-block">
            <el-upload
              :show-file-list="false"
              :http-request="doUpload"
              accept=".txt,.md,.pdf,.docx"
            >
              <el-button type="primary" class="new-chat-btn" :loading="uploading">
                <el-icon><Upload /></el-icon>&nbsp;上传文档
              </el-button>
            </el-upload>
            <p class="kb-tip">支持 txt / md / pdf / docx；上传后首次问答时自动建立向量索引</p>
            <div v-if="docs.length" class="doc-list">
              <div v-for="d in docs" :key="d.id" class="doc-item">
                <div class="doc-info">
                  <span class="doc-name" :title="d.docName">{{ d.docName }}</span>
                  <span class="doc-meta">{{ d.chunkCount }} 块 · {{ d.status === 1 ? '已启用' : '已停用' }}</span>
                </div>
                <div class="doc-actions">
                  <el-button link size="small" type="primary" @click="viewDoc(d)">查看</el-button>
                  <el-button link size="small" :type="d.status === 1 ? 'warning' : 'success'" @click="toggleDoc(d)">
                    {{ d.status === 1 ? '停用' : '启用' }}
                  </el-button>
                  <el-button link size="small" type="danger" @click="handleDeleteDoc(d)">删除</el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="知识库为空，请上传政策文档" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 右侧：聊天窗口 -->
    <div class="chat-main">
      <div class="chat-header">
        <div class="chat-header-title">
          <div>
            <div class="bot-name">AI 知识库</div>
            <div class="bot-desc">基于政策知识库的检索增强问答（RAG）</div>
          </div>
        </div>
        <el-tag v-if="docs.length" size="small" type="success" effect="plain">
          知识库 {{ docs.length }} 篇文档
        </el-tag>
      </div>

      <div ref="msgBox" class="chat-messages">
        <div v-if="!messages.length && !streaming" class="chat-welcome">
          <div class="welcome-bot">🌿</div>
          <h3>您好，我是 AI 知识库</h3>
          <p>我可以基于已上传的政策文件回答碳排放相关问题，并标注引用来源</p>
          <div class="quick-questions">
            <el-tag v-for="q in quickQuestions" :key="q" class="quick-q" @click="inputText = q">
              {{ q }}
            </el-tag>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="msg-bubble">
            <div class="msg-content">
              <div v-if="m.role === 'assistant'" class="md-body" v-html="renderMd(m.content)"></div>
              <template v-else>{{ m.content }}</template>
              <span v-if="streaming && i === messages.length - 1" class="cursor">▌</span>
            </div>
            <div v-if="m.sources && m.sources.length" class="msg-sources">
              <el-tag
                v-for="(s, si) in m.sources"
                :key="si"
                size="small"
                type="info"
                effect="plain"
                class="source-tag"
                title="点击浏览原文并定位到该分块"
                @click="jumpToSource(s)"
              >
                📄 {{ s.doc_name }} 分块{{ s.chunk_index }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- DOCX 在线预览弹窗（docx-preview 渲染） -->
      <el-dialog v-model="docPreviewVisible" :title="previewDocName" width="80%" top="4vh" destroy-on-close @closed="previewSeq++">
        <div v-if="downloadProgress >= 0 && downloadProgress < 100" class="dl-bar">
          <el-progress :percentage="downloadProgress" :stroke-width="12" striped striped-flow />
          <span class="dl-text">正在加载文档</span>
        </div>
        <div ref="docxContainer" class="docx-container" v-loading="docPreviewLoading" element-loading-text="正在渲染文档…"></div>
      </el-dialog>

      <!-- TXT/MD 原文预览弹窗（定位分块） -->
      <el-dialog v-model="textPreviewVisible" :title="textPreviewName" width="80%" top="4vh" destroy-on-close @closed="previewSeq++">
        <div v-if="downloadProgress >= 0 && downloadProgress < 100" class="dl-bar">
          <el-progress :percentage="downloadProgress" :stroke-width="12" striped striped-flow />
          <span class="dl-text">正在加载文档</span>
        </div>
        <div ref="textContainer" class="text-container">
          <pre v-html="textPreviewHtml"></pre>
        </div>
      </el-dialog>

      <!-- PDF 应用内预览弹窗（pdf.js 渲染，不依赖浏览器原生预览） -->
      <el-dialog v-model="pdfPreviewVisible" :title="pdfPreviewName" width="80%" top="4vh" destroy-on-close @closed="previewSeq++">
        <div v-if="downloadProgress >= 0 && downloadProgress < 100" class="dl-bar">
          <el-progress :percentage="downloadProgress" :stroke-width="12" striped striped-flow />
          <span class="dl-text">正在加载文档</span>
        </div>
        <div v-if="pdfRenderTotal > 0 && pdfRenderDone < pdfRenderTotal" class="dl-bar">
          <el-progress :percentage="Math.round((pdfRenderDone / pdfRenderTotal) * 100)" :stroke-width="12" striped striped-flow />
          <span class="dl-text">正在渲染 PDF（{{ pdfRenderDone }}/{{ pdfRenderTotal }} 页）</span>
        </div>
        <div ref="pdfContainer" class="pdf-container" v-loading="pdfPreviewLoading" element-loading-text="正在渲染 PDF…"></div>
      </el-dialog>

      <div class="chat-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入问题，如：碳达峰是什么意思？"
          @keydown.enter.exact.prevent="sendQuestion"
        />
        <div class="input-bar">
          <span class="input-hint">Enter 发送 · 提问将检索知识库并标注引用</span>
          <el-button type="primary" :loading="streaming" :disabled="!inputText.trim()" @click="sendQuestion">
            {{ streaming ? '回答中…' : '发送' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sessionListApi, messageListApi, saveMessageApi, deleteSessionApi } from '@/api/chat'
import { kbListApi, kbContentApi, kbUploadApi, kbUpdateStatusApi, kbDeleteApi } from '@/api/kb'
import { renderAsync } from 'docx-preview'
import { marked } from 'marked'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

const sideTab = ref('sessions')
const sessions = ref([])
const docs = ref([])
const sessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const uploading = ref(false)
const msgBox = ref()

const quickQuestions = ['碳达峰和碳中和有什么区别？', '碳排放核算用什么方法？', '我国碳达峰目标年份是？']

// 部署时可在 public/config.js 中直接填写后端地址（不做 Nginx 反代）；默认走相对路径（Vite 代理）
const API_BASE = (window.APP_CONFIG && window.APP_CONFIG.API_BASE) || '/api'
const ALGO_BASE = (window.APP_CONFIG && window.APP_CONFIG.ALGO_BASE) || '/algo'
function renderMd(content) {
  return content ? marked.parse(content, { breaks: true, gfm: true }) : ''
}

// ===== 会话 =====
async function loadSessions() {
  const res = await sessionListApi()
  sessions.value = res.data
}

async function openSession(s) {
  sessionId.value = s.id
  const res = await messageListApi(s.id)
  messages.value = res.data.map(m => ({
    role: m.role,
    content: m.content,
    sources: m.sources ? JSON.parse(m.sources) : []
  }))
  scrollToBottom()
}

function newChat() {
  sessionId.value = null
  messages.value = []
}

async function handleDeleteSession(s) {
  await ElMessageBox.confirm(`删除会话「${s.title}」？`, '提示', { type: 'warning' })
  await deleteSessionApi(s.id)
  if (sessionId.value === s.id) newChat()
  await loadSessions()
  ElMessage.success('已删除')
}

// ===== 问答（SSE 流式直连 Python 算法服务） =====
async function sendQuestion() {
  const question = inputText.value.trim()
  if (!question || streaming.value) return
  inputText.value = ''
  messages.value.push({ role: 'user', content: question })
  const assistantMsg = { role: 'assistant', content: '', sources: [] }
  messages.value.push(assistantMsg)
  streaming.value = true
  scrollToBottom()

  // 上下文：最近 8 轮
  const history = messages.value
    .slice(0, -2)
    .filter(m => !m.sources)
    .slice(-8)
    .map(m => ({ role: m.role, content: m.content }))
  history.push({ role: 'user', content: question })

  try {
    const resp = await fetch(`${ALGO_BASE}/api/alg/rag-chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, history })
    })
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let sources = []
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (!payload || payload === '[DONE]') continue
        try {
          const data = JSON.parse(payload)
          if (data.sources) {
            sources = data.sources
            assistantMsg.sources = sources
          }
          if (data.content) {
            assistantMsg.content += data.content
          }
        } catch (e) {
          // 忽略解析失败片段
        }
      }
      scrollToBottom()
    }
    // 落库留痕
    const saveRes = await saveMessageApi({ sessionId: sessionId.value, question, answer: assistantMsg.content, sources })
    sessionId.value = saveRes.data
    await loadSessions()
  } catch (e) {
    assistantMsg.content = assistantMsg.content || '（AI 服务不可用，请确认 Python 算法服务已启动）'
  } finally {
    streaming.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

// ===== 知识库 =====
async function loadDocs() {
  const res = await kbListApi()
  docs.value = res.data
}

async function doUpload({ file }) {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await kbUploadApi(formData)
    ElMessage.success(`「${res.data.docName}」已入库，共 ${res.data.chunkCount} 个分块`)
    await loadDocs()
  } catch (e) {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

async function toggleDoc(d) {
  const target = d.status === 1 ? 0 : 1
  await kbUpdateStatusApi(d.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  await loadDocs()
}

async function handleDeleteDoc(d) {
  await ElMessageBox.confirm(`删除文档「${d.docName}」及其全部分块？`, '提示', { type: 'warning' })
  await kbDeleteApi(d.id)
  ElMessage.success('已删除')
  await loadDocs()
}

// ===== 查看文档原件 =====
// PDF/TXT/MD：新窗口内嵌预览（浏览器原生渲染）
// DOCX：docx-preview 库渲染为 HTML 在弹窗内预览
const docPreviewVisible = ref(false)
const docPreviewLoading = ref(false)
const previewDocName = ref('')
const docxContainer = ref()

// ===== 下载/渲染进度（方案 C：真实下载进度 + PDF 分页渲染进度） =====
const downloadProgress = ref(-1)   // -1=不显示；0-100=下载中
const pdfRenderDone = ref(0)
const pdfRenderTotal = ref(0)

// 预览请求序号：防止竞态——关掉/换开新文档后，旧文档的迟到渲染不得覆盖当前弹窗
let previewSeq = 0

/** 先打开对应预览弹窗（显示下载进度条），再取文件渲染 */
function openPreviewShell(docType, docName) {
  if (docType === 'docx') {
    previewDocName.value = docName
    docPreviewVisible.value = true
  } else if (docType === 'pdf') {
    pdfPreviewName.value = docName
    pdfPreviewVisible.value = true
  } else {
    textPreviewName.value = docName
    textPreviewVisible.value = true
  }
}

async function viewDoc(d) {
  const seq = ++previewSeq
  openPreviewShell(d.docType, d.docName)
  downloadProgress.value = 0
  try {
    const blob = await fetchDocBlob(d.id, p => { if (seq === previewSeq) downloadProgress.value = p })
    if (seq !== previewSeq) return   // 期间已关窗/换开新文档：放弃本次渲染
    downloadProgress.value = 100
    if (d.docType === 'docx') {
      await openDocxPreview(blob, d.docName, null)
    } else if (d.docType === 'pdf') {
      await openPdfPreview(blob, d.docName, null)
    } else {
      await openTextPreview(blob, d.docName, null)
    }
  } catch (e) {
    if (seq === previewSeq) {
      downloadProgress.value = -1
      ElMessage.error(e.message || '文件加载失败')
    }
  }
}

// ===== 引用分块跳转：点击来源 → 浏览原件并定位到分块开头 =====
// 流式读取并汇报真实下载进度（onProgress：0-100）；Content-Length 缺失时进度条不显示
async function fetchDocBlob(docId, onProgress) {
  const token = localStorage.getItem('satoken')
  const resp = await fetch(`${API_BASE}/kb/${docId}/file`, { headers: { satoken: token } })
  if (!resp.ok) {
    const err = await resp.json().catch(() => null)
    throw new Error(err?.msg || '文件加载失败')
  }
  const total = Number(resp.headers.get('content-length')) || 0
  const reader = resp.body.getReader()
  const chunks = []
  let received = 0
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    received += value.length
    if (onProgress && total > 0) {
      onProgress(Math.min(100, Math.round((received / total) * 100)))
    }
  }
  return new Blob(chunks)
}

// 文档分块内容缓存：docId -> { docType, chunks }
const chunkCache = new Map()

async function getChunkAnchor(s) {
  // 新数据带 doc_id；历史会话的旧来源按文档名回查
  const docId = s.doc_id ?? docs.value.find(d => d.docName === s.doc_name)?.id
  if (!docId) return null
  let cached = chunkCache.get(docId)
  if (!cached) {
    const res = await kbContentApi(docId)
    cached = { docType: res.data.docType, chunks: res.data.chunks }
    chunkCache.set(docId, cached)
  }
  const chunk = cached.chunks.find(c => c.chunkIndex === s.chunk_index)
  return { docId, docType: cached.docType, content: chunk?.content }
}

async function jumpToSource(s) {
  let anchor
  try {
    anchor = await getChunkAnchor(s)
  } catch (e) {
    ElMessage.error('分块信息加载失败')
    return
  }
  if (!anchor) {
    ElMessage.warning('未找到对应文档，可能已被删除')
    return
  }
  const seq = ++previewSeq
  openPreviewShell(anchor.docType, s.doc_name)
  downloadProgress.value = 0
  try {
    const blob = await fetchDocBlob(anchor.docId, p => { if (seq === previewSeq) downloadProgress.value = p })
    if (seq !== previewSeq) return   // 期间已关窗/换开新文档：放弃本次渲染
    downloadProgress.value = 100
    if (anchor.docType === 'docx') {
      await openDocxPreview(blob, s.doc_name, anchor.content)
    } else if (anchor.docType === 'pdf') {
      await openPdfPreview(blob, s.doc_name, anchor.content)
    } else {
      await openTextPreview(blob, s.doc_name, anchor.content)
    }
  } catch (e) {
    if (seq === previewSeq) {
      downloadProgress.value = -1
      ElMessage.error(e.message || '文件加载失败')
    }
  }
}

async function openDocxPreview(blob, docName, anchorText) {
  previewDocName.value = docName
  docPreviewVisible.value = true
  docPreviewLoading.value = true
  try {
    // el-dialog 内容为异步挂载，必须等待 nextTick 后容器才存在
    await nextTick()
    docxContainer.value.innerHTML = ''
    await renderAsync(blob, docxContainer.value, null, {
      inWrapper: true, ignoreWidth: false, ignoreHeight: false,
      className: 'docx-render', breakPages: true
    })
    if (anchorText) {
      await nextTick()
      locateInDocx(docxContainer.value, anchorText)
    }
  } catch (e) {
    console.error('docx 渲染失败', e)
    ElMessage.error(`文档渲染失败：${e?.message || '格式暂不支持'}`)
    docPreviewVisible.value = false
  } finally {
    docPreviewLoading.value = false
  }
}

// 在 docx-preview 渲染出的 DOM 中查找分块文本并滚动定位
// 后端分块文本做过空白折叠（\s+ → 单个空格，KBServiceImpl），因此与渲染原文永远不完全一致，
// 必须按同样规则归一化后匹配，再通过“归一化字符 → 原始字符”映射回文本节点做逐字高亮
const BLOCK_TAGS = new Set(['P', 'DIV', 'SECTION', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'LI', 'TR', 'BR'])

// 归一化串字符 i 在原始串中的偏移映射（空白折叠映射到折叠段首字符）
function buildCharMap(raw, norm) {
  const map = []
  let r = 0
  for (let i = 0; i < norm.length; i++) {
    while (r < raw.length && /\s/.test(raw[r])) r++
    map.push(r)
    r++
  }
  return map
}

// 滚动容器使目标元素出现在顶部（用可视矩形差值，不受弹窗过渡 transform 影响）
function scrollToInContainer(container, el, margin = 40) {
  if (!container || !el) return
  const delta = el.getBoundingClientRect().top - container.getBoundingClientRect().top - margin
  container.scrollTop += delta
}

function locateInDocx(root, anchor) {
  const anchorText = (anchor || '').trim()
  const normalize = t => t.replace(/\s+/g, ' ')
  const strip = t => t.replace(/\s+/g, '')

  // 一次遍历构建三种缓冲（norm/raw/strip）+ 字符映射，三者共享文本节点记录
  const parts = [] // { node, el, normStart, normLen, map, rawStart, rawLen, stripStart, stripLen }
  let bufNorm = ''
  let bufRaw = ''
  let bufStrip = ''

  function walk(el) {
    for (const child of el.childNodes) {
      if (child.nodeType === 3) {
        const raw = child.nodeValue
        if (!raw.trim()) continue
        const t = normalize(raw)
        parts.push({
          node: child, el: child.parentElement,
          normStart: bufNorm.length, normLen: t.length,
          map: buildCharMap(raw, t),
          rawStart: bufRaw.length, rawLen: raw.length,
          stripStart: bufStrip.length, stripLen: strip(t).length
        })
        bufNorm += t
        bufRaw += raw
        bufStrip += strip(t)
      } else if (child.nodeType === 1) {
        if (BLOCK_TAGS.has(child.tagName)) {
          if (bufNorm && !bufNorm.endsWith(' ')) bufNorm += ' '
          bufRaw += '\n'
          walk(child)
          if (bufNorm && !bufNorm.endsWith(' ')) bufNorm += ' '
          bufRaw += '\n'
        } else {
          walk(child)
        }
      }
    }
  }
  walk(root)

  // ---- 通道1：归一化匹配（与后端分块文本同构），逐字高亮 ----
  const keys = [normalize(anchorText), normalize(anchorText).slice(0, Math.max(60, Math.floor(anchorText.length / 2)))]
  for (const key of keys) {
    if (!key) continue
    let idx = bufNorm.indexOf(key)
    if (idx < 0) continue
    // 起点可能落在块边界补的空格上，推进到下一个真实字符
    while (idx < bufNorm.length && !parts.some(p => idx >= p.normStart && idx < p.normStart + p.normLen)) idx++
    let end = idx + key.length
    // 终点同理：把边界空格并入高亮区间
    while (end > idx && !parts.some(p => end - 1 >= p.normStart && end - 1 < p.normStart + p.normLen)) end++
    const pFirst = parts.find(p => idx >= p.normStart && idx < p.normStart + p.normLen)
    const pLast = parts.find(p => end - 1 >= p.normStart && end - 1 < p.normStart + p.normLen)
    if (!pFirst || !pLast) continue
    const rawStart = pFirst.rawStart + pFirst.map[idx - pFirst.normStart]
    const rawEnd = pLast.rawStart + pLast.map[end - 1 - pLast.normStart] + 1
    if (wrapTextRange(root, parts, rawStart, rawEnd)) return true
  }

  // ---- 通道2：原始文本精确匹配（docx 无空白差异时的直接命中） ----
  const rawCandidates = [anchorText, anchorText.slice(0, Math.max(60, Math.floor(anchorText.length / 2)))]
  for (const key of rawCandidates) {
    if (!key) continue
    const idx = bufRaw.indexOf(key)
    if (idx < 0) continue
    if (wrapTextRange(root, parts, idx, idx + key.length)) return true
  }

  // ---- 通道3：去全部空白匹配（整段高亮兜底） ----
  const stripAnchor = strip(anchorText)
  if (stripAnchor) {
    let idx = bufStrip.indexOf(stripAnchor)
    if (idx < 0) {
      const shorter = stripAnchor.slice(0, Math.max(12, Math.floor(stripAnchor.length / 2)))
      idx = bufStrip.indexOf(shorter)
    }
    if (idx >= 0) {
      const hit = parts.find(p => idx >= p.stripStart && idx < p.stripStart + p.stripLen)
      const el = hit?.el
      if (el) {
        el.classList.add('chunk-anchor-el')
        scrollToInContainer(el.closest('.docx-container'), el, 24)
        return true
      }
    }
  }
  ElMessage.info('已打开文档，但未定位到该分块（分块内容与原件排版可能不一致）')
  return false
}

// 把 [rangeStart, rangeEnd) 对应的原始文本片段用 span 逐字高亮（精确到分块真实位置）
function wrapTextRange(root, parts, rangeStart, rangeEnd) {
  const first = parts.find(p => rangeStart >= p.rawStart && rangeStart < p.rawStart + p.rawLen)
  const last = parts.find(p => rangeEnd > p.rawStart && rangeEnd <= p.rawStart + p.rawLen)
  if (!first || !last) return false
  const startOffset = rangeStart - first.rawStart
  const endOffset = rangeEnd - last.rawStart
  if (first.node === last.node) {
    last.node.splitText(endOffset)
    const mid = first.node.splitText(startOffset)
    const span = document.createElement('span')
    span.className = 'chunk-anchor-el'
    mid.parentNode.replaceChild(span, mid)
    span.appendChild(mid)
    scrollToInContainer(span.closest('.docx-container'), span, 24)
    return true
  }
  // 跨多个文本节点：切开两端，收集中间所有文本节点逐一包 span
  last.node.splitText(endOffset)
  const mid = first.node.splitText(startOffset)
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const toWrap = []
  let n
  let inRange = false
  while ((n = walker.nextNode())) {
    if (n === mid) inRange = true
    if (inRange) toWrap.push(n)
    if (n === last.node) break
  }
  for (const t of toWrap) {
    if (!t.nodeValue) continue
    const span = document.createElement('span')
    span.className = 'chunk-anchor-el'
    t.parentNode.replaceChild(span, t)
    span.appendChild(t)
  }
  const firstSpan = toWrap[0]?.parentElement
  if (firstSpan) {
    scrollToInContainer(firstSpan.closest('.docx-container'), firstSpan, 24)
  }
  return true
}

// TXT/MD 原文预览：全文转义渲染，分块文本高亮并滚动到该处
const textPreviewVisible = ref(false)
const textPreviewName = ref('')
const textPreviewHtml = ref('')
const textContainer = ref()

function escapeHtml(str) {
  return str.replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ))
}

// PDF 应用内预览：pdf.js 逐页渲染到 canvas；分块定位用文本层坐标换算页面内滚动位置
const PDF_SCALE = 1.5
const pdfPreviewVisible = ref(false)
const pdfPreviewLoading = ref(false)
const pdfPreviewName = ref('')
const pdfContainer = ref()

// 在 PDF 文本层中查找分块：逐页拼接去空白文本，返回 { page, segs }，
// segs 为逐行高亮段（分块起点行→终点行的行级矩形，覆盖分块全片区域）
async function findPdfAnchor(pdf, anchorText) {
  const strip = t => t.replace(/\s+/g, '')
  const target = strip(anchorText)
  // 优先全文匹配（高亮覆盖整个分块），跨页/排版差异时退化为前缀匹配
  for (const len of [target.length, 200, 80, 40, 20]) {
    const key = target.slice(0, len)
    if (!key) break
    for (let p = 1; p <= pdf.numPages; p++) {
      const page = await pdf.getPage(p)
      const tc = await page.getTextContent()
      const items = []
      let text = ''
      const ranges = [] // 去空白坐标下的 { start, len }
      for (const it of tc.items) {
        const s = strip(it.str || '')
        if (!s) continue
        items.push(it)
        ranges.push({ start: text.length, len: s.length })
        text += s
      }
      const idx = text.indexOf(key)
      if (idx < 0) continue
      const endIdx = idx + key.length - 1
      const iFirst = ranges.findIndex(r => idx >= r.start && idx < r.start + r.len)
      const iLast = ranges.findIndex(r => endIdx >= r.start && endIdx < r.start + r.len)
      if (iFirst < 0 || iLast < 0) continue
      const vp = page.getViewport({ scale: 1 })
      // 逐项生成行级高亮段：起点行从匹配字符处开始，终点行到匹配字符处结束，中间行整行覆盖
      const segs = []
      for (let i = iFirst; i <= iLast; i++) {
        const it = items[i]
        const r = ranges[i]
        let ratio0 = 0
        let ratio1 = 1
        if (i === iFirst) ratio0 = (idx - r.start) / r.len
        if (i === iLast) ratio1 = (endIdx - r.start + 1) / r.len
        const glyphH = it.height || 12
        segs.push({
          x: (it.transform[4] + (it.width || 0) * ratio0) * PDF_SCALE,
          top: (vp.height - it.transform[5] - glyphH * 0.8) * PDF_SCALE,
          width: Math.max((it.width || 0) * (ratio1 - ratio0) * PDF_SCALE, 6),
          height: glyphH * PDF_SCALE
        })
      }
      // 同一行的多个文本项合并为一个框（避免重叠的零碎高亮）
      const merged = []
      for (const s of segs) {
        const m = merged.find(m => Math.abs(m.top - s.top) < 2)
        if (m) {
          const end = Math.max(m.x + m.width, s.x + s.width)
          m.x = Math.min(m.x, s.x)
          m.width = end - m.x
        } else {
          merged.push({ ...s })
        }
      }
      return { page: p, segs: merged }
    }
  }
  return null
}

async function openPdfPreview(blob, docName, anchorText) {
  pdfPreviewName.value = docName
  pdfPreviewVisible.value = true
  pdfPreviewLoading.value = true
  try {
    await nextTick()
    pdfContainer.value.innerHTML = ''
    const pdf = await pdfjsLib.getDocument({ data: await blob.arrayBuffer() }).promise
    // 先定位分块（纯文本层操作，渲染前完成）
    const anchor = anchorText ? await findPdfAnchor(pdf, anchorText) : null
    // 逐页渲染（先建 canvas 保持 DOM 顺序，再并行渲染；每页完成更新渲染进度）
    pdfRenderTotal.value = pdf.numPages
    pdfRenderDone.value = 0
    const renderTasks = []
    for (let p = 1; p <= pdf.numPages; p++) {
      const page = await pdf.getPage(p)
      const viewport = page.getViewport({ scale: PDF_SCALE })
      const canvas = document.createElement('canvas')
      canvas.className = 'pdf-page'
      canvas.dataset.page = p
      canvas.width = Math.floor(viewport.width)
      canvas.height = Math.floor(viewport.height)
      pdfContainer.value.appendChild(canvas)
      renderTasks.push(page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
        .then(() => { pdfRenderDone.value += 1 }))
    }
    await Promise.all(renderTasks)
    if (anchor) {
      const pageCanvas = pdfContainer.value.querySelector(`canvas[data-page="${anchor.page}"]`)
      if (pageCanvas) {
        // 页 canvas 外包一层定位容器，叠放逐行高亮框
        const wrap = document.createElement('div')
        wrap.className = 'pdf-page-wrap'
        pageCanvas.parentNode.replaceChild(wrap, pageCanvas)
        wrap.appendChild(pageCanvas)
        for (const seg of anchor.segs) {
          const mark = document.createElement('div')
          mark.className = 'chunk-anchor-el'
          wrap.appendChild(mark)
          // canvas 可能因 max-width:100% 被等比缩小显示，高亮框坐标须按显示比例修正
          const ratio = (pageCanvas.clientWidth / pageCanvas.width) || 1
          mark.style.left = seg.x * ratio + 'px'
          mark.style.top = seg.top * ratio + 'px'
          mark.style.width = Math.max(seg.width * ratio, 8) + 'px'
          mark.style.height = Math.max(seg.height * ratio, 10) + 'px'
        }
        await nextTick()
        const first = wrap.querySelector('.chunk-anchor-el')
        scrollToInContainer(pdfContainer.value, first, 60)
      } else {
        ElMessage.info('已打开文档，但未定位到该分块')
      }
    }
  } catch (e) {
    console.error('pdf 渲染失败', e)
    ElMessage.error(`PDF 渲染失败：${e?.message || '格式暂不支持'}`)
    pdfPreviewVisible.value = false
  } finally {
    pdfPreviewLoading.value = false
  }
}

async function openTextPreview(blob, docName, anchorText) {
  const text = await blob.text()
  textPreviewName.value = docName
  textPreviewVisible.value = true
  let html = escapeHtml(text)
  if (anchorText) {
    const escTarget = escapeHtml(anchorText.trim())
    let idx = html.indexOf(escTarget)
    let target = escTarget
    if (idx < 0) {
      target = escapeHtml(anchorText.trim().slice(0, Math.max(12, Math.floor(anchorText.length / 2))))
      idx = html.indexOf(target)
    }
    if (idx >= 0) {
      html = html.slice(0, idx) + '<mark>' + target + '</mark>' + html.slice(idx + target.length)
    }
  }
  textPreviewHtml.value = html
  await nextTick()
  const mark = textContainer.value?.querySelector('mark')
  if (mark) {
    mark.scrollIntoView({ block: 'start' })
  } else if (anchorText) {
    ElMessage.info('已打开文档，但未定位到该分块')
  }
}

onMounted(() => {
  loadSessions()
  loadDocs()
})

onBeforeUnmount(() => {
  // 页面离开时流式请求随组件销毁自然中断
})
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 92px);
}

/* ===== 左侧 ===== */
.chat-side {
  width: 260px;
  flex-shrink: 0;
  /* 显式高度 + 隐藏溢出：保证文档/会话列表在容器内部滚动，不撑破布局 */
  height: calc(100vh - 92px);
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(16, 42, 67, 0.06);
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.side-tabs {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.side-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  flex-shrink: 0;
}
.side-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.side-block {
  padding: 6px 0 24px;
}
.new-chat-btn {
  width: 100%;
  margin-bottom: 12px;
}
.kb-tip {
  font-size: 12px;
  color: #98a4b3;
  margin: 4px 0 12px;
  line-height: 1.6;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #1f2d3d;
}
.session-item:hover {
  background: #f0f7fc;
}
.session-item.active {
  background: linear-gradient(90deg, rgba(14, 165, 233, 0.12), rgba(16, 185, 129, 0.06));
  color: #0ea5e9;
  font-weight: 600;
}
.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-del {
  color: #c0c8d4;
  font-size: 14px;
  flex-shrink: 0;
}
.session-del:hover {
  color: #f56c6c;
}
.doc-item {
  padding: 8px 4px;
  border-bottom: 1px dashed #eef2f7;
}
.doc-name {
  display: block;
  font-size: 13px;
  color: #1f2d3d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-meta {
  font-size: 11px;
  color: #98a4b3;
}
.doc-actions {
  margin-top: 4px;
}

/* ===== 聊天主区 ===== */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(16, 42, 67, 0.06);
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #eef2f7;
}
.chat-header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bot-icon {
  font-size: 26px;
}
.bot-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}
.bot-desc {
  font-size: 12px;
  color: #98a4b3;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f7fafc;
}
.chat-welcome {
  text-align: center;
  padding-top: 60px;
  color: #51606e;
}
.welcome-bot {
  font-size: 48px;
}
.chat-welcome h3 {
  margin: 12px 0 6px;
}
.chat-welcome p {
  font-size: 13px;
  color: #98a4b3;
  margin: 0 0 18px;
}
.quick-questions {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}
.quick-q {
  cursor: pointer;
  font-size: 12px;
}
.quick-q:hover {
  background: #e7f7fd;
  border-color: #0ea5e9;
  color: #0ea5e9;
}

.msg-row {
  display: flex;
  margin-bottom: 16px;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-row.user .msg-bubble {
  background: linear-gradient(120deg, #0ea5e9, #10b981);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-row.assistant .msg-bubble {
  background: #fff;
  color: #1f2d3d;
  border: 1px solid #e8eef5;
  border-bottom-left-radius: 4px;
}
.cursor {
  animation: blink 1s step-start infinite;
  color: #0ea5e9;
}

/* Markdown 渲染样式（与 AI 分析助手一致） */
/* 关键：覆盖气泡继承的 pre-wrap——否则 marked 输出 HTML 源码中的换行会被原样渲染成可见空行 */
.md-body {
  white-space: normal;
}
/* 段落上下边距 ≈ 一行行高（14px × 1.7 ≈ 24px）：
   \n\n 的段落分隔在视觉上等于一个空行；单个 \n 的 <br> 仍是普通换行 */
.md-body :deep(p) {
  margin: 8px 0;
}
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  font-size: 15px;
  margin: 18px 0 8px;
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
@keyframes blink {
  50% {
    opacity: 0;
  }
}
.msg-sources {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.source-tag {
  font-size: 11px;
  cursor: pointer;
}
.source-tag:hover {
  background: #e7f7fd;
  border-color: #0ea5e9;
  color: #0ea5e9;
}

.chat-input {
  padding: 14px 20px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}
.input-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.input-hint {
  font-size: 12px;
  color: #98a4b3;
}

/* ===== DOCX 预览 ===== */
.docx-container {
  min-height: 300px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  background: #f0f2f5;
  padding: 12px;
}
/* docx-preview 传入 className:'docx-render'，包裹层/页面真实类名为 *-render-* */
.docx-container :deep(.docx-render-wrapper) {
  background: transparent;
  max-width: 100%;
  padding: 8px;
  box-sizing: border-box;
}
/* 页面边距来自文档自身的 sectPr/pgMar（内联样式）；没有设置边距的文档为 0 导致内容贴边。
   强制统一最小页边距（!important 覆盖内联样式），保证所有文档内容与纸边都有间距 */
.docx-container :deep(.docx-render-wrapper > section.docx-render) {
  margin-bottom: 20px;
  padding: 32px 44px !important;
  box-sizing: border-box;
  max-width: 100%;
}
/* 定位到的分块位置高亮（docx：逐字 span） */
.docx-container :deep(.chunk-anchor-el) {
  background: #fff3c4;
  border-radius: 3px;
  scroll-margin-top: 24px;
}

/* ===== TXT/MD 原文预览 ===== */
.text-container {
  min-height: 300px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  padding: 16px 20px;
}
.text-container :deep(pre) {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #1f2d3d;
  margin: 0;
}
.text-container :deep(mark) {
  background: #fff3c4;
  padding: 0 2px;
  border-radius: 3px;
  scroll-margin-top: 12px;
}

/* ===== 下载/渲染进度条 ===== */
.dl-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.dl-bar .el-progress {
  flex: 1;
}
.dl-text {
  font-size: 12px;
  color: #51606e;
  white-space: nowrap;
}

/* ===== PDF 应用内预览 ===== */
.pdf-container {
  min-height: 300px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  background: #525659;
  padding: 16px;
}
.pdf-container :deep(.pdf-page-wrap) {
  position: relative;
  margin: 0 auto 14px;
  width: fit-content;
  max-width: 100%;
}
.pdf-container :deep(.pdf-page) {
  display: block;
  margin: 0 auto 14px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);
  max-width: 100%;
  height: auto;
}
/* PDF：行级半透明高亮框，覆盖在页 canvas 上 */
.pdf-container :deep(.chunk-anchor-el) {
  position: absolute;
  background: rgba(255, 211, 74, 0.45);
  border-radius: 3px;
  pointer-events: none;
}
</style>
