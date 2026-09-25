<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime } from '@/utils/format'
import { INSPECT_STATUS, labelOf } from '../api/common'
import { RECEIPT_STATUS, RECEIPT_TYPE_OPTIONS, receiptApi, type ReceiptDetail, type ReceiptLine } from '../api/receipt'

defineOptions({ name: 'PurReceiptDetail' })

/** 到货单详情（需求 07-06 3.3，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ReceiptDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await receiptApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:receipt:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除到货单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await receiptApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/receipt')
    } },
  { key: 'unapprove', label: '反审核', permission: 'pur:receipt:unapprove', visible: () => s.value === 'APPROVED', reasonRequired: true, reasonTitle: '反审核原因',
    confirm: '反审核后作废生成的入库单草稿，到货单回到草稿状态。', handler: (reason) => run(receiptApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'edit', label: '编辑', permission: 'pur:receipt:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/receipt/${id.value}/edit`) },
  { key: 'approve', label: '审核', type: 'primary', permission: 'pur:receipt:approve', visible: () => s.value === 'DRAFT',
    handler: () => run(receiptApi.approve(id.value), '审核成功，已生成入库单') }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'APPROVED', label: '已审核' },
  { status: 'COMPLETED', label: '已完成' }
]

const columns: TableColumn<ReceiptLine>[] = [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'orderNo', label: '订单号', width: 150, formatter: (r) => `${r.orderNo ?? ''}${r.orderLineNo ? `-${r.orderLineNo}` : ''}` },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'qty', label: '到货数量', width: 100, type: 'qty' },
  { prop: 'supplierBatchNo', label: '供应商批号', width: 120 },
  { prop: 'productionDate', label: '生产日期', width: 110, type: 'date', hidden: true },
  { prop: 'targetWarehouseName', label: '入库仓', width: 100 },
  { prop: 'stockInNo', label: '入库单', width: 150, type: 'link', onClick: (r) => r.stockInId && router.push(`/inventory/in/${r.stockInId}`) },
  { prop: 'stockedQty', label: '已入库', width: 90, type: 'qty' },
  { prop: 'batchNo', label: '批次', width: 130 },
  { prop: 'inspectStatus', label: '检验', width: 90, type: 'status', statusMap: INSPECT_STATUS },
  { prop: 'qualifiedQty', label: '合格', width: 90, type: 'qty' },
  { prop: 'concessionQty', label: '特采', width: 80, type: 'qty', hidden: true },
  { prop: 'rejectedQty', label: '不合格', width: 90, type: 'qty' },
  { prop: 'returnedQty', label: '已退货', width: 90, type: 'qty' },
  { prop: 'statementQty', label: '已对账', width: 90, type: 'qty', hidden: true },
  { prop: 'inspectionNo', label: '检验单', width: 140 },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
]

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '到货单'" :status="d?.status" :status-map="RECEIPT_STATUS" :actions="actions" @back="router.push('/purchase/receipt')">
        <template #actions-prefix>
          <PrintButton v-if="d" biz-type="PUR_RECEIPT" :ids="[id]" permission="pur:receipt:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="供应商">
            <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${d.supplierId}`)">{{ d.supplierName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="到货类型">{{ labelOf(RECEIPT_TYPE_OPTIONS, d.receiptType) }}</el-descriptions-item>
          <el-descriptions-item label="送货单号">{{ d.deliveryNoteNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到货时间">{{ formatDateTime(d.arrivalAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{ d.receiverName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="制单">{{ d.createdByName || '-' }} {{ formatDateTime(d.createdAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines"><ErpTable :columns="columns" :data="d.lines" storage-key="pur.receipt-lines" /></el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_RECEIPT" :biz-id="id" :status-map="RECEIPT_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_RECEIPT" :biz-id="id" editable /></el-tab-pane>
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
