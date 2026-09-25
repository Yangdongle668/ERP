<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { toDateString } from '@/utils/format'
import { INSPECT_STATUS, joinList, optionsOf } from '../api/common'
import { RECEIPT_STATUS, RECEIPT_STATUS_OPTIONS, RECEIPT_TYPE_OPTIONS, receiptApi, STOCK_STATUS, type ReceiptQuery, type ReceiptRow } from '../api/receipt'

defineOptions({ name: 'PurReceiptList' })

/** 到货单列表（需求 07-06 3.1，T1） */
const router = useRouter()

type Query = Omit<ReceiptQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; dates?: [string, string] }
function last30(): [string, string] {
  const from = new Date()
  from.setDate(from.getDate() - 30)
  return [toDateString(from), toDateString(new Date())]
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, ReceiptRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return receiptApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as ReceiptQuery)
  },
  defaultQuery: () => ({ dates: last30() }),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'deliveryNoteNo', label: '送货单号' },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'inspectStatus', label: '检验状态', type: 'select', options: optionsOf(INSPECT_STATUS) },
  { prop: 'statuses', label: '状态', type: 'select', options: RECEIPT_STATUS_OPTIONS, multiple: true },
  { prop: 'receiptType', label: '到货类型', type: 'select', options: RECEIPT_TYPE_OPTIONS },
  { prop: 'dates', label: '到货日期', type: 'daterange' }
]

const columns: TableColumn<ReceiptRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/receipt/${r.id}`) },
  { prop: 'receiptType', label: '类型', width: 90, type: 'enum', options: RECEIPT_TYPE_OPTIONS },
  { prop: 'supplierName', label: '供应商', width: 130 },
  { prop: 'deliveryNoteNo', label: '送货单号', width: 130 },
  { prop: 'arrivalAt', label: '到货时间', width: 140, type: 'datetime' },
  { prop: 'materialSummary', label: '物料摘要', minWidth: 200 },
  { prop: 'lineCount', label: '行数', width: 60, align: 'right' },
  { prop: 'inspectSummary', label: '检验状态', width: 150 },
  { prop: 'stockStatus', label: '入库状态', width: 90, type: 'status', statusMap: STOCK_STATUS },
  { prop: 'receiverName', label: '收货人', width: 90 },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: RECEIPT_STATUS }
]
</script>

<template>
  <ErpPage description="登记供应商送货：审核后自动生成入库单草稿（需检物料入待检仓），入库确认后通知品质检验">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pur.receipt" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:receipt:create'" type="primary" icon="Plus" @click="router.push('/purchase/receipt/new')">新建到货</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
