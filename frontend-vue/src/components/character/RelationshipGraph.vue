<template>
  <div class="rel-graph" v-if="characters.length > 0">
    <svg :viewBox="`0 0 ${svgSize} ${svgSize}`" class="rel-svg">
      <!-- Edges -->
      <g v-for="(edge, i) in edges" :key="'edge-' + i">
        <line
          :x1="edge.x1" :y1="edge.y1"
          :x2="edge.x2" :y2="edge.y2"
          :stroke="edge.color"
          :stroke-width="1.5"
          :stroke-dasharray="edge.isDashed ? '6,3' : 'none'"
          opacity="0.4"
        />
        <text
          v-if="edge.label"
          :x="(edge.x1 + edge.x2) / 2"
          :y="(edge.y1 + edge.y2) / 2 - 4"
          text-anchor="middle"
          :fill="edge.color"
          font-size="10"
          opacity="0.7"
        >{{ edge.label }}</text>
      </g>

      <!-- Nodes -->
      <g
        v-for="node in nodes"
        :key="'node-' + node.id"
        class="graph-node"
        :class="{ 'graph-node--active': selectedId === node.id }"
        @click="$emit('select', node.id)"
      >
        <!-- Glow ring for active -->
        <circle
          v-if="selectedId === node.id"
          :cx="node.x" :cy="node.y" r="28"
          fill="none"
          :stroke="node.glowColor"
          stroke-width="1"
          opacity="0.3"
        >
          <animate attributeName="r" from="28" to="34" dur="2s" repeatCount="indefinite"/>
          <animate attributeName="opacity" from="0.3" to="0" dur="2s" repeatCount="indefinite"/>
        </circle>

        <!-- Node circle -->
        <circle
          :cx="node.x" :cy="node.y" r="22"
          :fill="node.bgColor"
          :stroke="node.borderColor"
          stroke-width="1.5"
        />
        <!-- Role dot -->
        <circle
          :cx="node.x - 8" :cy="node.y - 10" r="4"
          :fill="node.dotColor"
        />
        <!-- Name -->
        <text
          :x="node.x" :y="node.y + 4"
          text-anchor="middle"
          fill="var(--text-primary)"
          font-size="11"
          font-weight="500"
        >{{ node.name.length > 3 ? node.name.slice(0, 3) + '…' : node.name }}</text>
        <!-- Role label below -->
        <text
          :x="node.x" :y="node.y + 32"
          text-anchor="middle"
          fill="var(--text-muted)"
          font-size="9"
          opacity="0.6"
        >{{ node.roleLabel }}</text>
      </g>
    </svg>

    <!-- Legend -->
    <div class="graph-legend">
      <span v-for="rel in legendItems" :key="rel.label" class="legend-item">
        <span class="legend-dot" :style="{ background: rel.color }"></span>
        {{ rel.label }}
      </span>
    </div>
  </div>
  <div v-else class="graph-empty">
    <span>📊</span>
    <p>没有足够的角色数据来生成关系图</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { roleLabel } from '@/lib/utils'
import type { Character } from '@/types/character'

const props = defineProps<{
  characters: Character[]
  selectedId?: number | null
}>()

defineEmits<{
  select: [id: number]
}>()

const svgSize = 500
const centerX = svgSize / 2
const centerY = svgSize / 2
const radius = 180

interface GraphNode {
  id: number
  name: string
  x: number
  y: number
  bgColor: string
  borderColor: string
  glowColor: string
  dotColor: string
  roleLabel: string
}

interface GraphEdge {
  x1: number; y1: number
  x2: number; y2: number
  color: string
  label: string
  isDashed: boolean
}

const roleColors: Record<string, { bg: string; border: string; glow: string; dot: string }> = {
  PROTAGONIST: { bg: 'rgba(194, 59, 34, 0.15)', border: 'rgba(194, 59, 34, 0.4)', glow: '#C23B22', dot: '#C23B22' },
  ANTAGONIST: { bg: 'rgba(155, 89, 182, 0.15)', border: 'rgba(155, 89, 182, 0.4)', glow: '#9b59b6', dot: '#9b59b6' },
  DEUTERAGONIST: { bg: 'rgba(212, 168, 83, 0.15)', border: 'rgba(212, 168, 83, 0.4)', glow: '#D4A853', dot: '#D4A853' },
  SUPPORTING: { bg: 'rgba(61, 184, 176, 0.15)', border: 'rgba(61, 184, 176, 0.4)', glow: '#3DB8B0', dot: '#3DB8B0' },
  MINOR: { bg: 'rgba(255, 255, 255, 0.05)', border: 'rgba(255, 255, 255, 0.15)', glow: '#787066', dot: '#787066' },
}

const relColorMap: Record<string, string> = {
  '恋人': '#f472b6', '夫妻': '#f472b6', '情人': '#f472b6', 'love': '#f472b6',
  '仇敌': '#C23B22', '敌人': '#C23B22', '对手': '#C23B22', 'enemy': '#C23B22',
  '师徒': '#3DB8B0', '老师': '#3DB8B0', '学生': '#3DB8B0', 'master': '#3DB8B0',
  '朋友': '#22c55e', '好友': '#22c55e', 'friend': '#22c55e',
  '家人': '#f97316', '父亲': '#f97316', '母亲': '#f97316', '兄弟': '#f97316', '姐妹': '#f97316', 'family': '#f97316',
  '同门': '#a78bfa', '同事': '#a78bfa',
}

function getRelColor(relation: string): string {
  if (!relation) return '#787066'
  for (const [key, color] of Object.entries(relColorMap)) {
    if (relation.includes(key)) return color
  }
  return '#787066'
}

const nodes = computed<GraphNode[]>(() => {
  const chars = props.characters
  return chars.map((c, i) => {
    const angle = (2 * Math.PI * i) / chars.length - Math.PI / 2
    const x = centerX + radius * Math.cos(angle)
    const y = centerY + radius * Math.sin(angle)
    const colors = roleColors[c.roleType] ?? roleColors.MINOR
    return {
      id: c.id,
      name: c.canonicalName,
      x, y,
      bgColor: colors.bg,
      borderColor: colors.border,
      glowColor: colors.glow,
      dotColor: colors.dot,
      roleLabel: roleLabel(c.roleType),
    }
  })
})

const edges = computed<GraphEdge[]>(() => {
  const nodeMap = new Map(nodes.value.map((n) => [n.id, n]))
  const result: GraphEdge[] = []
  const seen = new Set<string>()

  for (const char of props.characters) {
    const source = nodeMap.get(char.id)
    if (!source) continue
    const rels = char.relationships ?? []
    for (const rel of rels) {
      if (!rel?.target || !rel?.relation) continue
      const target = props.characters.find((c) =>
        c.canonicalName === rel.target || (c.aliases ?? []).includes(rel.target)
      )
      if (!target) continue
      const targetNode = nodeMap.get(target.id)
      if (!targetNode) continue

      const key = [Math.min(char.id, target.id), Math.max(char.id, target.id)].join('-')
      if (seen.has(key)) continue
      seen.add(key)

      result.push({
        x1: source.x, y1: source.y,
        x2: targetNode.x, y2: targetNode.y,
        color: getRelColor(rel.relation),
        label: rel.relation,
        isDashed: rel.relation === '旧识' || rel.relation === 'neutral',
      })
    }
  }

  return result
})

const legendItems = computed(() => [
  { label: '恋人/夫妻', color: '#f472b6' },
  { label: '仇敌/对手', color: '#C23B22' },
  { label: '师徒', color: '#3DB8B0' },
  { label: '朋友', color: '#22c55e' },
  { label: '家人', color: '#f97316' },
])
</script>

<style scoped>
.rel-graph {
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 16px;
}

.rel-svg {
  width: 100%;
  height: auto;
}

.graph-node {
  cursor: pointer;
  transition: opacity 0.2s;
}

.graph-node:hover {
  opacity: 0.8;
}

.graph-node--active circle:first-of-type {
  opacity: 1;
}

.graph-legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--text-muted);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.graph-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px;
  color: var(--text-muted);
  font-size: 13px;
}
</style>
