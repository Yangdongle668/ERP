<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { download } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty, toDateString } from '@/utils/format'
import {
  BIZ_TYPE_OPTIONS, docRoute, labelOf, stockApi, WAREHOUSE_TYPE_OPTIONS,
  type AlertRow, type StockRow, type TxnRow
} from '../api/inventory'

defineOptions({ name: 'InvStockQuery' })

/** 库存查询（需求 08-08 2.1、2.2、2.6）：即时库存、库存流水、库存预警 */
const route = useRoute()
const router = useRouter()
const me = useUserStore()
const tab = ref(String(route.query.tab ?? 'stock'))
const canCost = computed(() => me.hasPermission('inv:stock:cost'))

// ==================== 即时库存 ====================
const GROUP_OPTIONS = [{ value: 'MATERIAL', label: '按物料' }, { value: 'WAREHOUSE', label: '按物料+仓库' }, { value: 'BATCH', label: '明细（含批次、库位）' }]
const sq = reactive<Record<string, unknown>>({ groupBy: 'WAREHOUSE', showZero: false, pageNo: 1, pageSize: 20 })
const stockRows = ref<StockRow[]>([])
const stockTotal = ref(0)
const stockSum = ref<{ totalQty?: string; totalAmount?: string }>({})
const stockLoading = ref(false)
const stockFields: SearchField[] = [
  { prop: 'keyword', label: '物料', placeholder: '编码前缀或名称规格' },
  { prop: 'warehouseTypes', label: '仓库类型', type: 'select', options: WAREHOUSE_TYPE_OPTIONS, multiple: true },
  { prop: 'warehouseIds', label: '仓库', type: 'slot' },
  { prop: 'batchNo', label: '批次号', upper: true },
  { prop: 'showZero', label: '显示零库存', type: 'slot' },
  { prop: 'groupBy', label: '汇总方式', type: 'select', options: GROUP_OPTIONS, clearable: false }
]
function stockParams() {
  const p = { ...sq } as Record<string, unknown>
  if (Array.isArray(p.warehouseTypes)) p.warehouseTypes = (p.warehouseTypes as string[]).join(',') || undefined
  if (Array.isArray(p.warehouseIds)) p.warehouseIds = (p.warehouseIds as string[]).join(',') || undefined
  return p
}
async function loadStocks() {
  stockLoading.value = true
  try {
    const r = await stockApi.stocks(stockParams())
    stockRows.value = r.list
    stockTotal.value = r.total
    stockSum.value = { totalQty: r.totalQty, totalAmount: r.totalAmount }
  } finally {
    stockLoading.value = false
  }
}
function searchStocks() {
  sq.pageNo = 1
  loadStocks()
}
function resetStocks() {
  Object.keys(sq).forEach((k) => delete sq[k])
  Object.assign(sq, { groupBy: 'WAREHOUSE', showZero: false, pageNo: 1, pageSize: 20 })
  loadStocks()
}
const stockColumns = computed<TableColumn<StockRow>[]>(() => {
  const g = sq.groupBy
  return [
    { prop: 'materialCode', label: '物料编码', width: 130 },
    { prop: 'materialName', label: '名称', minWidth: 150 },
    { prop: 'materialSpec', label: '规格', minWidth: 140 },
    { prop: 'baseUom', label: '单位', width: 60 },
    { prop: 'categoryName', label: '类别', width: 100, hidden: true },
    ...(g === 'MATERIAL' ? [] : [
      { prop: 'warehouseName', label: '仓库', width: 110 },
      { prop: 'warehouseType', label: '仓库类型', width: 90, type: 'enum' as const, options: WAREHOUSE_TYPE_OPTIONS, hidden: true }
    ]),
    ...(g === 'BATCH' ? [
      { prop: 'locationCode', label: '库位', width: 90 },
      { prop: 'batchNo', label: '批次', width: 140, slot: true },
      { prop: 'productionDate', label: '生产日期', width: 100, type: 'date' as const },
      { prop: 'expireDate', label: '到期日期', width: 100, type: 'date' as const }
    ] : []),
    { prop: 'onHandQty', label: '现存量', width: 100, type: 'qty', uomProp: 'baseUom' },
    { prop: 'availableQty', label: '可用量', width: 100, slot: true, align: 'right' },
    ...(g === 'MATERIAL' ? [
      { prop: 'reservedQty', label: '预留量', width: 90, type: 'qty' as const, uomProp: 'baseUom' },
      { prop: 'qcQty', label: '待检量', width: 90, type: 'qty' as const, uomProp: 'baseUom' },
      { prop: 'ngQty', label: '不良量', width: 90, type: 'qty' as const, uomProp: 'baseUom' },
      { prop: 'safetyStock', label: '安全库存', width: 90, type: 'qty' as const, uomProp: 'baseUom' }
    ] : []),
    ...(canCost.value ? [
      { prop: 'refCost', label: '参考单价', width: 100, type: 'price' as const },
      { prop: 'amount', label: '金额', width: 110, type: 'amount' as const }
    ] : []),
    { prop: 'lastInDate', label: '最近入库', width: 100, type: 'date' },
    { prop: 'lastOutDate', label: '最近出库', width: 100, type: 'date' }
  ]
})
const stockRowClass = ({ row }: { row: unknown }) => ((row as StockRow).belowSafety ? 'below-safety' : '')
function toTxn(r: StockRow) {
  Object.assign(tq, { keyword: r.materialCode, warehouseId: r.warehouseId, batchNo: r.batchNo, pageNo: 1 })
  tab.value = 'txn'
  loadTxns()
}
async function exportStocks() {
  const r = await download<{ async?: boolean }>('/inventory/stocks/export', { ...stockParams(), pageNo: undefined, pageSize: undefined }, '库存查询.xlsx')
  if (r?.async) ElMessage.info('数据量较大，已转为后台导出，完成后可在任务中心下载')
}

// ==================== 库存流水 ====================
function monthStart() {
  const d = new Date()
  return toDateString(new Date(d.getFullYear(), d.getMonth(), 1))
}
const tq = reactive<Record<string, unknown>>({ dates: [monthStart(), toDateString(new Date())], pageNo: 1, pageSize: 20 })
const txnRows = ref<TxnRow[]>([])
const txnTotal = ref(0)
const txnLoading = ref(false)
const txnFields: SearchField[] = [
  { prop: 'keyword', label: '物料', placeholder: '编码前缀或名称规格' },
  { prop: 'warehouseId', label: '仓库', type: 'slot' },
  { prop: 'batchNo', label: '批次', upper: true },
  { prop: 'direction', label: '方向', type: 'select', options: [{ value: 'IN', label: '入库' }, { value: 'OUT', label: '出库' }] },
  { prop: 'bizTypes', label: '类型', type: 'select', options: BIZ_TYPE_OPTIONS, multiple: true },
  { prop: 'docNo', label: '单号/来源单号', upper: true },
  { prop: 'dates', label: '业务日期', type: 'daterange' }
]
function txnParams() {
  const { dates, bizTypes, ...rest } = tq as { dates?: string[]; bizTypes?: string[] }
  return { ...rest, bizTypes: bizTypes?.length ? bizTypes.join(',') : undefined, dateFrom: dates?.[0], dateTo: dates?.[1] }
}
async function loadTxns() {
  txnLoading.value = true
  try {
    const r = await stockApi.txns(txnParams())
    txnRows.value = r.list
    txnTotal.value = r.total
  } finally {
    txnLoading.value = false
  }
}
function resetTxns() {
  Object.keys(tq).forEach((k) => delete tq[k])
  Object.assign(tq, { dates: [monthStart(), toDateString(new Date())], pageNo: 1, pageSize: 20 })
  loadTxns()
}
const txnColumns = computed<TableColumn<TxnRow>[]>(() => [
  { prop: 'bizDate', label: '业务日期', width: 100, type: 'date' },
  { prop: 'docNo', label: '单号', width: 160, slot: true },
  { prop: 'bizType', label: '类型', width: 110, formatter: (r) => labelOf(BIZ_TYPE_OPTIONS, r.bizType) },
  { prop: 'sourceNo', label: '来源单号', width: 140 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'warehouseName', label: '仓库', width: 100 },
  { prop: 'locationCode', label: '库位', width: 80, hidden: true },
  { prop: 'batchNo', label: '批次', width: 130 },
  { prop: 'inQty', label: '入库数量', width: 100, type: 'qty', uomProp: 'baseUom' },
  { prop: 'outQty', label: '出库数量', width: 100, type: 'qty', uomProp: 'baseUom' },
  { prop: 'balanceQty', label: '结存', width: 100, type: 'qty', uomProp: 'baseUom' },
  ...(canCost.value ? [
    { prop: 'unitCost', label: '单价', width: 90, type: 'price' as const },
    { prop: 'amount', label: '金额', width: 100, type: 'amount' as const }
  ] : []),
  { prop: 'operatorName', label: '操作人', width: 90 },
  { prop: 'createdAt', label: '时间', width: 150, type: 'datetime' }
])
async function exportTxns() {
  const r = await download<{ async?: boolean }>('/inventory/stock-txns/export', { ...txnParams(), pageNo: undefined, pageSize: undefined }, '库存流水.xlsx')
  if (r?.async) ElMessage.info('数据量较大，已转为后台导出，完成后可在任务中心下载')
}

// ==================== 库存预警 ====================
const alertType = ref(String(route.query.type ?? 'LOW'))
const alertRows = ref<AlertRow[]>([])
const alertCounts = ref({ low: 0, high: 0, expiry: 0, qcOverdue: 0 })
const alertLoading = ref(false)
async function loadAlerts() {
  alertLoading.value = true
  try {
    const [rows, counts] = await Promise.all([stockApi.alerts(alertType.value), stockApi.alertCounts()])
    alertRows.value = rows
    alertCounts.value = counts
  } finally {
    alertLoading.value = false
  }
}
const alertColumns = computed<TableColumn<AlertRow>[]>(() => {
  const base: TableColumn<AlertRow>[] = [
    { prop: 'materialCode', label: '物料编码', width: 130 },
    { prop: 'materialName', label: '名称', minWidth: 150 },
    { prop: 'materialSpec', label: '规格', minWidth: 140 },
    { prop: 'baseUom', label: '单位', width: 60 }
  ]
  if (alertType.value === 'LOW') return [...base,
    { prop: 'safetyStock', label: '安全库存', width: 100, type: 'qty' },
    { prop: 'availableQty', label: '可用量', width: 100, type: 'qty' },
    { prop: 'inTransitQty', label: '在途', width: 90, type: 'qty' },
    { prop: 'gap', label: '缺口', width: 100, type: 'qty' },
    { prop: 'buyerName', label: '采购员', width: 90 }]
  if (alertType.value === 'HIGH') return [...base,
    { prop: 'maxStock', label: '最高库存', width: 100, type: 'qty' },
    { prop: 'qty', label: '现存量', width: 100, type: 'qty' },
    { prop: 'gap', label: '超出', width: 100, type: 'qty' }]
  if (alertType.value === 'EXPIRY') return [...base,
    { prop: 'warehouseName', label: '仓库', width: 100 },
    { prop: 'batchNo', label: '批次', width: 130 },
    { prop: 'qty', label: '数量', width: 100, type: 'qty' },
    { prop: 'expireDate', label: '到期日', width: 100, type: 'date' },
    { prop: 'daysLeft', label: '剩余天数', width: 90, slot: true, align: 'right' }]
  return [...base,
    { prop: 'batchNo', label: '批次', width: 130 },
    { prop: 'qty', label: '数量', width: 100, type: 'qty' },
    { prop: 'inAt', label: '入库时间', width: 150, type: 'datetime' },
    { prop: 'waitHours', label: '已等待(小时)', width: 110, align: 'right' },
    { prop: 'sourceNo', label: '来源单号', width: 140 }]
})

const loaded = new Set<string>()
function ensure(t: string) {
  if (loaded.has(t)) return
  loaded.add(t)
  if (t === 'stock') loadStocks()
  else if (t === 'txn') loadTxns()
  else loadAlerts()
}
watch(tab, ensure)
watch(alertType, loadAlerts)
onMounted(() => ensure(tab.value))

const asStock = (r: unknown) => r as StockRow
const asTxn = (r: unknown) => r as TxnRow
const asAlert = (r: unknown) => r as AlertRow
</script>

<template>
  <ErpPage description="即时库存按仓库数据权限统计；可用量只计可用仓中未冻结、未过期的库存并扣除预留">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="即时库存" name="stock">
          <ErpSearchForm v-model="sq" :fields="stockFields" :loading="stockLoading" @search="searchStocks" @reset="resetStocks">
            <template #field-warehouseIds><WarehouseSelect v-model="(sq.warehouseIds as string)" placeholder="全部" class="w200" /></template>
            <template #field-showZero><el-switch v-model="(sq.showZero as boolean)" /></template>
          </ErpSearchForm>
          <ErpTable :columns="stockColumns" :data="stockRows" :loading="stockLoading" storage-key="inv.stock" :actions-width="110"
                    :row-class-name="stockRowClass" @refresh="loadStocks">
            <template #toolbar>
              <span class="sum">合计：<template v-if="stockSum.totalQty">现存 <strong class="num">{{ formatQty(stockSum.totalQty) }}</strong></template>
                <template v-if="canCost && stockSum.totalAmount"> 金额 <strong class="num">{{ formatAmount(stockSum.totalAmount) }}</strong></template>
                <template v-if="!stockSum.totalQty && !stockSum.totalAmount">-</template></span>
            </template>
            <template #toolbar-right>
              <ErpIconButton icon="Download" tooltip="导出" permission="inv:stock:export" @click="exportStocks" />
            </template>
            <template #col-batchNo="{ row }">
              <span>{{ asStock(row).batchNo ?? '-' }}</span>
              <ErpBadge v-if="asStock(row).frozen" type="danger">冻结</ErpBadge>
              <ErpBadge v-if="asStock(row).concession" type="warning">特采</ErpBadge>
            </template>
            <template #col-availableQty="{ row }">
              <span :class="['num', { 'text-muted': Number(asStock(row).availableQty) === 0 }]">{{ formatQty(asStock(row).availableQty) }}</span>
            </template>
            <template #actions="{ row }">
              <RowActions :actions="[
                { label: '流水', handler: () => toTxn(asStock(row)) },
                { label: '批次', handler: () => router.push({ path: '/inventory/batch', query: { materialId: asStock(row).materialId } }) }
              ]" />
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="(sq.pageNo as number)" v-model:page-size="(sq.pageSize as number)" :total="stockTotal" @change="loadStocks" />
        </el-tab-pane>

        <el-tab-pane label="库存流水" name="txn">
          <ErpSearchForm v-model="tq" :fields="txnFields" :loading="txnLoading" @search="() => { tq.pageNo = 1; loadTxns() }" @reset="resetTxns">
            <template #field-warehouseId><WarehouseSelect v-model="(tq.warehouseId as string)" placeholder="全部" class="w160" /></template>
          </ErpSearchForm>
          <ErpTable :columns="txnColumns" :data="txnRows" :loading="txnLoading" storage-key="inv.txn" @refresh="loadTxns">
            <template #toolbar-right>
              <ErpIconButton icon="Download" tooltip="导出" permission="inv:stock:export" @click="exportTxns" />
            </template>
            <template #col-docNo="{ row }">
              <el-link v-if="docRoute(asTxn(row).docType, asTxn(row).docId)" type="primary" underline="never"
                       @click="router.push(docRoute(asTxn(row).docType, asTxn(row).docId)!)">{{ asTxn(row).docNo }}</el-link>
              <span v-else>{{ asTxn(row).docNo }}</span>
              <ErpBadge v-if="asTxn(row).reversal" type="info">冲销</ErpBadge>
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="(tq.pageNo as number)" v-model:page-size="(tq.pageSize as number)" :total="txnTotal" @change="loadTxns" />
        </el-tab-pane>

        <el-tab-pane label="库存预警" name="alert">
          <el-radio-group v-model="alertType" class="alert-types">
            <el-radio-button value="LOW">低于安全库存 ({{ alertCounts.low }})</el-radio-button>
            <el-radio-button value="HIGH">超过最高库存 ({{ alertCounts.high }})</el-radio-button>
            <el-radio-button value="EXPIRY">临期/过期批次 ({{ alertCounts.expiry }})</el-radio-button>
            <el-radio-button value="QC_OVERDUE">待检超时 ({{ alertCounts.qcOverdue }})</el-radio-button>
          </el-radio-group>
          <ErpTable :columns="alertColumns" :data="alertRows" :loading="alertLoading" :storage-key="`inv.alert.${alertType}`" @refresh="loadAlerts">
            <template #toolbar-right>
              <ErpIconButton icon="Download" tooltip="导出" permission="inv:stock:export"
                             @click="download('/inventory/alerts/export', { type: alertType }, '库存预警.xlsx')" />
            </template>
            <template #col-daysLeft="{ row }">
              <span :class="['num', { 'text-danger': (asAlert(row).daysLeft ?? 0) < 0 }]">{{ (asAlert(row).daysLeft ?? 0) < 0 ? `已过期 ${-(asAlert(row).daysLeft ?? 0)} 天` : asAlert(row).daysLeft }}</span>
            </template>
            <template #empty><span class="text-muted">没有预警</span></template>
          </ErpTable>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.sum { color: var(--erp-color-text-secondary); }
.alert-types { margin-bottom: var(--erp-space-3); }
:deep(.below-safety) { background: var(--erp-color-error-bg); }
</style>
