<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime } from '@/utils/format'
import { CASH_STATUS, labelOf, REQUEST_STATUS, REQUEST_TYPES, requestApi, type RequestDetail } from '../api/finance'

defineOptions({ name: 'FinRequestDetail' })

/** 付款申请详情（需求 12-05 3.1，T5）：提交审批、付款（跳转付款单）、关闭（剩余不再支付）、作废、打印 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<RequestDetail>()
const tab = ref('lines')

async function load() {
  d.value = await requestApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.header.docNo)
}
onMounted(load)
const h = computed(() => d.value?.header)
const s = computed(() => h.value?.status)

const actions = computed<DocAction[]>(() => [
  { key: 'void', label: '作废', type: 'danger', permission: 'fin:payment-request:create', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    handler: async (reason) => { await requestApi.void(id.value, reason!); ElMessage.success('已作废'); load() } },
  { key: 'close', label: '关闭', permission: 'fin:payment-request:submit', visible: () => s.value === 'APPROVED' || s.value === 'PARTIAL',
    reasonRequired: true, reasonTitle: '关闭原因（剩余金额不再支付）', handler: async (reason) => { await requestApi.close(id.value, reason!); ElMessage.success('已关闭'); load() } },
  { key: 'withdraw', label: '撤回', permission: 'fin:payment-request:submit', visible: () => s.value === 'PENDING',
    handler: async () => { await requestApi.withdraw(id.value); ElMessage.success('已撤回'); load() } },
  { key: 'edit', label: '编辑', permission: 'fin:payment-request:create', visible: () => s.value === 'DRAFT', handler: () => router.push(`/finance/payment/request/${id.value}/edit`) },
  { key: 'pay', label: '付款', type: 'primary', permission: 'fin:payment:create', visible: () => s.value === 'APPROVED' || s.value === 'PARTIAL',
    handler: () => router.push({ path: '/finance/payment/new', query: { requestId: id.value } }) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'fin:payment-request:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      const r = await requestApi.submit(id.value)
      if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；') })
      ElMessage.success(r.status === 'APPROVED' ? '已提交，审批通过' : '已提交审批')
      load()
    } }
])
const steps = [{ status: 'DRAFT', label: '草稿' }, { status: 'PENDING', label: '审批中' }, { status: 'APPROVED', label: '待付款' }, { status: 'PARTIAL', label: '部分付款' }, { status: 'PAID', label: '已付款' }]
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="h?.docNo ?? '付款申请'" :status="s" :status-map="REQUEST_STATUS" :actions="actions" @back="router.push('/finance/payment/request')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="FIN_PAYMENT_REQUEST" :biz-id="id" @changed="load" />
          <PrintButton v-if="d" biz-type="FIN_PAYMENT_REQUEST" :ids="[id]" permission="fin:payment-request:query" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d && h">
      <ErpPanel>
        <DocSteps :steps="steps" :current="s" :terminal="{ CLOSED: '已关闭', VOIDED: '已作废' }" />
        <el-alert v-if="h.uninvoicedWarning" type="warning" :closable="false" title="含尚未收到发票的应付" class="gap" />
        <el-descriptions :column="4">
          <el-descriptions-item label="类型">{{ labelOf(REQUEST_TYPES, h.requestType) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ h.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="申请金额">{{ h.currency }} {{ formatAmount(h.amount) }}</el-descriptions-item>
          <el-descriptions-item label="已付 / 未付">{{ formatAmount(h.paidAmount) }} / {{ formatAmount(h.unpaidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="计划付款日">{{ h.planPayDate }}</el-descriptions-item>
          <el-descriptions-item label="收款账户" :span="2">{{ d.supplierBankText ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="采购订单">
            <el-link v-if="h.orderId" type="primary" underline="never" @click="router.push(`/purchase/order/${h.orderId}`)">{{ h.orderNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ h.ownerName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批通过">{{ formatDateTime(d.approvedAt, true) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">{{ h.reason ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane v-if="d.lines.length" :label="`应付明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="应付单" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/payable/${row.payableId}`)">{{ row.payableNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="statementNo" label="对账单" width="150" />
              <el-table-column prop="dueDate" label="到期日" width="100" />
              <el-table-column label="应付合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.payableTotal) }}</template></el-table-column>
              <el-table-column label="申请金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
              <el-table-column label="已付" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.paidAmount) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`付款记录(${d.payments.length})`" name="payments">
            <el-table :data="d.payments">
              <el-table-column label="付款单" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/payment/${row.id}`)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="payDate" label="付款日期" width="110" />
              <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="CASH_STATUS" /></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="FIN_PAYMENT_REQUEST" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="FIN_PAYMENT_REQUEST" :biz-id="id" :status-map="REQUEST_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="FIN_PAYMENT_REQUEST" :biz-id="id" :editable="s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.gap { margin-bottom: var(--erp-space-3); }
</style>
