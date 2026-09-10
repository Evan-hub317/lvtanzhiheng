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
            <div class="msg-content">{{ m.content }}<span v-if="streaming && i === messages.length - 1" class="cursor">▌</span></div>
            <div v-if="m.sources && m.sources.length" class="msg-sources">
              <el-tag v-for="(s, si) in m.sources" :key="si" size="small" type="info" effect="plain" class="source-tag">
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
import { kbListApi, kbUploadApi, kbUpdateStatusApi, kbDeleteApi } from '@/api/kb'
import { renderAsync } from 'docx-preview'

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
    const token = localStorage.getItem('satoken')
    const resp = await fetch(`/api/kb/${d.id}/file`, { headers: { satoken: token } })
    if (!resp.ok) {
      const err = await resp.json().catch(() => null)
      ElMessage.error(err?.msg || '文件加载失败')
      return
    }
    const blob = await resp.blob()
    if (d.docType === 'docx') {
      previewDocName.value = d.docName
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
      } catch (e) {
        console.error('docx 渲染失败', e)
        ElMessage.error(`文档渲染失败：${e?.message || '格式暂不支持'}`)
        docPreviewVisible.value = false
      } finally {
        docPreviewLoading.value = false
      }
    } else {
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank')
    }
  } catch (e) {
    ElMessage.error('文件加载失败')
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
.docx-container :deep(.docx-wrapper) {
  background: #fff;
  max-width: 100%;
  padding: 40px 48px;
  box-shadow: 0 2px 10px rgba(16, 42, 67, 0.08);
}
.docx-container :deep(.docx-wrapper section) {
  margin-bottom: 24px;
}
</style>
