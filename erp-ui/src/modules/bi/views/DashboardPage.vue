<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type CompareCode, dashboardApi, type Dashboard, type PeriodCode } from '../api/bi'
import PeriodFilter from '../components/PeriodFilter.vue'
import BiKpi from '../components/BiKpi.vue'
import BiLineChart from '../components/BiLineChart.vue'
import BiBarChart from '../components/BiBarChart.vue'
import BiDonut from '../components/BiDonut.vue'
import { fmtMetric } from '../components/biFormat'

/** 经营驾驶舱（需求 13-02）：KPI 行、12 个月趋势、客户 Top10、交付、品类占比、质量、库存结构；点击下钻到专题页并带入期间 */
const router = useRouter()
const period = ref<PeriodCode>('THIS_MONTH')
const compare = ref<CompareCode>('MOM')
const custom = ref<[string, string] | null>(null)
const data = ref<Dashboard>()
const loading = ref(false)

async function load() {
  if (period.value === 'CUSTOM' && !custom.value) return
  loading.value = true
  try {
    data.value = await dashboardApi.get({ period: period.value, compare: compare.value, from: custom.value?.[0], to: custom.value?.[1] })
  } finally {
    loading.value = false
  }
}
onMounted(load)

const compareLabel = computed(() => (compare.value === 'YOY' ? '同比' : '环比'))
const rangeQuery = computed(() => (data.value ? { from: data.value.from, to: data.value.to } : {}))
const go = (path: string, extra: Record<string, string> = {}) => router.push({ path, query: { ...rangeQuery.value, ...extra } })

const trendLabels = computed(() => data.value?.trend.map((t) => t.month.slice(2)) ?? [])
const trendSeries = computed(() => data.value ? [
  { name: '接单额', values: data.value.trend.map((t) => t.order) },
  { name: '出货额', values: data.value.trend.map((t) => t.ship) },
  { name: '回款额', values: data.value.trend.map((t) => t.receipt) }
] : [])
function onTrend(i: number) {
  const m = data.value?.trend[i]?.month
  if (!m) return
  const [y, mm] = m.split('-').map(Number)
  const last = new Date(y, mm, 0).getDate()
  router.push({ path: '/bi/sales', query: { from: `${m}-01`, to: `${m}-${String(last).padStart(2, '0')}` } })
}
const qualityLabels = computed(() => data.value?.quality.map((q) => q.month.slice(2)) ?? [])
const onTimeRate = computed(() => Number(data.value?.delivery.onTimeRate ?? 0))

function exportPdf() {
  window.print()
}
</script>

<template>
  <ErpPage description="管理层一屏查看经营全貌；点击卡片或图表下钻到专题分析">
    <template #actions>
      <el-button icon="Printer" @click="exportPdf">导出 PDF</el-button>
    </template>
    <ErpPanel>
      <PeriodFilter v-model:period="period" v-model:compare="compare" v-model:custom="custom" :updated-at="data?.dataUpdatedAt" @change="load" />
    </ErpPanel>

    <div v-loading="loading" class="bi-dash">
      <div class="bi-kpis">
        <BiKpi v-for="k in data?.kpis ?? []" :key="k.code" v-bind="k" :compare-label="compareLabel" clickable @click="k.route && go(k.route)" />
      </div>

      <div class="bi-grid">
        <ErpPanel title="近 12 个月 接单 / 出货 / 回款">
          <BiLineChart :labels="trendLabels" :series="trendSeries" unit="AMOUNT" @select="onTrend" />
        </ErpPanel>
        <ErpPanel title="客户 Top10 出货额">
          <BiBarChart :items="(data?.topCustomers ?? []).map((c) => ({ key: c.id, label: c.label, value: c.value, sub: c.share != null ? `${c.share}%` : '' }))"
                      unit="AMOUNT" @select="(it) => it.key && go('/bi/sales', { customer: it.key })" />
        </ErpPanel>
        <ErpPanel title="交付">
          <div class="bi-delivery" @click="router.push('/pmc/alert')">
            <div class="bi-gauge">
              <el-progress type="dashboard" :percentage="Math.min(100, Math.max(0, onTimeRate))" :width="140">
                <template #default>
                  <div class="bi-gauge__value">{{ fmtMetric(data?.delivery.onTimeRate, 'PERCENT') }}</div>
                  <div class="bi-gauge__label">交期达成率</div>
                </template>
              </el-progress>
            </div>
            <dl class="bi-facts">
              <div><dt>在手订单金额</dt><dd>{{ fmtMetric(data?.delivery.openOrderAmount, 'AMOUNT') }}</dd></div>
              <div><dt>已逾交期未出货订单行</dt><dd>{{ data?.delivery.overdueLines ?? '-' }}</dd></div>
            </dl>
          </div>
        </ErpPanel>
        <ErpPanel title="产品类别 出货额占比">
          <BiDonut :items="(data?.categories ?? []).map((c) => ({ key: c.id ?? null, label: c.label, value: c.value }))" unit="AMOUNT"
                   @select="(it) => it.key && go('/bi/sales', { category: it.key })" />
        </ErpPanel>
        <ErpPanel title="质量（近 6 个月）">
          <div class="bi-mini" @click="go('/bi/quality')">
            <div>
              <div class="bi-mini__title">来料批次合格率</div>
              <BiLineChart :labels="qualityLabels" :series="[{ name: '来料批次合格率', values: data?.quality.map((q) => q.iqcPassRate) ?? [] }]" unit="PERCENT" :height="120" />
            </div>
            <div>
              <div class="bi-mini__title">直通率</div>
              <BiLineChart :labels="qualityLabels" :series="[{ name: '直通率', values: data?.quality.map((q) => q.fpy) ?? [] }]" unit="PERCENT" :height="120" />
            </div>
            <div>
              <div class="bi-mini__title">客诉数</div>
              <BiLineChart :labels="qualityLabels" :series="[{ name: '客诉数', values: data?.quality.map((q) => q.complaints) ?? [] }]" unit="COUNT" :height="120" />
            </div>
          </div>
        </ErpPanel>
        <ErpPanel title="库存结构（按仓库类型）">
          <template #extra><span class="bi-note">呆滞金额 {{ fmtMetric(data?.slowMovingAmount, 'AMOUNT') }}</span></template>
          <BiBarChart :items="(data?.inventory ?? []).map((i) => ({ key: i.type, label: i.label, value: i.amount }))" unit="AMOUNT" :color="3"
                      @select="(it) => go('/bi/inventory', it.key ? { warehouse_type: it.key } : {})" />
        </ErpPanel>
      </div>
    </div>
  </ErpPage>
</template>

<style scoped>
.bi-dash { display: flex; flex-direction: column; gap: var(--erp-section-gap); }
.bi-kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: var(--erp-space-4); }
.bi-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--erp-section-gap); }
@media (max-width: 1200px) { .bi-grid { grid-template-columns: minmax(0, 1fr); } }
.bi-delivery { display: flex; align-items: center; gap: var(--erp-space-6); cursor: pointer; }
.bi-gauge__value { font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); color: var(--erp-color-text); }
.bi-gauge__label { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.bi-facts { margin: 0; display: flex; flex-direction: column; gap: var(--erp-space-3); }
.bi-facts dt { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.bi-facts dd { margin: 0; font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); font-variant-numeric: tabular-nums; }
.bi-mini { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--erp-space-3); cursor: pointer; }
.bi-mini__title { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.bi-note { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
@media print { .bi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
