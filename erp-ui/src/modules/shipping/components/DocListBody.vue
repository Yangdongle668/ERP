<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { download } from '@/api/http'
import { useListPage } from '@/composables/useListPage'
import { docApi, type DocRow } from '../api/shipping'

/**
 * 出货单证列表（需求 11-04 3.1，T1）：Packing List / Invoice / 报关资料共用。从出货单详情生成，列表只做查询、编辑、打印、导出。
 * 单证在出货单反确认后标记“已失效”。
 */
const props = defineProps<{ kind: 'packing-list' | 'invoice' | 'customs' }>()
const router = useRouter()
const conf = computed(() => ({
  'packing-list': { api: docApi.packingLists, url: '/shipping/packing-lists', route: '/shipping/packing-list', noLabel: 'PL No.', print: 'SHP_PACKING_LIST' },
  invoice: { api: docApi.invoices, url: '/shipping/invoices', route: '/shipping/invoice', noLabel: 'Invoice No.', print: 'SHP_INVOICE' },
  customs: { api: docApi.customsList, url: '/shipping/customs', route: '/shipping/customs', noLabel: '编号 / 报关单号', print: '' }
})[props.kind])

type Query = { no?: string; shipmentNo?: string; customerId?: string; dates?: [string, string]; invalid?: boolean }
const { query, list, total, loading, load, search, reset } = useListPage<Query, DocRow>({
  api: (q) => {
    const { dates, ...rest } = q
    return conf.value.api({ ...rest, dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'no', label: '单号', upper: true },
  { prop: 'shipmentNo', label: '出货单', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'dates', label: '日期', type: 'daterange' },
  { prop: 'invalid', label: '已失效', type: 'select', options: [{ value: false, label: '有效' }, { value: true, label: '已失效' }] }
]
const columns = computed<TableColumn<DocRow>[]>(() => [
  { prop: 'no', label: conf.value.noLabel, width: 170, type: 'link', onClick: (r) => router.push(`${conf.value.route}/${r.id}`) },
  { prop: 'shipmentNo', label: '出货单', width: 150, type: 'link', onClick: (r) => router.push(`/shipping/shipment/${r.shipmentId}`) },
  { prop: 'customerName', label: '客户', minWidth: 150 },
  { prop: 'date', label: '日期', width: 110, type: 'date' },
  { prop: 'totalQty', label: '数量', width: 110, type: 'qty' },
  { prop: 'cartons', label: '箱数', width: 70, align: 'right' },
  ...(props.kind === 'packing-list' ? [] : [{ prop: 'totalAmount', label: '金额', width: 130, type: 'amount', currencyProp: 'currency' } as TableColumn<DocRow>]),
  { prop: 'invalid', label: '有效性', width: 80, slot: true }
])
const asRow = (r: unknown) => r as DocRow
const rowActions = (r: DocRow): RowAction[] => [
  { label: '编辑', permission: 'shp:document:update', handler: () => router.push(`${conf.value.route}/${r.id}`) },
  { label: '导出 Excel', permission: 'shp:document:print', handler: () => download(`${conf.value.url}/${r.id}/export`, undefined, `${r.no}.xlsx`) }
]
</script>

<template>
  <ErpPanel>
    <template #filter>
      <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
        <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
      </ErpSearchForm>
    </template>
    <ErpTable :columns="columns" :data="list" :loading="loading" :storage-key="`shp.${kind}`" :actions-width="200" @refresh="load">
      <template #col-invalid="{ row }">
        <ErpBadge v-if="asRow(row).invalid" type="danger" :dot="false">已失效</ErpBadge><span v-else>有效</span>
      </template>
      <template #actions="{ row }">
        <RowActions :actions="rowActions(asRow(row))" />
        <PrintButton v-if="conf.print" :biz-type="conf.print" :ids="[asRow(row).id]" permission="shp:document:print" label="打印" />
      </template>
      <template #empty><ErpEmpty compact description="在出货单详情中生成单证" /></template>
    </ErpTable>
    <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
  </ErpPanel>
</template>
