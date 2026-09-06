<template>
  <el-drawer
    :model-value="visible"
    title="知识补全建议"
    direction="rtl"
    size="480px"
    @close="emit('update:visible', false)"
  >
    <div v-loading="loading" class="suggestion-list">
      <el-empty v-if="!loading && localSuggestions.length === 0" description="暂无补全建议" />
      <div
        v-for="item in localSuggestions"
        :key="item.id"
        class="suggestion-item"
      >
        <div class="suggestion-header">
          <span class="suggestion-title">{{ item.suggestionTitle }}</span>
          <el-tag :type="suggestionTypeTag(item.suggestionType)" size="small">
            {{ suggestionTypeLabel(item.suggestionType) }}
          </el-tag>
        </div>
        <div class="suggestion-content">{{ item.suggestionContent }}</div>
        <div v-if="item.suggestionReason" class="suggestion-reason">
          <span class="label">原因：</span>{{ item.suggestionReason }}
        </div>
        <div class="suggestion-meta">
          <span class="time">{{ item.createTime }}</span>
          <span class="status">
            <el-tag :type="statusType(item.status)" size="small">
              {{ statusLabel(item.status) }}
            </el-tag>
          </span>
        </div>
        <el-divider v-if="item.status === 0" />
        <div v-if="item.status === 0" class="suggestion-actions">
          <el-button
            type="primary"
            size="small"
            :loading="approvingId === item.id"
            :disabled="approvingId === item.id"
            @click="handleApprove(item.id)"
          >
            采纳
          </el-button>
          <el-button
            size="small"
            :loading="rejectingId === item.id"
            :disabled="rejectingId === item.id"
            @click="handleReject(item.id)"
          >
            拒绝
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { suggestionApi } from '@/api'
import type { KnowledgeSuggestionVO } from '@/types'
import { ElMessage } from 'element-plus'

interface Props {
  knowledgeId: number
  visible: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'approved', suggestionId: number): void
  (e: 'rejected', suggestionId: number): void
}>()

const loading = ref(false)
const localSuggestions = ref<KnowledgeSuggestionVO[]>([])
const approvingId = ref<number | null>(null)
const rejectingId = ref<number | null>(null)

async function fetchSuggestions() {
  if (!props.visible) return
  loading.value = true
  try {
    const res: any = await suggestionApi.list(props.knowledgeId)
    localSuggestions.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) fetchSuggestions()
  }
)

async function handleApprove(id: number) {
  approvingId.value = id
  try {
    await suggestionApi.approve(id)
    ElMessage.success('已采纳建议')
    emit('approved', id)
    fetchSuggestions()
  } finally {
    approvingId.value = null
  }
}

async function handleReject(id: number) {
  rejectingId.value = id
  try {
    await suggestionApi.reject(id)
    ElMessage.success('已拒绝建议')
    emit('rejected', id)
    fetchSuggestions()
  } finally {
    rejectingId.value = null
  }
}

function suggestionTypeTag(type: number) {
  const map: Record<number, string> = { 1: 'primary', 2: 'warning', 3: 'info' }
  return map[type] || 'info'
}

function suggestionTypeLabel(type: number) {
  const map: Record<number, string> = { 1: '内容补全', 2: '漏洞标注', 3: '时效更新' }
  return map[type] || '未知'
}

function statusType(status: number) {
  const map: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'info' }
  return map[status] || 'info'
}

function statusLabel(status: number) {
  const map: Record<number, string> = { 0: '待确认', 1: '已采纳', 2: '已拒绝' }
  return map[status] || '未知'
}
</script>

<style scoped>
.suggestion-list {
  padding: 0 16px;
}
.suggestion-item {
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}
.suggestion-item:last-child {
  border-bottom: none;
}
.suggestion-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.suggestion-title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}
.suggestion-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
}
.suggestion-reason {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.suggestion-reason .label {
  font-weight: 600;
  color: #606266;
}
.suggestion-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}
.suggestion-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
