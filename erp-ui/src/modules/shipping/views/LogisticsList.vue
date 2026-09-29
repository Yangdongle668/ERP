<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { forwarderApi, joinList, LOGISTICS_STATUS, optionsOf, shipmentApi, type ForwarderOption, type ShipmentRow } from '../api/shipping'
import LogisticsDialogs from '../components/LogisticsDialogs.vue'

defineOptions({ name: 'ShpLogisticsList' })

/** 物流跟踪（需求 11-05 3.1，T1）：已出货的出货单，默认未签收；已过 ETA 未到港标橙 */
const router = useRouter()
const forwarders = ref<ForwarderOption[]>([])
const dialogs = ref<InstanceType<typeof LogisticsDialogs>>()
type Query = { customerId?: string; transportMode?: string; logisticsStatuses?: string[]; etas?: [string, string]; forwarderId?: string; unsigned?: boolean }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ShipmentRow>({
  api: (q) => {
    const { logisticsStatuses, etas, unsigned, ...rest } = q
    return shipmentApi.logistics({ ...rest, statuses: unsigned ? 'SHIPPED' : 'SHIPPED,COMPLETED', logisticsStatuses: joinList(logisticsStatuses),
      etaFrom: etas?.[0], etaTo: etas?.[1] } as never)
  },
  defaultQuery: () => ({ unsigned: true }),
  refreshOnActivated: true
})
onMounted(async () => (forwarders.value = await forwarderApi.options().catch(() => [])))
const fields: SearchField[] = [
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'transportMode', label: '运输方式', type: 'dict', dictType: 'shp_transport_mode' },
  { prop: 'logisticsStatuses', label: '物流状态', type: 'select', options: [{ value: 'NONE', label: '未登记' }, ...optionsOf(LOGISTICS_STATUS)], multiple: true },
  { prop: 'etas', label: 'ETA', type: 'daterange' },
  { prop: 'forwarderId', label: '货代', type: 'slot' },
  { prop: 'unsigned', label: '只看未签收', type: 'select', options: [{ value: true, label: '是' }, { value: false, label: '否' }] }
]
const columns: TableColumn<ShipmentRow>[] = [
  { prop: 'docNo', label: '出货单', width: 150, type: 'link', onClick: (r) => router.push(`/shipping/shipment/${r.id}`) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'transportMode', label: '运输方式', width: 90, type: 'dict', dictType: 'shp_transport_mode' },
  { prop: 'forwarderName', label: '货代', width: 120 },
  { prop: 'blNo', label: '提单号', width: 130 },
  { prop: 'containerNo', label: '柜号', width: 120 },
  { prop: 'etd', label: 'ETD', width: 100, type: 'date' },
  { prop: 'eta', label: 'ETA', width: 100, slot: true },
  { prop: 'logisticsStatus', label: '物流状态', width: 90, type: 'status', statusMap: LOGISTICS_STATUS },
  { prop: 'logisticsUpdatedAt', label: '最近更新', width: 140, type: 'datetime' },
  { prop: 'shipDate', label: '出货日期', width: 110, type: 'date', hidden: true }
]
const asRow = (r: unknown) => r as ShipmentRow
const rowActions = (r: ShipmentRow): RowAction[] => [
  { label: '更新状态', permission: 'shp:logistics:update', handler: () => dialogs.value?.openEvent(r) },
  { label: '登记提单', permission: 'shp:logistics:update', handler: () => dialogs.value?.openLogistics(r) },
  { label: '登记签收', permission: 'shp:logistics:update', visible: r.shipmentStatus === 'SHIPPED', handler: () => dialogs.value?.openSign(r) }
]
</script>

<template>
  <ErpPage description="登记订舱、装柜、离港、到港、清关、签收；ETA 已过 3 天仍未到港时提醒船务">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-forwarderId>
            <el-select v-model="query.forwarderId" clearable filterable>
              <el-option v-for="f in forwarders" :key="f.id" :value="f.id" :label="`${f.code} ${f.name}`" />
            </el-select>
          </template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="shp.logistics" :actions-width="220" @refresh="load">
        <template #col-eta="{ row }"><span :class="{ late: asRow(row).etaOverdue }">{{ asRow(row).eta ?? '' }}</span></template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
    <LogisticsDialogs ref="dialogs" :forwarders="forwarders" @changed="load" />
  </ErpPage>
</template>

<style scoped>
.late { color: var(--erp-color-warning); font-weight: var(--erp-font-weight-medium); }
</style>
