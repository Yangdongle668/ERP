<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, optionsOf, ORDER_TYPE_OPTIONS, PROD_STATUS, prodOrderApi, labelOf, type ProdOrderRow } from '../api/production'

defineOptions({ name: 'MfgProdOrderList' })

/** 生产订单列表（需求 09-01 3.1，T1）：默认显示已计划、已下达、生产中、暂停；可批量下达、打印流程卡 */
const router = useRouter()

type Query = { docNo?: string; materialId?: string; orderType?: string; statuses?: string[]; deptId?: string; plan?: [string, string]; salesOrderNo?: string; overdue?: boolean }
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, ProdOrderRow>({
  api: (q) => prodOrderApi.page(toParams(q) as never),
  refreshOnActivated: true
})
function toParams(q: Query) {
  const { statuses, plan, ...rest } = q
  return { ...rest, statuses: joinList(statuses), planFrom: plan?.[0], planTo: plan?.[1] }
}

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'materialId', label: '产品', type: 'slot' },
  { prop: 'orderType', label: '类型', type: 'select', options: ORDER_TYPE_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(PROD_STATUS), multiple: true },
  { prop: 'deptId', label: '车间', type: 'org' },
  { prop: 'plan', label: '计划开工', type: 'daterange' },
  { prop: 'salesOrderNo', label: '销售订单', upper: true },
  { prop: 'overdue', label: '逾期', type: 'select', options: [{ value: true, label: '只看逾期未完工' }] }
]

const columns: TableColumn<ProdOrderRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.id}`) },
  { prop: 'orderType', label: '类型', width: 70, formatter: (r) => labelOf(ORDER_TYPE_OPTIONS, r.orderType) },
  { prop: 'materialCode', label: '产品编码', width: 130 },
  { prop: 'materialName', label: '产品名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', width: 120, hidden: true },
  { prop: 'qty', label: '计划数量', width: 100, type: 'qty', uomProp: 'baseUom' },
  { prop: 'completedQty', label: '完工', width: 90, type: 'qty', uomProp: 'baseUom' },
  { prop: 'qualifiedStockedQty', label: '合格入库', width: 90, type: 'qty', uomProp: 'baseUom' },
  { prop: 'scrappedQty', label: '报废', width: 80, type: 'qty', uomProp: 'baseUom', hidden: true },
  { prop: 'progress', label: '进度', width: 120, slot: true },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'planStart', label: '计划开工', width: 110, type: 'date' },
  { prop: 'planEnd', label: '计划完工', width: 110, slot: true },
  { prop: 'deptName', label: '车间', width: 100 },
  { prop: 'priority', label: '优先级', width: 70, align: 'center' },
  { prop: 'salesOrderNo', label: '销售订单', width: 140 },
  { prop: 'prodStatus', label: '状态', width: 90, type: 'status', statusMap: PROD_STATUS },
  { prop: 'ownerName', label: '计划员', width: 90 }
]
const asRow = (r: unknown) => r as ProdOrderRow

const printIds = () => selection.value.map((r) => r.id)
const exportParams = () => toParams({ ...query })

// ---------- 批量下达 ----------
const releasing = ref(false)
async function batchRelease() {
  const ids = selection.value.filter((r) => r.prodStatus === 'PLANNED').map((r) => r.id)
  if (!ids.length) return ElMessage.warning('请勾选已计划的生产订单')
  const confirmShortage = await ElMessageBox.confirm(`将下达 ${ids.length} 张生产订单。缺料的订单是否也下达？`, '批量下达', {
    type: 'warning', distinguishCancelAndClose: true, confirmButtonText: '缺料也下达', cancelButtonText: '跳过缺料订单'
  }).then(() => true).catch((a) => (a === 'cancel' ? false : undefined))
  if (confirmShortage === undefined) return
  releasing.value = true
  try {
    const r = await prodOrderApi.batchRelease(ids, confirmShortage)
    if (r.errors.length) ElNotification({ type: 'warning', title: `成功 ${r.success} 张，失败 ${r.errors.length} 张`, message: r.errors.join('；'), duration: 10000 })
    else ElMessage.success(`已下达 ${r.success} 张`)
    load()
  } finally {
    releasing.value = false
  }
}
</script>

<template>
  <ErpPage description="生产订单：按 BOM 和工艺路线生成用料与工序，下达后领料、派工、报工、完工入库">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="mfg.prod-order" @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'mfg:prod-order:create'" type="primary" icon="Plus" @click="router.push('/production/prod-order/new')">新建生产订单</el-button>
          <el-button v-perm="'mfg:prod-order:release'" :loading="releasing" @click="batchRelease">批量下达</el-button>
          <PrintButton biz-type="MFG_PROD_ORDER" :ids="printIds" permission="mfg:prod-order:print" label="打印流程卡" />
        </template>
        <template #toolbar-right>
          <ExportButton url="/production/prod-orders/export" :params="exportParams" filename="生产订单" permission="mfg:prod-order:query" />
        </template>
        <template #col-progress="{ row }">
          <el-progress :percentage="Math.min(100, Math.round(Number(asRow(row).progress) * 100))" :stroke-width="6" />
        </template>
        <template #col-planEnd="{ row }">
          <span :class="{ 'text-danger': asRow(row).overdue }">{{ asRow(row).planEnd }}</span>
          <ErpBadge v-if="asRow(row).overdue" type="danger" :dot="false">逾期</ErpBadge>
        </template>
        <template #empty>
          <el-button v-perm="'mfg:prod-order:create'" icon="Plus" @click="router.push('/production/prod-order/new')">新建生产订单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
