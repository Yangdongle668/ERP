<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { formatDateTime, toDateString } from '@/utils/format'
import GenerateOrderDialog from '../components/GenerateOrderDialog.vue'
import { joinList, num } from '../api/common'
import { ORDER_STATUS, ORDER_STATUS_OPTIONS, ORDER_TYPE_OPTIONS, orderApi, type OrderQuery, type OrderRow } from '../api/order'
import { requisitionApi, type PendingLine } from '../api/requisition'

defineOptions({ name: 'PurOrderList' })

/** 采购订单列表（需求 07-05 3.1，T1） */
const router = useRouter()
const me = useUserStore()
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<PendingLine[]> }>()
const genRef = ref<{ open: (rows: PendingLine[]) => void }>()

type Query = Omit<OrderQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; dates?: [string, string]; required?: [string, string] }
const toParams = (q: Query) => {
  const { statuses, dates, required, ...rest } = q
  return { ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1], requiredFrom: required?.[0], requiredTo: required?.[1] }
}
function threeMonths(): [string, string] {
  const to = new Date()
  const from = new Date()
  from.setMonth(from.getMonth() - 3)
  return [toDateString(from), toDateString(to)]
}

const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, OrderRow>({
  api: (q) => orderApi.page(toParams(q) as OrderQuery),
  defaultQuery: () => ({ statuses: ['APPROVED', 'IN_PROGRESS'], dates: threeMonths() }),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: ORDER_STATUS_OPTIONS, multiple: true },
  { prop: 'ownerId', label: '采购员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' },
  { prop: 'required', label: '要求到货', type: 'daterange' },
  { prop: 'orderType', label: '订单类型', type: 'select', options: ORDER_TYPE_OPTIONS },
  { prop: 'overdue', label: '逾期', type: 'select', options: [{ value: true, label: '只看逾期' }] }
]

const canPrice = computed(() => me.hasPermission('pur:price:view'))
const columns = computed<TableColumn<OrderRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/order/${r.id}`) },
  { prop: 'orderType', label: '类型', width: 90, type: 'enum', options: ORDER_TYPE_OPTIONS },
  { prop: 'supplierName', label: '供应商', width: 130 },
  { prop: 'ownerName', label: '采购员', width: 90 },
  { prop: 'currency', label: '币别', width: 70 },
  ...(canPrice.value ? [{ prop: 'totalAmount', label: '价税合计', width: 130, type: 'amount', currencyProp: 'currency' } as TableColumn<OrderRow>] : []),
  { prop: 'earliestDate', label: '最早交期', width: 110, type: 'date' },
  { prop: 'progress', label: '到货进度', width: 140, slot: true },
  { prop: 'overdueLines', label: '逾期行', width: 80, slot: true },
  { prop: 'sentAt', label: '发送', width: 140, formatter: (r) => (r.sentAt ? formatDateTime(r.sentAt, true) : '未发送') },
  { prop: 'orderVersion', label: '版本', width: 70, formatter: (r) => (r.orderVersion > 1 ? `V${r.orderVersion}` : '') },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: ORDER_STATUS },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
])

const progress = (r: OrderRow) => (num(r.orderedQty) > 0 ? Math.min(100, Math.round((num(r.receivedQty) / num(r.orderedQty)) * 100)) : 0)

// ---------- 从申请生成 ----------
const pendingColumns: TableColumn<PendingLine>[] = [
  { prop: 'docNo', label: '申请单号', width: 150 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 140 },
  { prop: 'pendingQty', label: '未转数量', width: 100, type: 'qty' },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'requiredDate', label: '需求日期', width: 110, type: 'date' },
  { prop: 'suggestedSupplierName', label: '建议供应商', width: 130 },
  { prop: 'ownerName', label: '申请人', width: 90 }
]
const pendingFields: SearchField[] = [
  { prop: 'docNo', label: '申请单号', upper: true },
  { prop: 'required', label: '需求日期', type: 'daterange' }
]
const pendingApi = (q: Record<string, any>) => {
  const { required, ...rest } = q
  return requisitionApi.pending({ ...rest, requiredFrom: required?.[0], requiredTo: required?.[1] } as never)
}
async function fromRequisitions() {
  const rows = await pickerRef.value?.open()
  if (rows?.length) genRef.value?.open(rows)
}

const printIds = () => selection.value.map((r) => r.id)
const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const asRow = (r: unknown) => r as OrderRow
</script>

<template>
  <ErpPage description="向供应商下达的采购订单：审核后可登记到货，执行完成或关闭后结束">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable ref="tableRef" :columns="columns" :data="list" :loading="loading" selection storage-key="pur.order" @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:order:create'" type="primary" icon="Plus" @click="router.push('/purchase/order/new')">新建采购订单</el-button>
          <el-button v-perm="'pur:order:create'" @click="fromRequisitions">从申请生成</el-button>
          <PrintButton biz-type="PUR_ORDER" :ids="printIds" permission="pur:order:print" label="批量打印" />
        </template>
        <template #toolbar-right>
          <ExportButton url="/purchase/orders/export" :params="() => toParams({ ...query })" :columns="exportColumns" filename="采购订单" permission="pur:order:export" />
        </template>
        <template #col-progress="{ row }"><el-progress :percentage="progress(asRow(row))" :stroke-width="6" /></template>
        <template #col-overdueLines="{ row }">
          <span :class="{ 'text-danger': asRow(row).overdueLines > 0 }">{{ asRow(row).overdueLines || '' }}</span>
        </template>
        <template #empty>
          <el-button v-perm="'pur:order:create'" icon="Plus" @click="router.push('/purchase/order/new')">新建采购订单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <SourceDocPicker ref="pickerRef" title="选择待转订单的申请行" :api="pendingApi" :columns="pendingColumns" :search-fields="pendingFields" />
    <GenerateOrderDialog ref="genRef" @done="(ids) => ids.length !== 1 && load()" />
  </ErpPage>
</template>
