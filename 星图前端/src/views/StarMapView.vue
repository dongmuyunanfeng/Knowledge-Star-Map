<template>
  <div class="star-map-page">
    <div class="cosmos-bg" aria-hidden="true">
      <div class="twinkle" v-for="s in twinkles" :key="s.id" :style="s.style"></div>
      <div class="meteor"></div>
      <div class="meteor meteor-2"></div>
    </div>

    <div class="page-header">
      <h2 class="page-title">知识星图</h2>
    </div>

    <el-tabs v-model="activeTab" class="deep-tabs">
      <el-tab-pane label="知识星图" name="knowledge">
        <div class="stats-bar">
          <el-card shadow="never" class="stat-item glass-card">
            <div class="stat-value">{{ stats.totalKnowledgeCount }}</div>
            <div class="stat-label">知识点总数</div>
          </el-card>
          <el-card shadow="never" class="stat-item glass-card">
            <div class="stat-value">{{ stats.averageMasteryScore?.toFixed(1) }}</div>
            <div class="stat-label">平均掌握度</div>
          </el-card>
          <el-card shadow="never" class="stat-item glass-card">
            <div class="stat-value">{{ stats.domains?.length || 0 }}</div>
            <div class="stat-label">知识领域数</div>
          </el-card>
          <el-button class="deep-btn" @click="handleRecalculate">重新计算布局</el-button>
        </div>

        <div class="chart-container glass-card">
          <KnowledgeStarMapEchart
            :domain-data="domainData"
            :knowledge-data="knowledgeData"
            height="500px"
            @domain-click="handleDomainClick"
            @knowledge-click="handleKnowledgeClick"
          />
          <div class="chart-hint">滚轮缩放 · 拖拽平移 · 点击领域进入检索</div>
        </div>

        <div class="domain-list glass-card">
          <h3>知识领域</h3>
          <el-table :data="domainData" class="deep-table">
            <el-table-column prop="domainName" label="领域名称" />
            <el-table-column prop="knowledgeCount" label="知识点数" width="120" />
            <el-table-column prop="masteryScore" label="平均掌握度" width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.masteryScore"
                  :color="row.weakFlag === 1 ? '#ff7a45' : '#6fb7ff'"
                  :stroke-width="8"
                />
              </template>
            </el-table-column>
            <el-table-column prop="weakFlag" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.weakFlag === 1 ? 'danger' : 'success'" size="small">
                  {{ row.weakFlag === 1 ? '薄弱' : '良好' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="项目星图" name="project">
        <div class="chart-container glass-card">
          <ProjectStarMapEchart
            :project-data="projectData"
            height="500px"
            @project-click="handleProjectClick"
          />
          <div class="chart-hint">滚轮缩放 · 拖拽平移 · 点击项目进入检索</div>
        </div>

        <div class="domain-list glass-card">
          <h3>项目列表</h3>
          <el-table :data="projectData" class="deep-table">
            <el-table-column prop="projectName" label="项目名称" />
            <el-table-column prop="knowledgeCount" label="知识点数" width="120" />
            <el-table-column prop="masteryScore" label="平均掌握度" width="140">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.masteryScore"
                  :color="row.weakFlag === 1 ? '#ff7a45' : '#6fb7ff'"
                  :stroke-width="8"
                />
              </template>
            </el-table-column>
            <el-table-column prop="weakFlag" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.weakFlag === 1 ? 'danger' : 'success'" size="small">
                  {{ row.weakFlag === 1 ? '薄弱' : '良好' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { starMapApi, knowledgeApi } from '@/api'
import KnowledgeStarMapEchart from '@/components/starmap/KnowledgeStarMapEchart.vue'
import ProjectStarMapEchart from '@/components/starmap/ProjectStarMapEchart.vue'
import type { KnowledgeStarMapVO, KnowledgeInfoVO, ProjectStarMapVO } from '@/types'

const activeTab = ref('knowledge')
const stats = ref({ totalKnowledgeCount: 0, averageMasteryScore: 0, domains: [] as KnowledgeStarMapVO[] })
const domainData = ref<KnowledgeStarMapVO[]>([])
const knowledgeData = ref<KnowledgeInfoVO[]>([])
const projectData = ref<ProjectStarMapVO[]>([])

// 闪烁星：确定性伪随机布置（种子可复现），仅 transform/opacity 动画
const twinkles = Array.from({ length: 16 }, (_, i) => {
  const rand = (n: number) => {
    const x = Math.sin(i * 127.1 + n * 311.7) * 43758.5453
    return x - Math.floor(x)
  }
  return {
    id: i,
    style: {
      left: `${(rand(1) * 100).toFixed(2)}%`,
      top: `${(rand(2) * 100).toFixed(2)}%`,
      width: `${(1.5 + rand(3) * 1.5).toFixed(1)}px`,
      height: `${(1.5 + rand(3) * 1.5).toFixed(1)}px`,
      animationDelay: `${(rand(4) * 4).toFixed(2)}s`,
      animationDuration: `${(2.5 + rand(5) * 2.5).toFixed(2)}s`
    }
  }
})

async function fetchData() {
  const [statsRes, domainsRes, knowledgeRes]: any[] = await Promise.all([
    starMapApi.getStats(),
    starMapApi.listDomains(),
    knowledgeApi.list({ page: 1, pageSize: 200 })
  ])
  stats.value = statsRes.data
  domainData.value = domainsRes.data
  knowledgeData.value = knowledgeRes.data?.list || []
}

async function fetchProjects() {
  const res: any = await starMapApi.listProjects()
  projectData.value = res.data || []
}

async function handleRecalculate() {
  await starMapApi.recalculate()
  ElMessage.success('星图布局已重新计算')
  fetchData()
}

function handleDomainClick(domainName: string) {
  const encoded = encodeURIComponent(JSON.stringify({ domain: domainName }))
  window.open(`/search?filter=${encoded}`, '_self')
}

function handleKnowledgeClick(knowledgeId: number) {
  const encoded = encodeURIComponent(JSON.stringify({ knowledgeId }))
  window.open(`/search?filter=${encoded}`, '_self')
}

function handleProjectClick(projectId: number) {
  const encoded = encodeURIComponent(JSON.stringify({ projectId }))
  window.open(`/search?filter=${encoded}`, '_self')
}

watch(activeTab, (tab) => {
  if (tab === 'project' && projectData.value.length === 0) {
    fetchProjects()
  }
})

onMounted(fetchData)
</script>

<style scoped>
.star-map-page {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 12px;
  margin: -12px;
  width: calc(100% + 24px);
  overflow: hidden;
  border-radius: 12px;
  background:
    radial-gradient(ellipse at 55% 35%, #10234d 0%, #0a1630 52%, #04070f 100%);
}

/* ===== 动效叠加（限定本页内，不遮挡 header/侧边栏） ===== */
.cosmos-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  border-radius: 12px;
}
.twinkle {
  position: absolute;
  border-radius: 50%;
  background: #eaf6ff;
  box-shadow: 0 0 6px 1px rgba(200,230,255,0.8);
  animation: twinkle 3s ease-in-out infinite;
  will-change: transform, opacity;
}
@keyframes twinkle {
  0%, 100% { opacity: 0.2; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}
.meteor {
  position: absolute;
  left: 12%;
  top: -6%;
  width: 1.5px;
  height: 120px;
  background: linear-gradient(to bottom, rgba(255,255,255,0), rgba(255,255,255,0.9));
  transform: rotate(38deg);
  opacity: 0;
  animation: meteor 11s linear infinite;
  will-change: transform, opacity;
}
@keyframes meteor {
  0%, 88% { opacity: 0; transform: rotate(38deg) translate3d(0, 0, 0); }
  90% { opacity: 0.9; }
  100% { opacity: 0; transform: rotate(38deg) translate3d(-32vw, 46vh, 0); }
}
.meteor-2 {
  left: 68%;
  top: -4%;
  height: 90px;
  animation-duration: 13s;
  animation-delay: 6s;
  animation-name: meteor-alt;
}
@keyframes meteor-alt {
  0%, 90% { opacity: 0; transform: rotate(38deg) translate3d(0, 0, 0); }
  92% { opacity: 0.7; }
  100% { opacity: 0; transform: rotate(38deg) translate3d(-26vw, 38vh, 0); }
}

/* ===== 内容层 ===== */
.page-header { position: relative; z-index: 1; }
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #eaf6ff;
  text-shadow: 0 0 12px rgba(111,183,255,0.6);
}

.deep-tabs { position: relative; z-index: 1; }
.deep-tabs :deep(.el-tabs__item) {
  color: #8fb8e8;
}
.deep-tabs :deep(.el-tabs__item.is-active) {
  color: #eaf6ff;
  text-shadow: 0 0 10px rgba(111,183,255,0.7);
}
.deep-tabs :deep(.el-tabs__item:hover) {
  color: #cfe4ff;
}
.deep-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: rgba(120,170,255,0.18);
}
.deep-tabs :deep(.el-tabs__active-bar) {
  background-color: #6fb7ff;
  box-shadow: 0 0 8px rgba(111,183,255,0.8);
}

.glass-card {
  background: rgba(8,15,35,0.72) !important;
  border: 1px solid rgba(120,170,255,0.18);
  border-radius: 16px;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: inset 0 0 40px rgba(20,40,90,0.35), 0 4px 24px rgba(0,0,0,0.4);
}

.stats-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.stat-item {
  flex: 1;
  text-align: center;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  background: linear-gradient(135deg, #eaf6ff 10%, #6fb7ff 90%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: stat-pulse 0.6s ease-out;
}
.stat-label {
  font-size: 13px;
  color: #8fb8e8;
  margin-top: 4px;
}
@keyframes stat-pulse {
  0% { filter: brightness(1.8); }
  100% { filter: brightness(1); }
}

.deep-btn {
  align-self: center;
  background: transparent;
  border: 1px solid rgba(120,170,255,0.35);
  color: #cfe4ff;
  border-radius: 10px;
  transition: box-shadow 0.3s, border-color 0.3s;
}
.deep-btn:hover,
.deep-btn:focus {
  background: rgba(111,183,255,0.12);
  border-color: rgba(120,170,255,0.6);
  color: #eaf6ff;
  box-shadow: 0 0 12px rgba(111,183,255,0.35);
}

.chart-container {
  position: relative;
  padding: 16px;
  margin-bottom: 16px;
  background: url('/galaxy-bg.png') center / cover no-repeat !important;
  border: 1px solid rgba(120,170,255,0.25);
}
/* 图内暗角：让图上文字与节点更清晰 */
.chart-container::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 16px;
  pointer-events: none;
  background: radial-gradient(ellipse at 50% 45%, transparent 50%, rgba(4,7,15,0.5) 100%);
}
.chart-container :deep(.el-card__body) {
  padding: 0;
}
.chart-hint {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: rgba(143,184,232,0.65);
  letter-spacing: 0.08em;
}

.domain-list {
  padding: 16px;
}
.domain-list h3 {
  margin-bottom: 12px;
  font-size: 16px;
  color: #cfe4ff;
}

/* ===== 表格深色化 ===== */
.deep-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(20,40,90,0.45);
  --el-table-header-text-color: #9fc3f0;
  --el-table-text-color: #cfe4ff;
  --el-table-border-color: rgba(120,170,255,0.14);
  --el-table-row-hover-bg-color: rgba(111,183,255,0.08);
}
.deep-table :deep(.el-table__row--striped td.el-table__cell) {
  background: rgba(255,255,255,0.03);
}

/* Element 深色弹层（tag/进度条内文字可读性） */
.domain-list :deep(.el-tag--danger) {
  background: rgba(232,79,43,0.18);
  border-color: rgba(255,122,69,0.4);
  color: #ffb89a;
}
.domain-list :deep(.el-tag--success) {
  background: rgba(111,183,255,0.12);
  border-color: rgba(111,183,255,0.4);
  color: #bfe3ff;
}
</style>
