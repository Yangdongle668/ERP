<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatDateTime, formatPrice, today, toDateString } from '@/utils/format'
import { joinList, LINE_STATUS, num } from '../api/common'
import { ORDER_STATUS_OPTIONS } from '../api/order'
import {
  reportApi, SUMMARY_DIM_OPTIONS,
  type ExecutionQuery, type ExecutionRow, type PriceTrend, type SummaryRow, type TrackingQuery, type TrackingRow
} from '../api/report'

defineOptions({ name: 'PurReportPage' })

/** 采购报表（需求 07-11，T7）：交期跟踪、订单执行表、价格趋势、采购汇总 */
const router = useRouter()
const me = useUserStore()
const tab = ref<'tracking' | 'execution' | 'trend' | 'summary'>('tracking')
const updatedAt = ref(`${today()} ${new Date().toTimeString().slice(0, 5)}`)
const canPrice = computed(() => me.hasPermission('pur:price:view'))

// ---------- 交期跟踪 ----------
type TQuery = Omit<TrackingQuery, 'pageNo' | 'pageSize'> & { required?: [string, string]; confirmed?: [string, string] }
const toTrackParams = (q: TQuery) => {
  const { required, confirmed, ...rest } = q
  return { ...rest, requiredFrom: required?.[0], requiredTo: required?.[1], confirmedFrom: confirmed?.[0], confirmedTo: confirmed?.[1] }
}
const tracking = useListPage<TQuery, TrackingRow>({
  api: (q) => reportApi.tracking(toTrackParams(q) as TrackingQuery),
  defaultQuery: () => ({ overdueOnly: true })
})
const trackQuery = tracking.query
const trackFields: SearchField[] = [
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'ownerId', label: '采购员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'overdueOnly', label: '只看逾期', type: 'select', options: [{ value: true, label: '是' }, { value: false, label: '否' }], clearable: false },
  { prop: 'required', label: '要求日期', type: 'daterange' },
  { prop: 'confirmed', label: '确认交期', type: 'daterange' },
  { prop: 'unconfirmedOnly', label: '未回复交期', type: 'select', options: [{ value: true, label: '只看未回复' }] }
]
const trackColumns: TableColumn<TrackingRow>[] = [
  { prop: 'orderNo', label: '订单号', width: 150, type: 'link', onClick: (r) => router.push(`/purchase/order/${r.orderId}`) },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'supplierName', label: '供应商', width: 120 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'materialSpec', label: '规格', minWidth: 140, hidden: true },
  { prop: 'qty', label: '订购', width: 90, type: 'qty' },
  { prop: 'receivedQty', label: '已到货', width: 90, type: 'qty' },
  { prop: 'openQty', label: '未到货', width: 90, type: 'qty' },
  { prop: 'requiredDate', label: '要求日期', width: 110, type: 'date' },
  { prop: 'confirmedDate', label: '确认交期', width: 110, type: 'date' },
  { prop: 'overdueDays', label: '逾期天数', width: 90, slot: true },
  { prop: 'demand', label: '关联需求', width: 140 },
  { prop: 'ownerName', label: '采购员', width: 90 },
  { prop: 'lastFollowUp', label: '最近跟催', minWidth: 180, formatter: (r) => (r.lastFollowUp ? `${r.lastFollowUp}（${formatDateTime(r.followUpAt, true)}）` : '') }
]
const asTrack = (r: unknown) => r as TrackingRow

const followVisible = ref(false)
const followRow = ref<TrackingRow>()
const followContent = ref('')
const followDate = ref<string>()
const following = ref(false)
function openFollow(r: TrackingRow) {
  followRow.value = r
  followContent.value = ''
  followDate.value = undefined
  followVisible.value = true
}
async function saveFollow() {
  if (!followContent.value.trim()) return ElMessage.warning('请填写跟催内容')
  following.value = true
  try {
    await reportApi.followUp(followRow.value!.orderLineId, followContent.value.trim(), followDate.value)
    followVisible.value = false
    ElMessage.success('已记录跟催')
    tracking.load()
  } finally {
    following.value = false
  }
}

// ---------- 订单执行表 ----------
type EQuery = Omit<ExecutionQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; dates?: [string, string] }
const toExecParams = (q: EQuery) => {
  const { statuses, dates, ...rest } = q
  return { ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] }
}
const execution = useListPage<EQuery, ExecutionRow>({
  api: (q) => reportApi.execution(toExecParams(q) as ExecutionQuery),
  defaultQuery: () => ({ statuses: ['APPROVED', 'IN_PROGRESS'] }),
  immediate: false
})
const execQuery = execution.query
const execFields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: ORDER_STATUS_OPTIONS, multiple: true },
  { prop: 'ownerId', label: '采购员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]
const execColumns = computed<TableColumn<ExecutionRow>[]>(() => [
  { prop: 'orderNo', label: '订单号', width: 150, type: 'link', onClick: (r) => router.push(`/purchase/order/${r.orderId}`) },
  { prop: 'docDate', label: '日期', width: 110, type: 'date' },
  { prop: 'supplierName', label: '供应商', width: 120 },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'qty', label: '订购', width: 90, type: 'qty' },
  { prop: 'receivedQty', label: '到货', width: 90, type: 'qty' },
  { prop: 'stockedQty', label: '入库', width: 90, type: 'qty' },
  { prop: 'qualifiedQty', label: '合格', width: 90, type: 'qty' },
  { prop: 'returnedQty', label: '退货', width: 90, type: 'qty' },
  { prop: 'statementQty', label: '对账', width: 90, type: 'qty' },
  { prop: 'openQty', label: '未到货', width: 90, type: 'qty' },
  ...(canPrice.value ? [
    { prop: 'priceInclTax', label: '含税单价', width: 100, type: 'price' } as TableColumn<ExecutionRow>,
    { prop: 'totalAmount', label: '金额', width: 120, type: 'amount', currencyProp: 'currency' } as TableColumn<ExecutionRow>
  ] : []),
  { prop: 'lineStatus', label: '行状态', width: 80, type: 'status', statusMap: LINE_STATUS }
])

// ---------- 价格趋势 ----------
function monthsAgo(n: number) {
  const d = new Date()
  d.setMonth(d.getMonth() - n)
  return toDateString(d)
}
const trendMaterials = ref<string[]>([])
const trendDates = ref<[string, string]>([monthsAgo(12), today()])
const trend = ref<PriceTrend>()
const loadingTrend = ref(false)
async function loadTrend() {
  if (!trendMaterials.value.length) return ElMessage.warning('请选择物料')
  if (trendMaterials.value.length > 5) return ElMessage.warning('最多选择 5 个物料')
  loadingTrend.value = true
  try {
    trend.value = await reportApi.priceTrend({ materialIds: trendMaterials.value.join(','), dateFrom: trendDates.value[0], dateTo: trendDates.value[1] })
  } finally {
    loadingTrend.value = false
  }
}
/** 每月加权平均价的透视表：行 = 物料，列 = 月份 */
const pivot = computed(() => (trend.value?.series ?? []).map((s) => {
  const row: Record<string, string> = { material: `${s.materialCode} ${s.materialName}` }
  for (const p of s.points) row[p.month] = p.avgPrice ? formatPrice(p.avgPrice) : '-'
  return row
}))
const trendParams = () => ({ materialIds: trendMaterials.value.join(','), dateFrom: trendDates.value[0], dateTo: trendDates.value[1] })

// ---------- 采购汇总 ----------
const dim1 = ref('SUPPLIER')
const dim2 = ref<string>()
const sumDates = ref<[string, string]>([monthsAgo(3), today()])
const summary = ref<SummaryRow[]>([])
const loadingSummary = ref(false)
async function loadSummary() {
  loadingSummary.value = true
  try {
    summary.value = await reportApi.summary({ dim1: dim1.value, dim2: dim2.value, dateFrom: sumDates.value[0], dateTo: sumDates.value[1] })
  } finally {
    loadingSummary.value = false
  }
}
const summaryParams = () => ({ dim1: dim1.value, dim2: dim2.value, dateFrom: sumDates.value[0], dateTo: sumDates.value[1] })
const topSuppliers = computed(() => (dim1.value === 'SUPPLIER' && !dim2.value ? [...summary.value].sort((a, b) => num(b.orderAmount) - num(a.orderAmount)).slice(0, 10) : []))
const maxAmount = computed(() => Math.max(1, ...topSuppliers.value.map((r) => num(r.orderAmount))))
const rate = (v?: string) => (v === undefined || v === null ? '-' : `${Number(v).toFixed(2)}%`)

function onTab(t: string | number) {
  if (t === 'execution' && !execution.list.value.length) execution.load()
  if (t === 'summary' && !summary.value.length) loadSummary()
}
</script>

<template>
  <ErpPage description="交期跟踪、订单执行、价格趋势与采购汇总；所有报表可按当前条件导出">
    <template #meta><span class="text-muted">数据更新于 {{ updatedAt }}</span></template>
    <ErpPanel flush>
      <el-tabs v-model="tab" class="list-tabs" @tab-change="onTab">
        <el-tab-pane label="交期跟踪" name="tracking">
          <ErpSearchForm v-model="trackQuery" :fields="trackFields" :loading="tracking.loading.value" @search="tracking.search" @reset="tracking.reset">
            <template #field-supplierId><SupplierSelect v-model="trackQuery.supplierId" /></template>
            <template #field-materialId><MaterialSelect v-model="trackQuery.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="trackColumns" :data="tracking.list.value" :loading="tracking.loading.value" storage-key="pur.report.tracking" :actions-width="100"
                    @refresh="tracking.load">
            <template #toolbar-right>
              <ExportButton url="/purchase/reports/delivery-tracking/export" :params="() => toTrackParams({ ...trackQuery })" filename="交期跟踪" permission="pur:report:export" />
            </template>
            <template #col-overdueDays="{ row }">
              <span :class="{ 'text-danger': asTrack(row).overdueDays > 0 }">{{ asTrack(row).overdueDays > 0 ? `${asTrack(row).overdueDays} 天` : '-' }}</span>
            </template>
            <template #actions="{ row }">
              <RowActions :actions="[{ label: '记录跟催', handler: () => openFollow(asTrack(row)) }]" />
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="trackQuery.pageNo" v-model:page-size="trackQuery.pageSize" :total="tracking.total.value" @change="tracking.load" />
        </el-tab-pane>

        <el-tab-pane label="订单执行表" name="execution">
          <ErpSearchForm v-model="execQuery" :fields="execFields" :loading="execution.loading.value" @search="execution.search" @reset="execution.reset">
            <template #field-supplierId><SupplierSelect v-model="execQuery.supplierId" /></template>
            <template #field-materialId><MaterialSelect v-model="execQuery.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="execColumns" :data="execution.list.value" :loading="execution.loading.value" storage-key="pur.report.execution" @refresh="execution.load">
            <template #toolbar-right>
              <ExportButton url="/purchase/reports/order-execution/export" :params="() => toExecParams({ ...execQuery })" filename="采购订单执行表" permission="pur:report:export" />
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="execQuery.pageNo" v-model:page-size="execQuery.pageSize" :total="execution.total.value" @change="execution.load" />
        </el-tab-pane>

        <el-tab-pane label="价格趋势" name="trend">
          <div class="bar">
            <MaterialSelect v-model="trendMaterials" multiple placeholder="选择物料（最多 5 个）" class="w-materials" />
            <el-date-picker v-model="trendDates" type="daterange" value-format="YYYY-MM-DD" />
            <el-button type="primary" icon="Search" :loading="loadingTrend" @click="loadTrend">查询</el-button>
            <span class="erp-spacer" />
            <ExportButton url="/purchase/reports/price-trend/export" :params="trendParams" filename="采购价格趋势" permission="pur:report:export" />
          </div>
          <template v-if="trend">
            <div class="group-title">每月加权平均单价（本位币、不含税，按合格数量加权）</div>
            <el-table :data="pivot">
              <el-table-column prop="material" label="物料" min-width="200" fixed="left" />
              <el-table-column v-for="m in trend.months" :key="m" :prop="m" :label="m" width="100" align="right" />
            </el-table>
            <div class="group-title">明细</div>
            <el-table :data="trend.rows" max-height="400">
              <el-table-column prop="month" label="月份" width="90" />
              <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="supplierName" label="供应商" width="130" />
              <el-table-column prop="qty" label="数量" width="100" align="right" />
              <el-table-column label="平均单价" width="110" align="right"><template #default="{ row }">{{ formatPrice(row.avgPrice) }}</template></el-table-column>
              <el-table-column label="最高" width="100" align="right"><template #default="{ row }">{{ formatPrice(row.maxPrice) }}</template></el-table-column>
              <el-table-column label="最低" width="100" align="right"><template #default="{ row }">{{ formatPrice(row.minPrice) }}</template></el-table-column>
              <template #empty><ErpEmpty compact /></template>
            </el-table>
          </template>
          <ErpEmpty v-else compact description="选择物料后查询" />
        </el-tab-pane>

        <el-tab-pane label="采购汇总" name="summary">
          <div class="bar">
            <el-select v-model="dim1" class="w160"><el-option v-for="o in SUMMARY_DIM_OPTIONS" :key="o.value" :value="o.value" :label="`按${o.label}`" /></el-select>
            <el-select v-model="dim2" clearable placeholder="交叉维度（可选）" class="w160">
              <el-option v-for="o in SUMMARY_DIM_OPTIONS.filter((x) => x.value !== dim1)" :key="o.value" :value="o.value" :label="o.label" />
            </el-select>
            <el-date-picker v-model="sumDates" type="daterange" value-format="YYYY-MM-DD" />
            <el-button type="primary" icon="Search" :loading="loadingSummary" @click="loadSummary">查询</el-button>
            <span class="erp-spacer" />
            <ExportButton url="/purchase/reports/summary/export" :params="summaryParams" filename="采购汇总" permission="pur:report:export" />
          </div>
          <template v-if="topSuppliers.length && canPrice">
            <div class="group-title">下单金额前 10 名供应商</div>
            <div class="top">
              <div v-for="r in topSuppliers" :key="r.key1" class="top-row">
                <span class="top-name">{{ r.label1 }}</span>
                <el-progress :percentage="Math.round((num(r.orderAmount) / maxAmount) * 100)" :stroke-width="10" :format="() => formatAmount(r.orderAmount)" class="top-bar" />
              </div>
            </div>
          </template>
          <el-table v-loading="loadingSummary" :data="summary">
            <el-table-column prop="label1" :label="SUMMARY_DIM_OPTIONS.find((o) => o.value === dim1)?.label" min-width="160" />
            <el-table-column v-if="dim2" prop="label2" :label="SUMMARY_DIM_OPTIONS.find((o) => o.value === dim2)?.label" min-width="140" />
            <template v-if="canPrice">
              <el-table-column label="下单金额" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.orderAmount) }}</template></el-table-column>
              <el-table-column label="到货金额" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.receivedAmount) }}</template></el-table-column>
              <el-table-column label="合格金额" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.qualifiedAmount) }}</template></el-table-column>
              <el-table-column label="退货金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.returnAmount) }}</template></el-table-column>
            </template>
            <el-table-column prop="orderLineCount" label="订单行数" width="100" align="right" />
            <el-table-column label="准时率" width="100" align="right"><template #default="{ row }">{{ rate(row.ontimeRate) }}</template></el-table-column>
            <el-table-column label="批次合格率" width="110" align="right"><template #default="{ row }">{{ rate(row.lotPassRate) }}</template></el-table-column>
            <template #empty><ErpEmpty compact /></template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="followVisible" title="记录跟催" width="480px" :close-on-click-modal="false" append-to-body>
      <p v-if="followRow" class="form-tip">{{ followRow.orderNo }}-{{ followRow.lineNo }} {{ followRow.materialCode }} {{ followRow.materialName }}，确认交期 {{ followRow.confirmedDate || '-' }}</p>
      <el-form label-width="110px">
        <el-form-item label="跟催内容" required><el-input v-model="followContent" type="textarea" :rows="3" maxlength="500" /></el-form-item>
        <el-form-item label="新承诺日期"><el-date-picker v-model="followDate" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followVisible = false">取消</el-button>
        <el-button type="primary" :loading="following" @click="saveFollow">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.list-tabs { padding: 0 var(--erp-space-5) var(--erp-space-4); }
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); margin: var(--erp-space-3) 0; }
.w-materials { width: 420px; }
.group-title { margin: var(--erp-space-3) 0 var(--erp-space-2); }
.top { display: flex; flex-direction: column; gap: var(--erp-space-2); margin-bottom: var(--erp-space-4); }
.top-row { display: flex; align-items: center; gap: var(--erp-space-3); }
.top-name { width: 160px; flex-shrink: 0; }
.top-bar { flex: 1; }
</style>
