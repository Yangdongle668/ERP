<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { toDateString } from '@/utils/format'
import { DOC_STATUS, joinList, optionsOf, orderApi, PROGRESS_STATUS, type OrderRow, type QuoteOpenLine } from '../api/sales'

defineOptions({ name: 'SalOrderList' })

/** 销售订单列表（需求 04-03 3.1，T1） */
const router = useRouter()
const me = useUserStore()
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<QuoteOpenLine[]> }>()
const importRef = ref<{ open: () => void }>()

type Query = {
  docNo?: string; customerId?: string; statuses?: string[]; ownerId?: string; materialId?: string; orderType?: string; dates?: [string, string]
  required?: [string, string]; deliveryRisk?: boolean; unshipped?: boolean
}
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
  api: (q) => orderApi.page(toParams(q) as never),
  defaultQuery: () => ({ dates: threeMonths() }),
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号/客户PO', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(DOC_STATUS), multiple: true },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '下单日期', type: 'daterange' },
  { prop: 'required', label: '要求交期', type: 'daterange' },
  { prop: 'orderType', label: '订单类型', type: 'dict', dictType: 'sal_order_type' },
  { prop: 'deliveryRisk', label: '交期风险', type: 'select', options: [{ value: true, label: '只看有风险' }] },
  { prop: 'unshipped', label: '出货', type: 'select', options: [{ value: true, label: '只看未出完' }] }
]

const canCost = computed(() => me.hasPermission('sales:order:cost'))
const columns = computed<TableColumn<OrderRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/sales/order/${r.id}`) },
  { prop: 'orderType', label: '类型', width: 90, type: 'dict', dictType: 'sal_order_type' },
  { prop: 'customerName', label: '客户', minWidth: 150 },
  { prop: 'customerPoNo', label: '客户PO', width: 130 },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'totalAmount', label: '价税合计', width: 130, type: 'amount', currencyProp: 'currency' },
  ...(canCost.value ? [{ prop: 'minMarginRate', label: '最低毛利率', width: 110, slot: true } as TableColumn<OrderRow>] : []),
  { prop: 'earliestRequiredDate', label: '最早交期', width: 110, slot: true },
  { prop: 'shipProgress', label: '出货', width: 80, type: 'status', statusMap: PROGRESS_STATUS },
  { prop: 'receiveProgress', label: '回款', width: 80, type: 'status', statusMap: PROGRESS_STATUS },
  { prop: 'orderVersion', label: '版本', width: 70, formatter: (r) => (r.orderVersion > 1 ? `V${r.orderVersion}` : '') },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: DOC_STATUS },
  { prop: 'ownerName', label: '业务员', width: 90 },
  { prop: 'docDate', label: '下单日期', width: 110, type: 'date' }
])
const asRow = (r: unknown) => r as OrderRow

// ---------- 从报价生成 ----------
const quoteColumns: TableColumn<QuoteOpenLine>[] = [
  { prop: 'docNo', label: '报价单', width: 160, formatter: (r) => (r.revision > 0 ? `${r.docNo} R${r.revision}` : r.docNo) },
  { prop: 'customerName', label: '客户', width: 140 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'minQty', label: '数量阶梯', width: 100, type: 'qty' },
  { prop: 'price', label: '单价', width: 100, type: 'price' },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'validUntil', label: '有效期至', width: 110, type: 'date' }
]
async function quoteApi(q: Record<string, any>) {
  const rows = await orderApi.quotationLines(q.customerId)
  const from = (q.pageNo - 1) * q.pageSize
  return { list: rows.slice(from, from + q.pageSize), total: rows.length }
}
async function fromQuotations() {
  const rows = await pickerRef.value?.open()
  if (!rows?.length) return
  const r = await orderApi.fromQuotations(rows.map((x) => ({ quotationLineId: x.lineId, qty: Number(x.minQty) > 0 ? x.minQty : '1' })))
  if (r.messages.length) ElNotification({ type: 'info', title: '提示', message: r.messages.join('；'), duration: 8000 })
  ElMessage.success(`已生成 ${r.orderIds.length} 张订单草稿，请核对数量与交期`)
  if (r.orderIds.length === 1) router.push(`/sales/order/${r.orderIds[0]}/edit`)
  else load()
}

const printIds = () => selection.value.map((r) => r.id)
const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const exportParams = (level: string) => () => ({ ...toParams({ ...query }), level })
</script>

<template>
  <ErpPage description="客户订单：审核后下达出货与生产；可变更、关闭，跟踪出货、开票和回款">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable ref="tableRef" :columns="columns" :data="list" :loading="loading" selection storage-key="sal.order" @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'sales:order:create'" type="primary" icon="Plus" @click="router.push('/sales/order/new')">新建销售订单</el-button>
          <el-button v-perm="'sales:order:create'" @click="fromQuotations">从报价生成</el-button>
          <el-button v-perm="'sales:order:create'" icon="Upload" @click="importRef?.open()">导入</el-button>
          <PrintButton biz-type="SAL_ORDER" :ids="printIds" permission="sales:order:print" label="批量打印" />
        </template>
        <template #toolbar-right>
          <ExportButton url="/sales/orders/export" :params="exportParams('ORDER')" :columns="exportColumns" filename="销售订单" permission="sales:order:export" />
          <ExportButton url="/sales/orders/export" :params="exportParams('LINE')" filename="销售订单明细" permission="sales:order:export" label="导出明细" />
        </template>
        <template #col-minMarginRate="{ row }">
          <span v-if="asRow(row).minMarginRate != null" :class="{ 'text-danger': asRow(row).belowFloor }">{{ (Number(asRow(row).minMarginRate) * 100).toFixed(2) }}%</span>
          <span v-else class="text-muted">-</span>
        </template>
        <template #col-earliestRequiredDate="{ row }">
          <span :class="{ 'text-danger': asRow(row).deliveryRisk }">{{ asRow(row).earliestRequiredDate || '-' }}</span>
          <ErpBadge v-if="asRow(row).deliveryRisk" type="danger" :dot="false">风险</ErpBadge>
        </template>
        <template #empty>
          <el-button v-perm="'sales:order:create'" icon="Plus" @click="router.push('/sales/order/new')">新建销售订单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <SourceDocPicker ref="pickerRef" title="选择报价行（已审核、已发送且在有效期内）" :api="quoteApi" :columns="quoteColumns" row-key="lineId" />
    <ImportDialog ref="importRef" title="导入销售订单" base="/sales/orders" template-name="销售订单" @done="load" />
  </ErpPage>
</template>
