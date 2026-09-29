<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { AP_TYPES, apApi, AR_STATUS, joinList, labelOf, optionsOf, PROGRESS, type ApRow } from '../api/finance'

defineOptions({ name: 'FinPayableList' })

/** 应付单列表（需求 12-04 3.1，T1）：供应商对账单确认后生成（含退货负数、扣款）；登记发票做三单匹配，按到期日申请付款 */
const route = useRoute()
const router = useRouter()
const me = useUserStore()
type Query = {
  docNo?: string; supplierId?: string; apTypes?: string[]; statuses?: string[]; dates?: [string, string]; statementNo?: string; dues?: [string, string]
  overdueOnly?: boolean; invoiceState?: string; payState?: string
}
const toParams = (q: Query) => {
  const { apTypes, statuses, dates, dues, ...rest } = q
  return { ...rest, apTypes: joinList(apTypes), statuses: joinList(statuses), bizDateFrom: dates?.[0], bizDateTo: dates?.[1], dueFrom: dues?.[0], dueTo: dues?.[1] }
}
const summary = ref<{ totalAmountBase: string; unpaidBase: string }>()
const { query, list, total, loading, load, search, reset, selection, onSelectionChange } = useListPage<Query, ApRow>({
  api: (q) => {
    apApi.summary(toParams(q)).then((s) => (summary.value = s)).catch(() => undefined)
    return apApi.page(toParams(q) as never)
  },
  defaultQuery: () => (typeof route.query.dueTo === 'string' ? { dues: ['2000-01-01', route.query.dueTo] as [string, string], statuses: ['CONFIRMED'], payState: 'OPEN' } : {}),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'apTypes', label: '类型', type: 'select', options: AP_TYPES, multiple: true },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(AR_STATUS), multiple: true },
  { prop: 'dates', label: '业务日期', type: 'daterange' },
  { prop: 'statementNo', label: '对账单号', upper: true },
  { prop: 'dues', label: '到期日', type: 'daterange' },
  { prop: 'overdueOnly', label: '仅逾期', type: 'select', options: [{ value: true, label: '是' }] },
  { prop: 'invoiceState', label: '发票', type: 'select', options: PROGRESS },
  { prop: 'payState', label: '付款', type: 'select', options: PROGRESS }
]
const columns: TableColumn<ApRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/finance/payable/${r.id}`) },
  { prop: 'apType', label: '类型', width: 90, formatter: (r) => labelOf(AP_TYPES, r.apType) },
  { prop: 'supplierName', label: '供应商', minWidth: 140 },
  { prop: 'statementNo', label: '对账单', width: 150, type: 'link', onClick: (r) => r.statementId && router.push(`/purchase/statement/${r.statementId}`) },
  { prop: 'bizDate', label: '业务日期', width: 100, type: 'date' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'totalAmountBase', label: '本位币', width: 120, type: 'amount', summary: true },
  { prop: 'invoicedAmount', label: '已匹配发票', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'requestedAmount', label: '已申请', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'verifiedAmount', label: '已付款', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'unpaidAmount', label: '未付款', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'dueDate', label: '到期日', width: 100, slot: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: AR_STATUS },
  { prop: 'description', label: '说明', minWidth: 140, hidden: true }
]

function invoice() {
  const rows = selection.value.filter((r) => r.status === 'CONFIRMED')
  if (!rows.length) return ElMessage.warning('请勾选已确认的应付单')
  if (new Set(rows.map((r) => `${r.supplierId}|${r.currency}`)).size > 1) return ElMessage.warning('请选择同一供应商、同一币别的应付')
  router.push({ path: '/finance/payable/invoice/new', query: { supplierId: rows[0].supplierId, payableIds: rows.map((r) => r.id).join(',') } })
}
function request() {
  const rows = selection.value.filter((r) => r.status === 'CONFIRMED' && Number(r.requestableAmount) > 0)
  if (!rows.length) return ElMessage.warning('请勾选有可申请金额的已确认应付单')
  if (new Set(rows.map((r) => `${r.supplierId}|${r.currency}`)).size > 1) return ElMessage.warning('请选择同一供应商、同一币别的应付')
  router.push({ path: '/finance/payment/request/new', query: { supplierId: rows[0].supplierId, currency: rows[0].currency, payableIds: rows.map((r) => r.id).join(',') } })
}
</script>

<template>
  <ErpPage description="对账单确认生成应付（草稿，确认后可登记发票、申请付款）；已匹配发票 / 已申请 / 已付款的应付不能反确认">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="fin.payable" @refresh="load" @selection-change="onSelectionChange">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:payable:create-other')" type="primary" @click="router.push('/finance/payable/other/new')">新建其他应付</el-button>
          <el-button v-if="me.hasPermission('fin:payable:invoice')" @click="invoice">登记发票</el-button>
          <el-button v-if="me.hasPermission('fin:payment-request:create')" @click="request">申请付款</el-button>
          <ExportButton url="/finance/payables/export" :params="() => toParams(query)" permission="fin:payable:export" />
        </template>
        <template #col-dueDate="{ row }">
          <span :class="{ overdue: row.overdueDays > 0 }">{{ row.dueDate ?? '-' }}<template v-if="row.overdueDays > 0">（逾期 {{ row.overdueDays }} 天）</template></span>
        </template>
      </ErpTable>
      <div v-if="summary" class="summary">
        合计（本位币）：价税合计 <b class="num">{{ formatAmount(summary.totalAmountBase) }}</b>，未付款 <b class="num">{{ formatAmount(summary.unpaidBase) }}</b>
      </div>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.overdue { color: var(--erp-color-error); }
.summary { padding: var(--erp-space-2) var(--erp-space-3); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
.summary b { color: var(--erp-color-text); }
</style>
