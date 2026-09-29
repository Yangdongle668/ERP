<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney, formatQty, formatRate } from '@/utils/format'
import { DOC_STATUS, HANDLING_OPTIONS, labelOf, num, returnApi, submitText, type ReturnDetail, type ReturnLine } from '../api/sales'

defineOptions({ name: 'SalReturnDetail' })

/** 销售退货详情（需求 04-06 3.3～3.5，T5）：收货进度、品质判定（品质模块上线前可手工登记） */
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
const received = computed(() => (d.value?.lines ?? []).some((l) => num(l.receivedQty) > 0))

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:return:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除退货单「${d.value?.docNo}」吗？`,
    handler: async () => {
      await returnApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/sales/return')
    } },
  { key: 'void', label: '作废', permission: 'sales:return:void', visible: () => s.value === 'DRAFT' || (s.value === 'APPROVED' && !received.value),
    reasonRequired: true, reasonTitle: '作废原因', confirm: '作废后退货入库单同时作废，不可恢复。', handler: async (reason) => {
      await returnApi.void(id.value, reason!)
      ElMessage.success('已作废')
      load()
    } },
  { key: 'judge', label: '登记判定', permission: 'sales:return:update', visible: () => (s.value === 'APPROVED' || s.value === 'COMPLETED') && received.value,
    handler: openJudge },
  { key: 'edit', label: '编辑', permission: 'sales:return:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/sales/return/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'sales:return:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await returnApi.submit(id.value)).status))
      load()
    } }
])

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '待收货' },
  { status: 'COMPLETED', label: '已完成' }
]

const lineColumns: TableColumn<ReturnLine>[] = [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'orderNo', label: '原订单', width: 170, type: 'link', formatter: (r) => `${r.orderNo ?? ''} 行 ${r.orderLineNo ?? ''}`, onClick: (r) => r.orderId && router.push(`/sales/order/${r.orderId}`) },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'qty', label: '退货数量', width: 100, type: 'qty', summary: true },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'priceInclTax', label: '含税单价', width: 100, type: 'price' },
  { prop: 'totalAmount', label: '金额', width: 120, type: 'amount', summary: true },
  { prop: 'batchNo', label: '批次', width: 110 },
  { prop: 'receivedQty', label: '已收货', width: 90, type: 'qty' },
  { prop: 'goodQty', label: '良品', width: 80, type: 'qty' },
  { prop: 'reworkQty', label: '返工', width: 80, type: 'qty' },
  { prop: 'scrapQty', label: '报废', width: 80, type: 'qty' },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
]

// ---------- 判定 ----------
const judgeVisible = ref(false)
const judgeRows = ref<{ lineId: string; materialCode?: string; materialName?: string; receivedQty?: string; goodQty?: string; reworkQty?: string; scrapQty?: string; baseUom?: string }[]>([])
function openJudge() {
  judgeRows.value = (d.value?.lines ?? []).filter((l) => num(l.receivedQty) > 0).map((l) => ({ lineId: l.id!, materialCode: l.materialCode,
    materialName: l.materialName, receivedQty: l.receivedQty, goodQty: l.goodQty, reworkQty: l.reworkQty, scrapQty: l.scrapQty, baseUom: l.baseUom }))
  judgeVisible.value = true
}
async function saveJudge() {
  const bad = judgeRows.value.find((r) => num(r.goodQty) + num(r.reworkQty) + num(r.scrapQty) > num(r.receivedQty))
  if (bad) return ElMessage.warning(`${bad.materialCode}：判定数量合计不能超过已收货数量`)
  await returnApi.judge(id.value, judgeRows.value.map((r) => ({ lineId: r.lineId, goodQty: r.goodQty, reworkQty: r.reworkQty, scrapQty: r.scrapQty })))
  judgeVisible.value = false
  ElMessage.success('判定结果已登记')
  load()
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '销售退货'" :status="d?.status" :status-map="DOC_STATUS" :actions="actions" @back="router.push('/sales/return')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="SAL_RETURN" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && s !== 'VOIDED'" biz-type="SAL_RETURN" :ids="[id]" permission="sales:return:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ VOIDED: `已作废：${d.voidReason ?? ''}` }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="退货原因"><DictTag type="sal_return_reason" :value="d.returnReason" /></el-descriptions-item>
          <el-descriptions-item label="处理方式">{{ labelOf(HANDLING_OPTIONS, d.handling) }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="RMA 号">{{ d.rmaNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客诉单号">{{ d.complaintNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="预计到货">{{ d.expectedArrivalDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ d.currency }} / {{ formatRate(d.exchangeRate) }}</el-descriptions-item>
          <el-descriptions-item label="退货金额">{{ formatMoney(d.totalAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(d.createdAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines"><ErpTable :columns="lineColumns" :data="d.lines" storage-key="sal.return-lines" /></el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SAL_RETURN" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SAL_RETURN" :biz-id="id" :status-map="DOC_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="SAL_RETURN" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="judgeVisible" title="登记品质判定" width="860px" :close-on-click-modal="false" append-to-body>
      <el-table :data="judgeRows">
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="已收货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.receivedQty) }}</template></el-table-column>
        <el-table-column label="良品" width="140"><template #default="{ row }"><QtyInput v-model="row.goodQty" :uom="row.baseUom" /></template></el-table-column>
        <el-table-column label="返工" width="140"><template #default="{ row }"><QtyInput v-model="row.reworkQty" :uom="row.baseUom" /></template></el-table-column>
        <el-table-column label="报废" width="140"><template #default="{ row }"><QtyInput v-model="row.scrapQty" :uom="row.baseUom" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveJudge">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
