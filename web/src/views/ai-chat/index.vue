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
          <span class="bot-icon">🌿</span>
          <div>
            <div class="bot-name">AI 碳管家</div>
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
          <h3>您好，我是 AI 碳管家</h3>
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
      <el-dialog v-model="docPreviewVisible" :title="previewDocName" width="80%" top="4vh" destroy-on-close>
        <div ref="docxContainer" class="docx-container" v-loading="docPreviewLoading" element-loading-text="正在渲染文档…"></div>
      </el-dialog>

      <!-- TXT/MD 原文预览弹窗（定位分块） -->
      <el-dialog v-model="textPreviewVisible" :title="textPreviewName" width="80%" top="4vh" destroy-on-close>
        <div ref="textContainer" class="text-container">
          <pre v-html="textPreviewHtml"></pre>
        </div>
      </el-dialog>

      <!-- PDF 应用内预览弹窗（pdf.js 渲染，不依赖浏览器原生预览） -->
      <el-dialog v-model="pdfPreviewVisible" :title="pdfPreviewName" width="80%" top="4vh" destroy-on-close>
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

// AI 回答为 Markdown 文本，转 HTML 渲染（加粗/列表/标题等）
function renderMd(content) {
  return content ? marked.parse(content) : ''
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
    const resp = await fetch('/algo/api/alg/rag-chat', {
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

async function viewDoc(d) {
  try {
    const blob = await fetchDocBlob(d.id)
    if (d.docType === 'docx') {
      await openDocxPreview(blob, d.docName, null)
    } else if (d.docType === 'pdf') {
      await openPdfPreview(blob, d.docName, null)
    } else {
      await openTextPreview(blob, d.docName, null)
    }
  } catch (e) {
    ElMessage.error(e.message || '文件加载失败')
  }
}

// ===== 引用分块跳转：点击来源 → 浏览原件并定位到分块开头 =====
async function fetchDocBlob(docId) {
  const token = localStorage.getItem('satoken')
  const resp = await fetch(`/api/kb/${docId}/file`, { headers: { satoken: token } })
  if (!resp.ok) {
    const err = await resp.json().catch(() => null)
    throw new Error(err?.msg || '文件加载失败')
  }
  return await resp.blob()
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
  try {
    const blob = await fetchDocBlob(anchor.docId)
    if (anchor.docType === 'docx') {
      await openDocxPreview(blob, s.doc_name, anchor.content)
    } else if (anchor.docType === 'pdf') {
      await openPdfPreview(blob, s.doc_name, anchor.content)
    } else {
      await openTextPreview(blob, s.doc_name, anchor.content)
    }
  } catch (e) {
    ElMessage.error(e.message || '文件加载失败')
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
// 关键：按块级元素（段落/标题/分页）插入空格分隔再拼接，与后端分块文本（段落间换行）对齐，
// 否则段落边界处两个词会粘连（"…监管。二是…"），导致与分块文本（"…监管。 二是…"）匹配失败
const BLOCK_TAGS = new Set(['P', 'DIV', 'SECTION', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'LI', 'TR', 'BR'])

function locateInDocx(root, anchor) {
  const normalize = t => t.replace(/\s+/g, ' ')
  const strip = t => t.replace(/\s+/g, '')
  const parts = [] // { el, normStart, normLen, stripStart, stripLen }
  let bufNorm = ''
  let bufStrip = ''

  function walk(el) {
    for (const child of el.childNodes) {
      if (child.nodeType === 3) {
        const t = normalize(child.nodeValue)
        if (!t.trim()) continue
        parts.push({
          el: child.parentElement,
          normStart: bufNorm.length, normLen: t.length,
          stripStart: bufStrip.length, stripLen: strip(t).length
        })
        bufNorm += t
        bufStrip += strip(t)
      } else if (child.nodeType === 1) {
        if (BLOCK_TAGS.has(child.tagName)) {
          if (bufNorm && !bufNorm.endsWith(' ')) bufNorm += ' '
          walk(child)
          if (bufNorm && !bufNorm.endsWith(' ')) bufNorm += ' '
        } else {
          walk(child)
        }
      }
    }
  }
  walk(root)

  // 依次尝试：归一化空格匹配 → 去全部空白匹配；全文 → 前缀退化
  const candidates = [
    { buf: bufNorm, start: p => p.normStart, len: p => p.normLen, text: normalize(anchor).trim() },
    { buf: bufStrip, start: p => p.stripStart, len: p => p.stripLen, text: strip(anchor) }
  ]
  for (const c of candidates) {
    if (!c.text) continue
    let idx = c.buf.indexOf(c.text)
    if (idx < 0) {
      const shorter = c.text.slice(0, Math.max(12, Math.floor(c.text.length / 2)))
      idx = c.buf.indexOf(shorter)
    }
    if (idx < 0) continue
    const hit = parts.find(p => idx >= c.start(p) && idx < c.start(p) + c.len(p))
    const el = hit?.el
    if (el) {
      el.classList.add('chunk-anchor-el')
      el.scrollIntoView({ block: 'start', behavior: 'smooth' })
      return true
    }
  }
  ElMessage.info('已打开文档，但未定位到该分块（分块内容与原件排版可能不一致）')
  return false
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

// 在 PDF 文本层中查找分块开头：逐页拼接去空白文本，返回 { page, top }（top 为渲染后的页内像素偏移）
async function findPdfAnchor(pdf, anchorText) {
  const strip = t => t.replace(/\s+/g, '')
  const target = strip(anchorText)
  for (const len of [80, 40, 20]) {
    const key = target.slice(0, len)
    if (!key) break
    for (let p = 1; p <= pdf.numPages; p++) {
      const page = await pdf.getPage(p)
      const tc = await page.getTextContent()
      let text = ''
      const ranges = [] // 去空白坐标下的 { start, len, y }
      for (const it of tc.items) {
        const s = strip(it.str || '')
        if (!s) continue
        ranges.push({ start: text.length, len: s.length, y: it.transform[5] })
        text += s
      }
      const idx = text.indexOf(key)
      if (idx >= 0) {
        const hit = ranges.find(r => idx >= r.start && idx < r.start + r.len)
        // PDF 坐标系原点在左下角：换算为距页面顶部的渲染像素
        const vp = page.getViewport({ scale: 1 })
        const top = (vp.height - (hit ? hit.y : vp.height)) * PDF_SCALE
        return { page: p, top }
      }
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
    // 逐页渲染（先建 canvas 保持 DOM 顺序，再并行渲染）
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
      renderTasks.push(page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise)
    }
    await Promise.all(renderTasks)
    if (anchor) {
      const canvas = pdfContainer.value.querySelector(`canvas[data-page="${anchor.page}"]`)
      if (canvas) {
        canvas.classList.add('chunk-anchor-el')
        await nextTick()
        pdfContainer.value.scrollTo({ top: canvas.offsetTop + Math.max(0, anchor.top - 40), behavior: 'smooth' })
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
/* 定位到的分块位置高亮 */
.docx-container :deep(.chunk-anchor-el) {
  background: #fff3c4;
  border-radius: 3px;
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

/* ===== PDF 应用内预览 ===== */
.pdf-container {
  min-height: 300px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  background: #525659;
  padding: 16px;
}
.pdf-container :deep(.pdf-page) {
  display: block;
  margin: 0 auto 14px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);
  max-width: 100%;
  height: auto;
}
.pdf-container :deep(.chunk-anchor-el) {
  outline: 3px solid #f59e0b;
  outline-offset: -3px;
}
</style>
