<template>
  <div class="agent-chat-dialog" :class="{ 'debug-mode': mode === 'debug' }">
    <div class="dialog-header">
      <span class="dialog-title">Agent 智能对话</span>
      <el-switch
        v-model="debugMode"
        active-text="调试模式"
        @change="onModeChange"
      />
    </div>

    <div ref="messagesRef" class="messages-container">
      <div v-if="messages.length === 0" class="empty-hint">
        输入问题，开始与Agent对话
      </div>
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="message-row"
        :class="msg.role"
      >
        <div class="message-bubble">
          <div class="message-content">{{ msg.content }}</div>
        </div>
        <div v-if="mode === 'debug' && msg.rounds" class="debug-panel">
          <div
            v-for="(round, rIdx) in msg.rounds"
            :key="rIdx"
            class="debug-round"
          >
            <div class="round-header">
              <span>Round {{ round.roundNo }}</span>
              <el-button
                text
                size="small"
                @click="toggleRound(idx, rIdx)"
              >
                {{ expandedRounds[`${idx}-${rIdx}`] ? '收起' : '展开' }}
              </el-button>
            </div>
            <div v-if="expandedRounds[`${idx}-${rIdx}`]" class="round-body">
              <div class="thought-block">
                <span class="icon">💭</span>
                <span>{{ round.agentThought }}</span>
              </div>
              <div v-if="round.toolCalls?.length" class="tool-calls">
                <div
                  v-for="(tc, tIdx) in round.toolCalls"
                  :key="tIdx"
                  class="tool-call-item"
                >
                  <span class="tool-name">🔧 {{ tc.toolName }}</span>
                  <pre class="tool-params">{{ JSON.stringify(tc.parameters, null, 2) }}</pre>
                  <span :class="['tool-status', tc.success ? 'success' : 'error']">
                    {{ tc.success ? '✅ 成功' : '❌ ' + tc.errorMessage }}
                  </span>
                </div>
              </div>
              <div v-if="round.toolObservations?.length" class="tool-observations">
                <div
                  v-for="(to, oIdx) in round.toolObservations"
                  :key="oIdx"
                  class="tool-obs-item"
                >
                  <span class="tool-name">📊 {{ to.toolName }}</span>
                  <pre class="tool-result">{{ JSON.stringify(to.result, null, 2) }}</pre>
                  <span :class="['tool-status', to.success ? 'success' : 'error']">
                    {{ to.success ? '✅ 成功' : '❌' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="loading || generating" class="message-row assistant">
        <div class="message-bubble">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <el-input
        v-model="inputQuery"
        type="textarea"
        :rows="3"
        placeholder="输入您的问题..."
        :disabled="loading || generating"
        @keydown.enter.ctrl="sendMessage"
      />
      <el-button
        type="primary"
        :loading="loading"
        @click="sendMessage"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import { getAgentSession } from '@/api'
import { streamPost, type SseHandlers } from '@/utils/sse'
import type { AgentSessionDTO, AgentRoundDTO } from '@/types'
import { ElMessage } from 'element-plus'

interface Props {
  sessionId?: string
  initialQuery?: string
  mode?: 'concise' | 'debug'
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'concise'
})

const emit = defineEmits<{
  (e: 'session-created', sessionId: string): void
  (e: 'session-ended'): void
}>()

interface MessageItem {
  role: string
  content: string
  rounds?: AgentRoundDTO[]
}

interface SessionState {
  messages: MessageItem[]
  loading: boolean
  status: string
  abortController: AbortController | null
  pollTimer: ReturnType<typeof setTimeout> | null
  pollEpoch: number
}

const mode = ref<'concise' | 'debug'>(props.mode || 'concise')
const debugMode = computed({
  get: () => mode.value === 'debug',
  set: (v) => { mode.value = v ? 'debug' : 'concise' }
})
const inputQuery = ref('')
const expandedRounds = ref<Record<string, boolean>>({})
const messagesRef = ref<HTMLElement | null>(null)
const currentSessionId = ref<string>(props.sessionId || '')

// 每个会话独立持有消息与生成状态：切换会话不再中止在途 SSE 流，
// 后台流继续写入对应会话的消息，切回时即可看到流式内容。
const sessionStates = ref<Record<string, SessionState>>({})

const EMPTY_STATE: SessionState = {
  messages: [],
  loading: false,
  status: 'completed',
  abortController: null,
  pollTimer: null,
  pollEpoch: 0
}

function stateOf(sessionId: string): SessionState {
  const key = sessionId || '__empty__'
  if (!sessionStates.value[key]) {
    sessionStates.value[key] = {
      messages: [],
      loading: false,
      status: 'completed',
      abortController: null,
      pollTimer: null,
      pollEpoch: 0
    }
  }
  // 必须返回 reactive 代理而非裸对象，否则对 messages/status/loading 的修改无法被模板追踪
  return sessionStates.value[key]
}

const currentState = computed<SessionState>(() => {
  const key = currentSessionId.value || '__empty__'
  return sessionStates.value[key] || EMPTY_STATE
})

const messages = computed<MessageItem[]>(() => currentState.value.messages)
const loading = computed<boolean>(() => currentState.value.loading)
const generating = computed<boolean>(() => currentState.value.status === 'active')

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function typewriter(text: string, target: MessageItem, sessionId: string) {
  target.content = ''
  let i = 0
  const step = () => {
    if (i >= text.length) {
      if (currentSessionId.value === sessionId) scrollToBottom()
      return
    }
    target.content += text.slice(i, i + 3)
    i += 3
    if (currentSessionId.value === sessionId) scrollToBottom()
    setTimeout(step, 12)
  }
  step()
}

function onModeChange() {
  // mode already updated via computed
}

function adoptSessionId(sessionId: string, state: SessionState) {
  const oldKey = currentSessionId.value || '__empty__'
  if (sessionStates.value[oldKey] === state) {
    delete sessionStates.value[oldKey]
  }
  sessionStates.value[sessionId] = state
  currentSessionId.value = sessionId
  localStorage.setItem('agent_current_session_id', sessionId)
}

async function sendMessage() {
  const query = inputQuery.value.trim()
  const state = stateOf(currentSessionId.value)
  if (!query || state.loading || state.status === 'active') return
  inputQuery.value = ''

  state.messages.push({ role: 'user', content: query })
  state.messages.push({ role: 'assistant', content: '' })
  const answerIdx = state.messages.length - 1
  await scrollToBottom()

  state.loading = true
  state.status = 'active'
  const controller = new AbortController()
  state.abortController = controller
  let gotSessionId = currentSessionId.value || ''
  let streamSessionId = currentSessionId.value || ''

  const handlers: SseHandlers = {
    onSession: (sessionId) => {
      if (!sessionId) return
      gotSessionId = sessionId
      streamSessionId = sessionId
      if (sessionId !== currentSessionId.value) {
        adoptSessionId(sessionId, state)
      } else {
        localStorage.setItem('agent_current_session_id', sessionId)
      }
      emit('session-created', sessionId)
    },
    onDelta: (content) => {
      state.messages[answerIdx].content += content
      if (currentSessionId.value === streamSessionId) scrollToBottom()
    },
    onDone: (data) => {
      if (data?.sessionId && !gotSessionId) {
        gotSessionId = data.sessionId
        streamSessionId = data.sessionId
        adoptSessionId(data.sessionId, state)
        emit('session-created', data.sessionId)
      }
      const final = data?.finalAnswer
      if (final) {
        if (state.messages[answerIdx].content !== final) {
          typewriter(final, state.messages[answerIdx], streamSessionId)
        }
      } else if (!state.messages[answerIdx].content) {
        state.messages[answerIdx].content = 'Agent 已处理您的请求。'
      }
    },
    onError: (payload) => {
      const code = payload?.code
      if (code === 1403 || code === 1401) {
        localStorage.removeItem('agent_current_session_id')
        currentSessionId.value = ''
        state.messages[answerIdx].content = '对话已过期，请重新发起对话。'
        ElMessage.info('对话已过期，请重新发起对话')
        emit('session-ended')
      } else {
        state.messages[answerIdx].content = '发送失败，请重试。'
        ElMessage.error(payload?.message || '发送失败，请重试')
      }
    }
  }

  try {
    const sessionId = currentSessionId.value
    if (sessionId) {
      await streamPost(`/agent/sessions/${sessionId}/continue/stream`, { query }, handlers, controller.signal)
    } else {
      await streamPost('/agent/sessions/stream', { query }, handlers, controller.signal)
    }

    if (gotSessionId) {
      try {
        const detail: any = await getAgentSession(gotSessionId)
        const session = detail.data as AgentSessionDTO
        const rounds = session.rounds || []
        if (rounds.length > 0) {
          state.messages[answerIdx].rounds = rounds
        }
      } catch {
        // 调试面板数据拉取失败不影响主流程
      }
    }
  } catch (e) {
    if (!state.messages[answerIdx].content) {
      state.messages[answerIdx].content = '发送失败，请重试。'
    }
    ElMessage.error('发送失败，请重试')
  } finally {
    state.abortController = null
    state.loading = false
    state.status = 'completed'
    if (currentSessionId.value === streamSessionId) await scrollToBottom()
  }
}

function toggleRound(msgIdx: number, roundIdx: number) {
  const key = `${msgIdx}-${roundIdx}`
  expandedRounds.value[key] = !expandedRounds.value[key]
}

watch(
  () => props.sessionId,
  (val) => {
    if (val === currentSessionId.value) return
    if (val) {
      activateSession(val)
    } else {
      clearCurrentView()
    }
  }
)

function activateSession(sessionId: string) {
  currentSessionId.value = sessionId
  expandedRounds.value = {}
  const state = stateOf(sessionId)
  // 该会话仍在本地流式生成中则直接展示，无需回源；否则首次进入需从后端加载
  if (state.status !== 'active' && state.messages.length === 0) {
    loadSession(sessionId)
  }
}

function clearCurrentView() {
  const empty = sessionStates.value['__empty__']
  if (empty) empty.messages = []
  currentSessionId.value = ''
  inputQuery.value = ''
  expandedRounds.value = {}
}

function clearPoll(state: SessionState) {
  state.pollEpoch++
  if (state.pollTimer) {
    clearTimeout(state.pollTimer)
    state.pollTimer = null
  }
}

function renderSessionInto(state: SessionState, session: AgentSessionDTO) {
  state.messages = []
  const history = session.historyMessages || []
  if (history.length > 0) {
    for (const m of history) {
      if (m.role === 'user') {
        state.messages.push({ role: 'user', content: m.content || '' })
      } else if (m.role === 'assistant') {
        state.messages.push({ role: 'assistant', content: m.content || '' })
      }
    }
    // 最后一轮 assistant 附上 rounds，供调试面板展示
    const lastAssistant = [...state.messages].reverse().find(m => m.role === 'assistant')
    if (lastAssistant && session.rounds?.length) {
      lastAssistant.rounds = session.rounds
    }
  } else {
    // 降级：无完整历史时退回 query + finalAnswer
    if (session.query) {
      state.messages.push({ role: 'user', content: session.query })
    }
    if (session.finalAnswer) {
      state.messages.push({
        role: 'assistant',
        content: session.finalAnswer,
        rounds: session.rounds || []
      })
    }
  }
  state.status = session.status || 'completed'
  scrollToBottom()
}

// 会话仍在后端生成中（status=active）时轮询等待完成，主要用于页面刷新后恢复生成中会话
function schedulePoll(sessionId: string, state: SessionState) {
  clearPoll(state)
  const epoch = state.pollEpoch
  state.pollTimer = setTimeout(async () => {
    if (currentSessionId.value !== sessionId || epoch !== state.pollEpoch) return
    try {
      const res: any = await getAgentSession(sessionId)
      if (currentSessionId.value !== sessionId || epoch !== state.pollEpoch) return
      const session = res.data as AgentSessionDTO
      renderSessionInto(state, session)
      if (session.status === 'active') {
        schedulePoll(sessionId, state)
      }
    } catch {
      // 会话已过期/不存在，停止轮询
    }
  }, 1500)
}

async function loadSession(sessionId: string) {
  const state = stateOf(sessionId)
  if (state.loading) return
  clearPoll(state)
  try {
    const res: any = await getAgentSession(sessionId)
    const session = res.data as AgentSessionDTO
    renderSessionInto(state, session)
    if (session.status === 'active') {
      schedulePoll(sessionId, state)
    }
  } catch {
    localStorage.removeItem('agent_current_session_id')
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = ''
    }
  }
}

function resetSession() {
  const cur = currentState.value
  cur.abortController?.abort()
  clearPoll(cur)
  cur.loading = false
  cur.status = 'completed'
  clearCurrentView()
}

defineExpose({ resetSession })

onUnmounted(() => {
  for (const key of Object.keys(sessionStates.value)) {
    const s = sessionStates.value[key]
    s.abortController?.abort()
    clearPoll(s)
  }
})

const savedSessionId = localStorage.getItem('agent_current_session_id')
if (savedSessionId) {
  currentSessionId.value = savedSessionId
  loadSession(savedSessionId)
}

if (props.initialQuery) {
  inputQuery.value = props.initialQuery
}
</script>

<style scoped>
.agent-chat-dialog {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}
.dialog-title {
  font-size: 16px;
  font-weight: 600;
}
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.empty-hint {
  text-align: center;
  color: #909399;
  margin-top: 60px;
}
.message-row {
  display: flex;
  margin-bottom: 16px;
}
.message-row.user {
  justify-content: flex-end;
}
.message-row.assistant {
  justify-content: flex-start;
}
.message-bubble {
  max-width: 75%;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.message-row.user .message-bubble {
  background: #409eff;
  color: #fff;
}
.message-row.assistant .message-bubble {
  background: #f0f2f5;
  color: #303133;
}
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}
.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #909399;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
.debug-panel {
  margin-top: 8px;
  margin-left: 48px;
  width: 100%;
  max-width: 600px;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 12px;
  color: #d4d4d4;
  font-size: 12px;
}
.round-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #4ec9b0;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #333;
}
.thought-block {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  color: #ce9178;
}
.tool-calls,
.tool-observations {
  margin-left: 16px;
  margin-bottom: 8px;
}
.tool-call-item,
.tool-obs-item {
  margin-bottom: 8px;
  padding: 8px;
  background: #2d2d2d;
  border-radius: 4px;
}
.tool-name {
  font-weight: 600;
  color: #569cd6;
  display: block;
  margin-bottom: 4px;
}
.tool-params,
.tool-result {
  background: #1e1e1e;
  padding: 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 11px;
  overflow-x: auto;
  white-space: pre-wrap;
  margin: 4px 0;
  color: #4ec9b0;
}
.tool-status {
  font-size: 11px;
  margin-top: 4px;
  display: block;
}
.tool-status.success { color: #6a9955; }
.tool-status.error { color: #f44747; }
.input-area {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
}
.input-area :deep(.el-textarea__inner) {
  flex: 1;
  resize: none;
}
</style>
