<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime, formatQty } from '@/utils/format'
import { AP_TYPES, apApi, AR_STATUS, INVOICE_STATUS, labelOf, MATCH_STATUS, REQUEST_STATUS, type ApDetail } from '../api/finance'
import VerificationTable from '../components/VerificationTable.vue'

defineOptions({ name: 'FinPayableDetail' })

/** 应付单详情（需求 12-04 3.2，T5）：确认 / 反确认 / 作废、登记发票、申请付款；页签明细、发票匹配、付款申请、付款核销 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ApDetail>()
const tab = ref('lines')

async function load() {
  d.value = await apApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.header.docNo)
}
onMounted(load)
const h = computed(() => d.value?.header)
const s = computed(() => h.value?.status)
const manual = computed(() => !h.value?.statementId)
const processed = computed(() => !!h.value && (Number(h.value.invoicedAmount) !== 0 || Number(h.value.requestedAmount) !== 0 || Number(h.value.verifiedAmount) !== 0))

const actions = computed<DocAction[]>(() => [
  { key: 'void', label: '作废', type: 'danger', permission: 'fin:payable:unconfirm', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    handler: async (reason) => { await apApi.void(id.value, reason!); ElMessage.success('已作废'); load() } },
  { key: 'unconfirm', label: '反确认', permission: 'fin:payable:unconfirm', visible: () => s.value === 'CONFIRMED' && !processed.value,
    reasonRequired: true, reasonTitle: '反确认原因', handler: async (reason) => { await apApi.unconfirm(id.value, reason!); ElMessage.success('已反确认'); load() } },
  { key: 'withdraw', label: '撤回', permission: 'fin:payable:create-other', visible: () => s.value === 'PENDING',
    handler: async () => { await apApi.withdraw(id.value); ElMessage.success('已撤回'); load() } },
  { key: 'edit', label: '编辑', permission: 'fin:payable:create-other', visible: () => s.value === 'DRAFT' && manual.value,
    handler: () => router.push(`/finance/payable/other/${id.value}/edit`) },
  { key: 'invoice', label: '登记发票', permission: 'fin:payable:invoice', visible: () => s.value === 'CONFIRMED' && Number(h.value?.invoicedAmount) !== Number(h.value?.totalAmount),
    handler: () => router.push({ path: '/finance/payable/invoice/new', query: { supplierId: h.value!.supplierId, payableIds: id.value } }) },
  { key: 'request', label: '申请付款', permission: 'fin:payment-request:create', visible: () => s.value === 'CONFIRMED' && Number(h.value?.requestableAmount) > 0,
    handler: () => router.push({ path: '/finance/payment/request/new', query: { supplierId: h.value!.supplierId, currency: h.value!.currency, payableIds: id.value } }) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'fin:payable:create-other', visible: () => s.value === 'DRAFT' && manual.value,
    handler: async () => { const r = await apApi.submit(id.value); ElMessage.success(r.status === 'CONFIRMED' ? '已确认' : '已提交审批'); load() } },
  { key: 'confirm', label: '确认', type: 'primary', permission: 'fin:payable:confirm', visible: () => s.value === 'DRAFT' && !manual.value,
    handler: async () => { await apApi.confirm(id.value); ElMessage.success('已确认'); load() } }
])
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="h?.docNo ?? '应付单'" :status="s" :status-map="AR_STATUS" :actions="actions" @back="router.push('/finance/payable')">
        <template #actions-prefix>
          <ApprovalActions v-if="d && manual" biz-type="FIN_OTHER_PAYABLE" :biz-id="id" @changed="load" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d && h">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="类型">{{ labelOf(AP_TYPES, h.apType) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ h.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="对账单">
            <el-link v-if="h.statementId" type="primary" underline="never" @click="router.push(`/purchase/statement/${h.statementId}`)">{{ h.statementNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="业务日期">{{ h.bizDate }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ h.currency }} / {{ Number(h.exchangeRate) === 0 ? '未维护（确认时取汇率）' : h.exchangeRate }}</el-descriptions-item>
          <el-descriptions-item label="不含税 / 税额">{{ formatAmount(d.amount) }} / {{ formatAmount(d.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="价税合计"><b>{{ formatAmount(h.totalAmount) }}</b></el-descriptions-item>
          <el-descriptions-item label="本位币">{{ formatAmount(h.totalAmountBase) }}</el-descriptions-item>
          <el-descriptions-item label="已匹配发票">{{ formatAmount(h.invoicedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="已申请 / 已付款">{{ formatAmount(h.requestedAmount) }} / {{ formatAmount(h.verifiedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未付 / 可申请">{{ formatAmount(h.unpaidAmount) }} / {{ formatAmount(h.requestableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="到期日">
            <span :class="{ red: h.overdueDays > 0 }">{{ h.dueDate ?? '-' }}<template v-if="h.overdueDays > 0">（逾期 {{ h.overdueDays }} 天）</template></span>
          </el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDateTime(d.confirmedAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="3">{{ h.description ?? '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.voidReason" label="作废原因" :span="4">{{ d.voidReason }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column prop="lineType" label="类型" width="90" />
              <el-table-column prop="sourceNo" label="来源单号" width="140" />
              <el-table-column prop="orderNo" label="订单" width="130" />
              <el-table-column label="物料 / 说明" min-width="180">
                <template #default="{ row }">{{ row.materialCode ? `${row.materialCode} ${row.materialName ?? ''}` : row.description }}</template>
              </el-table-column>
              <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column prop="priceInclTax" label="含税单价" width="100" align="right" />
              <el-table-column label="税率" width="70" align="right"><template #default="{ row }">{{ Number(row.taxRate) * 100 }}%</template></el-table-column>
              <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
              <el-table-column label="已开票数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.invoicedQty) }}</template></el-table-column>
              <el-table-column label="已开票金额" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.invoicedAmount) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`发票匹配(${d.invoices.length})`" name="invoices">
            <el-table :data="d.invoices">
              <el-table-column label="登记号" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/payable/invoice/${row.invoiceId}`)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="invoiceNo" label="发票号码" width="150" />
              <el-table-column prop="invoiceDate" label="开票日期" width="100" />
              <el-table-column label="匹配" width="90"><template #default="{ row }"><StatusTag :value="row.matchStatus" :map="MATCH_STATUS" /></template></el-table-column>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="发票金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="INVOICE_STATUS" /></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`付款申请(${d.requests.length})`" name="requests">
            <el-table :data="d.requests">
              <el-table-column label="申请单" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/payment/request/${row.requestId}`)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="planPayDate" label="计划付款日" width="110" />
              <el-table-column label="申请金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
              <el-table-column label="已付" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.paidAmount) }}</template></el-table-column>
              <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" :map="REQUEST_STATUS" /></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`付款记录(${d.verifications.length})`" name="verify">
            <VerificationTable :data="d.verifications" doc-type="PAYABLE" :doc-id="id" unverify-permission="fin:payment:verify" @changed="load" />
          </el-tab-pane>
          <el-tab-pane v-if="manual" label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="FIN_OTHER_PAYABLE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="FIN_PAYABLE" :biz-id="id" :status-map="AR_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="FIN_PAYABLE" :biz-id="id" :editable="s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.red { color: var(--erp-color-error); }
</style>
