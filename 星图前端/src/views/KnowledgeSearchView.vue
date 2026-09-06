<template>
  <div class="search-page">
    <div class="page-header">
      <h2 class="page-title">知识检索</h2>
    </div>

    <el-card shadow="never" class="search-form">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="搜索知识点..." clearable />
        </el-form-item>
        <el-form-item label="领域">
          <DynamicTagSelect v-model="searchForm.domain" placeholder="选择领域" :multiple="false" :fetch-tags="domainApi.list" />
        </el-form-item>
        <el-form-item label="标签">
          <DynamicTagSelect v-model="searchForm.tag" placeholder="选择标签" :multiple="false" :fetch-tags="knowledgeApi.tags" />
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="searchForm.projectId" placeholder="全部项目" clearable style="width: 160px">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="掌握度">
          <el-select v-model="searchForm.masteryLevel" placeholder="全部" clearable>
            <el-option label="入门" :value="1" />
            <el-option label="熟练" :value="2" />
            <el-option label="精通" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="results" stripe v-loading="loading">
      <el-table-column prop="knowledgeName" label="知识点名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="knowledgeDomain" label="领域" width="90" />
      <el-table-column prop="knowledgeTag" label="标签" width="100" />
      <el-table-column label="项目" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.projectName" size="small" type="warning">{{ row.projectName }}</el-tag>
          <span v-else class="no-source">—</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" min-width="150">
        <template #default="{ row }">
          <template v-if="row.fileSources && row.fileSources.length">
            <div v-for="src in row.fileSources" :key="src.fileId" class="file-source-item">
              <div class="file-source-line">
                <el-tag size="small" type="info">{{ src.fileName }}</el-tag>
                <span v-if="src.contentSegment" class="content-segment">{{ src.contentSegment }}</span>
              </div>
            </div>
          </template>
          <span v-else class="no-source">—</span>
        </template>
      </el-table-column>
      <el-table-column label="掌握度" width="90">
        <template #default="{ row }">
          <el-tag :type="masteryLevelTag(row.masteryLevel)" size="small">
            {{ masteryLevelLabel(row.masteryLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="200">
        <template #default="{ row }">
          <div class="content-preview">{{ omitContent(row.knowledgeContent, 120) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handleSearch"
      style="margin-top: 16px; justify-content: center; display: flex"
    />

    <el-dialog v-model="detailVisible" :title="isEditing ? '编辑知识点' : '知识点详情'" width="700px">
      <el-descriptions v-if="currentKnowledge && !isEditing" :column="2" border>
        <el-descriptions-item label="知识点名称" :span="2">{{ currentKnowledge.knowledgeName }}</el-descriptions-item>
        <el-descriptions-item label="领域">{{ currentKnowledge.knowledgeDomain || '—' }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ currentKnowledge.knowledgeTag }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceTypeLabel(currentKnowledge.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="掌握度">
          <el-tag :type="masteryLevelTag(currentKnowledge.masteryLevel)" size="small">{{ masteryLevelLabel(currentKnowledge.masteryLevel) }}</el-tag>
          <span style="margin-left: 8px; color: #909399">得分：{{ currentKnowledge.masteryScore }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="文件来源" :span="2" v-if="currentKnowledge.fileSources && currentKnowledge.fileSources.length">
          <div v-for="src in currentKnowledge.fileSources" :key="src.fileId" class="file-source-line">
            <el-tag size="small">{{ src.fileName }}</el-tag>
            <el-tag v-if="src.contentSegment" size="small" type="success">{{ src.contentSegment }}</el-tag>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="内容" :span="2">{{ currentKnowledge.knowledgeContent }}</el-descriptions-item>
        <el-descriptions-item label="补全内容" :span="2">{{ currentKnowledge.completeContent || '暂无' }}</el-descriptions-item>
      </el-descriptions>

      <el-form v-if="currentKnowledge && isEditing" :model="editForm" label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="editForm.knowledgeName" />
        </el-form-item>
        <el-form-item label="领域">
          <DynamicTagSelect v-model="editForm.knowledgeDomain" placeholder="选择领域" :multiple="false" :fetch-tags="domainApi.list" />
        </el-form-item>
        <el-form-item label="标签">
          <DynamicTagSelect v-model="editForm.knowledgeTag" placeholder="选择标签" :multiple="false" :fetch-tags="knowledgeApi.tags" />
        </el-form-item>
        <el-form-item label="掌握度">
          <el-select v-model="editForm.masteryLevel" style="width: 120px">
            <el-option label="入门" :value="1" />
            <el-option label="熟练" :value="2" />
            <el-option label="精通" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editForm.knowledgeContent" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="补全内容">
          <el-input v-model="editForm.completeContent" type="textarea" :rows="4" placeholder="暂无" />
        </el-form-item>
      </el-form>

      <template #footer>
        <template v-if="isEditing">
          <el-button @click="isEditing = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSaveEdit">保存</el-button>
        </template>
        <template v-else>
          <el-select v-model="suggestionType" style="width: 130px" placeholder="补全类型">
            <el-option label="内容补全" :value="1" />
            <el-option label="漏洞标注" :value="2" />
            <el-option label="时效更新" :value="3" />
          </el-select>
          <el-button type="warning" :loading="triggerLoading" @click="handleTriggerSuggestion">手动触发补全建议</el-button>
          <el-button type="primary" @click="startEdit">编辑</el-button>
        </template>
      </template>
    </el-dialog>

    <KnowledgeSuggestionDrawer
      v-model:visible="suggestionVisible"
      :knowledge-id="currentKnowledge?.id || 0"
      @approved="handleSuggestionApproved"
      @rejected="handleSuggestionRejected"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchApi, knowledgeApi, domainApi, projectApi } from '@/api'
import DynamicTagSelect from '@/components/common/DynamicTagSelect.vue'
import KnowledgeSuggestionDrawer from '@/components/knowledge/KnowledgeSuggestionDrawer.vue'

const searchForm = reactive({ keyword: '', domain: '', tag: '', projectId: undefined as number | undefined, masteryLevel: undefined as number | undefined })
const results = ref<any[]>([])
const projects = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const currentKnowledge = ref<any>(null)
const suggestionVisible = ref(false)
const suggestionType = ref<number>(1)
const triggerLoading = ref(false)
const saving = ref(false)
const isEditing = ref(false)
const editForm = reactive({
  knowledgeName: '',
  knowledgeDomain: '',
  knowledgeTag: '',
  masteryLevel: 1,
  knowledgeContent: '',
  completeContent: ''
})

function masteryLevelTag(level: number) {
  const map: Record<number, string> = { 1: 'danger', 2: 'warning', 3: 'success' }
  return map[level] || 'info'
}

function masteryLevelLabel(level: number) {
  const map: Record<number, string> = { 1: '入门', 2: '熟练', 3: '精通' }
  return map[level] || '未知'
}

function sourceTypeLabel(type: number) {
  const map: Record<number, string> = { 1: '文件解析', 2: '手动录入', 3: 'AI补全' }
  return map[type] || '未知'
}

function omitContent(text: string | undefined, max: number) {
  if (!text) return '—'
  return text.length > max ? text.slice(0, max) + '…' : text
}

async function handleSearch() {
  loading.value = true
  try {
    const res: any = await searchApi.knowledge({
      keyword: searchForm.keyword || undefined,
      domain: searchForm.domain || undefined,
      tag: searchForm.tag || undefined,
      projectId: searchForm.projectId,
      masteryLevel: searchForm.masteryLevel,
      page: page.value,
      pageSize: pageSize.value
    })
    results.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function handleView(row: any) {
  currentKnowledge.value = row
  try {
    const res: any = await knowledgeApi.get(row.id)
    if (res?.data) currentKnowledge.value = res.data
  } catch {}
  editForm.knowledgeName = currentKnowledge.value.knowledgeName || ''
  editForm.knowledgeDomain = currentKnowledge.value.knowledgeDomain || ''
  editForm.knowledgeTag = currentKnowledge.value.knowledgeTag || ''
  editForm.masteryLevel = currentKnowledge.value.masteryLevel || 1
  editForm.knowledgeContent = currentKnowledge.value.knowledgeContent || ''
  editForm.completeContent = currentKnowledge.value.completeContent || ''
  isEditing.value = false
  detailVisible.value = true
}

function startEdit() {
  isEditing.value = true
}

async function handleSaveEdit() {
  if (!currentKnowledge.value?.id) return
  if (!editForm.knowledgeName.trim()) {
    ElMessage.warning('知识点名称不能为空')
    return
  }
  if (!editForm.knowledgeTag.trim()) {
    ElMessage.warning('标签不能为空')
    return
  }
  saving.value = true
  try {
    const payload: any = {
      knowledgeName: editForm.knowledgeName,
      knowledgeDomain: editForm.knowledgeDomain,
      knowledgeTag: editForm.knowledgeTag,
      knowledgeContent: editForm.knowledgeContent,
      completeContent: editForm.completeContent
    }
    if (editForm.masteryLevel !== currentKnowledge.value.masteryLevel) {
      payload.masteryLevel = editForm.masteryLevel
    }
    const res: any = await knowledgeApi.update(currentKnowledge.value.id, payload)
    currentKnowledge.value = res.data
    isEditing.value = false
    ElMessage.success('已保存')
    handleSearch()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除知识点「${row.knowledgeName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await knowledgeApi.delete(row.id)
    ElMessage.success('已删除')
    if (detailVisible.value && currentKnowledge.value?.id === row.id) {
      detailVisible.value = false
    }
    handleSearch()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function handleTriggerSuggestion() {
  if (!currentKnowledge.value?.id) return
  triggerLoading.value = true
  try {
    await knowledgeApi.triggerSuggestion(currentKnowledge.value.id, suggestionType.value)
    ElMessage.success('补全建议已生成，请查看抽屉')
    detailVisible.value = false
    suggestionVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '补全建议生成失败')
  } finally {
    triggerLoading.value = false
  }
}

function handleSuggestionApproved() {
  detailVisible.value = false
  ElMessage.success('建议已采纳，知识点已更新')
}

function handleRejected() {
  suggestionVisible.value = false
}

async function fetchProjects() {
  try {
    const res: any = await projectApi.list({ page: 1, pageSize: 200 })
    projects.value = res.data?.list || []
  } catch {}
}

const handlePopState = () => {
  const params = new URLSearchParams(window.location.search)
  const filter = params.get('filter')
  if (filter) {
    try {
      const f = JSON.parse(decodeURIComponent(filter))
      if (f.domain) searchForm.domain = f.domain
      if (f.tag) searchForm.tag = f.tag
      if (f.projectId) searchForm.projectId = f.projectId
    } catch {}
    page.value = 1
    handleSearch()
  }
}

onMounted(() => {
  fetchProjects()
  handlePopState()
  if (!new URLSearchParams(window.location.search).has('filter')) {
    handleSearch()
  }
  window.addEventListener('popstate', handlePopState)
})

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState)
})
</script>

<style scoped>
.search-page { display: flex; flex-direction: column; gap: 16px; }
.search-form { margin-bottom: 16px; }
.content-preview { font-size: 13px; color: #606266; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
.file-source-item { display: flex; flex-direction: column; gap: 4px; margin-bottom: 4px; }
.file-source-item:last-child { margin-bottom: 0; }
.no-source { color: #c0c4cc; font-size: 12px; }
.file-source-detail { display: flex; flex-direction: column; gap: 4px; }
.file-source-line { display: flex; align-items: center; gap: 8px; }
.file-source-path { font-size: 12px; color: #909399; word-break: break-all; }
.content-segment { font-size: 11px; color: #67c23a; }
</style>
