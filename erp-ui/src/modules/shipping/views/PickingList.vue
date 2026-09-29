<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, optionsOf, PICKING_STATUS, pickingApi, type PickingRow } from '../api/shipping'

defineOptions({ name: 'ShpPickingList' })

/** 拣货单列表（需求 11-02 3.1，T1）：出货通知审核后生成，仓管员按单拣货 */
const router = useRouter()
type Query = { docNo?: string; noticeNo?: string; customerId?: string; warehouseId?: string; statuses?: string[]; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, PickingRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return pickingApi.page({ ...rest, statuses: joinList(statuses), shipDateFrom: dates?.[0], shipDateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'noticeNo', label: '出货通知', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'warehouseId', label: '仓库', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: [{ value: 'OPEN', label: '未完成' }, ...optionsOf(PICKING_STATUS)], multiple: true },
  { prop: 'dates', label: '出货日期', type: 'daterange' }
]
const columns: TableColumn<PickingRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/shipping/picking/${r.id}`) },
  { prop: 'noticeNo', label: '出货通知', width: 150, type: 'link', onClick: (r) => router.push(`/shipping/notice/${r.noticeId}`) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'shipDate', label: '出货日期', width: 110, type: 'date' },
  { prop: 'warehouseName', label: '仓库', width: 110 },
  { prop: 'lineCount', label: '行数', width: 60, align: 'right' },
  { prop: 'suggestedQty', label: '推荐数量', width: 100, type: 'qty' },
  { prop: 'pickedQty', label: '实拣', width: 100, type: 'qty' },
  { prop: 'pickingStatus', label: '状态', width: 90, type: 'status', statusMap: PICKING_STATUS },
  { prop: 'pickerName', label: '拣货人', width: 90 },
  { prop: 'completedAt', label: '完成时间', width: 140, type: 'datetime', hidden: true }
]
const asRow = (r: unknown) => r as PickingRow
</script>

<template>
  <ErpPage description="系统按 FIFO/FEFO 推荐批次与库位；仓管员录入实拣批次和数量后完成拣货">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-warehouseId><WarehouseSelect v-model="query.warehouseId" :only-mine="false" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="shp.picking" :actions-width="150" @refresh="load">
        <template #actions="{ row }">
          <el-button v-if="asRow(row).pickingStatus === 'WAITING' || asRow(row).pickingStatus === 'PICKING'" v-perm="'shp:picking:pick'" link type="primary"
                     @click="router.push(`/shipping/picking/${asRow(row).id}`)">{{ asRow(row).pickingStatus === 'WAITING' ? '开始拣货' : '继续拣货' }}</el-button>
          <PrintButton biz-type="SHP_PICKING" :ids="[asRow(row).id]" permission="shp:picking:print" label="打印" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
