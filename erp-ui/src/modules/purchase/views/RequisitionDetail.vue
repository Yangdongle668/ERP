<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { LINE_STATUS, num, submitText } from '../api/common'
import { orderApi } from '../api/order'
import { REQ_STATUS, requisitionApi, type ReqDetail, type ReqLine } from '../api/requisition'

defineOptions({ name: 'PurRequisitionDetail' })

/** 采购申请详情（需求 07-03 3.3，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ReqDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await requisitionApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const ordered = computed(() => (d.value?.lines ?? []).some((l) => num(l.orderedQty) > 0))
const executable = computed(() => s.value === 'APPROVED' || s.value === 'IN_PROGRESS')

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:requisition:delete', visible: () => s.value === 'DRAFT',
    confirm: `确定删除采购申请「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await requisitionApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/requisition')
    } },
  { key: 'void', label: '作废', permission: 'pur:requisition:delete', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，也不再参与任何统计。', handler: (reason) => run(requisitionApi.void(id.value, reason!), '已作废') },
  { key: 'unapprove', label: '反审核', permission: 'pur:requisition:unapprove', visible: () => s.value === 'APPROVED' && !ordered.value, reasonRequired: true,
    reasonTitle: '反审核原因', confirm: '反审核后单据回到草稿状态，可以修改。', handler: (reason) => run(requisitionApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'close', label: '关闭', permission: 'pur:requisition:close', visible: () => executable.value, reasonRequired: true, reasonTitle: '关闭原因',
    confirm: '关闭后剩余未转订单的行不再执行。', handler: (reason) => run(requisitionApi.close(id.value, reason!), '已关闭') },
  { key: 'edit', label: '编辑', permission: 'pur:requisition:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/requisition/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'pur:requisition:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await requisitionApi.submit(id.value)).status))
      load()
    } },
  { key: 'toOrder', label: '转采购订单', type: 'primary', permission: 'pur:order:create', visible: () => executable.value, handler: toOrder }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

/** 未转完的行按建议供应商生成草稿订单；没有建议供应商的行到“待转订单明细”中选择供应商 */
async function toOrder() {
  const open = (d.value?.lines ?? []).filter((l) => l.lineStatus === 'OPEN' && num(l.qty) > num(l.orderedQty))
  const withSupplier = open.filter((l) => l.suggestedSupplierId)
  if (!withSupplier.length) {
    ElMessage.warning('没有指定建议供应商的未转行，请在“待转订单明细”中选择供应商后生成')
    return
  }
  const r = await orderApi.fromRequisitions(withSupplier.map((l) => ({ requisitionLineId: l.id!, supplierId: l.suggestedSupplierId! })))
  const skipped = open.length - withSupplier.length
  const msgs = [...r.messages, ...(skipped ? [`${skipped} 行没有建议供应商，未生成`] : [])]
  if (msgs.length) {
    await ElMessageBox.alert(`<p>已生成 ${r.orderIds.length} 张草稿订单。</p><ul>${msgs.map((m) => `<li>${m}</li>`).join('')}</ul>`, '生成结果',
      { dangerouslyUseHTMLString: true })
  } else {
    ElMessage.success(`已生成 ${r.orderIds.length} 张草稿订单`)
  }
  if (r.orderIds.length === 1) router.push(`/purchase/order/${r.orderIds[0]}/edit`)
  else load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已审核' },
  { status: 'IN_PROGRESS', label: '部分转订单' },
  { status: 'COMPLETED', label: '已转订单' }
]

const columns = computed<TableColumn<ReqLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 150 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'qty', label: '数量', width: 100, type: 'qty' },
  { prop: 'orderedQty', label: '已转订单', width: 100, type: 'qty' },
  { prop: 'requiredDate', label: '需求日期', width: 110, type: 'date' },
  { prop: 'suggestedSupplierName', label: '建议供应商', width: 130 },
  ...(d.value?.priceVisible ? [{ prop: 'referencePrice', label: '参考价', width: 100, type: 'price' } as TableColumn<ReqLine>] : []),
  { prop: 'purpose', label: '用途', width: 120 },
  { prop: 'sourceDemand', label: '需求来源', width: 140 },
  { prop: 'lineStatus', label: '行状态', width: 90, type: 'status', statusMap: LINE_STATUS },
  { prop: 'remark', label: '备注', minWidth: 120 }
])

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '采购申请'" :status="d?.status" :status-map="REQ_STATUS" :actions="actions" @back="router.push('/purchase/requisition')">
        <template #extra><ErpBadge v-if="d?.urgent" type="danger">紧急</ErpBadge></template>
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="PUR_REQUISITION" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CLOSED: '已关闭：剩余未转行不再执行', VOIDED: '已作废' }" />
        <el-descriptions :column="3" class="head">
          <el-descriptions-item label="申请类型"><DictTag type="pur_requisition_type" :value="d.requisitionType" /></el-descriptions-item>
          <el-descriptions-item label="申请部门">{{ d.requestDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ d.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="MRP 运算">{{ d.mrpRunId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines"><ErpTable :columns="columns" :data="d.lines" no-toolbar /></el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_REQUISITION" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_REQUISITION" :biz-id="id" :status-map="REQ_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_REQUISITION" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
