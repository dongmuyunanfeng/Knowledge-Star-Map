<template>
  <div class="project-exp-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">项目经历</h2>
        <p class="page-subtitle">解析项目源文件时自动沉淀的简历级项目经历，可独立重新生成</p>
      </div>
      <el-button @click="fetchList" :loading="loading">刷新</el-button>
    </div>

    <div v-loading="loading" class="exp-list">
      <el-card v-for="item in list" :key="item.projectId" shadow="never" class="exp-card">
        <div class="exp-card-head">
          <div class="exp-title">
            <span class="exp-name">{{ item.projectName || '未命名项目' }}</span>
            <el-tag v-if="item.generateStatus === 1" type="success" size="small">已生成</el-tag>
            <el-tag v-else-if="item.generateStatus === 2" type="danger" size="small">生成失败</el-tag>
            <el-tag v-else type="info" size="small">待生成</el-tag>
          </div>
          <el-button
            type="primary"
            size="small"
            :loading="regeneratingId === item.projectId"
            @click="handleRegenerate(item.projectId)"
          >
            {{ item.generateStatus === 1 ? '重新生成项目经历' : '立即生成' }}
          </el-button>
        </div>

        <div v-if="item.projectTechStack" class="exp-tech">
          <span v-for="(t, i) in splitTags(item.projectTechStack)" :key="i" class="exp-tech-tag">{{ t }}</span>
        </div>

        <el-descriptions :column="1" border class="exp-desc">
          <el-descriptions-item v-if="item.projectDesc" label="项目描述">{{ item.projectDesc }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="item.generateStatus === 1">
          <div class="exp-section">
            <div class="exp-section-title">个人职责</div>
            <ul v-if="splitLines(item.responsibilities).length" class="exp-list-items">
              <li v-for="(r, i) in splitLines(item.responsibilities)" :key="i">{{ r }}</li>
            </ul>
            <p v-else class="exp-empty">暂无</p>
          </div>
        </template>
        <el-empty v-else description="尚未生成项目经历，点击「立即生成」" :image-size="60" />
      </el-card>

      <el-empty v-if="!loading && !list.length" description="暂无项目，请先在文件管理中上传并解析项目源文件" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { projectExperienceApi } from '@/api'
import type { ProjectExperienceVO } from '@/types'

const list = ref<ProjectExperienceVO[]>([])
const loading = ref(false)
const regeneratingId = ref<number | null>(null)

function splitTags(s: string): string[] {
  if (!s) return []
  return s.split(/[、，,;；]+/).map((t) => t.trim()).filter(Boolean)
}

function splitLines(s: string): string[] {
  if (!s) return []
  return s.split(/[；;]+|\n+/).map((t) => t.trim()).filter(Boolean)
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await projectExperienceApi.list()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleRegenerate(projectId: number) {
  regeneratingId.value = projectId
  try {
    const res: any = await projectExperienceApi.regenerate(projectId)
    const updated = res.data
    const idx = list.value.findIndex((e) => e.projectId === projectId)
    if (idx >= 0 && updated) {
      list.value[idx] = updated
    }
    ElMessage.success('项目经历生成成功')
  } catch {
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    regeneratingId.value = null
  }
}

onMounted(fetchList)
</script>

<style scoped>
.project-exp-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1f2329;
}
.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #8a9099;
}
.exp-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.exp-card {
  border-radius: 10px;
}
.exp-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.exp-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.exp-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}
.exp-tech {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.exp-tech-tag {
  font-size: 12px;
  color: #1d4ed8;
  background: #eaf1ff;
  border: 1px solid #d4e2ff;
  border-radius: 12px;
  padding: 4px 12px;
}
.exp-desc {
  margin-bottom: 14px;
}
.exp-section {
  margin-bottom: 14px;
}
.exp-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 6px;
  padding-left: 10px;
  border-left: 3px solid #2563eb;
}
.exp-list-items {
  margin: 0;
  padding-left: 20px;
}
.exp-list-items li {
  font-size: 14px;
  line-height: 1.8;
  color: #4b5563;
  margin-bottom: 4px;
}
.exp-empty {
  margin: 0;
  font-size: 14px;
  color: #b0b6bd;
}
</style>
