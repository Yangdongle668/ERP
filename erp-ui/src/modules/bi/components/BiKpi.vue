<script setup lang="ts">
import { computed } from 'vue'
import type { MetricUnit } from '../api/bi'
import { type AmountScale, fmtMetric, LOWER_IS_BETTER } from './biFormat'

/** KPI 卡片：本期值、对比变化（比率类显示百分点），向好为绿色、变差为红色；可附一个辅助值（如毛利率、逾期应收） */
const props = withDefaults(defineProps<{
  code: string
  name: string
  unit: MetricUnit
  value?: number | null
  compareValue?: number | null
  changePct?: number | null
  changePt?: number | null
  compareLabel?: string
  extraName?: string
  extraUnit?: MetricUnit
  extra?: number | null
  scale?: AmountScale
  clickable?: boolean
}>(), { compareLabel: '环比', scale: 1, clickable: false })
const emit = defineEmits<{ click: [] }>()

const delta = computed(() => (props.unit === 'PERCENT' ? props.changePt : props.changePct))
const tone = computed(() => {
  const d = Number(delta.value)
  if (delta.value === null || delta.value === undefined || d === 0) return 'flat'
  return (d > 0) !== LOWER_IS_BETTER.has(props.code) ? 'good' : 'bad'
})
const deltaText = computed(() => {
  if (delta.value === null || delta.value === undefined) return '-'
  const d = Number(delta.value)
  return `${d > 0 ? '+' : ''}${d.toFixed(1)}${props.unit === 'PERCENT' ? ' 个百分点' : '%'}`
})
</script>

<template>
  <div :class="['bi-kpi', { 'is-clickable': clickable }]" @click="clickable && emit('click')">
    <div class="bi-kpi__name">{{ name }}</div>
    <div class="bi-kpi__value">{{ fmtMetric(value, unit, scale) }}</div>
    <div class="bi-kpi__meta">
      <span :class="['bi-kpi__delta', `is-${tone}`]">
        <el-icon v-if="tone !== 'flat'"><component :is="Number(delta) > 0 ? 'Up' : 'Down'" /></el-icon>{{ compareLabel }} {{ deltaText }}
      </span>
      <span v-if="extraName" class="bi-kpi__extra">{{ extraName }} {{ fmtMetric(extra, extraUnit, scale) }}</span>
    </div>
  </div>
</template>

<style scoped>
.bi-kpi { background: var(--erp-color-surface); border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-card); padding: var(--erp-space-4); min-width: 0; }
.bi-kpi.is-clickable { cursor: pointer; }
.bi-kpi.is-clickable:hover { border-color: var(--erp-color-primary); }
.bi-kpi__name { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.bi-kpi__value { margin-top: var(--erp-space-1); font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); color: var(--erp-color-text);
  font-variant-numeric: tabular-nums; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bi-kpi__meta { margin-top: var(--erp-space-1); display: flex; flex-wrap: wrap; gap: var(--erp-space-3); font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.bi-kpi__delta { display: inline-flex; align-items: center; gap: 2px; }
.bi-kpi__delta.is-good { color: var(--erp-chart-up); }
.bi-kpi__delta.is-bad { color: var(--erp-chart-down); }
.bi-kpi__extra { color: var(--erp-color-text-secondary); }
</style>
