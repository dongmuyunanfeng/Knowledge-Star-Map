<template>
  <div class="agent-chat-page">
    <div class="page-header">
      <h2 class="page-title">Agent 智能对话</h2>
    </div>
    <div class="chat-layout">
      <div class="session-list">
        <div class="list-header">
          <span>会话列表</span>
          <el-button size="small" @click="createNewSession">+ 新建</el-button>
        </div>
        <div class="session-items">
          <div
            v-for="session in sessions"
            :key="session.sessionId"
            :class="['session-item', { active: currentSessionId === session.sessionId }]"
            @click="loadSession(session.sessionId)"
          >
            <div class="session-query">{{ session.query?.slice(0, 30) }}{{ session.query?.length > 30 ? '...' : '' }}</div>
            <div class="session-meta">
              <span>{{ session.roundNo }}轮</span>
              <el-tag v-if="session.isPinned === 1" type="warning" size="small">已收藏</el-tag>
              <el-button
                link
                :type="session.isPinned === 1 ? 'warning' : 'primary'"
                size="small"
                @click.stop="handleTogglePin(session)"
              >{{ session.isPinned === 1 ? '取消收藏' : '收藏' }}</el-button>
              <el-button
                link
                type="danger"
                size="small"
                class="delete-btn"
                @click.stop="handleDeleteSession(session)"
              >删除</el-button>
            </div>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
        </div>
        <el-pagination
          v-if="total > 0"
          v-model:current-page="sessionPage"
          :page-size="10"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchSessions"
          style="margin-top: 12px"
        />
      </div>
      <div class="chat-area">
        <AgentChatDialog
          ref="chatDialogRef"
          :session-id="currentSessionId"
          mode="concise"
          @session-created="handleSessionCreated"
          @session-ended="handleSessionEnded"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAgentSessions, deleteAgentSession, pinAgentSession, unpinAgentSession } from '@/api'
import AgentChatDialog from '@/components/agent/AgentChatDialog.vue'

const sessions = ref<any[]>([])
const total = ref(0)
const sessionPage = ref(1)
const currentSessionId = ref<string>('')
const chatDialogRef = ref<InstanceType<typeof AgentChatDialog> | null>(null)

async function fetchSessions() {
  const res: any = await listAgentSessions(sessionPage.value)
  sessions.value = res.data?.list || []
  total.value = res.data?.total || 0
}

async function loadSession(sessionId: string) {
  currentSessionId.value = sessionId
  localStorage.setItem('agent_current_session_id', sessionId)
  await fetchSessions()
}

function createNewSession() {
  currentSessionId.value = ''
  localStorage.removeItem('agent_current_session_id')
  chatDialogRef.value?.resetSession()
}

async function handleTogglePin(session: any) {
  try {
    if (session.isPinned === 1) {
      await unpinAgentSession(session.sessionId)
      ElMessage.success('已取消收藏')
    } else {
      await pinAgentSession(session.sessionId)
      ElMessage.success('已收藏')
    }
    fetchSessions()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleDeleteSession(session: any) {
  await ElMessageBox.confirm('确定删除该会话？', '提示', { type: 'warning' })
  await deleteAgentSession(session.sessionId)
  ElMessage.success('删除成功')
  if (currentSessionId.value === session.sessionId) {
    currentSessionId.value = ''
    localStorage.removeItem('agent_current_session_id')
    chatDialogRef.value?.resetSession()
  }
  fetchSessions()
}

function handleSessionCreated(sessionId: string) {
  currentSessionId.value = sessionId
  localStorage.setItem('agent_current_session_id', sessionId)
  fetchSessions()
}

function handleSessionEnded() {
  currentSessionId.value = ''
  localStorage.removeItem('agent_current_session_id')
}

const handlePopState = () => {
  const saved = localStorage.getItem('agent_current_session_id')
  if (saved) {
    loadSession(saved)
  }
}

onMounted(() => {
  const saved = localStorage.getItem('agent_current_session_id')
  if (saved) {
    loadSession(saved)
  } else {
    fetchSessions()
  }
  window.addEventListener('popstate', handlePopState)
})

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState)
})
</script>

<style scoped>
.agent-chat-page { display: flex; flex-direction: column; gap: 12px; height: calc(100vh - 84px); }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.chat-layout { display: flex; flex: 1; gap: 12px; min-height: 0; }
.session-list {
  width: 280px;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.session-items { flex: 1; overflow-y: auto; }
.session-item {
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  transition: all 0.2s;
}
.session-item:hover { background: #f5f7fa; }
.session-item.active { border-color: #409eff; background: #ecf5ff; }
.session-query { font-size: 14px; color: #303133; margin-bottom: 4px; }
.session-meta { font-size: 12px; color: #909399; display: flex; gap: 8px; align-items: center; }
.delete-btn { margin-left: auto; }
.chat-area {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.chat-area :deep(.agent-chat-dialog) { display: flex; flex: 1; flex-direction: column; min-height: 0; }
</style>
