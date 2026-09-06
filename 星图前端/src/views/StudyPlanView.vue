<template>
  <div class="study-plan-page">
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">学习规划</h2>
        <p class="page-subtitle">共 {{ plans.length }} 个学习计划</p>
      </div>
      <el-button type="primary" @click="handleCreate">+ 新建计划</el-button>
    </div>

    <div v-loading="loading" class="plan-grid">
      <el-card
        v-for="plan in plans"
        :key="plan.id"
        class="plan-card"
        shadow="hover"
        :class="{ 'is-finished': plan.finishStatus === 1 }"
      >
        <div class="card-head">
          <h3 class="card-title" :title="plan.planTitle">{{ plan.planTitle }}</h3>
          <el-tag
            :type="plan.finishStatus === 1 ? 'success' : 'info'"
            size="small"
            effect="light"
            class="status-tag"
          >
            {{ plan.finishStatus === 1 ? '已完成' : '进行中' }}
          </el-tag>
        </div>

        <div class="card-tags">
          <el-tag :type="planTypeTag(plan.planType)" size="small">{{ planTypeLabel(plan.planType) }}</el-tag>
          <el-tag :type="priorityTag(plan.priority)" size="small">优先级{{ priorityLabel(plan.priority) }}</el-tag>
        </div>

        <div class="card-progress">
          <el-progress
            :percentage="Math.round(Number(plan.progressRate || 0))"
            :color="plan.finishStatus === 1 ? '#67c23a' : '#409eff'"
            :stroke-width="8"
          />
        </div>

        <div class="card-need" :title="plan.targetNeed">{{ plan.targetNeed }}</div>

        <div class="card-foot">
          <span class="card-time">{{ plan.createTime }}</span>
          <div class="card-actions">
            <el-button link type="primary" size="small" @click="handleView(plan)">查看</el-button>
            <el-button link type="primary" size="small" @click="handleRefreshProgress(plan.id)">标记完成</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(plan)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(plan.id)">删除</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <el-empty v-if="!loading && !plans.length" description="暂无学习计划，点击右上角新建" />

    <el-dialog v-model="editVisible" :title="isEdit ? '编辑计划' : '新建计划'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="计划标题" prop="planTitle">
          <el-input v-model="form.planTitle" placeholder="请输入计划标题" />
        </el-form-item>
        <el-form-item label="计划类型" prop="planType">
          <el-select v-model="form.planType" style="width: 100%">
            <el-option label="短期（7天）" :value="1" />
            <el-option label="中期（30天）" :value="2" />
            <el-option label="长期（90天）" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标需求" prop="targetNeed">
          <el-input v-model="form.targetNeed" type="textarea" :rows="3" placeholder="描述你的学习目标，10-200字" />
        </el-form-item>
        <el-form-item label="计划描述" prop="planDesc">
          <el-input v-model="form.planDesc" type="textarea" :rows="3" placeholder="补充说明（可选）" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option label="高" :value="1" />
            <el-option label="中" :value="2" />
            <el-option label="低" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="计划详情" width="760px" top="5vh">
      <template v-if="currentPlan">
        <div class="plan-detail-head">
          <div class="plan-title-row">
            <h3 class="detail-title">{{ currentPlan.planTitle }}</h3>
            <el-tag :type="planTypeTag(currentPlan.planType)" size="small">{{ planTypeLabel(currentPlan.planType) }}</el-tag>
            <el-tag :type="priorityTag(currentPlan.priority)" size="small">优先级{{ priorityLabel(currentPlan.priority) }}</el-tag>
            <el-tag :type="currentPlan.finishStatus === 1 ? 'success' : 'info'" size="small">
              {{ currentPlan.finishStatus === 1 ? '已完成' : '进行中' }}
            </el-tag>
          </div>
          <div class="plan-meta">
            <span>目标需求：{{ currentPlan.targetNeed }}</span>
            <span v-if="currentPlan.progressRate != null" class="meta-progress">
              进度 {{ Math.round(Number(currentPlan.progressRate || 0)) }}%
            </span>
          </div>
          <div v-if="currentPlan.planDesc" class="plan-desc">{{ currentPlan.planDesc }}</div>
        </div>

        <div class="plan-knowledge-list">
          <el-timeline v-if="(currentPlan.waitKnowledge || []).length">
            <el-timeline-item
              v-for="(item, idx) in currentPlan.waitKnowledge"
              :key="idx"
              :timestamp="item.schedule"
              placement="top"
              :type="idx === 0 ? 'primary' : ''"
            >
              <div class="pk-item">
                <div class="pk-head">
                  <span class="pk-name">{{ item.name }}</span>
                  <el-tag v-if="item.tag" size="small" type="info">{{ item.tag }}</el-tag>
                </div>
                <div v-if="item.learnContent" class="pk-content">{{ item.learnContent }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无待学知识点" :image-size="60" />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { studyPlanApi } from '@/api'
import { streamPost, type SseHandlers } from '@/utils/sse'
import type { StudyPlanCreateDTO, StudyPlanUpdateDTO } from '@/types'

const plans = ref<any[]>([])
const loading = ref(false)
const editVisible = ref(false)
const detailVisible = ref(false)
const isEdit = ref(false)
const currentPlan = ref<any>(null)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  planTitle: '',
  planType: 2 as number,
  targetNeed: '',
  planDesc: '',
  priority: 2 as number
})

const rules: FormRules = {
  planTitle: [{ required: true, message: '请输入计划标题', trigger: 'blur' }],
  planType: [{ required: true, message: '请选择计划类型', trigger: 'change' }],
  targetNeed: [
    { required: true, message: '请输入目标需求', trigger: 'blur' },
    { min: 10, max: 200, message: '目标需求长度10-200字', trigger: 'blur' }
  ]
}

function planTypeLabel(type: number) {
  const map: Record<number, string> = { 1: '短期', 2: '中期', 3: '长期' }
  return map[type] || '未知'
}
function planTypeTag(type: number) {
  const map: Record<number, string> = { 1: 'success', 2: 'primary', 3: 'warning' }
  return map[type] || 'info'
}
function priorityLabel(p: number) {
  const map: Record<number, string> = { 1: '高', 2: '中', 3: '低' }
  return map[p] || '中'
}
function priorityTag(p: number) {
  const map: Record<number, string> = { 1: 'danger', 2: 'warning', 3: 'info' }
  return map[p] || 'info'
}

async function fetchPlans() {
  loading.value = true
  try {
    const res: any = await studyPlanApi.list()
    plans.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { planTitle: '', planType: 2, targetNeed: '', planDesc: '', priority: 2 })
  editVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    planTitle: row.planTitle,
    planType: row.planType,
    targetNeed: row.targetNeed,
    planDesc: row.planDesc || '',
    priority: row.priority
  })
  editVisible.value = true
}

function handleView(row: any) {
  currentPlan.value = row
  detailVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true

  if (isEdit.value && editingId.value) {
    try {
      const updateDto: StudyPlanUpdateDTO = {
        planTitle: form.planTitle,
        planDesc: form.planDesc,
        priority: form.priority
      }
      await studyPlanApi.update(editingId.value, updateDto)
      ElMessage.success('更新成功')
      editVisible.value = false
      fetchPlans()
    } finally {
      submitting.value = false
    }
    return
  }

  const createDto: StudyPlanCreateDTO = {
    planTitle: form.planTitle,
    planType: form.planType,
    targetNeed: form.targetNeed,
    planDesc: form.planDesc,
    priority: form.priority
  }

  let progressMsg: any = null
  const handlers: SseHandlers = {
    onProgress: (message) => {
      progressMsg?.close()
      progressMsg = ElMessage({ message, type: 'info', duration: 0 })
    },
    onDone: () => {
      progressMsg?.close()
      ElMessage.success('学习规划生成成功')
      editVisible.value = false
      fetchPlans()
    },
    onError: (payload) => {
      progressMsg?.close()
      ElMessage.error(payload?.message || '学习规划生成失败')
    }
  }

  try {
    await streamPost('/study-plans/generate', createDto, handlers)
  } catch {
    progressMsg?.close()
    ElMessage.error('学习规划生成失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该学习计划？', '提示', { type: 'warning' })
  await studyPlanApi.delete(id)
  ElMessage.success('删除成功')
  fetchPlans()
}

async function handleRefreshProgress(id: number) {
  try {
    await studyPlanApi.complete(id)
    ElMessage.success('已标记完成')
    fetchPlans()
  } catch {
    ElMessage.error('标记完成失败，请稍后重试')
  }
}

onMounted(fetchPlans)
</script>

<style scoped>
.study-plan-page { display: flex; flex-direction: column; gap: 20px; }

.page-header { display: flex; justify-content: space-between; align-items: center; }
.page-header-left { display: flex; align-items: baseline; gap: 12px; }
.page-title { margin: 0; font-size: 22px; font-weight: 700; color: #303133; }
.page-subtitle { margin: 0; font-size: 13px; color: #909399; }

.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.plan-card { transition: transform 0.2s ease, box-shadow 0.2s ease; }
.plan-card:hover { transform: translateY(-2px); }
.plan-card.is-finished { border-left: 3px solid #67c23a; }

.card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}
.status-tag { flex-shrink: 0; }

.card-tags { display: flex; gap: 6px; margin-top: 12px; }

.card-progress { margin-top: 14px; }

.card-need {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 42px;
}

.card-foot {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.card-time { font-size: 12px; color: #c0c4cc; flex-shrink: 0; }
.card-actions { display: flex; align-items: center; gap: 2px; flex-wrap: nowrap; }

.plan-detail-head { margin-bottom: 18px; }
.plan-title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.detail-title { font-size: 18px; font-weight: 600; margin: 0; }
.plan-meta { display: flex; align-items: center; gap: 12px; font-size: 13px; color: #606266; margin-bottom: 8px; flex-wrap: wrap; }
.meta-progress { color: #409eff; font-weight: 600; }
.plan-desc { font-size: 13px; color: #909399; line-height: 1.6; padding: 10px 12px; background: #f5f7fa; border-radius: 6px; }

.plan-knowledge-list { max-height: 58vh; overflow-y: auto; padding: 4px 4px 4px 0; }
.pk-item { padding: 6px 0; }
.pk-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.pk-name { font-weight: 600; font-size: 14px; color: #303133; }
.pk-content { font-size: 13px; color: #606266; line-height: 1.6; }
</style>
