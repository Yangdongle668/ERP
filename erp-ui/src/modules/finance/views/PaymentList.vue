<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { CASH_STATUS, joinList, labelOf, optionsOf, paymentApi, REQUEST_TYPES, type PaymentRow } from '../api/finance'

defineOptions({ name: 'FinPaymentList' })

/** 付款单列表（需求 12-05 3.2，T1）：出纳按已审批的付款申请付款；确认后自动核销申请中的应付，预付款记为预付余额 */
const router = useRouter()
const me = useUserStore()
type Query = { docNo?: string; requestNo?: string; supplierId?: string; dates?: [string, string]; statuses?: string[] }
const toParams = (q: Query) => {
  const { statuses, dates, ...rest } = q
  return { ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, PaymentRow>({
  api: (q) => paymentApi.page(toParams(q) as never),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'requestNo', label: '付款申请', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'dates', label: '付款日期', type: 'daterange' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(CASH_STATUS), multiple: true }
]
const columns: TableColumn<PaymentRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/finance/payment/${r.id}`) },
  { prop: 'requestNo', label: '申请单', width: 150, type: 'link', onClick: (r) => router.push(`/finance/payment/request/${r.requestId}`) },
  { prop: 'requestType', label: '类型', width: 100, formatter: (r) => labelOf(REQUEST_TYPES, r.requestType) },
  { prop: 'supplierName', label: '供应商', minWidth: 140 },
  { prop: 'bankAccountName', label: '付款账户', width: 120 },
  { prop: 'payDate', label: '付款日期', width: 100, type: 'date' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'amount', label: '金额', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'bankFee', label: '手续费', width: 90, type: 'amount', currencyProp: 'currency' },
  { prop: 'amountBase', label: '本位币', width: 120, type: 'amount', summary: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: CASH_STATUS },
  { prop: 'ownerName', label: '出纳', width: 90 },
  { prop: 'bankRefNo', label: '银行流水号', width: 140, hidden: true }
]
</script>

<template>
  <ErpPage description="付款必须基于已审批的付款申请；付款账户币别 = 申请币别，金额 ≤ 申请未付金额">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="fin.payment" @refresh="load">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:payment-request:query')" type="primary" @click="router.push({ path: '/finance/payment/request', query: {} })">待付款申请</el-button>
          <el-button v-if="me.hasPermission('fin:payment:verify')" @click="router.push('/finance/payment/verify')">预付冲应付</el-button>
          <ExportButton url="/finance/payments/export" :params="() => toParams(query)" permission="fin:payment:query" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
