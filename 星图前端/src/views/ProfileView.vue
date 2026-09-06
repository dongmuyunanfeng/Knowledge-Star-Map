<template>
  <div class="profile-page">
    <div class="page-header">
      <h2 class="page-title">个人中心</h2>
    </div>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">用户信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ user?.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ user?.nickname || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="学习方向">{{ user?.studyDirection || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="求职目标">{{ user?.jobTarget || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ user?.email || '未设置' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top: 16px">
            <el-button type="primary" @click="editVisible = true">编辑信息</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">Agent 配置</span></template>
          <div class="config-item">
            <div class="config-label">
              <span>允许Agent自动生成知识补全建议</span>
              <span class="config-desc">开启后，Agent在文件解析过程中会自动检测知识漏洞并生成补全建议，所有建议需您手动确认后才能生效。</span>
            </div>
            <el-switch
              v-model="autoSuggestion"
              @change="handleConfigChange"
              :loading="configLoading"
            />
          </div>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span class="card-title">检索历史</span></template>
          <el-table :data="historyList" stripe size="small" max-height="200">
            <el-table-column prop="keyword" label="搜索关键词" />
            <el-table-column prop="resultCount" label="结果数" width="80" />
            <el-table-column prop="createTime" label="时间" width="160" />
            <el-table-column label="操作" width="60">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="handleDeleteHistory(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="historyTotal > 0"
            v-model:current-page="historyPage"
            :page-size="10"
            :total="historyTotal"
            layout="prev, pager, next"
            @current-change="fetchHistory"
            style="margin-top: 12px; justify-content: center; display: flex"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="editVisible" title="编辑用户信息" width="400px">
      <el-form ref="formRef" :model="editForm" label-width="80px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" />
        </el-form-item>
        <el-form-item label="学习方向">
          <el-input v-model="editForm.studyDirection" />
        </el-form-item>
        <el-form-item label="求职目标">
          <el-input v-model="editForm.jobTarget" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="handleUpdate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { userApi, searchHistoryApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { UserVO, UserConfigVO } from '@/types'

const authStore = useAuthStore()
const user = ref<UserVO | null>(authStore.user)
const autoSuggestion = ref(false)
const configLoading = ref(false)
const editVisible = ref(false)
const updating = ref(false)
const formRef = ref<FormInstance>()
const editForm = ref({ nickname: '', studyDirection: '', jobTarget: '', email: '' })

const historyList = ref<any[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)

async function fetchUser() {
  const res: any = await userApi.getMe()
  user.value = res.data
  authStore.setUser(res.data)
}

async function fetchConfig() {
  const res: any = await userApi.getConfig()
  autoSuggestion.value = res.data?.enableAutoKnowledgeSuggestion || false
}

async function fetchHistory() {
  const res: any = await searchHistoryApi.list({ page: historyPage.value, pageSize: 10 })
  historyList.value = res.data?.list || []
  historyTotal.value = res.data?.total || 0
}

async function handleConfigChange() {
  configLoading.value = true
  try {
    await userApi.updateConfig({ enableAutoKnowledgeSuggestion: autoSuggestion.value })
    ElMessage.success('配置已更新')
  } finally {
    configLoading.value = false
  }
}

async function handleUpdate() {
  updating.value = true
  try {
    const res: any = await userApi.updateMe(editForm.value)
    user.value = res.data
    authStore.setUser(res.data)
    editVisible.value = false
    ElMessage.success('信息已更新')
  } finally {
    updating.value = false
  }
}

async function handleDeleteHistory(id: number) {
  await searchHistoryApi.delete(id)
  ElMessage.success('已删除')
  fetchHistory()
}

onMounted(() => {
  fetchUser()
  fetchConfig()
  fetchHistory()
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; }
.config-item { display: flex; justify-content: space-between; align-items: flex-start; }
.config-label { flex: 1; }
.config-desc { font-size: 12px; color: #909399; margin-top: 4px; display: block; }
</style>
