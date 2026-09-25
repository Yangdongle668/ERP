<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { download } from '@/api/http'
import { toDateString } from '@/utils/format'
import {
  docApi, DOC_STATUS, DOC_STATUS_OPTIONS, IN_TYPE_OPTIONS, labelOf, OUT_TYPE_OPTIONS, TRANSFER_TYPE_OPTIONS,
  type DocKind, type DocQuery, type DocRow
} from '../api/inventory'

defineOptions({ name: 'InvDocList' })

/**
 * 入库单、出库单、调拨单列表（需求 08-03/04/05 第 3.1 节，T1），三个菜单共用本页面，类型由路由决定。
 * 快捷筛选：待处理（业务生成的草稿 + 已审核的手工单）/ 今日已确认 / 全部。
 */
const route = useRoute()
const router = useRouter()
const kind = route.path.split('/')[2] as DocKind
const api = docApi(kind)

const DESCRIPTION = {
  in: '所有入库都通过入库单完成：业务模块生成的单据由仓管员补充库位、批次后确认入库；只有“其他入库”可以手工新建',
  out: '所有出库都通过出库单完成：确认时选择或自动分配批次（FIFO/FEFO），实发数量可少于申请（领料），销售出库必须等于申请',
  transfer: '库存在仓库、库位之间移动，确认时同时完成调出和调入；检验调拨由品质判定后系统生成'
}[kind]

const META = {
  in: { name: '入库单', perm: 'inv:in', route: '/inventory/in', types: IN_TYPE_OPTIONS, todo: '待入库', today: '今日已入库', exportPerm: 'inv:in:export' },
  out: { name: '出库单', perm: 'inv:out', route: '/inventory/out', types: OUT_TYPE_OPTIONS, todo: '待出库', today: '今日已出库', exportPerm: 'inv:out:export' },
  transfer: { name: '调拨单', perm: 'inv:transfer', route: '/inventory/transfer', types: TRANSFER_TYPE_OPTIONS, todo: '待调拨', today: '今日已调拨', exportPerm: 'inv:transfer:query' }
}[kind]

type Query = Omit<DocQuery, 'pageNo' | 'pageSize' | 'types' | 'statuses'> & { types?: string[]; statuses?: string[]; dates?: string[] }

function monthAgo() {
  const d = new Date()
  d.setDate(d.getDate() - 30)
  return toDateString(d)
}

const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, DocRow>({
  api: (q) => api.page(toParams(q)),
  defaultQuery: () => ({ quick: 'TODO', dates: [monthAgo(), toDateString(new Date())] }),
  refreshOnActivated: true
})

function toParams(q: Query & { pageNo?: number; pageSize?: number }): DocQuery {
  const { types, statuses, dates, ...rest } = q
  return {
    ...rest, pageNo: q.pageNo ?? 1, pageSize: q.pageSize ?? 20,
    types: types?.length ? types.join(',') : undefined,
    statuses: statuses?.length ? statuses.join(',') : undefined,
    dateFrom: q.quick === 'TODO' ? undefined : dates?.[0], dateTo: q.quick === 'TODO' ? undefined : dates?.[1]
  }
}

// ---------- 快捷筛选 ----------
const counts = ref<{ todo: number; today: number }>({ todo: 0, today: 0 })
async function loadCounts() {
  counts.value = await api.quickCounts().catch(() => counts.value)
}
onMounted(loadCounts)
onActivated(loadCounts)
function setQuick(v: string) {
  query.quick = v
  search()
}
function reload() {
  load()
  loadCounts()
}

const fields = computed<SearchField[]>(() => [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'types', label: '类型', type: 'select', options: META.types, multiple: true },
  { prop: 'warehouseId', label: kind === 'transfer' ? '调出仓' : '仓库', type: 'slot' },
  ...(kind === 'transfer' ? [{ prop: 'toWarehouseId', label: '调入仓', type: 'slot' as const }] : []),
  { prop: 'statuses', label: '状态', type: 'select', options: DOC_STATUS_OPTIONS, multiple: true },
  { prop: 'sourceNo', label: '来源单号' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
])

const whLabel = (r: DocRow) => r.warehouseName ?? '-'
const columns = computed<TableColumn<DocRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`${META.route}/${r.id}`) },
  { prop: 'type', label: '类型', width: 110, formatter: (r) => labelOf(META.types, r.type) },
  kind === 'transfer'
    ? { prop: 'warehouseName', key: 'route', label: '调出仓 → 调入仓', minWidth: 190, formatter: (r) => `${whLabel(r)} → ${r.toWarehouseName ?? '-'}` }
    : { prop: 'warehouseName', label: '仓库', width: 120 },
  { prop: 'sourceNo', label: kind === 'transfer' ? '来源检验单' : '来源单号', width: 150 },
  ...(kind === 'out' ? [{ prop: 'partnerName', label: '领用人', width: 100 }] : []),
  { prop: 'materialSummary', label: '物料', minWidth: 200 },
  { prop: 'totalQty', label: '总数量', width: 100, type: 'qty' },
  ...(kind === 'in' ? [{ prop: 'amount', label: '金额', width: 110, type: 'amount' as const, hidden: true }] : []),
  { prop: 'docDate', label: '日期', width: 100, type: 'date' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: DOC_STATUS },
  { prop: 'confirmedAt', key: 'confirmed', label: '确认人/时间', width: 170, formatter: (r) => (r.confirmedAt ? `${r.confirmedByName ?? ''} ${r.confirmedAt.slice(5, 16)}` : '-') },
  { prop: 'createdByName', label: '创建人', width: 90, hidden: true }
])

const canConfirm = (r: DocRow) => (kind === 'transfer' ? r.status === 'DRAFT' : (r.manual ? r.status === 'APPROVED' : r.status === 'DRAFT'))
const canEdit = (r: DocRow) => r.status === 'DRAFT'

async function confirmOne(r: DocRow) {
  // 批次、库位、序列号需要补充的单据进入确认页
  router.push(`${META.route}/${r.id}/edit`)
}

async function batchConfirm() {
  const ids = selection.value.filter(canConfirm).map((r) => r.id)
  if (!ids.length) return ElMessage.warning('请勾选可确认的单据')
  await ElMessageBox.confirm(`确认 ${ids.length} 张单据？信息不全的单据将确认失败并列出原因。`, '批量确认', { type: 'warning' })
  const r = await api.batchConfirm(ids)
  if (r.failures.length) {
    ElMessageBox.alert(r.failures.map((f) => `${f.docNo}：${f.message}`).join('<br/>'), `成功 ${r.success} 张，失败 ${r.failures.length} 张`,
      { dangerouslyUseHTMLString: true, type: 'warning' })
  } else ElMessage.success(`已确认 ${r.success} 张`)
  reload()
}

async function voidDoc(r: DocRow) {
  await api.void(r.id)
  ElMessage.success('已作废')
  reload()
}

async function exportList() {
  const ids = selection.value.map((r) => r.id).join(',') || undefined
  const res = await download<{ async?: boolean }>(`${api.base}/export`, { ...toParams({ ...query }), pageNo: undefined, pageSize: undefined, ids }, `${META.name}.xlsx`)
  if (res?.async) ElMessage.info('数据量较大，已转为后台导出，完成后可在任务中心下载')
}

const asRow = (r: unknown) => r as DocRow
</script>

<template>
  <ErpPage :description="DESCRIPTION">
  <ErpPanel>
    <template #filter>
      <div class="quick">
        <el-radio-group :model-value="query.quick ?? ''" @change="(v) => setQuick(String(v))">
          <el-radio-button value="TODO">{{ META.todo }} ({{ counts.todo }})</el-radio-button>
          <el-radio-button value="TODAY">{{ META.today }} ({{ counts.today }})</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
      </div>
      <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
        <template #field-warehouseId><WarehouseSelect v-model="query.warehouseId" placeholder="全部" class="w160" /></template>
        <template #field-toWarehouseId><WarehouseSelect v-model="query.toWarehouseId" placeholder="全部" class="w160" /></template>
        <template #field-materialId><MaterialSelect v-model="query.materialId" placeholder="全部" class="w200" /></template>
      </ErpSearchForm>
    </template>
    <ErpTable :columns="columns" :data="list" :loading="loading" selection :storage-key="`inv.doc.${kind}`" :actions-width="160"
              @selection-change="onSelectionChange" @refresh="reload">
      <template #toolbar>
        <el-button v-if="kind === 'in'" v-perm="'inv:in:create'" type="primary" icon="Plus" @click="router.push('/inventory/in/new')">新建其他入库</el-button>
        <el-button v-if="kind === 'out'" v-perm="'inv:out:create'" type="primary" icon="Plus" @click="router.push('/inventory/out/new')">新建其他出库</el-button>
        <template v-if="kind === 'transfer'">
          <el-button v-perm="'inv:transfer:create'" type="primary" icon="Plus" @click="router.push('/inventory/transfer/new')">新建调拨</el-button>
          <el-button v-perm="'inv:transfer:create'" @click="router.push('/inventory/transfer/new?type=RECHECK')">复检送检</el-button>
        </template>
        <el-button v-perm="`${META.perm}:confirm`" icon="Check" @click="batchConfirm">批量确认</el-button>
      </template>
      <template #toolbar-right>
        <ErpIconButton icon="Download" tooltip="导出" :permission="META.exportPerm" @click="exportList" />
      </template>
      <template #actions="{ row }">
        <RowActions :actions="[
          { label: '确认', permission: `${META.perm}:confirm`, visible: canConfirm(asRow(row)), handler: () => confirmOne(asRow(row)) },
          { label: '编辑', permission: `${META.perm}:update`, visible: canEdit(asRow(row)) && asRow(row).manual, handler: () => router.push(`${META.route}/${asRow(row).id}/edit`) },
          { label: '作废', permission: `${META.perm}:void`, danger: true,
            visible: (asRow(row).status === 'DRAFT' || asRow(row).status === 'APPROVED') && asRow(row).manual && asRow(row).type !== 'INSPECTION',
            confirm: `确定作废 ${asRow(row).docNo} 吗？`, handler: () => voidDoc(asRow(row)) }
        ]" />
      </template>
      <template #empty>
        <span class="text-muted">{{ query.quick === 'TODO' ? `没有${META.todo}的单据` : '没有符合条件的单据' }}</span>
      </template>
    </ErpTable>
    <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
  </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.quick { margin-bottom: var(--erp-space-3); }
</style>
