<template>
  <div ref="chartRef" :style="{ width, height }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import type { ProjectStarMapVO } from '@/types'

interface Props {
  projectData: ProjectStarMapVO[]
  width?: string
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: '100%',
  height: '500px'
})

const emit = defineEmits<{
  (e: 'project-click', projectId: number): void
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const DEEP_TOOLTIP = {
  backgroundColor: 'rgba(10,18,40,0.88)',
  borderColor: 'rgba(120,170,255,0.35)',
  textStyle: { color: '#dbe8ff' },
  extraCssText:
    'backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); border-radius: 10px; box-shadow: 0 4px 24px rgba(0,0,0,0.5);'
}

function masteryBar(score: number): string {
  const n = Math.max(0, Math.min(10, Math.round(score / 10)))
  return '▰'.repeat(n) + '▱'.repeat(10 - n)
}

// 与知识星图同一套恒星语义：正常蓝白 / 薄弱红超巨星
function projectColor(p: ProjectStarMapVO) {
  if (p.weakFlag === 1) {
    return {
      color: {
        type: 'radial', x: 0.35, y: 0.3, r: 0.9,
        colorStops: [
          { offset: 0, color: '#ffe0c2' },
          { offset: 0.45, color: '#ff9a4d' },
          { offset: 1, color: '#e84f2b' }
        ]
      },
      shadowColor: 'rgba(255,122,69,0.95)',
      shadowBlur: 34,
      labelColor: '#ffd0b0'
    }
  }
  return {
    color: {
      type: 'radial', x: 0.35, y: 0.3, r: 0.9,
      colorStops: [
        { offset: 0, color: '#eaf6ff' },
        { offset: 0.45, color: '#6fb7ff' },
        { offset: 1, color: '#2b6de0' }
      ]
    },
    shadowColor: 'rgba(111,183,255,0.9)',
    shadowBlur: 24,
    labelColor: '#cfe4ff'
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)

  const count = props.projectData.length
  const nodes = props.projectData.map((p, i) => {
    const angle = (2 * Math.PI * i) / Math.max(count, 1)
    const radius = 200
    const c = projectColor(p)
    const size = Math.max(22, Math.min(64, 24 + (p.knowledgeCount || 0) * 2))
    return {
      id: p.id,
      name: p.projectName,
      x: Math.cos(angle) * radius,
      y: Math.sin(angle) * radius,
      value: p.knowledgeCount || 1,
      symbolSize: size,
      itemStyle: {
        color: c.color,
        shadowColor: c.shadowColor,
        shadowBlur: c.shadowBlur,
        ...((p.masteryScore || 0) >= 85 ? { borderColor: '#ffffff', borderWidth: 1.5 } : {})
      },
      label: {
        show: true,
        fontSize: 13,
        fontWeight: 'bold',
        color: c.labelColor,
        textShadowColor: 'rgba(80,140,255,0.8)',
        textShadowBlur: 8
      },
      projectInfo: p
    }
  })

  // 后端无项目间共享领域数据，不臆造连线，仅星点 + 星光 label
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      ...DEEP_TOOLTIP,
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const p = params.data.projectInfo as ProjectStarMapVO
          return `<span style="font-size:15px;font-weight:bold;color:#eaf6ff">${params.name}</span><br/>知识点: ${p?.knowledgeCount ?? 0}<br/>掌握度: ${masteryBar(p?.masteryScore ?? 0)} ${p?.masteryScore ?? 0}<br/>状态: ${p?.weakFlag === 1 ? '薄弱' : '良好'}`
        }
        return ''
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: nodes,
        links: [],
        force: {
          edgeLength: 180,
          repulsion: 500,
          gravity: 0.1
        },
        roam: true,
        labelLayout: { hideOverlap: true },
        label: { position: 'right', formatter: '{b}' },
        emphasis: {
          focus: 'adjacency',
          label: { show: true }
        },
        blur: {
          itemStyle: { opacity: 0.06 }
        }
      }
    ]
  }

  chart.setOption(option)

  chart.on('click', (params: any) => {
    if (params.dataType === 'node') {
      emit('project-click', params.data.id)
    }
  })
}

function handleResize() {
  chart?.resize()
}

watch(
  () => props.projectData,
  () => {
    chart?.dispose()
    chart = null
    initChart()
  },
  { deep: true }
)

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>
