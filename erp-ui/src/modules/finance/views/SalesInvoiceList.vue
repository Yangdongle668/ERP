<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { INVOICE_STATUS, labelOf, optionsOf, SALES_INVOICE_TYPES, salesInvoiceApi, type SalesInvoiceLine, type SalesInvoiceRow } from '../api/finance'

defineOptions({ name: 'FinSalesInvoiceList' })

/** 销项发票登记列表（需求 12-02 3.4）：只做开票登记（发票与应收的对应关系），作废 / 红冲回退应收已开票 */
const router = useRouter()
const me = useUserStore()
type Query = { keyword?: string; customerId?: string; invoiceType?: string; status?: string; dates?: [string, string] }
const toParams = (q: Query) => {
  const { dates, ...rest } = q
  return { ...rest, dateFrom: dates?.[0], dateTo: dates?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, SalesInvoiceRow>({
  api: (q) => salesInvoiceApi.page(toParams(q) as never),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'keyword', label: '发票号码' },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'invoiceType', label: '发票类型', type: 'select', options: SALES_INVOICE_TYPES },
  { prop: 'status', label: '状态', type: 'select', options: optionsOf(INVOICE_STATUS) },
  { prop: 'dates', label: '开票日期', type: 'daterange' }
]
const columns: TableColumn<SalesInvoiceRow>[] = [
  { prop: 'docNo', label: '登记号', width: 150, type: 'link', onClick: (r) => open(r) },
  { prop: 'invoiceNo', label: '发票号码', width: 150 },
  { prop: 'invoiceType', label: '发票类型', width: 110, formatter: (r) => labelOf(SALES_INVOICE_TYPES, r.invoiceType) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'invoiceDate', label: '开票日期', width: 100, type: 'date' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'amount', label: '不含税', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'taxAmount', label: '税额', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: INVOICE_STATUS },
  { prop: 'createdByName', label: '登记人', width: 90 },
  { prop: 'voidReason', label: '作废原因', minWidth: 120, hidden: true }
]

const dialog = ref(false)
const current = ref<{ header: SalesInvoiceRow; lines: SalesInvoiceLine[] }>()
async function open(r: SalesInvoiceRow) {
  current.value = await salesInvoiceApi.get(r.id)
  dialog.value = true
}
const reasonRef = ref<{ open: (o?: { title?: string }) => Promise<string | undefined> }>()
async function cancel(red: boolean) {
  const reason = await reasonRef.value?.open({ title: red ? '红冲原因' : '作废原因' })
  if (!reason || !current.value) return
  if (red) await salesInvoiceApi.red(current.value.header.id, reason)
  else await salesInvoiceApi.void(current.value.header.id, reason)
  ElMessage.success(red ? '已红冲' : '已作废')
  dialog.value = false
  load()
}
</script>

<template>
  <ErpPage description="开票登记记录已开发票与应收的对应关系（不对接税控系统）；作废或红冲后回退应收已开票金额与订单已开票数量">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="fin.sales-invoice" @refresh="load">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:receivable:invoice')" type="primary" @click="router.push('/finance/receivable/invoice/new')">开票登记</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dialog" :title="`发票 ${current?.header.invoiceNo ?? ''}`" width="900px">
      <template v-if="current">
        <el-descriptions :column="3">
          <el-descriptions-item label="客户">{{ current.header.customerName }}</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ labelOf(SALES_INVOICE_TYPES, current.header.invoiceType) }}</el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ current.header.invoiceDate }}</el-descriptions-item>
          <el-descriptions-item label="价税合计">{{ current.header.currency }} {{ formatAmount(current.header.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :value="current.header.status" :map="INVOICE_STATUS" /></el-descriptions-item>
          <el-descriptions-item label="备注">{{ current.header.remark ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="current.lines" max-height="360">
          <el-table-column prop="lineNo" label="行" width="50" />
          <el-table-column label="应收单" width="150">
            <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/receivable/${row.receivableId}`)">{{ row.receivableNo }}</el-link></template>
          </el-table-column>
          <el-table-column prop="orderNo" label="订单" width="140" />
          <el-table-column label="物料" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
          <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
        </el-table>
      </template>
      <template #footer>
        <template v-if="current?.header.status === 'REGISTERED' && me.hasPermission('fin:receivable:invoice')">
          <el-button type="danger" plain @click="cancel(false)">作废</el-button>
          <el-button type="warning" plain @click="cancel(true)">红冲</el-button>
        </template>
        <el-button @click="dialog = false">关闭</el-button>
      </template>
    </el-dialog>
    <ReasonDialog ref="reasonRef" />
  </ErpPage>
</template>
