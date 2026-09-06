<template>
  <div class="file-manage-page">
    <div class="page-header">
      <h2 class="page-title">文件知识管理</h2>
      <el-upload
        :before-upload="beforeUpload"
        :show-file-list="false"
        accept=".pdf,.md,.txt,.zip,.png,.jpg,.jpeg,.bmp,.gif,.java,.xml,.json,.yml,.yaml,.properties,.html,.css,.js,.py,.go,.c,.cpp,.h,.hpp,.sql,.csv,.ts,.tsx,.vue,.rb,.rs,.kt,.swift,.scala,.cs,.docx,.pptx,.xlsx"
      >
        <el-button type="primary">
          <el-icon><Upload /></el-icon> 上传文件
        </el-button>
      </el-upload>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="搜索文件名..." clearable style="width: 200px" />
      <el-button type="primary" @click="fetchFiles">搜索</el-button>
    </div>

    <el-table :data="files" stripe v-loading="loading">
      <el-table-column prop="fileName" label="文件名" />
      <el-table-column prop="fileSuffix" label="格式" width="80">
        <template #default="{ row }">{{ row.fileSuffix?.toUpperCase() }}</template>
      </el-table-column>
      <el-table-column label="文件类型" width="130">
        <template #default="{ row }">
          <el-select v-model="row.fileCategory" size="small" @change="handleCategoryChange(row)">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="fileSize" label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="parseStatus" label="解析状态" width="100">
        <template #default="{ row }">
          <el-tag :type="parseStatusType(row.parseStatus)">
            {{ parseStatusLabel(row.parseStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
          <el-button link type="warning" @click="handleParse(row.id)">重新解析</el-button>
          <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetchFiles"
      style="margin-top: 16px; justify-content: center; display: flex"
    />

    <el-dialog v-model="parseProgress.visible" title="正在解析" width="420px" :close-on-click-modal="false" :show-close="false">
      <div class="parse-progress">
        <el-progress :percentage="parseProgress.percent" />
        <p class="parse-progress-msg">{{ parseProgress.message }}</p>
      </div>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="文件详情" width="600px">
      <el-descriptions :column="2" border v-if="currentFile">
        <el-descriptions-item label="文件名">{{ currentFile.fileName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentFile.fileSuffix?.toUpperCase() }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatSize(currentFile.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ currentFile.createTime }}</el-descriptions-item>
        <el-descriptions-item label="解析状态" :span="2">
          <el-tag :type="parseStatusType(currentFile.parseStatus)">
            {{ parseStatusLabel(currentFile.parseStatus) }}
          </el-tag>
          <span v-if="currentFile.parseMessage" style="margin-left: 8px; color: #f56c6c">{{ currentFile.parseMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fileApi } from '@/api'
import { streamPost } from '@/utils/sse'
import { Upload } from '@element-plus/icons-vue'

const files = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const detailVisible = ref(false)
const currentFile = ref<any>(null)
const parseProgress = ref<{ visible: boolean; percent: number; message: string }>({ visible: false, percent: 0, message: '' })

const categoryOptions = [
  { value: 'note', label: '笔记' },
  { value: 'project', label: '项目' },
  { value: 'code', label: '代码' },
  { value: 'image', label: '图片' },
  { value: 'manual', label: '手册' },
  { value: 'other', label: '其他' },
]

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function parseStatusType(status: number) {
  const map: Record<number, string> = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

function parseStatusLabel(status: number) {
  const map: Record<number, string> = { 0: '待解析', 1: '成功', 2: '失败' }
  return map[status] || '未知'
}

async function fetchFiles() {
  loading.value = true
  try {
    const res: any = await fileApi.list({ page: page.value, pageSize: pageSize.value, keyword: keyword.value })
    files.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function beforeUpload(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  fileApi.upload(formData).then(() => {
    ElMessage.success('上传成功')
    fetchFiles()
  }).catch(() => {})
  return false
}

function handleView(row: any) {
  currentFile.value = row
  detailVisible.value = true
}

async function handleParse(id: number) {
  parseProgress.value = { visible: true, percent: 0, message: '开始解析…' }
  try {
    await streamPost(`/files/${id}/parse/stream`, {}, {
      onProgress: (message) => {
        parseProgress.value.message = message
        parseProgress.value.percent = Math.min(parseProgress.value.percent + 15, 95)
      },
      onDone: () => {
        parseProgress.value.percent = 100
        ElMessage.success('解析完成')
        fetchFiles()
      },
      onError: (payload) => ElMessage.error(payload?.message || '解析失败')
    })
  } catch {
    ElMessage.error('解析失败，请稍后重试')
  } finally {
    setTimeout(() => { parseProgress.value.visible = false }, 800)
  }
}

async function handleCategoryChange(row: any) {
  try {
    await fileApi.updateCategory(row.id, row.fileCategory)
    ElMessage.success('类型已修改，请重新解析')
  } catch {
    ElMessage.error('类型修改失败')
    fetchFiles()
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该文件？', '提示', { type: 'warning' })
  await fileApi.delete(id)
  ElMessage.success('删除成功')
  fetchFiles()
}

onMounted(fetchFiles)
</script>

<style scoped>
.file-manage-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.filter-bar { display: flex; gap: 12px; align-items: center; }
.parse-progress-msg { margin: 12px 0 0; font-size: 13px; color: #606266; }
</style>
