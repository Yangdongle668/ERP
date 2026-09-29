<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import {
  DEDUCTION_STATUS, INVOICE_STATUS, labelOf, MATCH_STATUS, optionsOf, PURCHASE_INVOICE_TYPES, purchaseInvoiceApi, type PurchaseInvoiceRow
} from '../api/finance'

defineOptions({ name: 'FinPurchaseInvoiceList' })

/** 进项发票列表（需求 12-04）：三单匹配结果（已匹配 / 有差异待确认）、专票认证抵扣状态 */
const router = useRouter()
const me = useUserStore()
type Query = { keyword?: string; supplierId?: string; invoiceType?: string; matchStatus?: string; deductionStatus?: string; status?: string; dates?: [string, string] }
const toParams = (q: Query) => {
  const { dates, ...rest } = q
  return { ...rest, dateFrom: dates?.[0], dateTo: dates?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, PurchaseInvoiceRow>({
  api: (q) => purchaseInvoiceApi.page(toParams(q) as never),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'keyword', label: '发票号码' },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'invoiceType', label: '发票类型', type: 'select', options: PURCHASE_INVOICE_TYPES },
  { prop: 'matchStatus', label: '匹配状态', type: 'select', options: optionsOf(MATCH_STATUS) },
  { prop: 'deductionStatus', label: '认证', type: 'select', options: optionsOf(DEDUCTION_STATUS) },
  { prop: 'status', label: '状态', type: 'select', options: optionsOf(INVOICE_STATUS, ['RED']) },
  { prop: 'dates', label: '开票日期', type: 'daterange' }
]
const columns: TableColumn<PurchaseInvoiceRow>[] = [
  { prop: 'docNo', label: '登记号', width: 150, type: 'link', onClick: (r) => router.push(`/finance/payable/invoice/${r.id}`) },
  { prop: 'invoiceNo', label: '发票号码', width: 150 },
  { prop: 'invoiceType', label: '发票类型', width: 110, formatter: (r) => labelOf(PURCHASE_INVOICE_TYPES, r.invoiceType) },
  { prop: 'supplierName', label: '供应商', minWidth: 140 },
  { prop: 'invoiceDate', label: '开票日期', width: 100, type: 'date' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'amount', label: '不含税', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'taxAmount', label: '税额', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'matchStatus', label: '匹配', width: 80, type: 'status', statusMap: MATCH_STATUS },
  { prop: 'deductionStatus', label: '认证', width: 80, type: 'status', statusMap: DEDUCTION_STATUS },
  { prop: 'certifiedPeriod', label: '认证期', width: 80 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: INVOICE_STATUS },
  { prop: 'createdByName', label: '登记人', width: 90, hidden: true }
]
</script>

<template>
  <ErpPage description="登记供应商开来的发票并与应付做三单匹配；单价差异超容差为“有差异”，财务主管确认后生成价差调整应付行">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="fin.purchase-invoice" @refresh="load">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:payable:invoice')" type="primary" @click="router.push('/finance/payable/invoice/new')">登记发票</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
