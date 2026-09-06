<template>
  <div ref="chartRef" :style="{ width, height }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import type { KnowledgeStarMapVO, KnowledgeInfoVO } from '@/types'

interface Props {
  domainData: KnowledgeStarMapVO[]
  knowledgeData?: KnowledgeInfoVO[]
  width?: string
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: '100%',
  height: '500px'
})

const emit = defineEmits<{
  (e: 'domain-click', domainName: string): void
  (e: 'knowledge-click', knowledgeId: number): void
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

// 确定性散列：同一 id 每次渲染角度一致，禁止裸 Math.random
function seedHash(id: number): number {
  let h = id * 2654435761
  h = (h ^ (h >>> 13)) * 1274126177
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295
}

// 后端 JSON 返回小写键 xcoordinate/ycoordinate，此处做归一化
function coordX(d: any): number | null {
  const v = d?.xCoordinate ?? d?.xcoordinate
  return v == null ? null : Number(v)
}
function coordY(d: any): number | null {
  const v = d?.yCoordinate ?? d?.ycoordinate
  return v == null ? null : Number(v)
}

// 领域关联键是 knowledgeDomain（knowledgeTag 是领域下二级分类，用于子星分色）
function computeEdges() {
  const edgeSet = new Set<string>()
  const result: any[] = []
  const data = props.knowledgeData || []
  const domainNames = props.domainData.map((d) => d.domainName)

  if (data.length > 0) {
    // 统计每对领域共现的知识点数量（按知识点两两组合会 O(n²)，直接按领域对计数）
    const pairCount = new Map<string, number>()
    const domainCount = new Map<string, number>()
    data.forEach((k) => {
      const dom = k.knowledgeDomain
      if (!dom) return
      domainCount.set(dom, (domainCount.get(dom) || 0) + 1)
    })
    // 同一知识点不能同时属于两个领域，改用领域间 tag 共享度：同 tag 出现在多个领域则连线
    const tagDomains = new Map<string, Set<string>>()
    data.forEach((k) => {
      if (!k.knowledgeTag) return
      const set = tagDomains.get(k.knowledgeTag) || new Set<string>()
      if (k.knowledgeDomain) set.add(k.knowledgeDomain)
      tagDomains.set(k.knowledgeTag, set)
    })
    tagDomains.forEach((domains) => {
      const arr = [...domains]
      for (let i = 0; i < arr.length; i++) {
        for (let j = i + 1; j < arr.length; j++) {
          const key = [arr[i], arr[j]].sort().join('|')
          pairCount.set(key, (pairCount.get(key) || 0) + 1)
        }
      }
    })

    pairCount.forEach((count, key) => {
      const [a, b] = key.split('|')
      const domainA = props.domainData.find((d) => d.domainName === a)
      const domainB = props.domainData.find((d) => d.domainName === b)
      if (!domainA || !domainB) return
      const maxCount = Math.max(domainA.knowledgeCount, domainB.knowledgeCount) || 1
      const strength = Math.min(1, count / maxCount)
      if (strength >= 0.1) {
        result.push({ source: a, target: b, value: strength })
      }
    })
  }

  if (result.length < domainNames.length * 0.3 && domainNames.length > 1) {
    for (let i = 0; i < domainNames.length; i++) {
      for (let j = i + 1; j < domainNames.length; j++) {
        const exists = result.some(
          (e) =>
            (e.source === domainNames[i] && e.target === domainNames[j]) ||
            (e.source === domainNames[j] && e.target === domainNames[i])
        )
        if (!exists) {
          result.push({ source: domainNames[i], target: domainNames[j], value: 0.05 })
        }
      }
    }
  }

  return result
}

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

// 领域主星样式：正常蓝白 / 薄弱红超巨星 / 高掌握度白高光边
// symbol 用十字衍射尖角图形（pin 参考），亮星特征
function domainColor(d: KnowledgeStarMapVO) {
  if (d.weakFlag === 1) {
    return {
      color: {
        type: 'radial', x: 0.35, y: 0.3, r: 0.9,
        colorStops: [
          { offset: 0, color: '#fff4e5' },
          { offset: 0.25, color: '#ffe0c2' },
          { offset: 0.55, color: '#ff9a4d' },
          { offset: 1, color: '#c93a1a' }
        ]
      },
      shadowColor: 'rgba(255,140,80,1)',
      shadowBlur: 45,
      labelColor: '#ffd0b0'
    }
  }
  return {
    color: {
      type: 'radial', x: 0.35, y: 0.3, r: 0.9,
      colorStops: [
        { offset: 0, color: '#ffffff' },
        { offset: 0.25, color: '#eaf6ff' },
        { offset: 0.55, color: '#6fb7ff' },
        { offset: 1, color: '#1d55c0' }
      ]
    },
    shadowColor: 'rgba(120,180,255,1)',
    shadowBlur: 38,
    labelColor: '#cfe4ff'
  }
}

function domainSymbolSize(d: KnowledgeStarMapVO): number {
  let size = 10 + (d.knowledgeCount || 0) * 0.6 + (d.masteryScore || 0) * 0.08
  size = Math.max(22, Math.min(64, size))
  if ((d.weightFactor || 1) > 1) size *= 1.15
  return size
}

// 子星按 masteryScore 分圈：掌握度越高轨道越近主星；同 tag 同色相
const TAG_HUES = [210, 195, 225, 185, 240, 170, 255, 160, 270, 150]
function tagHue(tag: string): number {
  let h = 0
  for (let i = 0; i < tag.length; i++) h = (h * 31 + tag.charCodeAt(i)) >>> 0
  return TAG_HUES[h % TAG_HUES.length]
}

function buildSatellites(domain: KnowledgeStarMapVO, mainSize: number, mainX: number, mainY: number, list: KnowledgeInfoVO[], heavyScene: boolean) {
  const MAX_SATS = 40
  const sorted = [...list].sort((a, b) => (b.masteryScore || 0) - (a.masteryScore || 0))
  const kept = sorted.slice(0, MAX_SATS)
  const overflow = sorted.length - kept.length
  const cx = mainX
  const cy = mainY
  const weak = domain.weakFlag === 1

  const nodes: any[] = []
  const links: any[] = []

  kept.forEach((k, idx) => {
    const ringCount = 3
    const perRing = Math.ceil(kept.length / ringCount)
    const ring = Math.min(ringCount - 1, Math.floor(idx / perRing))
    const posInRing = idx - ring * perRing
    const inRing = Math.min(perRing, kept.length - ring * perRing)
    const baseAngle = (2 * Math.PI * posInRing) / Math.max(inRing, 1)
    const jitter = (seedHash(k.id) - 0.5) * (Math.PI / 7.5) // ±12° 抖动
    const radius = mainSize * (0.9 + ring * 0.55) + 14
    const score = k.masteryScore || 0

    let color: string
    if (weak && score < 40) color = '#ff7a45'
    else if (k.isCompleted === 1) color = '#ffd9a0'
    else {
      const hue = tagHue(k.knowledgeTag || '')
      color = `hsl(${hue}, 85%, 78%)`
    }

    nodes.push({
      id: 'k-' + k.id,
      name: k.knowledgeName,
      x: cx + Math.cos(baseAngle + jitter) * radius,
      y: cy + Math.sin(baseAngle + jitter) * radius,
      symbolSize: 4 + (score / 100) * 5,
      itemStyle: {
        color,
        shadowColor: color,
        shadowBlur: heavyScene ? 0 : 8
      },
      label: { show: false },
      knowledgeId: k.id,
      isSatellite: true,
      domainName: domain.domainName,
      tag: k.knowledgeTag,
      masteryScore: score
    })

    links.push({
      source: domain.domainName,
      target: 'k-' + k.id,
      lineStyle: {
        type: 'dashed',
        opacity: 0.25,
        width: 0.6,
        color: weak ? 'rgba(255,154,77,0.5)' : 'rgba(111,183,255,0.5)'
      }
    })
  })

  return { nodes, links, overflow }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)

  const domainNames = props.domainData.map((d) => d.domainName)
  const domainGroups = new Map<string, KnowledgeInfoVO[]>()
  ;(props.knowledgeData || []).forEach((k) => {
    if (!k.knowledgeDomain) return
    const arr = domainGroups.get(k.knowledgeDomain) || []
    arr.push(k)
    domainGroups.set(k.knowledgeDomain, arr)
  })

  const nodes: any[] = []
  const satLinks: any[] = []
  let overflowTotal = 0

  // 质心归一化：后端坐标全为正值，减去质心让云团中心对齐视口中心
  const coords = props.domainData.map((d) => ({ x: coordX(d), y: coordY(d) }))
  const withCoord = coords.filter((c) => c.x != null && c.y != null)
  const cx0 = withCoord.length
    ? withCoord.reduce((s, c) => s + (c.x as number), 0) / withCoord.length
    : 0
  const cy0 = withCoord.length
    ? withCoord.reduce((s, c) => s + (c.y as number), 0) / withCoord.length
    : 0

  const totalNodesEstimate = props.domainData.length + (props.knowledgeData || []).length
  const heavyScene = totalNodesEstimate > 220

  props.domainData.forEach((d) => {
    const size = domainSymbolSize(d)
    const c = domainColor(d)
    const hasCoord = coordX(d) != null && coordY(d) != null
    const fallbackAngle = (2 * Math.PI * nodes.length) / Math.max(props.domainData.length, 1)
    const fallbackRadius = 200

    const mainX = hasCoord ? coordX(d)! - cx0 : Math.cos(fallbackAngle) * fallbackRadius
    const mainY = hasCoord ? coordY(d)! - cy0 : Math.sin(fallbackAngle) * fallbackRadius

    // 光晕伴星先入队（绘制在底层），半透明外圈制造恒星光晕
    nodes.push({
      name: '__halo__' + d.domainName,
      x: mainX,
      y: mainY,
      symbolSize: size * 2.6,
      itemStyle: {
        color: {
          type: 'radial', x: 0.5, y: 0.5, r: 0.5,
          colorStops: [
            { offset: 0, color: d.weakFlag === 1 ? 'rgba(255,140,80,0.28)' : 'rgba(120,180,255,0.28)' },
            { offset: 0.6, color: d.weakFlag === 1 ? 'rgba(255,100,50,0.10)' : 'rgba(80,140,255,0.10)' },
            { offset: 1, color: 'rgba(0,0,0,0)' }
          ]
        }
      },
      label: { show: false },
      isHalo: true,
      silent: true
    })

    nodes.push({
      name: d.domainName,
      x: mainX,
      y: mainY,
      value: d.weightFactor || 1,
      symbolSize: size,
      itemStyle: {
        color: c.color,
        shadowColor: c.shadowColor,
        shadowBlur: c.shadowBlur,
        ...(d.masteryScore >= 85 ? { borderColor: '#ffffff', borderWidth: 1.5 } : {})
      },
      symbol: 'circle',
      label: {
        show: true,
        fontSize: 13,
        fontWeight: 'bold',
        color: c.labelColor,
        textShadowColor: 'rgba(80,140,255,0.9)',
        textShadowBlur: 10
      },
      isDomain: true,
      domainInfo: d
    })

    const sats = buildSatellites(d, size, mainX, mainY, domainGroups.get(d.domainName) || [], heavyScene)
    nodes.push(...sats.nodes)
    satLinks.push(...sats.links)
    overflowTotal += sats.overflow
  })

  const filteredEdges = computeEdges().filter((e: any) => {
    const sourceNode = nodes.find((n: any) => n.name === e.source)
    const targetNode = nodes.find((n: any) => n.name === e.target)
    return sourceNode && targetNode
  })

  const domainLinks = filteredEdges.map((e: any) => {
    const strong = e.value >= 0.6
    const weakFallback = e.value <= 0.05
    const srcDomain = props.domainData.find((d) => d.domainName === e.source)
    const tgtDomain = props.domainData.find((d) => d.domainName === e.target)
    const srcColor = srcDomain ? domainColor(srcDomain).labelColor : '#6fb7ff'
    const tgtColor = tgtDomain ? domainColor(tgtDomain).labelColor : '#6fb7ff'
    return {
      source: e.source,
      target: e.target,
      value: e.value,
      lineStyle: {
        width: 0.6 + e.value * 2.2,
        opacity: weakFallback ? 0.08 : Math.min(0.85, 0.15 + e.value * 0.55),
        curveness: 0.25,
        color: {
          type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [
            { offset: 0, color: srcColor },
            { offset: 1, color: tgtColor }
          ]
        }
      },
      ...(strong && !heavyScene
        ? {
            effect: {
              show: true, symbol: 'circle', symbolSize: 3,
              color: '#bfe3ff', trailLength: 0.25, period: 5
            }
          }
        : {})
    }
  })

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      ...DEEP_TOOLTIP,
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          if (params.data.isSatellite) {
            return `<b>${params.name}</b><br/>领域: ${params.data.domainName}<br/>分类: ${params.data.tag || '—'}<br/>掌握度: ${params.data.masteryScore ?? 0}`
          }
          if (!params.data.isDomain) return ''
          const d = params.data.domainInfo as KnowledgeStarMapVO
          const extra = overflowTotal > 0 ? `<br/><span style="opacity:0.7">另有 ${overflowTotal} 个知识点未展示</span>` : ''
          return `<span style="font-size:15px;font-weight:bold;color:#eaf6ff">${params.name}</span><br/>知识点: ${d.knowledgeCount ?? 0}${extra}<br/>掌握度: ${masteryBar(d.masteryScore ?? 0)} ${d.masteryScore ?? 0}<br/>状态: ${d.weakFlag === 1 ? '薄弱' : '良好'}`
        }
        return `${params.data.source} ↔ ${params.data.target}<br/>关联强度: ${params.data.value?.toFixed(2)}`
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: nodes,
        links: [...domainLinks, ...satLinks],
        force: {
          edgeLength: 150,
          repulsion: 300,
          gravity: 0.1,
          layoutAnimation: !heavyScene
        },
        // 后端坐标以 (0,0) 为原点，center 把原点对齐视口中心
        center: [0.5, 0.5],
        left: 0,
        top: 0,
        zoom: 0.55,
        roam: true,
        labelLayout: { hideOverlap: true },
        label: { position: 'right', formatter: '{b}' },
        lineStyle: { curveness: 0.25 },
        emphasis: {
          focus: 'adjacency',
          blurScope: 'coordinateSystem',
          label: { show: true },
          lineStyle: { width: 4 }
        },
        blur: {
          itemStyle: { opacity: 0.06 },
          lineStyle: { opacity: 0.06 }
        }
      }
    ]
  }

  chart.setOption(option)

  chart.on('click', (params: any) => {
    if (params.dataType !== 'node') return
    if (params.data.isSatellite) {
      emit('knowledge-click', params.data.knowledgeId)
    } else if (params.data.isDomain) {
      emit('domain-click', params.name)
    }
  })
}

function handleResize() {
  chart?.resize()
}

watch(
  () => props.domainData,
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
