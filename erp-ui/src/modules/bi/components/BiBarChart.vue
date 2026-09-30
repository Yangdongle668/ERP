<script setup lang="ts">
import { computed } from 'vue'
import type { MetricUnit } from '../api/bi'
import { fmtMetric } from './biFormat'

/** 横向条形图：排名、Top N、结构对比；点击条目触发 select（下钻）。sub 为附加说明（如累计占比） */
const props = withDefaults(defineProps<{
  items: { key?: string | null; label: string; value: number | null | undefined; sub?: string }[]
  unit?: MetricUnit
  color?: number
  emptyText?: string
}>(), { color: 1, emptyText: '暂无数据' })
const emit = defineEmits<{ select: [item: { key?: string | null; label: string }] }>()

const max = computed(() => Math.max(0, ...props.items.map((i) => Math.abs(Number(i.value) || 0))))
const width = (v: unknown) => (max.value <= 0 ? 0 : (Math.abs(Number(v) || 0) / max.value) * 100)
</script>

<template>
  <div class="bi-bar">
    <ErpEmpty v-if="!items.length" :description="emptyText" />
    <div v-for="(it, i) in items" :key="`${it.key ?? it.label}-${i}`" class="bi-bar__row" @click="emit('select', it)">
      <span class="bi-bar__label" :title="it.label">{{ it.label }}</span>
      <span class="bi-bar__track"><span :class="['bi-bar__fill', `c${((color - 1) % 8) + 1}`]" :style="{ width: `${width(it.value)}%` }" /></span>
      <span class="bi-bar__value">{{ fmtMetric(it.value, unit) }}<em v-if="it.sub">{{ it.sub }}</em></span>
    </div>
  </div>
</template>

<style scoped>
.bi-bar { display: flex; flex-direction: column; gap: var(--erp-space-2); }
.bi-bar__row { display: grid; grid-template-columns: minmax(80px, 30%) 1fr auto; align-items: center; gap: var(--erp-space-3); cursor: pointer; }
.bi-bar__row:hover .bi-bar__label { color: var(--erp-color-primary); }
.bi-bar__label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--erp-color-text); font-size: var(--erp-font-size-secondary); }
.bi-bar__track { height: 10px; background: var(--erp-color-surface-subtle); border-radius: var(--erp-radius-xs); overflow: hidden; }
.bi-bar__fill { display: block; height: 100%; border-radius: var(--erp-radius-xs); }
.bi-bar__value { font-variant-numeric: tabular-nums; color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); text-align: right; min-width: 88px; }
.bi-bar__value em { font-style: normal; color: var(--erp-color-text-tertiary); margin-left: var(--erp-space-2); font-size: var(--erp-font-size-caption); }
.c1 { background: var(--erp-chart-1); }
.c2 { background: var(--erp-chart-2); }
.c3 { background: var(--erp-chart-3); }
.c4 { background: var(--erp-chart-4); }
.c5 { background: var(--erp-chart-5); }
.c6 { background: var(--erp-chart-6); }
.c7 { background: var(--erp-chart-7); }
.c8 { background: var(--erp-chart-8); }
</style>
