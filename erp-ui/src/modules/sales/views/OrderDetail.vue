<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney, formatQty, formatRate } from '@/utils/format'
import { submitWithCredit } from '../components/credit'
import {
  BASE_EVENT_OPTIONS, changeApi, DOC_STATUS, EXEC_TYPE_OPTIONS, labelOf, LINE_STATUS, orderApi, PLAN_STATUS, reportApi, submitText,
  type ChangeRow, type ExecRow, type OrderDetail, type OrderLine, type OrderPaymentSummary, type PaymentPlanRow, type SnapshotRow, type TraceStep
} from '../api/sales'

defineOptions({ name: 'SalOrderDetail' })

/** 销售订单详情（需求 04-03 3.3～3.8，T5） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<OrderDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await orderApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  exec.value = undefined
  plans.value = undefined
  snapshots.value = undefined
  changes.value = undefined
  loadTab(activeTab.value)
}

const s = computed(() => d.value?.status)
const active = computed(() => s.value === 'APPROVED' || s.value === 'IN_PROGRESS')
const hasExec = computed(() => (d.value?.lines ?? []).some((l) => Number(l.noticedQty) > 0 || Number(l.shippedQty) > 0))

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}
async function toChange() {
  if (d.value?.runningChangeId) return router.push(`/sales/order-change/${d.value.runningChangeId}/edit`)
  const cid = await changeApi.create(id.value)
  router.push(`/sales/order-change/${cid}/edit`)
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:order:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除销售订单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await orderApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/sales/order')
    } },
  { key: 'void', label: '作废', permission: 'sales:order:void', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，也不再参与任何统计。', handler: (reason) => run(orderApi.void(id.value, reason!), '已作废') },
  { key: 'unapprove', label: '反审核', permission: 'sales:order:unapprove', visible: () => s.value === 'APPROVED' && !hasExec.value, reasonRequired: true,
    reasonTitle: '反审核原因', confirm: '反审核后订单回到草稿状态，可以修改。', handler: (reason) => run(orderApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'close', label: '关闭', permission: 'sales:order:close', visible: () => active.value, reasonRequired: true, reasonTitle: '关闭原因',
    reasonOptions: ['客户取消剩余数量', '尾数不再出货', '订单转移'], confirm: '关闭后未出货数量不再执行，会释放信用占用。',
    handler: (reason) => run(orderApi.close(id.value, reason!), '已关闭') },
  { key: 'copy', label: '复制', permission: 'sales:order:create', handler: async () => {
    const r = await orderApi.copy(id.value)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    ElMessage.success('已复制为新草稿')
    router.push(`/sales/order/${r.id}/edit`)
  } },
  { key: 'change', label: d.value?.runningChangeId ? `变更中（${d.value.runningChangeNo}）` : '变更', permission: 'sales:order:change', visible: () => active.value,
    handler: toChange },
  { key: 'edit', label: '编辑', permission: 'sales:order:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/sales/order/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'sales:order:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      const r = await submitWithCredit((confirm) => orderApi.submit(id.value, confirm))
      if (!r) return
      ElMessage.success(submitText(r.status))
      if (r.warnings?.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
      load()
    } }
])

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
  { prop: 'customerPartNo', label: '客户料号', width: 110 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'qty', label: '数量', width: 100, type: 'qty', summary: true },
  { prop: d.value?.taxIncluded ? 'priceInclTax' : 'price', label: d.value?.taxIncluded ? '含税单价' : '不含税单价', width: 110, type: 'price' },
  { prop: 'taxRate', label: '税率', width: 80, type: 'percent' },
  { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', summary: true },
  ...(d.value?.costVisible ? [
    { prop: 'costPrice', label: '单位成本', width: 100, type: 'price' } as TableColumn<OrderLine>,
    { prop: 'marginRate', label: '毛利率', width: 100, slot: true } as TableColumn<OrderLine>
  ] : []),
  { prop: 'requiredDate', label: '要求交期', width: 110, type: 'date' },
  { prop: 'promisedDate', label: '承诺交期', width: 120, slot: true },
  { prop: 'shippedQty', label: '已出货', width: 90, type: 'qty' },
  { prop: 'openQty', label: '未出货', width: 90, type: 'qty' },
  { prop: 'lineStatus', label: '行状态', width: 80, type: 'status', statusMap: LINE_STATUS },
  { prop: 'priceSource', label: '价格来源', width: 140, hidden: true },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
])
const asLine = (r: unknown) => r as OrderLine
const pct = (v?: string | null) => (v == null ? '-' : `${(Number(v) * 100).toFixed(2)}%`)

// ---------- 懒加载页签 ----------
const exec = ref<ExecRow[]>()
const plans = ref<OrderPaymentSummary>()
const snapshots = ref<SnapshotRow[]>()
const changes = ref<ChangeRow[]>()
async function loadTab(t: string) {
  if (t === 'exec' && !exec.value) exec.value = await orderApi.execution(id.value)
  if (t === 'plans' && !plans.value) plans.value = await orderApi.paymentPlans(id.value)
  if (t === 'versions' && !snapshots.value) snapshots.value = await orderApi.snapshots(id.value)
  if (t === 'changes' && !changes.value) changes.value = (await changeApi.page({ orderId: id.value, pageNo: 1, pageSize: 100 } as never)).list
}
watch(activeTab, loadTab)

const execColumns: TableColumn<ExecRow>[] = [
  { prop: 'execDate', label: '日期', width: 110, type: 'date' },
  { prop: 'execType', label: '类型', width: 100, formatter: (r) => labelOf(EXEC_TYPE_OPTIONS, r.execType) },
  { prop: 'lineNo', label: '订单行', width: 70 },
  { prop: 'docNo', label: '单据号', width: 170 },
  { prop: 'qty', label: '数量', width: 110, type: 'qty' },
  { prop: 'amount', label: '金额', width: 130, type: 'amount' },
  { prop: 'createdAt', label: '记录时间', width: 150, type: 'datetime' }
]
const planColumns: TableColumn<PaymentPlanRow>[] = [
  { prop: 'seq', label: '期次', width: 60, formatter: (r) => (r.batchNo > 1 ? `${r.seq}-${r.batchNo}` : String(r.seq)) },
  { prop: 'nodeName', label: '节点', width: 120 },
  { prop: 'percent', label: '比例', width: 80, type: 'percent' },
  { prop: 'baseEvent', label: '起算', width: 90, formatter: (r) => labelOf(BASE_EVENT_OPTIONS, r.baseEvent) },
  { prop: 'days', label: '天数', width: 60, align: 'right' },
  { prop: 'planAmount', label: '计划金额', width: 130, type: 'amount', currencyProp: 'currency' },
  { prop: 'eventDate', label: '事件日期', width: 110, type: 'date' },
  { prop: 'dueDate', label: '到期日', width: 110, type: 'date' },
  { prop: 'receivedAmount', label: '已收', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'unreceivedAmount', label: '未收', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'planStatus', label: '状态', width: 90, type: 'status', statusMap: PLAN_STATUS },
  { prop: 'remark', label: '跟进', minWidth: 140 }
]
const changeColumns: TableColumn<ChangeRow>[] = [
  { prop: 'docNo', label: '变更单号', width: 170, type: 'link', onClick: (r) => router.push(`/sales/order-change/${r.id}`) },
  { prop: 'docDate', label: '日期', width: 110, type: 'date' },
  { prop: 'changeReason', label: '变更原因', width: 120, type: 'dict', dictType: 'sal_change_reason' },
  { prop: 'versionTo', label: '版本', width: 90, formatter: (r) => `V${r.versionFrom} → V${r.versionTo}` },
  { prop: 'amountDiff', label: '金额变化', width: 130, type: 'amount' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: DOC_STATUS },
  { prop: 'ownerName', label: '经办人', width: 90 }
]

// ---------- 版本内容 ----------
const snapVisible = ref(false)
const snapText = ref('')
function showSnapshot(row: unknown) {
  const r = row as SnapshotRow
  try {
    snapText.value = JSON.stringify(JSON.parse(r.content), null, 2)
  } catch {
    snapText.value = r.content
  }
  snapVisible.value = true
}

// ---------- 执行跟踪 ----------
const traceVisible = ref(false)
const trace = ref<{ orderNo: string; lineNo: number; materialCode: string; materialName: string; qty: string; steps: TraceStep[] }>()
async function showTrace(l: OrderLine) {
  trace.value = await reportApi.trace(l.id!)
  traceVisible.value = true
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '销售订单'" :status="d?.status" :status-map="DOC_STATUS" :actions="actions" @back="router.push('/sales/order')">
        <template #extra>
          <ErpBadge v-if="d && d.orderVersion > 1" type="primary" :dot="false">V{{ d.orderVersion }}</ErpBadge>
          <ErpBadge v-if="d?.deliveryRisk" type="danger">交期风险 {{ d.riskLineCount }} 行</ErpBadge>
          <ErpBadge v-if="d?.creditWarning" type="warning">信用警告</ErpBadge>
          <ErpBadge v-if="d?.costVisible && d.belowFloor" type="danger">毛利低于底线</ErpBadge>
        </template>
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="SAL_ORDER" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.status !== 'VOIDED'" biz-type="SAL_ORDER" :ids="[id]" permission="sales:order:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CLOSED: `已关闭：${d.closeReason ?? ''}`, VOIDED: '已作废' }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="客户">{{ d.customerCode }} {{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="订单类型"><DictTag type="sal_order_type" :value="d.orderType" /></el-descriptions-item>
          <el-descriptions-item label="客户 PO">{{ d.customerPoNo || '-' }}{{ d.customerPoDate ? `（${d.customerPoDate}）` : '' }}</el-descriptions-item>
          <el-descriptions-item label="下单日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ d.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ d.currency }} / {{ formatRate(d.exchangeRate) }}</el-descriptions-item>
          <el-descriptions-item label="付款条件">{{ d.paymentTermName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="价税合计">{{ formatMoney(d.totalAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="已出货">{{ formatMoney(d.shippedAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="已回款">{{ formatMoney(d.receivedAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款"><DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item v-if="d.portOfLoading || d.portOfDestination" label="港口">{{ d.portOfLoading || '-' }} → {{ d.portOfDestination || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.costVisible" label="最低毛利率"><span :class="{ 'text-danger': d.belowFloor }">{{ pct(d.minMarginRate) }}</span></el-descriptions-item>
          <el-descriptions-item label="来源报价">
            <el-link v-if="d.quotationId" type="primary" underline="never" @click="router.push(`/sales/quotation/${d.quotationId}`)">{{ d.quotationNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ d.approvedAt ? formatDateTime(d.approvedAt, true) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ d.shipToText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开票地址" :span="2">{{ d.billToText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合同条款" :span="2">{{ d.terms || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <ErpTable :columns="lineColumns" :data="d.lines" storage-key="sal.order-lines">
              <template #col-marginRate="{ row }"><span :class="{ 'text-danger': asLine(row).belowFloor }">{{ pct(asLine(row).marginRate) }}</span></template>
              <template #col-promisedDate="{ row }">
                <span :class="{ 'text-danger': asLine(row).delayed }">{{ asLine(row).promisedDate || '-' }}</span>
                <ErpBadge v-if="asLine(row).delayed" type="danger" :dot="false">延误</ErpBadge>
              </template>
              <template #actions="{ row }"><el-button link type="primary" @click="showTrace(asLine(row))">跟踪</el-button></template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane label="执行情况" name="exec">
            <el-table :data="d.lines" class="exec-sum">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column label="订单" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="已通知" width="100" align="right"><template #default="{ row }">{{ formatQty(row.noticedQty) }}</template></el-table-column>
              <el-table-column label="已出货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.shippedQty) }}</template></el-table-column>
              <el-table-column label="已退货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.returnedQty) }}</template></el-table-column>
              <el-table-column label="已开票" width="100" align="right"><template #default="{ row }">{{ formatQty(row.invoicedQty) }}</template></el-table-column>
              <el-table-column label="未出货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.openQty) }}</template></el-table-column>
            </el-table>
            <ErpTable :columns="execColumns" :data="exec ?? []" :loading="!exec" no-toolbar empty-text="还没有执行记录（出货、开票、回款后自动登记）" />
          </el-tab-pane>
          <el-tab-pane label="回款计划" name="plans">
            <template v-if="plans">
              <div class="plan-sum">
                <span>订单金额 <strong class="num">{{ formatMoney(plans.totalAmount, d.currency) }}</strong></span>
                <span>已收 <strong class="num">{{ formatMoney(plans.receivedAmount, d.currency) }}</strong></span>
                <span>未收 <strong class="num">{{ formatMoney(plans.unreceivedAmount, d.currency) }}</strong></span>
                <span v-if="Number(plans.advanceAmount) > 0">出货前应收 <strong class="num">{{ formatMoney(plans.advanceAmount, d.currency) }}</strong></span>
              </div>
              <ErpTable :columns="planColumns" :data="plans.plans" no-toolbar empty-text="审核后按付款条件生成回款计划" />
            </template>
            <el-skeleton v-else :rows="4" animated />
          </el-tab-pane>
          <el-tab-pane label="变更记录" name="changes">
            <ErpTable :columns="changeColumns" :data="changes ?? []" :loading="!changes" no-toolbar />
          </el-tab-pane>
          <el-tab-pane label="历史版本" name="versions">
            <el-table :data="snapshots ?? []" v-loading="!snapshots">
              <el-table-column label="版本" width="80"><template #default="{ row }">V{{ row.orderVersion }}</template></el-table-column>
              <el-table-column label="变更单" width="180">
                <template #default="{ row }">
                  <el-link v-if="row.changeId" type="primary" underline="never" @click="router.push(`/sales/order-change/${row.changeId}`)">{{ row.changeNo }}</el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="时间" width="160"><template #default="{ row }">{{ formatDateTime(row.createdAt, true) }}</template></el-table-column>
              <el-table-column label="内容" min-width="120"><template #default="{ row }"><el-button link type="primary" @click="showSnapshot(row)">查看</el-button></template></el-table-column>
              <template #empty><ErpEmpty compact description="订单变更生效时保存变更前的版本" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SAL_ORDER" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SAL_ORDER" :biz-id="id" :status-map="DOC_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="SAL_ORDER" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="snapVisible" title="版本内容" width="760px" append-to-body>
      <pre class="snap">{{ snapText }}</pre>
    </el-dialog>

    <el-dialog v-model="traceVisible" :title="trace ? `执行跟踪 - ${trace.orderNo} 行 ${trace.lineNo} ${trace.materialCode}` : '执行跟踪'" width="720px" append-to-body>
      <el-timeline v-if="trace">
        <el-timeline-item v-for="(st, i) in trace.steps" :key="i" :timestamp="st.time ? formatDateTime(st.time, true) : st.date" :type="st.date || st.time ? 'primary' : undefined">
          <strong>{{ st.label }}</strong>
          <span v-if="st.docNo" class="trace-doc">{{ st.docNo }}</span>
          <span v-if="st.qty" class="trace-doc">数量 {{ formatQty(st.qty) }}</span>
          <span v-if="st.amount" class="trace-doc">金额 {{ st.amount }}</span>
          <div v-if="st.remark" class="text-muted">{{ st.remark }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.exec-sum { margin-bottom: var(--erp-space-4); }
.plan-sum { display: flex; gap: var(--erp-space-6); margin-bottom: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.snap { max-height: 480px; overflow: auto; font-size: var(--erp-font-size-caption); }
.trace-doc { margin-left: var(--erp-space-2); color: var(--erp-color-text-secondary); }
</style>
