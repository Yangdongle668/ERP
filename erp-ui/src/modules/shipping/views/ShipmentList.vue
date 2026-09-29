<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, LOGISTICS_STATUS, optionsOf, SHIPMENT_STATUS, shipmentApi, type ShipmentRow } from '../api/shipping'

defineOptions({ name: 'ShpShipmentList' })

/** 出货单列表（需求 11-03 3.1，T1）：由出货通知生成；提交后生成销售出库单，仓库确认出库后已出货 */
const router = useRouter()
const iso = (d: Date) => d.toISOString().slice(0, 10)
type Query = {
  docNo?: string; customerId?: string; orderNo?: string; noticeNo?: string; blNo?: string; statuses?: string[]; dates?: [string, string]
  transportMode?: string; logisticsStatuses?: string[]
}
const toParams = (q: Query) => {
  const { statuses, dates, logisticsStatuses, ...rest } = q
  return { ...rest, statuses: joinList(statuses), logisticsStatuses: joinList(logisticsStatuses), shipDateFrom: dates?.[0], shipDateTo: dates?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, ShipmentRow>({
  api: (q) => shipmentApi.page(toParams(q) as never),
  defaultQuery: () => ({ dates: [iso(new Date(Date.now() - 30 * 86400000)), iso(new Date(Date.now() + 30 * 86400000))] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'noticeNo', label: '出货通知', upper: true },
  { prop: 'blNo', label: '提单号' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(SHIPMENT_STATUS), multiple: true },
  { prop: 'dates', label: '出货日期', type: 'daterange' },
  { prop: 'transportMode', label: '运输方式', type: 'dict', dictType: 'shp_transport_mode' },
  { prop: 'logisticsStatuses', label: '物流状态', type: 'select', options: [{ value: 'NONE', label: '未登记' }, ...optionsOf(LOGISTICS_STATUS)], multiple: true }
]
const columns: TableColumn<ShipmentRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/shipping/shipment/${r.id}`) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'shipDate', label: '出货日期', width: 110, type: 'date' },
  { prop: 'transportMode', label: '运输方式', width: 90, type: 'dict', dictType: 'shp_transport_mode' },
  { prop: 'portOfDestination', label: '目的港', width: 110 },
  { prop: 'totalQty', label: '数量', width: 100, type: 'qty' },
  { prop: 'totalAmount', label: '金额', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'cartonCount', label: '箱数', width: 60, align: 'right' },
  { prop: 'blNo', label: '提单号', width: 130 },
  { prop: 'etd', label: 'ETD', width: 100, type: 'date' },
  { prop: 'eta', label: 'ETA', width: 100, type: 'date' },
  { prop: 'logisticsStatus', label: '物流', width: 90, type: 'status', statusMap: LOGISTICS_STATUS },
  { prop: 'shipmentStatus', label: '状态', width: 90, type: 'status', statusMap: SHIPMENT_STATUS },
  { prop: 'ownerName', label: '船务', width: 90 },
  { prop: 'noticeNo', label: '出货通知', width: 150, hidden: true }
]
</script>

<template>
  <ErpPage description="从出货通知生成（可按箱分批）；提交后生成销售出库单，仓库确认出库后回写订单并发布出货确认">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="shp.shipment" @refresh="load">
        <template #toolbar>
          <ExportButton url="/shipping/shipments/export" :params="() => toParams(query)" permission="shp:shipment:export" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
