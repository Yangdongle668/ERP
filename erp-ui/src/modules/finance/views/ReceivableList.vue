<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { AR_STATUS, AR_TYPES, arApi, joinList, labelOf, optionsOf, PROGRESS, type ArRow } from '../api/finance'

defineOptions({ name: 'FinReceivableList' })

/** 应收单列表（需求 12-02 3.1，T1）：出货确认自动生成；红字金额为负；底部合计为本位币 */
const router = useRouter()
const me = useUserStore()
const iso = (d: Date) => d.toISOString().slice(0, 10)
const monthStart = () => { const d = new Date(); return iso(new Date(d.getFullYear(), d.getMonth(), 1)) }
type Query = {
  docNo?: string; customerId?: string; arTypes?: string[]; statuses?: string[]; dates?: [string, string]; sourceNo?: string; orderNo?: string
  verifyState?: string; invoiceState?: string; dues?: [string, string]; overdueOnly?: boolean
}
const toParams = (q: Query) => {
  const { arTypes, statuses, dates, dues, ...rest } = q
  return { ...rest, arTypes: joinList(arTypes), statuses: joinList(statuses), bizDateFrom: dates?.[0], bizDateTo: dates?.[1], dueFrom: dues?.[0], dueTo: dues?.[1] }
}
const summary = ref<{ totalAmountBase: string; unverifiedBase: string }>()
const { query, list, total, loading, load, search, reset, selection, onSelectionChange } = useListPage<Query, ArRow>({
  api: (q) => {
    arApi.summary(toParams(q) as never).then((s) => (summary.value = s)).catch(() => undefined)
    return arApi.page(toParams(q) as never)
  },
  defaultQuery: () => ({ dates: [monthStart(), iso(new Date())] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'arTypes', label: '类型', type: 'select', options: AR_TYPES, multiple: true },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(AR_STATUS), multiple: true },
  { prop: 'dates', label: '业务日期', type: 'daterange' },
  { prop: 'sourceNo', label: '来源单号', upper: true },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'verifyState', label: '核销', type: 'select', options: PROGRESS },
  { prop: 'invoiceState', label: '开票', type: 'select', options: PROGRESS },
  { prop: 'dues', label: '到期日', type: 'daterange' },
  { prop: 'overdueOnly', label: '仅逾期', type: 'select', options: [{ value: true, label: '是' }] }
]
const SOURCE_ROUTES: Record<string, string> = { SHP_SHIPMENT: '/shipping/shipment/', SAL_RETURN: '/sales/return/', QC_COMPLAINT: '/quality/complaint/' }
const columns: TableColumn<ArRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/finance/receivable/${r.id}`) },
  { prop: 'arType', label: '类型', width: 100, formatter: (r) => labelOf(AR_TYPES, r.arType) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'sourceNo', label: '来源单号', width: 150, type: 'link', onClick: (r) => r.sourceType && SOURCE_ROUTES[r.sourceType] && router.push(SOURCE_ROUTES[r.sourceType] + r.sourceId) },
  { prop: 'bizDate', label: '业务日期', width: 100, type: 'date' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'totalAmountBase', label: '本位币', width: 120, type: 'amount', summary: true },
  { prop: 'verifiedAmount', label: '已核销', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'unverifiedAmount', label: '未核销', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'invoicedAmount', label: '已开票', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'dueDate', label: '到期日', width: 100, slot: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: AR_STATUS },
  { prop: 'description', label: '说明', minWidth: 140, hidden: true },
  { prop: 'ownerName', label: '业务员', width: 90, hidden: true }
]

async function batchConfirm() {
  const ids = selection.value.filter((r) => r.status === 'DRAFT').map((r) => r.id)
  if (!ids.length) return ElMessage.warning('请勾选草稿状态的应收单')
  const r = await arApi.batchConfirm(ids)
  if (r.errors.length) ElNotification({ type: 'warning', title: `确认 ${r.success} 张，${r.errors.length} 张未确认`, message: r.errors.join('；'), duration: 8000 })
  else ElMessage.success(`已确认 ${r.success} 张`)
  load()
}
function invoice() {
  const rows = selection.value.filter((r) => r.status === 'CONFIRMED' && Number(r.totalAmount) > 0)
  if (!rows.length) return ElMessage.warning('请勾选已确认的蓝字应收单')
  if (new Set(rows.map((r) => `${r.customerId}|${r.currency}`)).size > 1) return ElMessage.warning('请选择同一客户、同一币别的应收')
  router.push({ path: '/finance/receivable/invoice/new', query: { customerId: rows[0].customerId, receivableIds: rows.map((r) => r.id).join(',') } })
}
</script>

<template>
  <ErpPage description="出货确认自动生成应收；退货退款、客诉赔偿生成红字；确认后参与收款核销、账龄与客户信用">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="fin.receivable" @refresh="load"
                @selection-change="onSelectionChange">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:receivable:create-other')" type="primary" @click="router.push('/finance/receivable/other/new')">新建其他应收</el-button>
          <el-button v-if="me.hasPermission('fin:receivable:confirm')" @click="batchConfirm">批量确认</el-button>
          <el-button v-if="me.hasPermission('fin:receivable:invoice')" @click="invoice">开票登记</el-button>
          <ExportButton url="/finance/receivables/export" :params="() => toParams(query)" permission="fin:receivable:export" />
        </template>
        <template #col-dueDate="{ row }">
          <span :class="{ overdue: row.overdueDays > 0 }">{{ row.dueDate ?? '-' }}<template v-if="row.overdueDays > 0">（逾期 {{ row.overdueDays }} 天）</template></span>
        </template>
      </ErpTable>
      <div v-if="summary" class="summary">
        合计（本位币）：价税合计 <b class="num">{{ formatAmount(summary.totalAmountBase) }}</b>，未核销 <b class="num">{{ formatAmount(summary.unverifiedBase) }}</b>
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
