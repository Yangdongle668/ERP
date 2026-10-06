<script setup lang="ts">
import { computed } from 'vue'
import type { MetricUnit } from '../api/bi'
import { fmtCompact, fmtMetric, niceMax } from './biFormat'

/**
 * 折线图（SVG）：多条序列共用纵轴（从 0 开始），类别色按序使用 --erp-chart-1..8；点击某个横轴位置触发 select（下钻）。
 */
const props = withDefaults(defineProps<{
  labels: string[]
  series: { name: string; values: (number | null | undefined)[] }[]
  unit?: MetricUnit
  height?: number
}>(), { height: 220 })
const emit = defineEmits<{ select: [index: number] }>()

const W = 640
const PAD = { l: 52, r: 16, t: 16, b: 28 }
const H = computed(() => props.height)
const max = computed(() => {
  const all = props.series.flatMap((s) => s.values.map((v) => Number(v) || 0))
  return niceMax(Math.max(0, ...all))
})
const ticks = computed(() => [0, 1, 2, 3, 4].map((i) => (max.value * i) / 4))
const xOf = (i: number) => {
  const n = props.labels.length
  return n <= 1 ? W / 2 : PAD.l + (i * (W - PAD.l - PAD.r)) / (n - 1)
}
const yOf = (v: number) => H.value - PAD.b - (v / max.value) * (H.value - PAD.t - PAD.b)
const step = computed(() => (props.labels.length <= 1 ? W : (W - PAD.l - PAD.r) / (props.labels.length - 1)))
/** 标签过密时隔点显示 */
const every = computed(() => Math.max(1, Math.ceil(props.labels.length / 12)))
const lines = computed(() => props.series.map((s, si) => ({
  name: s.name,
  cls: `c${(si % 8) + 1}`,
  path: s.values.map((v, i) => (v === null || v === undefined ? null : `${xOf(i)},${yOf(Number(v))}`)).filter(Boolean).join(' '),
  points: s.values.map((v, i) => ({ i, v, x: xOf(i), y: v === null || v === undefined ? null : yOf(Number(v)) }))
})))
</script>

<template>
  <div class="bi-line">
    <svg :viewBox="`0 0 ${W} ${H}`" class="bi-line__svg" :style="{ height: `${H}px` }" role="img" aria-label="趋势图">
      <g v-for="t in ticks" :key="t">
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="yOf(t)" :y2="yOf(t)" class="grid" />
        <text :x="PAD.l - 6" :y="yOf(t) + 4" text-anchor="end" class="axis">{{ fmtCompact(t, unit) }}</text>
      </g>
      <text v-for="(l, i) in labels" v-show="i % every === 0" :key="l" :x="xOf(i)" :y="H - 8" text-anchor="middle" class="axis">{{ l }}</text>
      <polyline v-for="s in lines" :key="s.name" :points="s.path" :class="['line', s.cls]" fill="none" />
      <g v-for="s in lines" :key="`${s.name}-p`">
        <template v-for="p in s.points" :key="p.i">
          <circle v-if="p.y !== null" :cx="p.x" :cy="p.y" r="3" :class="['dot', s.cls]">
            <title>{{ labels[p.i] }} {{ s.name }}：{{ fmtMetric(p.v, unit) }}</title>
          </circle>
        </template>
      </g>
      <rect v-for="(l, i) in labels" :key="`hit-${l}`" :x="xOf(i) - step / 2" :y="PAD.t" :width="step" :height="H - PAD.t - PAD.b" class="hit" fill="transparent"
            @click="emit('select', i)" />
    </svg>
    <div v-if="series.length > 1" class="bi-legend">
      <span v-for="s in lines" :key="s.name" class="bi-legend__item"><i :class="['swatch', s.cls]" />{{ s.name }}</span>
    </div>
  </div>
</template>

<style scoped>
.bi-line__svg { width: 100%; display: block; }
.grid { stroke: var(--erp-chart-grid); }
.axis { fill: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
.line { stroke-width: 2; }
.hit { fill: transparent; cursor: pointer; }
.bi-legend { display: flex; flex-wrap: wrap; gap: var(--erp-space-4); margin-top: var(--erp-space-2); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.bi-legend__item { display: inline-flex; align-items: center; gap: var(--erp-space-1); }
.swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.c1 { stroke: var(--erp-chart-1); fill: var(--erp-chart-1); }
.c2 { stroke: var(--erp-chart-2); fill: var(--erp-chart-2); }
.c3 { stroke: var(--erp-chart-3); fill: var(--erp-chart-3); }
.c4 { stroke: var(--erp-chart-4); fill: var(--erp-chart-4); }
.c5 { stroke: var(--erp-chart-5); fill: var(--erp-chart-5); }
.c6 { stroke: var(--erp-chart-6); fill: var(--erp-chart-6); }
.c7 { stroke: var(--erp-chart-7); fill: var(--erp-chart-7); }
.c8 { stroke: var(--erp-chart-8); fill: var(--erp-chart-8); }
polyline.line { fill: none; }
i.swatch.c1 { background: var(--erp-chart-1); }
i.swatch.c2 { background: var(--erp-chart-2); }
i.swatch.c3 { background: var(--erp-chart-3); }
i.swatch.c4 { background: var(--erp-chart-4); }
i.swatch.c5 { background: var(--erp-chart-5); }
i.swatch.c6 { background: var(--erp-chart-6); }
i.swatch.c7 { background: var(--erp-chart-7); }
i.swatch.c8 { background: var(--erp-chart-8); }
</style>
