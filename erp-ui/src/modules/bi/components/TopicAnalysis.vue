<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { type BiRow, type CompareCode, type Metric, metricApi, type PeriodCode, queryApi } from '../api/bi'
import { useUserStore } from '@/stores/user'
import PeriodFilter from './PeriodFilter.vue'
import BiKpi from './BiKpi.vue'
import BiLineChart from './BiLineChart.vue'
import BiBarChart from './BiBarChart.vue'
import BiDonut from './BiDonut.vue'
import { type AmountScale, changePct, downloadCsv, fmtMetric, lastMonths, periodRange } from './biFormat'
import { type ChartDef, TOPICS } from './topics'

/**
 * 专题分析通用内容（需求 13-03 第 1 节，T7）：期间与对比、维度筛选、KPI 卡片、图表、明细透视表（行维度切换、下钻、面包屑）。
 * 指标按当前用户权限显示；筛选条件只作用于支持该维度的指标（不支持的指标暂不显示）。
 */
const props = defineProps<{ topic: string }>()
const route = useRoute()
const user = useUserStore()
const def = computed(() => TOPICS[props.topic])

const metrics = ref<Record<string, Metric>>({})
const updatedAt = ref<string>()
const queryFrom = typeof route.query.from === 'string' ? route.query.from : undefined
const queryTo = typeof route.query.to === 'string' ? route.query.to : undefined
const period = ref<PeriodCode>(queryFrom && queryTo ? 'CUSTOM' : 'THIS_MONTH')
const compare = ref<CompareCode>('MOM')
const custom = ref<[string, string] | null>(queryFrom && queryTo ? [queryFrom, queryTo] : null)
const scale = ref<AmountScale>(1)
const range = computed(() => periodRange(period.value, compare.value, custom.value ?? undefined))

/** 顶部筛选（选择器）与来自下钻 / 链接的筛选 */
const picked = reactive<Record<string, string | undefined>>({ customer: undefined, supplier: undefined, material: undefined, owner: undefined })
const linked = ref<Record<string, string>>({})
for (const k of ['customer', 'category', 'warehouse_type', 'supplier', 'material']) {
  const v = route.query[k]
  if (typeof v === 'string' && v) linked.value[k] = v
}
const filters = computed<Record<string, string[]>>(() => {
  const f: Record<string, string[]> = {}
  for (const [k, v] of Object.entries(linked.value)) f[k] = [v]
  for (const [k, v] of Object.entries(picked)) if (v) f[k] = [v]
  return f
})
const dimLabel = (d: string) => Object.values(metrics.value).flatMap((m) => m.dimensions).find((x) => x.code === d)?.label ?? d
const supports = (code: string, dims: string[]) => {
  const m = metrics.value[code]
  return !!m && dims.every((d) => m.dimensions.some((x) => x.code === d))
}
const filterDims = computed(() => Object.keys(filters.value))
const usable = (codes: string[], extraDims: string[] = []) => codes.filter((c) => supports(c, [...filterDims.value, ...extraDims]))
const unit = (code: string) => metrics.value[code]?.unit
const name = (code: string) => metrics.value[code]?.name ?? code

// ==================== KPI ====================
const kpiRows = ref<{ cur: BiRow; prev: BiRow }>({ cur: {}, prev: {} })
const kpiCodes = computed(() => usable(def.value.kpis))
async function loadKpis() {
  const codes = kpiCodes.value
  if (!codes.length) {
    kpiRows.value = { cur: {}, prev: {} }
    return
  }
  const base = { metrics: codes, filters: filters.value }
  const [cur, prev] = await Promise.all([
    queryApi.query({ ...base, from: range.value.from, to: range.value.to }),
    queryApi.query({ ...base, from: range.value.compareFrom, to: range.value.compareTo })
  ])
  kpiRows.value = { cur: cur.rows[0] ?? {}, prev: prev.rows[0] ?? {} }
  updatedAt.value = cur.dataUpdatedAt
}

// ==================== 图表 ====================
interface ChartView { def: ChartDef; labels?: string[]; series?: { name: string; values: (number | null)[] }[]; items?: { key?: string | null; label: string; value: number | null; sub?: string }[] }
const charts = ref<ChartView[]>([])
async function loadChart(c: ChartDef): Promise<ChartView | null> {
  if (c.type === 'line') {
    const codes = usable(c.metrics, ['date', ...(c.seriesDim ? [c.seriesDim] : [])])
    if (!codes.length) return null
    const r = lastMonths(range.value.to, c.months)
    const res = await queryApi.query({ metrics: codes, dimensions: ['date', ...(c.seriesDim ? [c.seriesDim] : [])], filters: filters.value,
      from: r.from, to: r.to, granularity: 'month', limit: 2000 })
    const months: string[] = []
    for (let i = 0; i < c.months; i++) {
      const [y, m] = r.from.split('-').map(Number)
      const t = y * 12 + (m - 1) + i
      months.push(`${Math.floor(t / 12)}-${String((t % 12) + 1).padStart(2, '0')}`)
    }
    let series: { name: string; values: (number | null)[] }[]
    if (c.seriesDim) {
      const totals = new Map<string, number>()
      res.rows.forEach((row) => totals.set(String(row[`${c.seriesDim}_label`]), (totals.get(String(row[`${c.seriesDim}_label`])) ?? 0) + Number(row[codes[0]] ?? 0)))
      const top = [...totals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map((e) => e[0])
      series = top.map((label) => ({ name: label, values: months.map((m) => {
        const row = res.rows.find((x) => x.date === m && String(x[`${c.seriesDim}_label`]) === label)
        return row ? Number(row[codes[0]]) : null
      }) }))
    } else {
      series = codes.map((code) => ({ name: name(code), values: months.map((m) => {
        const row = res.rows.find((x) => x.date === m)
        return row && row[code] !== null ? Number(row[code]) : null
      }) }))
    }
    return { def: c, labels: months.map((m) => m.slice(2)), series }
  }
  if (!supports(c.metric, [...filterDims.value, c.dim])) return null
  const res = await queryApi.query({ metrics: [c.metric], dimensions: [c.dim], filters: filters.value, from: range.value.from, to: range.value.to,
    sort: c.metric, order: c.type === 'bar' ? (c.order ?? 'desc') : 'desc', limit: c.type === 'bar' ? c.limit : 200 })
  const rows = res.rows.filter((r) => r[c.metric] !== null)
  if (c.type === 'donut') {
    const items = rows.slice(0, 7).map((r) => ({ key: r[c.dim] as string, label: String(r[`${c.dim}_label`]), value: Number(r[c.metric]) }))
    const rest = rows.slice(7).reduce((s, r) => s + Number(r[c.metric] ?? 0), 0)
    if (rest > 0) items.push({ key: null as unknown as string, label: '其他', value: rest })
    return { def: c, items }
  }
  let acc = 0
  const total = rows.reduce((s, r) => s + Math.max(0, Number(r[c.metric] ?? 0)), 0)
  const items = rows.map((r) => {
    acc += Math.max(0, Number(r[c.metric] ?? 0))
    return { key: r[c.dim] as string, label: String(r[`${c.dim}_label`]), value: Number(r[c.metric]),
      sub: c.pareto && total > 0 ? `累计 ${((acc * 100) / total).toFixed(1)}%${acc / total <= 0.8 ? ' A' : acc / total <= 0.95 ? ' B' : ' C'}` : undefined }
  })
  return { def: c, items }
}
async function loadCharts() {
  const views = await Promise.all(def.value.charts.map((c) => loadChart(c).catch(() => null)))
  charts.value = views.filter((v): v is ChartView => v !== null)
}

// ==================== 明细透视 ====================
const pivotDim = ref<string>('')
const drillPath = ref<{ dim: string; value: string; label: string }[]>([])
const pivotRows = ref<BiRow[]>([])
const pivotYoy = ref<Record<string, number | null>>({})
const pivotLoading = ref(false)
const pivotFilters = computed(() => {
  const f = { ...filters.value }
  drillPath.value.forEach((p) => (f[p.dim] = [p.value]))
  return f
})
const pivotMetrics = computed(() => def.value.pivot.metrics.filter((c) => supports(c, [pivotDim.value, ...Object.keys(pivotFilters.value)])))
const pivotDims = computed(() => def.value.pivot.dims.filter((d) => Object.values(metrics.value).some((m) => m.dimensions.some((x) => x.code === d))))
async function loadPivot() {
  if (!pivotDim.value || !pivotMetrics.value.length) {
    pivotRows.value = []
    return
  }
  pivotLoading.value = true
  try {
    const base = { metrics: pivotMetrics.value, dimensions: [pivotDim.value], filters: pivotFilters.value, sort: pivotMetrics.value[0], order: 'desc' as const, limit: 500 }
    const [cur, yoy] = await Promise.all([
      queryApi.query({ ...base, from: range.value.from, to: range.value.to }),
      queryApi.query({ ...base, metrics: [pivotMetrics.value[0]], from: shiftYear(range.value.from), to: shiftYear(range.value.to) })
    ])
    pivotRows.value = cur.rows
    const map: Record<string, number | null> = {}
    yoy.rows.forEach((r) => (map[String(r[pivotDim.value])] = r[pivotMetrics.value[0]] as number | null))
    pivotYoy.value = map
  } finally {
    pivotLoading.value = false
  }
}
const shiftYear = (d: string) => `${Number(d.slice(0, 4)) - 1}${d.slice(4, 8)}${d.slice(5, 7) === '02' && d.slice(8) === '29' ? '28' : d.slice(8)}`
function drill(row: BiRow) {
  const next = def.value.pivot.drill[pivotDim.value]
  if (!next || drillPath.value.some((p) => p.dim === next)) return
  drillPath.value.push({ dim: pivotDim.value, value: String(row[pivotDim.value]), label: String(row[`${pivotDim.value}_label`]) })
  pivotDim.value = next
  loadPivot()
}
function backTo(index: number) {
  const target = drillPath.value[index]
  drillPath.value = drillPath.value.slice(0, index)
  pivotDim.value = target.dim
  loadPivot()
}
function exportPivot() {
  const header = [dimLabel(pivotDim.value), ...pivotMetrics.value.map(name), `${name(pivotMetrics.value[0])}同比`]
  downloadCsv(`${props.topic}-${pivotDim.value}-${range.value.from}_${range.value.to}`, header, pivotRows.value.map((r) => [
    String(r[`${pivotDim.value}_label`]), ...pivotMetrics.value.map((c) => r[c] as number | null),
    yoyText(r)
  ]))
}
const yoyText = (r: BiRow) => {
  const c = changePct(r[pivotMetrics.value[0]] as number | null, pivotYoy.value[String(r[pivotDim.value])])
  return c === null ? '-' : `${c > 0 ? '+' : ''}${c.toFixed(1)}%`
}

// ==================== 加载 ====================
const loading = ref(false)
async function loadAll() {
  if (period.value === 'CUSTOM' && !custom.value) return
  loading.value = true
  try {
    await Promise.all([loadKpis(), loadCharts(), loadPivot()])
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const cfg = await metricApi.pageConfig(props.topic)
  metrics.value = Object.fromEntries(cfg.metrics.map((m) => [m.code, m]))
  updatedAt.value = cfg.dataUpdatedAt
  pivotDim.value = pivotDims.value.find((d) => !(d in filters.value)) ?? pivotDims.value[0] ?? ''
  await loadAll()
})
watch(picked, () => {
  drillPath.value = []
  loadAll()
})
function removeLinked(k: string) {
  const next = { ...linked.value }
  delete next[k]
  linked.value = next
  loadAll()
}
function selectItem(c: ChartDef, key?: string | null) {
  if (!key || c.type === 'line') return
  if (def.value.pivot.dims.includes(c.dim)) {
    drillPath.value = [{ dim: c.dim, value: key, label: String(chartsLabel(c, key)) }]
    pivotDim.value = def.value.pivot.drill[c.dim] ?? c.dim
    loadPivot()
  }
}
const chartsLabel = (c: ChartDef, key: string) => charts.value.find((v) => v.def === c)?.items?.find((i) => i.key === key)?.label ?? key
const compareLabel = computed(() => (compare.value === 'YOY' ? '同比' : '环比'))
</script>

<template>
  <ErpPanel>
    <PeriodFilter v-model:period="period" v-model:compare="compare" v-model:custom="custom" :updated-at="updatedAt" @change="loadAll">
      <CustomerSelect v-if="def.filters.includes('customer')" v-model="picked.customer" placeholder="客户" class="bi-filter" />
      <SupplierSelect v-if="def.filters.includes('supplier')" v-model="picked.supplier" placeholder="供应商" class="bi-filter" />
      <MaterialSelect v-if="def.filters.includes('material')" v-model="picked.material" placeholder="物料" class="bi-filter" />
      <UserSelect v-if="def.filters.includes('owner')" v-model="picked.owner" placeholder="负责人" class="bi-filter" clearable />
      <el-radio-group v-model="scale" size="small">
        <el-radio-button :value="1">元</el-radio-button>
        <el-radio-button :value="10000">万元</el-radio-button>
      </el-radio-group>
    </PeriodFilter>
    <div v-if="Object.keys(linked).length" class="bi-linked">
      <el-tag v-for="(v, k) in linked" :key="k" closable @close="removeLinked(String(k))">{{ dimLabel(String(k)) }}：{{ v }}</el-tag>
    </div>
  </ErpPanel>

  <div v-loading="loading" class="bi-topic">
    <div class="bi-kpis">
      <BiKpi v-for="c in kpiCodes" :key="c" :code="c" :name="name(c)" :unit="unit(c)!" :value="kpiRows.cur[c] as number"
             :compare-value="kpiRows.prev[c] as number" :change-pct="changePct(kpiRows.cur[c] as number, kpiRows.prev[c] as number)"
             :change-pt="kpiRows.cur[c] != null && kpiRows.prev[c] != null ? Number(kpiRows.cur[c]) - Number(kpiRows.prev[c]) : null"
             :compare-label="compareLabel" :scale="scale" />
    </div>
    <ErpEmpty v-if="!kpiCodes.length && !charts.length" description="没有可查看的指标，或当前筛选条件不适用于本页指标" />

    <div class="bi-grid">
      <ErpPanel v-for="c in charts" :key="c.def.title" :title="c.def.title">
        <BiLineChart v-if="c.def.type === 'line'" :labels="c.labels!" :series="c.series!" :unit="unit(c.def.metrics[0])" />
        <BiBarChart v-else-if="c.def.type === 'bar'" :items="c.items!" :unit="unit(c.def.metric)" :color="c.def.color ?? 1"
                    @select="(it) => selectItem(c.def, it.key)" />
        <BiDonut v-else :items="c.items!" :unit="unit(c.def.metric)" @select="(it) => selectItem(c.def, it.key)" />
      </ErpPanel>
    </div>

    <ErpPanel title="明细">
      <template #extra>
        <el-select v-model="pivotDim" class="bi-dim" @change="drillPath = []; loadPivot()">
          <el-option v-for="d in pivotDims" :key="d" :value="d" :label="`按${dimLabel(d)}`" />
        </el-select>
        <el-button v-if="user.hasPermission('bi:export')" icon="Download" :disabled="!pivotRows.length" @click="exportPivot">导出</el-button>
      </template>
      <el-breadcrumb v-if="drillPath.length" separator="/" class="bi-crumb">
        <el-breadcrumb-item v-for="(p, i) in drillPath" :key="p.dim">
          <a @click="backTo(i)">{{ dimLabel(p.dim) }}：{{ p.label }}</a>
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ dimLabel(pivotDim) }}</el-breadcrumb-item>
      </el-breadcrumb>
      <el-table v-loading="pivotLoading" :data="pivotRows" max-height="520" @row-click="drill">
        <el-table-column :label="dimLabel(pivotDim)" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'bi-link': def.pivot.drill[pivotDim] }">{{ row[`${pivotDim}_label`] }}</span>
          </template>
        </el-table-column>
        <el-table-column v-for="c in pivotMetrics" :key="c" :label="name(c)" align="right" min-width="130" sortable
                         :sort-method="(a: BiRow, b: BiRow) => Number(a[c] ?? -Infinity) - Number(b[c] ?? -Infinity)">
          <template #default="{ row }">{{ fmtMetric(row[c], unit(c), scale) }}</template>
        </el-table-column>
        <el-table-column v-if="pivotMetrics.length" :label="`${name(pivotMetrics[0])}同比`" align="right" width="120">
          <template #default="{ row }">{{ yoyText(row) }}</template>
        </el-table-column>
      </el-table>
    </ErpPanel>
  </div>
</template>

<style scoped>
.bi-topic { display: flex; flex-direction: column; gap: var(--erp-section-gap); }
.bi-kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: var(--erp-space-4); }
.bi-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--erp-section-gap); }
@media (max-width: 1200px) { .bi-grid { grid-template-columns: minmax(0, 1fr); } }
.bi-filter { width: 180px; }
.bi-dim { width: 140px; }
.bi-linked { margin-top: var(--erp-space-3); display: flex; gap: var(--erp-space-2); flex-wrap: wrap; }
.bi-crumb { margin-bottom: var(--erp-space-3); }
.bi-crumb a { cursor: pointer; }
.bi-link { color: var(--erp-color-primary); cursor: pointer; }
</style>
