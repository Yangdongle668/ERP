<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney, formatQty, formatRate } from '@/utils/format'
import { DOC_STATUS, LINE_STATUS, labelOf, submitText } from '../api/common'
import { changeApi, ORDER_STATUS, ORDER_TYPE_OPTIONS, orderApi, type ChangeRow, type OrderDetail, type OrderLine } from '../api/order'

defineOptions({ name: 'PurOrderDetail' })

/** 采购订单详情（需求 07-05 3.4，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<OrderDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await orderApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  changes.value = undefined
  if (activeTab.value === 'changes') loadChanges()
}

const s = computed(() => d.value?.status)
const active = computed(() => s.value === 'APPROVED' || s.value === 'IN_PROGRESS')

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:order:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除采购订单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await orderApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/order')
    } },
  { key: 'void', label: '作废', permission: 'pur:order:void', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，也不再参与任何统计。', handler: (reason) => run(orderApi.void(id.value, reason!), '已作废') },
  { key: 'unapprove', label: '反审核', permission: 'pur:order:unapprove', visible: () => s.value === 'APPROVED' && !d.value?.hasReceipt, reasonRequired: true,
    reasonTitle: '反审核原因', confirm: '反审核后单据回到草稿状态，可以修改。', handler: (reason) => run(orderApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'close', label: '关闭', permission: 'pur:order:close', visible: () => active.value, reasonRequired: true, reasonTitle: '关闭原因',
    reasonOptions: ['供应商无法交货', '需求取消', '剩余数量不再采购'], confirm: '关闭后剩余数量不再执行。', handler: (reason) => run(orderApi.close(id.value, reason!), '已关闭') },
  { key: 'sent', label: '发送给供应商', permission: 'pur:order:print', visible: () => active.value, confirm: '确认已将订单发送给供应商（打印或下载 PDF 后发送）？',
    handler: () => run(orderApi.sent(id.value), '已记录发送时间') },
  { key: 'confirmDate', label: '回复交期', permission: 'pur:order:confirm-date', visible: () => active.value, handler: openConfirm },
  { key: 'change', label: '变更', permission: 'pur:order:change', visible: () => active.value,
    handler: () => (d.value?.runningChangeId ? router.push(`/purchase/order/change/${d.value.runningChangeId}`) : router.push(`/purchase/order/change/new?orderId=${id.value}`)) },
  { key: 'edit', label: '编辑', permission: 'pur:order:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/order/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'pur:order:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await orderApi.submit(id.value)).status))
      load()
    } },
  { key: 'receive', label: '登记到货', type: 'primary', permission: 'pur:receipt:create', visible: () => active.value,
    handler: () => router.push({ path: '/purchase/receipt/new', query: { supplierId: d.value!.supplierId, orderId: id.value, orderNo: d.value!.docNo } }) }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已审核' },
  { status: 'IN_PROGRESS', label: '执行中' },
  { status: 'COMPLETED', label: '已完成' }
]

const lineColumns = computed<TableColumn<OrderLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 140 },
  { prop: 'supplierPartNo', label: '供应商料号', width: 110, hidden: true },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'qty', label: '数量', width: 100, type: 'qty', summary: true },
  ...(d.value?.priceVisible ? [
    { prop: d.value.taxIncluded ? 'priceInclTax' : 'price', label: d.value.taxIncluded ? '含税单价' : '不含税单价', width: 110, slot: true } as TableColumn<OrderLine>,
    { prop: 'taxRate', label: '税率', width: 90, type: 'percent' } as TableColumn<OrderLine>,
    { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', summary: true } as TableColumn<OrderLine>
  ] : []),
  { prop: 'requiredDate', label: '要求日期', width: 110, type: 'date' },
  { prop: 'confirmedDate', label: '确认交期', width: 110, slot: true },
  { prop: 'receivedQty', label: '已到货', width: 90, type: 'qty' },
  { prop: 'stockedQty', label: '已入库', width: 90, type: 'qty' },
  { prop: 'qualifiedQty', label: '合格', width: 90, type: 'qty' },
  { prop: 'returnedQty', label: '已退货', width: 90, type: 'qty' },
  { prop: 'statementQty', label: '已对账', width: 90, type: 'qty' },
  { prop: 'openQty', label: '未到货', width: 90, type: 'qty' },
  { prop: 'requisitionNo', label: '来源申请', width: 140, type: 'link', onClick: (r) => r.requisitionId && router.push(`/purchase/requisition/${r.requisitionId}`) },
  { prop: 'lineStatus', label: '行状态', width: 80, type: 'status', statusMap: LINE_STATUS },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
])
const asLine = (r: unknown) => r as OrderLine

// ---------- 回复交期 ----------
const confirmVisible = ref(false)
const confirmRows = ref<{ lineId: string; lineNo?: number; materialCode?: string; materialName?: string; qty?: string; requiredDate?: string; confirmedDate?: string }[]>([])
const batchDate = ref<string>()
const confirming = ref(false)
function openConfirm() {
  confirmRows.value = (d.value?.lines ?? []).filter((l) => l.lineStatus === 'OPEN').map((l) => ({ lineId: l.id!, lineNo: l.lineNo, materialCode: l.materialCode,
    materialName: l.materialName, qty: l.qty, requiredDate: l.requiredDate, confirmedDate: l.confirmedDate }))
  batchDate.value = undefined
  confirmVisible.value = true
}
function applyBatch() {
  if (batchDate.value) confirmRows.value.forEach((r) => (r.confirmedDate = batchDate.value))
}
async function saveConfirm() {
  confirming.value = true
  try {
    await orderApi.confirmDates(id.value, confirmRows.value.map((r) => ({ lineId: r.lineId, confirmedDate: r.confirmedDate })))
    confirmVisible.value = false
    ElMessage.success('交期已更新')
    load()
  } finally {
    confirming.value = false
  }
}

// ---------- 变更记录 ----------
const changes = ref<ChangeRow[]>()
async function loadChanges() {
  changes.value = (await changeApi.page({ orderId: id.value, pageNo: 1, pageSize: 100 })).list
}
watch(activeTab, (t) => {
  if (t === 'changes' && !changes.value) loadChanges()
})
const changeColumns: TableColumn<ChangeRow>[] = [
  { prop: 'docNo', label: '变更单号', width: 170, type: 'link', onClick: (r) => router.push(`/purchase/order/change/${r.id}`) },
  { prop: 'docDate', label: '日期', width: 110, type: 'date' },
  { prop: 'changeReason', label: '变更原因', minWidth: 200 },
  { prop: 'newVersion', label: '新版本', width: 80, formatter: (r) => `V${r.newVersion}` },
  { prop: 'amountChangeBase', label: '金额变化(本位币)', width: 140, type: 'amount' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: DOC_STATUS },
  { prop: 'ownerName', label: '经办人', width: 90 }
]

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '采购订单'" :status="d?.status" :status-map="ORDER_STATUS" :actions="actions" @back="router.push('/purchase/order')">
        <template #extra>
          <ErpBadge v-if="d && d.orderVersion > 1" type="primary" :dot="false">V{{ d.orderVersion }}</ErpBadge>
          <ErpBadge v-if="d?.hasPriceOverrun" type="warning">超价格表</ErpBadge>
        </template>
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="PUR_ORDER" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.status !== 'VOIDED'" biz-type="PUR_ORDER" :ids="[id]" permission="pur:order:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CLOSED: `已关闭：${d.closeReason ?? ''}`, VOIDED: '已作废' }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="供应商">
            <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${d.supplierId}`)">{{ d.supplierCode }} {{ d.supplierName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="订单类型">{{ labelOf(ORDER_TYPE_OPTIONS, d.orderType) }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="采购员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ d.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ d.currency }} / {{ formatRate(d.exchangeRate) }}</el-descriptions-item>
          <el-descriptions-item label="付款条件">{{ d.paymentTermName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款"><DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item v-if="d.priceVisible" label="价税合计">{{ formatMoney(d.totalAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item v-if="d.priceVisible" label="税额">{{ formatMoney(d.taxAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="单价含税">{{ d.taxIncluded ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ d.sentAt ? formatDateTime(d.sentAt, true) : '未发送' }}</el-descriptions-item>
          <el-descriptions-item label="送货地址" :span="2">{{ d.deliveryAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <ErpTable :columns="lineColumns" :data="d.lines" storage-key="pur.order-lines">
              <template #col-priceInclTax="{ row }">
                <span class="num">{{ asLine(row).priceInclTax }}</span>
                <ErpBadge v-if="asLine(row).priceOverrun" type="warning" :dot="false">超价</ErpBadge>
              </template>
              <template #col-price="{ row }">
                <span class="num">{{ asLine(row).price }}</span>
                <ErpBadge v-if="asLine(row).priceOverrun" type="warning" :dot="false">超价</ErpBadge>
              </template>
              <template #col-confirmedDate="{ row }">
                <span :class="{ 'text-warning': asLine(row).delayed, 'text-danger': asLine(row).overdue }">{{ asLine(row).confirmedDate || '-' }}</span>
                <ErpBadge v-if="asLine(row).delayed" type="warning" :dot="false">交期延误</ErpBadge>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane label="执行情况" name="exec">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column label="订购" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="到货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.receivedQty) }}</template></el-table-column>
              <el-table-column label="入库" width="100" align="right"><template #default="{ row }">{{ formatQty(row.stockedQty) }}</template></el-table-column>
              <el-table-column label="合格" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qualifiedQty) }}</template></el-table-column>
              <el-table-column label="退货（退款）" width="110" align="right"><template #default="{ row }">{{ formatQty(row.returnedQty) }}</template></el-table-column>
              <el-table-column label="换货待补" width="100" align="right"><template #default="{ row }">{{ formatQty(row.replaceQty) }}</template></el-table-column>
              <el-table-column label="对账" width="100" align="right"><template #default="{ row }">{{ formatQty(row.statementQty) }}</template></el-table-column>
              <el-table-column label="未到货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.openQty) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="交期" name="delivery">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="requiredDate" label="要求日期" width="110" />
              <el-table-column prop="confirmedDate" label="确认交期" width="110" />
              <el-table-column prop="firstReceivedDate" label="首次到货" width="110" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <span v-if="row.overdue" class="text-danger">逾期未到</span>
                  <span v-else-if="row.delayed" class="text-warning">交期延误</span>
                  <span v-else class="text-muted">正常</span>
                </template>
              </el-table-column>
              <el-table-column prop="lastFollowUp" label="最近跟催" min-width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="变更记录" name="changes">
            <ErpTable :columns="changeColumns" :data="changes ?? []" :loading="!changes" no-toolbar />
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_ORDER" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_ORDER" :biz-id="id" :status-map="ORDER_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_ORDER" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="confirmVisible" title="回复交期" width="960px" :close-on-click-modal="false" append-to-body>
      <div class="batch">
        <span>批量设置</span>
        <el-date-picker v-model="batchDate" value-format="YYYY-MM-DD" />
        <el-button @click="applyBatch">应用到全部行</el-button>
      </div>
      <el-table :data="confirmRows" max-height="440">
        <el-table-column prop="lineNo" label="行" width="50" />
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
        <el-table-column prop="requiredDate" label="要求日期" width="110" />
        <el-table-column label="确认交期" width="170"><template #default="{ row }"><el-date-picker v-model="row.confirmedDate" value-format="YYYY-MM-DD" class="w-full" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有未到货的行" /></template>
      </el-table>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="saveConfirm">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.batch { display: flex; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
</style>
