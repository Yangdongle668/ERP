<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty, today, toDateString } from '@/utils/format'
import { centerApi, pct, PROD_STATUS, type Achievement, type OutputRow, type ProgressRow, type VarianceRow } from '../api/production'

defineOptions({ name: 'MfgReportCenter' })

/** 生产报表（需求 09-08，T7）：生产进度、用料差异、产出与工时、计划达成率 */
const router = useRouter()
const tab = ref<'progress' | 'variance' | 'output' | 'achievement'>('progress')
function monthRange(): [string, string] {
  const d = new Date()
  return [toDateString(new Date(d.getFullYear(), d.getMonth(), 1)), today()]
}
const toOrder = (id: string) => router.push(`/production/prod-order/${id}`)

// ---------- 生产进度 ----------
type PQuery = { deptId?: string; materialId?: string; planEnd?: [string, string] }
const toP = (q: PQuery) => ({ deptId: q.deptId, materialId: q.materialId, planEndFrom: q.planEnd?.[0], planEndTo: q.planEnd?.[1] })
const prog = useListPage<PQuery, ProgressRow>({ api: (q) => centerApi.progress({ ...toP(q), pageNo: q.pageNo, pageSize: q.pageSize }) })
const pq = prog.query
const pFields: SearchField[] = [
  { prop: 'deptId', label: '车间', type: 'org' },
  { prop: 'materialId', label: '产品', type: 'slot' },
  { prop: 'planEnd', label: '计划完工', type: 'daterange' }
]
const pColumns: TableColumn<ProgressRow>[] = [
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => toOrder(r.prodOrderId) },
  { prop: 'materialCode', label: '产品编码', width: 120 },
  { prop: 'materialName', label: '产品名称', minWidth: 130 },
  { prop: 'qty', label: '计划', width: 80, type: 'qty' },
  { key: 'ops', label: '工序进度（合格）', minWidth: 260, slot: true },
  { prop: 'completedQty', label: '完工', width: 80, type: 'qty' },
  { prop: 'stockedQty', label: '入库', width: 80, type: 'qty' },
  { prop: 'planEnd', label: '计划完工', width: 110, slot: true },
  { prop: 'salesOrderNo', label: '销售订单', width: 140 },
  { prop: 'customerDueDate', label: '客户交期', width: 110, type: 'date' },
  { prop: 'deptName', label: '车间', width: 100 },
  { prop: 'prodStatus', label: '状态', width: 80, type: 'status', statusMap: PROD_STATUS }
]
const asP = (r: unknown) => r as ProgressRow

// ---------- 用料差异 ----------
type VQuery = { prodOrderId?: string; closed?: [string, string]; materialId?: string; deptId?: string }
const toV = (q: VQuery) => ({ materialId: q.materialId, deptId: q.deptId, closedFrom: q.closed?.[0], closedTo: q.closed?.[1] })
const vari = useListPage<VQuery, VarianceRow>({ api: (q) => centerApi.variance(toV(q)), defaultQuery: () => ({ closed: monthRange() }), immediate: false })
const vq = vari.query
const vFields: SearchField[] = [
  { prop: 'closed', label: '关闭日期', type: 'daterange' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'deptId', label: '车间', type: 'org' }
]
const vColumns: TableColumn<VarianceRow>[] = [
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => toOrder(r.prodOrderId) },
  { prop: 'productCode', label: '产品', width: 120 },
  { prop: 'componentCode', label: '物料编码', width: 120 },
  { prop: 'componentName', label: '物料名称', minWidth: 130 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'theoreticalQty', label: '理论用量', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'netQty', label: '实际净耗用', width: 100, type: 'qty', uomProp: 'uom' },
  { prop: 'varianceQty', label: '差异', width: 90, slot: true },
  { prop: 'varianceRate', label: '差异率', width: 80, formatter: (r) => pct(r.varianceRate) },
  { prop: 'overIssuedQty', label: '超领', width: 80, type: 'qty', uomProp: 'uom' },
  { prop: 'overReasons', label: '超领原因', minWidth: 120 }
]
const asV = (r: unknown) => r as VarianceRow

// ---------- 产出与工时 ----------
const GROUPS = [
  { value: 'DATE', label: '日期' }, { value: 'DEPT', label: '车间' }, { value: 'WORK_CENTER', label: '工作中心' },
  { value: 'SHIFT', label: '班次' }, { value: 'OPERATOR', label: '人员' }, { value: 'PRODUCT', label: '产品' }
]
type OQuery = { dates?: [string, string]; deptId?: string; groupBy?: string }
const toO = (q: OQuery) => ({ from: q.dates?.[0], to: q.dates?.[1], deptId: q.deptId, groupBy: q.groupBy })
const out = useListPage<OQuery, OutputRow>({ api: (q) => centerApi.output(toO(q)), defaultQuery: () => ({ dates: monthRange(), groupBy: 'DATE' }), immediate: false })
const oq = out.query
const oFields: SearchField[] = [
  { prop: 'dates', label: '报工日期', type: 'daterange' },
  { prop: 'deptId', label: '车间', type: 'org' },
  { prop: 'groupBy', label: '分组', type: 'select', options: GROUPS, clearable: false }
]
const oColumns: TableColumn<OutputRow>[] = [
  { prop: 'label', label: '分组', minWidth: 160 },
  { prop: 'goodQty', label: '合格产出', width: 100, type: 'qty', summary: true },
  { prop: 'scrapQty', label: '报废', width: 90, type: 'qty', summary: true },
  { prop: 'workHours', label: '实际工时', width: 100, type: 'qty', precision: 2, summary: true },
  { prop: 'stdHours', label: '标准工时', width: 100, type: 'qty', precision: 2, summary: true },
  { prop: 'efficiency', label: '效率', width: 80, formatter: (r) => pct(r.efficiency) },
  { prop: 'headcount', label: '人数', width: 70, align: 'right' },
  { prop: 'perCapita', label: '人均产量', width: 90, type: 'qty', precision: 2 }
]

// ---------- 计划达成 ----------
const aq = ref<{ dates: [string, string]; deptId?: string }>({ dates: monthRange() })
const ach = ref<Achievement>()
const achLoading = ref(false)
const achParams = () => ({ from: aq.value.dates?.[0], to: aq.value.dates?.[1], deptId: aq.value.deptId })
async function loadAch() {
  achLoading.value = true
  try {
    ach.value = await centerApi.achievement(achParams())
  } finally {
    achLoading.value = false
  }
}

const loaded = new Set<string>(['progress'])
function onTab(t: string | number) {
  if (loaded.has(String(t))) return
  loaded.add(String(t))
  if (t === 'variance') vari.load()
  else if (t === 'output') out.load()
  else if (t === 'achievement') loadAch()
}
</script>

<template>
  <ErpPage description="生产进度、用料差异、产出与工时、计划达成率">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="page-tabs" @tab-change="onTab">
        <el-tab-pane label="生产进度" name="progress">
          <ErpSearchForm v-model="pq" :fields="pFields" :loading="prog.loading.value" @search="prog.search" @reset="prog.reset">
            <template #field-materialId><MaterialSelect v-model="pq.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="pColumns" :data="prog.list.value" :loading="prog.loading.value" row-key="prodOrderId" storage-key="mfg.center-progress" @refresh="prog.load">
            <template #toolbar-right>
              <ExportButton url="/production/reports/progress/export" :params="() => toP(pq)" filename="生产进度" permission="mfg:report-center:export" />
            </template>
            <template #col-ops="{ row }">
              <span v-for="o in asP(row).operations" :key="o.seq" class="op" :class="{ done: o.done }">{{ o.seq }} {{ o.operation }} {{ formatQty(o.goodQty) }}</span>
            </template>
            <template #col-planEnd="{ row }">
              <span :class="{ 'text-danger': asP(row).delayed }">{{ asP(row).planEnd }}</span>
              <ErpBadge v-if="asP(row).delayed" type="danger" :dot="false">延期</ErpBadge>
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="pq.pageNo" v-model:page-size="pq.pageSize" :total="prog.total.value" @change="prog.load" />
        </el-tab-pane>

        <el-tab-pane label="用料差异" name="variance">
          <ErpSearchForm v-model="vq" :fields="vFields" :loading="vari.loading.value" @search="vari.search" @reset="vari.reset">
            <template #field-materialId><MaterialSelect v-model="vq.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="vColumns" :data="vari.list.value" :loading="vari.loading.value" row-key="componentId" storage-key="mfg.center-variance"
                    empty-text="期间内没有已关闭的生产订单" @refresh="vari.load">
            <template #toolbar-right>
              <ExportButton url="/production/reports/material-variance/export" :params="() => toV(vq)" filename="用料差异" permission="mfg:report-center:export" />
            </template>
            <template #col-varianceQty="{ row }">
              <span :class="{ 'text-danger': Number(asV(row).varianceQty) > 0 }">{{ formatQty(asV(row).varianceQty) }}</span>
            </template>
          </ErpTable>
        </el-tab-pane>

        <el-tab-pane label="产出与工时" name="output">
          <ErpSearchForm v-model="oq" :fields="oFields" :loading="out.loading.value" @search="out.search" @reset="out.reset" />
          <ErpTable :columns="oColumns" :data="out.list.value" :loading="out.loading.value" row-key="key" storage-key="mfg.center-output" @refresh="out.load">
            <template #toolbar-right>
              <ExportButton url="/production/reports/output-hours/export" :params="() => toO(oq)" filename="产出与工时" permission="mfg:report-center:export" />
            </template>
          </ErpTable>
        </el-tab-pane>

        <el-tab-pane label="计划达成" name="achievement">
          <el-form inline @submit.prevent>
            <el-form-item label="计划完工"><el-date-picker v-model="aq.dates" type="daterange" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
            <el-form-item label="车间"><OrgTreeSelect v-model="aq.deptId" only-dept class="w-select" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="achLoading" @click="loadAch">查询</el-button>
              <ExportButton url="/production/reports/plan-achievement/export" :params="achParams" filename="计划达成" permission="mfg:report-center:export" />
            </el-form-item>
          </el-form>
          <template v-if="ach">
            <div class="kpis">
              <div class="kpi"><span>应完工</span><strong class="num">{{ ach.dueCount }}</strong></div>
              <div class="kpi"><span>按期完工</span><strong class="num">{{ ach.onTimeCount }}</strong></div>
              <div class="kpi"><span>计划达成率</span><strong class="num">{{ pct(ach.rate) }}</strong></div>
            </div>
            <el-table :data="ach.delayed">
              <el-table-column label="生产订单" width="160">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="toOrder(row.prodOrderId)">{{ row.prodOrderNo }}</el-link></template>
              </el-table-column>
              <el-table-column label="产品" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="planEnd" label="计划完工" width="110" />
              <el-table-column label="实际完工" width="110"><template #default="{ row }">{{ row.actualEnd ?? '-' }}</template></el-table-column>
              <el-table-column label="延期天数" width="90" align="right"><template #default="{ row }"><span class="text-danger">{{ row.delayDays }}</span></template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.prodStatus" :map="PROD_STATUS" /></template></el-table-column>
              <template #empty><ErpEmpty compact description="没有延期订单" /></template>
            </el-table>
          </template>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.page-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.op { display: inline-block; margin-right: var(--erp-space-2); padding: 0 var(--erp-space-1); border-radius: var(--erp-radius-xs); background: var(--erp-color-surface-subtle); }
.op.done { background: var(--erp-color-success-bg); color: var(--erp-color-success); }
.w-select { width: 220px; }
.kpis { display: flex; gap: var(--erp-space-8); margin-bottom: var(--erp-space-4); }
.kpi { display: flex; flex-direction: column; gap: var(--erp-space-1); color: var(--erp-color-text-secondary); }
.kpi strong { font-size: var(--erp-font-size-metric); color: var(--erp-color-text); }
</style>
