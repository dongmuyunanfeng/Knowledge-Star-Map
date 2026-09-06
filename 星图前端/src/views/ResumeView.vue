<template>
  <div class="resume-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">简历生成</h2>
        <p class="page-subtitle">填写目标岗位与求职需求，自动筛选最相关的知识与项目生成简历</p>
      </div>
      <div class="header-actions">
        <el-button v-if="resumeData" @click="handleSaveTemplate">保存为模板</el-button>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-field">
        <span class="toolbar-label">目标岗位</span>
        <el-input
          v-model="targetNeed"
          placeholder="如：Java 后端开发、网络安全工程师、前端开发…"
          clearable
          @keyup.enter="handleGenerate"
        />
      </div>
      <div class="toolbar-field toolbar-field-grow">
        <span class="toolbar-label">求职需求</span>
        <el-input
          v-model="requirement"
          placeholder="如：想突出项目经验、找实习、偏重后端方向…"
          clearable
          @keyup.enter="handleGenerate"
        />
      </div>
      <el-button type="primary" :loading="loading" @click="handleGenerate">生成</el-button>
    </div>

    <div class="paper-wrap" v-loading="detailLoading">
      <div class="paper">
        <template v-if="resumeData">
          <div class="paper-head">
            <div class="paper-title">个人简历</div>
            <div class="paper-subtitle">{{ targetNeed || '综合简历' }}</div>
          </div>

          <section class="block">
            <h3 class="block-title">个人简介</h3>
            <p class="summary-text">
              <template v-if="resumeData.resumeSummary">
                <template v-for="(seg, i) in parseMarkdownBold(resumeData.resumeSummary)" :key="i">
                  <strong v-if="seg.strong">{{ seg.text }}</strong>
                  <template v-else>{{ seg.text }}</template>
                </template>
              </template>
              <template v-else>暂无</template>
            </p>
          </section>

          <section class="block">
            <h3 class="block-title">技术栈</h3>
            <div class="tech-tags">
              <span v-for="(t, i) in techTags" :key="i" class="tech-tag">{{ t }}</span>
              <span v-if="!techTags.length" class="placeholder">暂无</span>
            </div>
          </section>

          <section class="block">
            <h3 class="block-title">专业技能</h3>
            <ul v-if="skillItems.length" class="skill-list">
              <li v-for="(s, i) in skillItems" :key="i">
                <template v-for="(seg, j) in parseMarkdownBold(s)" :key="j">
                  <strong v-if="seg.strong">{{ seg.text }}</strong>
                  <template v-else>{{ seg.text }}</template>
                </template>
              </li>
            </ul>
            <p v-else class="placeholder">暂无</p>
          </section>

          <section class="block">
            <h3 class="block-title">项目亮点</h3>
            <div v-if="highlightGroups.length" class="highlight-groups">
              <div v-for="(g, i) in highlightGroups" :key="i" class="highlight-group">
                <div v-if="g.project" class="highlight-project">{{ g.project }}</div>
                <ul class="highlight-list">
                  <li v-for="(h, j) in g.items" :key="j">
                    <template v-for="(seg, k) in parseMarkdownBold(h)" :key="k">
                      <strong v-if="seg.strong">{{ seg.text }}</strong>
                      <template v-else>{{ seg.text }}</template>
                    </template>
                  </li>
                </ul>
              </div>
            </div>
            <p v-else class="placeholder">暂无</p>
          </section>
        </template>
        <el-empty v-else description="暂无简历素材，填写信息后点击生成" :image-size="120" />
      </div>
    </div>

    <el-card shadow="never" v-loading="templatesLoading" class="tpl-card">
      <template #header>
        <span class="tpl-title">已保存的模板</span>
      </template>
      <el-table :data="templates" size="small" v-if="templates.length">
        <el-table-column prop="templateName" label="名称" min-width="180" />
        <el-table-column prop="createTime" label="保存时间" width="180" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewTemplate(row)">查看</el-button>
            <el-button link type="danger" @click="handleDeleteTemplate(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无保存的模板" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resumeApi } from '@/api'
import { streamPost, type SseHandlers } from '@/utils/sse'

const resumeData = ref<any>(null)
const targetNeed = ref('')
const requirement = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const templates = ref<any[]>([])
const templatesLoading = ref(false)

const techTags = computed(() => {
  const s = (resumeData.value?.techStack || '').trim()
  if (!s) return []
  return s.split(/[、，,;；]+/).map((t: string) => t.trim()).filter(Boolean)
})

type TextSeg = { text: string; strong: boolean }

function parseMarkdownBold(text: string): TextSeg[] {
  if (!text) return []
  return text.split(/\*\*(.+?)\*\*/g).map((part, i) => ({
    text: part,
    strong: i % 2 === 1
  }))
}

const skillItems = computed(() => {
  const s = (resumeData.value?.skillDesc || '').trim()
  if (!s) return []
  return s.split(/[\n；;]+/).map((t: string) => t.trim()).filter(Boolean)
})

const highlightGroups = computed(() => {
  const s = (resumeData.value?.projectHighlights || '').trim()
  if (!s) return []
  return s
    .split(/\n+/)
    .map((line: string) => line.trim())
    .filter(Boolean)
    .map((line: string) => {
      const idx = line.indexOf('：')
      if (idx > 0) {
        const project = line.slice(0, idx).trim()
        const items = line
          .slice(idx + 1)
          .split(/[；;。]+/)
          .map((t: string) => t.trim())
          .filter(Boolean)
        return { project, items }
      }
      return { project: '', items: [line] }
    })
})

async function fetchResume() {
  detailLoading.value = true
  try {
    const res: any = await resumeApi.get()
    resumeData.value = res.data
  } catch {
    resumeData.value = null
  } finally {
    detailLoading.value = false
  }
}

async function fetchTemplates() {
  templatesLoading.value = true
  try {
    const res: any = await resumeApi.listTemplates()
    templates.value = res.data || []
  } finally {
    templatesLoading.value = false
  }
}

async function handleGenerate() {
  loading.value = true
  let progressMsg: any = null
  const handlers: SseHandlers = {
    onProgress: (message) => {
      progressMsg?.close()
      progressMsg = ElMessage({ message, type: 'info', duration: 0 })
    },
    onDone: (data) => {
      progressMsg?.close()
      resumeData.value = data
      ElMessage.success('简历素材生成成功')
    },
    onError: (payload) => {
      progressMsg?.close()
      ElMessage.error(payload?.message || '简历生成失败')
    }
  }
  try {
    await streamPost('/resume/generate/stream', {
      forceRegenerate: true,
      targetNeed: targetNeed.value.trim(),
      requirement: requirement.value.trim()
    }, handlers)
  } catch {
    progressMsg?.close()
    ElMessage.error('简历生成失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function handleSaveTemplate() {
  const { value } = await ElMessageBox.prompt('请输入模板名称', '保存为模板', {
    inputValue: '简历模板 ' + new Date().toLocaleDateString(),
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value || !value.trim()) return
  await resumeApi.saveTemplate({
    templateName: value.trim(),
    techStack: resumeData.value?.techStack,
    skillDesc: resumeData.value?.skillDesc,
    projectHighlights: resumeData.value?.projectHighlights,
    resumeSummary: resumeData.value?.resumeSummary
  })
  ElMessage.success('保存成功')
  fetchTemplates()
}

function handleViewTemplate(row: any) {
  resumeData.value = { ...row }
  ElMessage.success('已加载模板：' + row.templateName)
}

async function handleDeleteTemplate(id: number) {
  await ElMessageBox.confirm('确定删除该模板？', '提示', { type: 'warning' })
  await resumeApi.deleteTemplate(id)
  ElMessage.success('删除成功')
  fetchTemplates()
}

onMounted(() => {
  fetchResume()
  fetchTemplates()
})
</script>

<style scoped>
.resume-page {
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
.header-actions {
  display: flex;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #ffffff;
  border-radius: 10px;
  padding: 16px 18px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
.toolbar-field {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}
.toolbar-field-grow {
  flex: 1.4;
}
.toolbar-label {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

/* A4 纸张：真实简历风 */
.paper-wrap {
  display: flex;
  justify-content: center;
}
.paper {
  width: 100%;
  max-width: 794px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 56px 64px;
  min-height: 1123px;
}
.paper-head {
  text-align: center;
  margin-bottom: 36px;
  padding-bottom: 24px;
  border-bottom: 2px solid #1f2937;
}
.paper-title {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 10px;
  color: #111827;
}
.paper-subtitle {
  margin-top: 12px;
  font-size: 14px;
  color: #6b7280;
  letter-spacing: 2px;
}

.block {
  margin-bottom: 30px;
}
.block:last-child {
  margin-bottom: 0;
}
.block-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #111827;
  padding-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
}

.summary-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.95;
  color: #374151;
  text-align: justify;
}
.summary-text strong,
.skill-list strong,
.highlight-list strong {
  font-weight: 700;
  color: #111827;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tech-tag {
  font-size: 13px;
  color: #374151;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 4px 10px;
}

.skill-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.skill-list li {
  position: relative;
  padding-left: 16px;
  font-size: 14px;
  line-height: 1.9;
  color: #374151;
  margin-bottom: 8px;
}
.skill-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.75em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #9ca3af;
}

.highlight-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.highlight-project {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 6px;
}
.highlight-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.highlight-list li {
  position: relative;
  padding-left: 16px;
  font-size: 14px;
  line-height: 1.9;
  color: #374151;
  margin-bottom: 4px;
}
.highlight-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.75em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #9ca3af;
}

.placeholder {
  font-size: 14px;
  color: #b0b6bd;
}

.tpl-title {
  font-size: 16px;
  font-weight: 600;
}
</style>
