<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty } from '@/utils/format'
import { joinList, NOTICE_STATUS, noticeApi, num, OQC_RESULT, optionsOf, type NoticeRow, type OrderLineOption, type PlanLineOption } from '../api/shipping'

defineOptions({ name: 'ShpNoticeList' })

/** 出货通知列表（需求 11-01 3.1，T1）：从订单选单新建、从出货计划生成 */
const router = useRouter()
const today = new Date()
const iso = (d: Date) => d.toISOString().slice(0, 10)
const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - ((today.getDay() + 6) % 7))
type Query = { docNo?: string; customerId?: string; orderNo?: string; statuses?: string[]; dates?: [string, string]; transportMode?: string; ownerId?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, NoticeRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return noticeApi.page({ ...rest, statuses: joinList(statuses), shipDateFrom: dates?.[0], shipDateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ statuses: ['OPEN'], dates: [iso(monday), iso(new Date(monday.getTime() + 30 * 86400000))] }),
  refreshOnActivated: true
})
const statusOptions = [{ value: 'OPEN', label: '未出货未关闭' }, ...optionsOf(NOTICE_STATUS)]
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'statuses', label: '状态', type: 'select', options: statusOptions, multiple: true },
  { prop: 'dates', label: '出货日期', type: 'daterange' },
  { prop: 'transportMode', label: '运输方式', type: 'dict', dictType: 'shp_transport_mode' },
  { prop: 'ownerId', label: '船务', type: 'user' }
]
const progress = (done: string, all: string) => (num(all) ? `${Math.round((num(done) / num(all)) * 100)}%` : '-')
const columns: TableColumn<NoticeRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/shipping/notice/${r.id}`) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'shipDate', label: '出货日期', width: 110, slot: true },
  { prop: 'transportMode', label: '运输方式', width: 90, type: 'dict', dictType: 'shp_transport_mode' },
  { prop: 'portOfDestination', label: '目的港', width: 110 },
  { prop: 'lineCount', label: '行数', width: 60, align: 'right' },
  { prop: 'totalQty', label: '数量', width: 100, type: 'qty' },
  { prop: 'totalAmount', label: '金额', width: 120, type: 'amount', currencyProp: 'currency' },
  { key: 'pick', label: '拣货', width: 70, align: 'right', formatter: (r) => progress(r.pickedQty, r.totalQty) },
  { key: 'pack', label: '装箱', width: 70, align: 'right', formatter: (r) => progress(r.packedQty, r.totalQty) },
  { key: 'ship', label: '出货', width: 70, align: 'right', formatter: (r) => progress(r.shippedQty, r.totalQty) },
  { prop: 'oqcResult', label: 'OQC', width: 80, slot: true },
  { prop: 'noticeStatus', label: '状态', width: 90, type: 'status', statusMap: NOTICE_STATUS },
  { prop: 'ownerName', label: '船务', width: 90 },
  { prop: 'warehouseName', label: '出货仓', width: 100, hidden: true },
  { prop: 'createdAt', label: '创建时间', width: 140, type: 'datetime', hidden: true }
]
const asRow = (r: unknown) => r as NoticeRow

// 从订单选单：选中的订单行（同一客户）直接生成通知草稿
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<OrderLineOption[]> }>()
const pickCustomer = ref<string>()
const orderColumns: TableColumn<OrderLineOption>[] = [
  { prop: 'orderNo', label: '订单号', width: 150 },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'customerPoNo', label: '客户 PO', width: 120 },
  { prop: 'materialCode', label: '物料编码', width: 120 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'customerPartNo', label: '客户料号', width: 110 },
  { prop: 'qty', label: '订单数量', width: 90, type: 'qty' },
  { prop: 'noticedQty', label: '已通知', width: 90, type: 'qty' },
  { prop: 'noticeableQty', label: '可通知', width: 90, type: 'qty' },
  { prop: 'promisedDate', label: '交期', width: 100, type: 'date' },
  { prop: 'availableQty', label: '可用库存', width: 90, type: 'qty' }
]
async function orderLinesApi(q: Record<string, any>) {
  const rows = await noticeApi.orderLines({ customerId: q.customerId, orderNo: q.orderNo })
  const from = (q.pageNo - 1) * q.pageSize
  return { list: rows.slice(from, from + q.pageSize), total: rows.length }
}
const customerDialog = ref(false)
async function fromOrders() {
  if (!pickCustomer.value) return ElMessage.warning('请选择客户')
  customerDialog.value = false
  const rows = await pickerRef.value?.open({ customerId: pickCustomer.value })
  if (!rows?.length) return
  const r = await noticeApi.fromOrders({ orderLineIds: rows.map((x) => x.orderLineId) })
  if (r.warnings.length) ElMessage.warning(r.warnings.join('；'))
  router.push(`/shipping/notice/${r.id}/edit`)
}

// 从出货计划生成
const planDialog = ref(false)
const planWeek = ref('')
const planLines = ref<PlanLineOption[]>([])
const planSelected = ref<PlanLineOption[]>([])
const planLoading = ref(false)
function weekOf(d: Date) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
  const day = t.getUTCDay() || 7
  t.setUTCDate(t.getUTCDate() + 4 - day)
  const y = new Date(Date.UTC(t.getUTCFullYear(), 0, 1))
  const w = Math.ceil(((t.getTime() - y.getTime()) / 86400000 + 1) / 7)
  return `${t.getUTCFullYear()}-W${String(w).padStart(2, '0')}`
}
async function openPlan() {
  planWeek.value = planWeek.value || weekOf(today)
  planDialog.value = true
  await loadPlan()
}
async function loadPlan() {
  planLoading.value = true
  try {
    planLines.value = await noticeApi.planLines(planWeek.value)
  } finally {
    planLoading.value = false
  }
}
async function generatePlan() {
  const ids = await noticeApi.fromPlan({ planWeek: planWeek.value, planLineIds: planSelected.value.map((p) => p.planLineId) })
  ElMessage.success(`已生成 ${ids.length} 张出货通知草稿`)
  planDialog.value = false
  load()
}
</script>

<template>
  <ErpPage description="船务按销售订单（或 PMC 出货计划）安排出货；审核后生成拣货单并占用订单可出货数量">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="shp.notice" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'shp:notice:create'" type="primary" icon="Plus" @click="router.push('/shipping/notice/new')">新建</el-button>
          <el-button v-perm="'shp:notice:create'" @click="customerDialog = true">从订单选单</el-button>
          <el-button v-perm="'shp:notice:create'" @click="openPlan">从出货计划生成</el-button>
          <ExportButton url="/shipping/notices/export" :params="() => ({ ...query, statuses: joinList(query.statuses) })" permission="shp:notice:query" />
        </template>
        <template #col-shipDate="{ row }">
          <span :class="{ overdue: asRow(row).overdue }">{{ asRow(row).shipDate }}</span>
        </template>
        <template #col-oqcResult="{ row }">
          <StatusTag v-if="asRow(row).oqcResult" :value="asRow(row).oqcResult" :map="OQC_RESULT" />
          <span v-else>{{ asRow(row).oqcRequired ? '需要' : '免检' }}</span>
        </template>
        <template #empty>
          <el-button v-perm="'shp:notice:create'" icon="Plus" @click="router.push('/shipping/notice/new')">新建出货通知</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="customerDialog" title="从订单选单" width="420px">
      <el-form label-width="60px"><el-form-item label="客户"><CustomerSelect v-model="pickCustomer" /></el-form-item></el-form>
      <template #footer>
        <el-button @click="customerDialog = false">取消</el-button>
        <el-button type="primary" @click="fromOrders">选择订单行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDialog" title="从出货计划生成" width="1000px">
      <el-form inline>
        <el-form-item label="计划周"><el-input v-model="planWeek" placeholder="2026-W40" class="week" @change="loadPlan" /></el-form-item>
      </el-form>
      <el-table v-loading="planLoading" :data="planLines" max-height="420" @selection-change="(v: PlanLineOption[]) => (planSelected = v)">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="planNo" label="计划" width="140" />
        <el-table-column prop="customerName" label="客户" min-width="120" />
        <el-table-column label="订单" width="160"><template #default="{ row }">{{ row.orderNo }} 行 {{ row.lineNo }}</template></el-table-column>
        <el-table-column label="物料" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="计划 / 已通知" width="130" align="right">
          <template #default="{ row }">{{ formatQty(row.planQty) }} / {{ formatQty(row.noticedQty) }}</template>
        </el-table-column>
        <el-table-column prop="planShipDate" label="计划出货" width="100" />
        <template #empty><ErpEmpty compact description="该周没有未通知完的出货计划行" /></template>
      </el-table>
      <template #footer>
        <span class="hint">不勾选时生成全部；按客户与币别分组，每组一张草稿</span>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!planLines.length" @click="generatePlan">生成</el-button>
      </template>
    </el-dialog>

    <SourceDocPicker ref="pickerRef" title="选择订单行（已审核、可通知数量 > 0）" :api="orderLinesApi" :columns="orderColumns" row-key="orderLineId"
                     :search-fields="[{ prop: 'orderNo', label: '订单号', upper: true }]" />
  </ErpPage>
</template>

<style scoped>
.overdue { color: var(--erp-color-error); }
.week { width: 140px; }
.hint { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
</style>
