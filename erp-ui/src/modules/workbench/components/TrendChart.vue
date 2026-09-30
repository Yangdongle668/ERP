<script setup lang="ts">
import { computed } from 'vue'
import { formatAmount } from '@/utils/format'
import type { CardPoint } from '../api/workbench'

/** 简单折线图（看板趋势卡片）：等距横轴，纵轴从 0 开始，点上显示数值 */
const props = defineProps<{ points: CardPoint[] }>()
const W = 600
const H = 160
const PAD = 28
const max = computed(() => Math.max(1, ...props.points.map((p) => Number(p.value) || 0)))
const coords = computed(() => {
  const n = props.points.length
  return props.points.map((p, i) => ({
    x: n <= 1 ? W / 2 : PAD + (i * (W - PAD * 2)) / (n - 1),
    y: H - PAD - ((Number(p.value) || 0) / max.value) * (H - PAD * 2),
    label: p.label,
    value: p.value
  }))
})
const path = computed(() => coords.value.map((c) => `${c.x},${c.y}`).join(' '))
</script>

<template>
  <svg :viewBox="`0 0 ${W} ${H}`" class="chart" preserveAspectRatio="none" role="img" aria-label="趋势图">
    <line :x1="PAD" :x2="W - PAD" :y1="H - PAD" :y2="H - PAD" class="axis" />
    <polyline :points="path" class="line" fill="none" />
    <g v-for="c in coords" :key="c.label">
      <circle :cx="c.x" :cy="c.y" r="4" class="dot" />
      <text :x="c.x" :y="c.y - 10" text-anchor="middle" class="val">{{ formatAmount(c.value, 0) }}</text>
      <text :x="c.x" :y="H - 8" text-anchor="middle" class="lbl">{{ c.label }}</text>
    </g>
  </svg>
</template>

<style scoped>
.chart { width: 100%; height: 180px; }
.axis { stroke: var(--erp-color-border); }
.line { stroke: var(--erp-color-primary); stroke-width: 2; }
.dot { fill: var(--erp-color-primary); }
.val { fill: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.lbl { fill: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
</style>
