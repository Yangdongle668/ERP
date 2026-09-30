<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { BiQuery, BiResult } from '../api/bi'
import BiLineChart from './BiLineChart.vue'
import BiBarChart from './BiBarChart.vue'
import BiDonut from './BiDonut.vue'
import { fmtMetric } from './biFormat'

/** AI 回答附带的数据表（真实数值，在本系统内渲染）、建议图表、口径说明与“在专题分析中打开” */
const props = defineProps<{ result: BiResult; query?: BiQuery; chart?: string; topics: Record<string, string> }>()
const router = useRouter()

const dims = computed(() => props.result.columns.filter((c) => c.kind === 'DIMENSION'))
const mets = computed(() => props.result.columns.filter((c) => c.kind === 'METRIC'))
const hasDate = computed(() => dims.value.some((d) => d.key === 'date'))
const firstDim = computed(() => dims.value[0])
const label = (row: Record<string, unknown>, key: string) => String(key === 'date' ? row.date : row[`${key}_label`] ?? '')
const chartKind = computed(() => {
  if (!firstDim.value || !mets.value.length || props.result.rows.length < 2 || props.chart === 'table') return null
  if (props.chart === 'line' || (hasDate.value && !props.chart)) return hasDate.value && dims.value.length === 1 ? 'line' : 'bar'
  if (props.chart === 'pie') return dims.value.length === 1 ? 'pie' : 'bar'
  return dims.value.length === 1 ? 'bar' : null
})
const lineSeries = computed(() => mets.value.slice(0, 3).map((m) => ({ name: m.label, values: props.result.rows.map((r) => r[m.key] as number | null) })))
const items = computed(() => props.result.rows.slice(0, 15).map((r) => ({
  key: firstDim.value ? String(r[firstDim.value.key] ?? '') : '', label: firstDim.value ? label(r, firstDim.value.key) : '', value: r[mets.value[0].key] as number | null
})))
const topic = computed(() => props.topics[mets.value[0]?.key])
function open() {
  if (!topic.value) return
  router.push({ path: `/bi/${topic.value}`, query: { from: props.result.from, to: props.result.to } })
}
const filterText = computed(() => {
  const f = props.query?.filters
  if (!f || !Object.keys(f).length) return '无'
  return Object.entries(f).map(([k, v]) => `${k} = ${v.join('、')}`).join('；')
})
</script>

<template>
  <div class="ai-result">
    <div v-if="chartKind" class="ai-result__chart">
      <BiLineChart v-if="chartKind === 'line'" :labels="result.rows.map((r) => String(r.date))" :series="lineSeries" :unit="mets[0].unit" :height="180" />
      <BiDonut v-else-if="chartKind === 'pie'" :items="items" :unit="mets[0].unit" />
      <BiBarChart v-else :items="items" :unit="mets[0].unit" />
    </div>
    <el-table :data="result.rows" size="small" max-height="320">
      <el-table-column v-for="d in dims" :key="d.key" :label="d.label" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ label(row, d.key) }}</template>
      </el-table-column>
      <el-table-column v-for="m in mets" :key="m.key" :label="m.label" align="right" min-width="120">
        <template #default="{ row }">{{ fmtMetric(row[m.key], m.unit) }}</template>
      </el-table-column>
    </el-table>
    <el-collapse class="ai-result__basis">
      <el-collapse-item title="口径" name="basis">
        <div class="ai-result__meta">期间 {{ result.from }} ~ {{ result.to }}<template v-if="result.granularity">，粒度 {{ result.granularity }}</template>；筛选 {{ filterText }}</div>
        <div v-for="m in result.metrics" :key="m.code" class="ai-result__meta">{{ m.name }}（{{ m.source }}）：{{ m.description }}</div>
        <el-button v-if="topic" link type="primary" @click="open">在专题分析中打开</el-button>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.ai-result { margin-top: var(--erp-space-3); display: flex; flex-direction: column; gap: var(--erp-space-2); }
.ai-result__chart { padding: var(--erp-space-2) 0; }
.ai-result__meta { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); line-height: var(--erp-line-height); }
</style>
