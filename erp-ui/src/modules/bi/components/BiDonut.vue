<script setup lang="ts">
import { computed } from 'vue'
import type { MetricUnit } from '../api/bi'
import { fmtMetric } from './biFormat'

/** 环形图（占比）：最多 7 类 + “其他”，其他使用 --erp-chart-other；点击图例触发 select */
const props = defineProps<{ items: { key?: string | null; label: string; value: number | null | undefined }[]; unit?: MetricUnit }>()
const emit = defineEmits<{ select: [item: { key?: string | null; label: string }] }>()

const R = 60
const C = 2 * Math.PI * R
const total = computed(() => props.items.reduce((s, i) => s + Math.max(0, Number(i.value) || 0), 0))
const arcs = computed(() => {
  let acc = 0
  return props.items.map((it, i) => {
    const v = Math.max(0, Number(it.value) || 0)
    const len = total.value > 0 ? (v / total.value) * C : 0
    const arc = { ...it, share: total.value > 0 ? (v * 100) / total.value : 0, dash: `${len} ${C - len}`, offset: -acc,
      cls: it.key === null && it.label === '其他' ? 'other' : `c${(i % 8) + 1}` }
    acc += len
    return arc
  })
})
</script>

<template>
  <div class="bi-donut">
    <ErpEmpty v-if="!items.length" description="暂无数据" />
    <template v-else>
      <svg viewBox="0 0 160 160" class="bi-donut__svg" role="img" aria-label="占比图">
        <circle cx="80" cy="80" :r="R" class="track" fill="none" />
        <circle v-for="a in arcs" :key="a.label" cx="80" cy="80" :r="R" :class="['arc', a.cls]" fill="none" :stroke-dasharray="a.dash" :stroke-dashoffset="a.offset"
                transform="rotate(-90 80 80)"><title>{{ a.label }}：{{ a.share.toFixed(1) }}%</title></circle>
      </svg>
      <ul class="bi-donut__legend">
        <li v-for="a in arcs" :key="a.label" @click="emit('select', a)">
          <i :class="['swatch', a.cls]" /><span class="name" :title="a.label">{{ a.label }}</span>
          <span class="val">{{ fmtMetric(a.value, unit) }}</span><span class="pct">{{ a.share.toFixed(1) }}%</span>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.bi-donut { display: flex; align-items: center; gap: var(--erp-space-5); }
.bi-donut__svg { width: 160px; height: 160px; flex: none; }
.track { fill: none; stroke: var(--erp-color-surface-subtle); stroke-width: 22; }
.arc { fill: none; stroke-width: 22; }
.bi-donut__legend { list-style: none; margin: 0; padding: 0; flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--erp-space-1); }
.bi-donut__legend li { display: grid; grid-template-columns: 12px 1fr auto 52px; align-items: center; gap: var(--erp-space-2); cursor: pointer;
  font-size: var(--erp-font-size-secondary); color: var(--erp-color-text); }
.bi-donut__legend .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bi-donut__legend .val, .bi-donut__legend .pct { font-variant-numeric: tabular-nums; color: var(--erp-color-text-secondary); text-align: right; }
.swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.arc.c1 { stroke: var(--erp-chart-1); } .swatch.c1 { background: var(--erp-chart-1); }
.arc.c2 { stroke: var(--erp-chart-2); } .swatch.c2 { background: var(--erp-chart-2); }
.arc.c3 { stroke: var(--erp-chart-3); } .swatch.c3 { background: var(--erp-chart-3); }
.arc.c4 { stroke: var(--erp-chart-4); } .swatch.c4 { background: var(--erp-chart-4); }
.arc.c5 { stroke: var(--erp-chart-5); } .swatch.c5 { background: var(--erp-chart-5); }
.arc.c6 { stroke: var(--erp-chart-6); } .swatch.c6 { background: var(--erp-chart-6); }
.arc.c7 { stroke: var(--erp-chart-7); } .swatch.c7 { background: var(--erp-chart-7); }
.arc.c8 { stroke: var(--erp-chart-8); } .swatch.c8 { background: var(--erp-chart-8); }
.arc.other { stroke: var(--erp-chart-other); } .swatch.other { background: var(--erp-chart-other); }
</style>
