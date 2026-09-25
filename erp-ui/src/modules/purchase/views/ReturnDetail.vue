<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatMoney } from '@/utils/format'
import { labelOf, submitText } from '../api/common'
import { HANDLING_OPTIONS, RETURN_STATUS, returnApi, type ReturnDetail, type ReturnLine } from '../api/receipt'

defineOptions({ name: 'PurReturnDetail' })

/** 退货单详情（需求 07-08 3.4，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ReturnDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await returnApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:return:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除退货单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await returnApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/return')
    } },
  { key: 'void', label: '作废', permission: 'pur:return:void', visible: () => s.value === 'DRAFT' || s.value === 'APPROVED', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，生成的出库单草稿同时作废。', handler: (reason) => run(returnApi.void(id.value, reason!), '已作废') },
  { key: 'edit', label: '编辑', permission: 'pur:return:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/return/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'pur:return:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await returnApi.submit(id.value)).status))
      load()
    } }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '待出库' },
  { status: 'COMPLETED', label: '已完成' }
]

const columns = computed<TableColumn<ReturnLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'receiptNo', label: '到货单', width: 150, type: 'link', onClick: (r) => r.receiptId && router.push(`/purchase/receipt/${r.receiptId}`) },
  { prop: 'orderNo', label: '订单号', width: 150 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'batchNo', label: '批次', width: 130 },
  { prop: 'qty', label: '退货数量', width: 100, type: 'qty', summary: true },
  ...(d.value?.priceVisible ? [
    { prop: 'priceInclTax', label: '含税单价', width: 100, type: 'price' } as TableColumn<ReturnLine>,
    { prop: 'totalAmount', label: '金额', width: 120, type: 'amount', summary: true } as TableColumn<ReturnLine>
  ] : []),
  { prop: 'outQty', label: '已出库', width: 90, type: 'qty' },
  { prop: 'outDate', label: '出库日期', width: 110, type: 'date' },
  { prop: 'statementQty', label: '已对账', width: 90, type: 'qty', hidden: true },
  { prop: 'remark', label: '备注', minWidth: 120 }
])

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '退货单'" :status="d?.status" :status-map="RETURN_STATUS" :actions="actions" @back="router.push('/purchase/return')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="PUR_RETURN" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.status !== 'VOIDED'" biz-type="PUR_RETURN" :ids="[id]" permission="pur:return:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ VOIDED: `已作废：${d.voidReason ?? ''}` }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="供应商">
            <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${d.supplierId}`)">{{ d.supplierName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="退货原因"><DictTag type="pur_return_reason" :value="d.returnReason" /></el-descriptions-item>
          <el-descriptions-item label="处理方式">{{ labelOf(HANDLING_OPTIONS, d.handling) }}</el-descriptions-item>
          <el-descriptions-item label="出库仓">{{ d.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出库单">
            <el-link v-if="d.stockOutId" type="primary" underline="never" @click="router.push(`/inventory/out/${d.stockOutId}`)">{{ d.stockOutNo || '查看' }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="d.priceVisible" label="金额">{{ formatMoney(d.totalAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="NCR">{{ d.ncrNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="经办人">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="4">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines"><ErpTable :columns="columns" :data="d.lines" no-toolbar /></el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_RETURN" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_RETURN" :biz-id="id" :status-map="RETURN_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_RETURN" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
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
