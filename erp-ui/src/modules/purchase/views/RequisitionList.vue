<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { today } from '@/utils/format'
import { joinList } from '../api/common'
import GenerateOrderDialog from '../components/GenerateOrderDialog.vue'
import { REQ_STATUS, REQ_STATUS_OPTIONS, requisitionApi, type PendingLine, type PendingQuery, type ReqQuery, type ReqRow } from '../api/requisition'

defineOptions({ name: 'PurRequisitionList' })

/** 采购申请（需求 07-03 3.1，T1）：申请单列表 + 待转订单明细 */
const router = useRouter()
const tab = ref<'doc' | 'pending'>('doc')

// ---------- 申请单 ----------
type Query = Omit<ReqQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; required?: [string, string] }
const docs = useListPage<Query, ReqRow>({
  api: (q) => {
    const { statuses, required, ...rest } = q
    return requisitionApi.page({ ...rest, statuses: joinList(statuses), requiredFrom: required?.[0], requiredTo: required?.[1] } as ReqQuery)
  },
  defaultQuery: () => ({ statuses: ['APPROVED', 'IN_PROGRESS'] }),
  refreshOnActivated: true
})
const docQuery = docs.query
const docFields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'requisitionType', label: '申请类型', type: 'dict', dictType: 'pur_requisition_type' },
  { prop: 'statuses', label: '状态', type: 'select', options: REQ_STATUS_OPTIONS, multiple: true },
  { prop: 'requestDeptId', label: '申请部门', type: 'org' },
  { prop: 'ownerId', label: '申请人', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'required', label: '需求日期', type: 'daterange' },
  { prop: 'urgent', label: '紧急', type: 'select', options: [{ value: true, label: '紧急' }, { value: false, label: '普通' }] }
]
const docColumns: TableColumn<ReqRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/requisition/${r.id}`) },
  { prop: 'requisitionType', label: '类型', width: 100, type: 'dict', dictType: 'pur_requisition_type' },
  { prop: 'requestDeptName', label: '申请部门', width: 120 },
  { prop: 'ownerName', label: '申请人', width: 90 },
  { prop: 'materialSummary', label: '物料摘要', minWidth: 200 },
  { prop: 'lineCount', label: '行数', width: 60, align: 'right' },
  { prop: 'urgent', label: '紧急', width: 70, slot: true },
  { prop: 'earliestRequiredDate', label: '最早需求日期', width: 120, type: 'date' },
  { prop: 'progress', label: '转单进度', width: 90, align: 'right', formatter: (r) => `${r.orderedLineCount}/${r.lineCount}` },
  { prop: 'status', label: '状态', width: 100, type: 'status', statusMap: REQ_STATUS },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]
const asReq = (r: unknown) => r as ReqRow

// ---------- 待转订单明细 ----------
type PQuery = Omit<PendingQuery, 'pageNo' | 'pageSize'> & { required?: [string, string] }
const pending = useListPage<PQuery, PendingLine>({
  api: (q) => {
    const { required, ...rest } = q
    return requisitionApi.pending({ ...rest, requiredFrom: required?.[0], requiredTo: required?.[1] } as PendingQuery)
  },
  immediate: false
})
const pendingQuery = pending.query
const pendingFields: SearchField[] = [
  { prop: 'docNo', label: '申请单号', upper: true },
  { prop: 'supplierId', label: '建议供应商', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'required', label: '需求日期', type: 'daterange' }
]

/** 需求日期距今 ≤ 采购提前期：橙色；已过期：红色 */
function dueClass(r: PendingLine) {
  const t = today()
  if (r.requiredDate < t) return 'text-danger'
  const lead = r.leadTimeDays ?? 0
  const days = (new Date(r.requiredDate).getTime() - new Date(t).getTime()) / 86400000
  return days <= lead ? 'text-warning' : ''
}

const pendingColumns: TableColumn<PendingLine>[] = [
  { prop: 'docNo', label: '申请单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/requisition/${r.requisitionId}`) },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 150 },
  { prop: 'pendingQty', label: '未转数量', width: 100, type: 'qty' },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'requiredDate', label: '需求日期', width: 110, slot: true },
  { prop: 'suggestedSupplierName', label: '建议供应商', width: 130 },
  { prop: 'referencePrice', label: '参考价', width: 100, type: 'price' },
  { prop: 'ownerName', label: '申请人', width: 90 },
  { prop: 'sourceDemand', label: '需求来源', width: 140, hidden: true }
]
const asPending = (r: unknown) => r as PendingLine

function onTab(t: string | number) {
  if (t === 'pending' && !pending.list.value.length) pending.load()
}

// ---------- 生成采购订单 ----------
const genRef = ref<{ open: (rows: PendingLine[]) => void }>()
function onGenerated(ids: string[]) {
  if (ids.length !== 1) router.push('/purchase/order')
}
</script>

<template>
  <ErpPage description="部门或 MRP 提出的采购需求；审核后由采购员转为采购订单">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="list-tabs" @tab-change="onTab">
        <el-tab-pane label="申请单" name="doc">
          <ErpSearchForm v-model="docQuery" :fields="docFields" :loading="docs.loading.value" @search="docs.search" @reset="docs.reset">
            <template #field-materialId><MaterialSelect v-model="docQuery.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="docColumns" :data="docs.list.value" :loading="docs.loading.value" storage-key="pur.requisition" @refresh="docs.load">
            <template #toolbar>
              <el-button v-perm="'pur:requisition:create'" type="primary" icon="Plus" @click="router.push('/purchase/requisition/new')">新建采购申请</el-button>
            </template>
            <template #col-urgent="{ row }"><ErpBadge v-if="asReq(row).urgent" type="danger">紧急</ErpBadge></template>
          </ErpTable>
          <ErpPagination v-model:page-no="docQuery.pageNo" v-model:page-size="docQuery.pageSize" :total="docs.total.value" @change="docs.load" />
        </el-tab-pane>

        <el-tab-pane label="待转订单明细" name="pending">
          <ErpSearchForm v-model="pendingQuery" :fields="pendingFields" :loading="pending.loading.value" @search="pending.search" @reset="pending.reset">
            <template #field-supplierId><SupplierSelect v-model="pendingQuery.supplierId" /></template>
            <template #field-materialId><MaterialSelect v-model="pendingQuery.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="pendingColumns" :data="pending.list.value" :loading="pending.loading.value" selection storage-key="pur.requisition-pending"
                    @selection-change="pending.onSelectionChange" @refresh="pending.load">
            <template #toolbar>
              <el-button v-perm="'pur:order:create'" type="primary" icon="Plus" :disabled="!pending.selection.value.length" @click="genRef?.open(pending.selection.value)">生成采购订单</el-button>
            </template>
            <template #col-requiredDate="{ row }"><span :class="dueClass(asPending(row))">{{ asPending(row).requiredDate }}</span></template>
          </ErpTable>
          <ErpPagination v-model:page-no="pendingQuery.pageNo" v-model:page-size="pendingQuery.pageSize" :total="pending.total.value" @change="pending.load" />
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <GenerateOrderDialog ref="genRef" @done="onGenerated" />
  </ErpPage>
</template>

<style scoped>
.list-tabs { padding: 0 var(--erp-space-5) var(--erp-space-4); }
</style>
