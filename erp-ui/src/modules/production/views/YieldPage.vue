<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { TableColumn } from '@/components'
import { formatQty, today, toDateString } from '@/utils/format'
import { pct, reportApi, type YieldReport, type YieldRow } from '../api/production'

defineOptions({ name: 'MfgYieldPage' })

/** 良率报表（需求 09-06 3.4，T7）：一次良率、最终良率、直通率（各报工点一次良率之积）、趋势与不良柏拉图 */
const GROUPS = [
  { value: 'PRODUCT', label: '产品' }, { value: 'OPERATION', label: '工序' }, { value: 'WORK_CENTER', label: '工作中心' },
  { value: 'SHIFT', label: '班次' }, { value: 'DATE', label: '日期' }
]
function monthStart() {
  const d = new Date()
  return toDateString(new Date(d.getFullYear(), d.getMonth(), 1))
}
const q = ref<{ dates: [string, string]; deptId?: string; materialId?: string; operationSeq?: number; groupBy: string }>({ dates: [monthStart(), today()], groupBy: 'PRODUCT' })
const data = ref<YieldReport>()
const loading = ref(false)
const params = () => ({ from: q.value.dates?.[0], to: q.value.dates?.[1], deptId: q.value.deptId, materialId: q.value.materialId,
  operationSeq: q.value.operationSeq, groupBy: q.value.groupBy })
async function load() {
  loading.value = true
  try {
    data.value = await reportApi.yield(params())
  } finally {
    loading.value = false
  }
}
onMounted(load)

const columns: TableColumn<YieldRow>[] = [
  { prop: 'label', label: '分组', minWidth: 180 },
  { prop: 'inputQty', label: '投入', width: 100, type: 'qty', summary: true },
  { prop: 'goodQty', label: '一次合格', width: 100, type: 'qty', summary: true },
  { prop: 'defectQty', label: '不良', width: 90, type: 'qty', summary: true },
  { prop: 'repairedQty', label: '返修合格', width: 90, type: 'qty', summary: true },
  { prop: 'scrapQty', label: '报废', width: 90, type: 'qty', summary: true },
  { prop: 'firstYield', label: '一次良率', width: 90, formatter: (r) => pct(r.firstYield) },
  { prop: 'finalYield', label: '最终良率', width: 90, formatter: (r) => pct(r.finalYield) },
  { prop: 'defectRate', label: '不良率', width: 90, formatter: (r) => pct(r.defectRate) },
  { prop: 'scrapRate', label: '报废率', width: 90, formatter: (r) => pct(r.scrapRate) }
]
const maxTrend = () => Math.max(1, ...(data.value?.trend ?? []).map((t) => Number(t.inputQty)))
</script>

<template>
  <ErpPage description="良率 = 合格 ÷ 投入（合格 + 不良 + 报废）；直通率按工序号把各报工点的一次良率相乘">
    <ErpPanel>
      <el-form inline class="filters" @submit.prevent>
        <el-form-item label="报工日期"><el-date-picker v-model="q.dates" type="daterange" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
        <el-form-item label="车间"><OrgTreeSelect v-model="q.deptId" only-dept class="w-select" /></el-form-item>
        <el-form-item label="产品"><MaterialSelect v-model="q.materialId" class="w-select" /></el-form-item>
        <el-form-item label="工序号"><el-input-number v-model="q.operationSeq" :min="0" controls-position="right" /></el-form-item>
        <el-form-item label="分组">
          <el-select v-model="q.groupBy" class="w-group"><el-option v-for="g in GROUPS" :key="g.value" :value="g.value" :label="g.label" /></el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="load">查询</el-button>
          <ExportButton url="/production/reports/yield/export" :params="params" filename="良率报表" permission="mfg:defect:query" />
        </el-form-item>
      </el-form>
      <div v-if="data" class="kpis">
        <div class="kpi"><span>投入</span><strong class="num">{{ formatQty(data.inputQty) }}</strong></div>
        <div class="kpi"><span>一次良率</span><strong class="num">{{ pct(data.firstYield) }}</strong></div>
        <div class="kpi"><span>直通率 FPY</span><strong class="num">{{ pct(data.fpy) }}</strong></div>
      </div>
    </ErpPanel>

    <ErpPanel title="良率明细">
      <ErpTable :columns="columns" :data="data?.rows ?? []" :loading="loading" storage-key="mfg.yield" row-key="key" />
    </ErpPanel>

    <el-row :gutter="16">
      <el-col :span="12">
        <ErpPanel title="工序良率" description="直通率 = 各报工点一次良率之积">
          <div v-for="r in data?.operations ?? []" :key="r.key" class="bar-row">
            <span class="bar-label">{{ r.label }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{ width: `${Math.round(Number(r.firstYield ?? 0) * 100)}%` }" /></div>
            <span class="bar-value num">{{ pct(r.firstYield) }}</span>
          </div>
          <ErpEmpty v-if="!data?.operations.length" compact description="没有报工数据" />
        </ErpPanel>
      </el-col>
      <el-col :span="12">
        <ErpPanel title="不良柏拉图" description="按不良代码汇总，累计占比 80% 以内为主要不良">
          <div v-for="p in data?.pareto ?? []" :key="p.defectCode" class="bar-row">
            <span class="bar-label">{{ p.label }}</span>
            <div class="bar-track"><div class="bar-fill" :class="{ minor: Number(p.cumulative) - Number(p.share) >= 0.8 }" :style="{ width: `${Math.round(Number(p.share) * 100)}%` }" /></div>
            <span class="bar-value num">{{ formatQty(p.qty) }}（{{ pct(p.cumulative) }}）</span>
          </div>
          <ErpEmpty v-if="!data?.pareto.length" compact description="没有不良明细" />
        </ErpPanel>
      </el-col>
    </el-row>

    <ErpPanel title="一次良率趋势">
      <div class="trend">
        <div v-for="t in data?.trend ?? []" :key="t.date" class="trend-col" :title="`${t.date} 投入 ${formatQty(t.inputQty)}，良率 ${pct(t.firstYield)}`">
          <span class="trend-value num">{{ pct(t.firstYield, 0) }}</span>
          <div class="trend-bar" :style="{ height: `${Math.max(4, Math.round((Number(t.inputQty) / maxTrend()) * 120))}px` }" />
          <span class="trend-date">{{ t.date.slice(5) }}</span>
        </div>
        <ErpEmpty v-if="!data?.trend.length" compact description="没有报工数据" />
      </div>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; }
.w-select { width: 220px; }
.w-group { width: 120px; }
.kpis { display: flex; gap: var(--erp-space-8); }
.kpi { display: flex; flex-direction: column; gap: var(--erp-space-1); color: var(--erp-color-text-secondary); }
.kpi strong { font-size: var(--erp-font-size-metric); color: var(--erp-color-text); }
.bar-row { display: flex; align-items: center; gap: var(--erp-space-3); margin-bottom: var(--erp-space-2); }
.bar-label { width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { flex: 1; height: var(--erp-space-3); background: var(--erp-color-surface-subtle); border-radius: var(--erp-radius-xs); }
.bar-fill { height: 100%; background: var(--erp-color-primary); border-radius: var(--erp-radius-xs); }
.bar-fill.minor { background: var(--erp-color-border-strong); }
.bar-value { width: 130px; text-align: right; color: var(--erp-color-text-secondary); }
.trend { display: flex; align-items: flex-end; gap: var(--erp-space-2); overflow-x: auto; min-height: 170px; }
.trend-col { display: flex; flex-direction: column; align-items: center; gap: var(--erp-space-1); min-width: 40px; }
.trend-bar { width: 20px; background: var(--erp-color-primary-bg); border-top: 2px solid var(--erp-color-primary); }
.trend-value, .trend-date { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-secondary); }
</style>
