<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime, formatQty } from '@/utils/format'
import { AR_STATUS, AR_TYPES, arApi, INVOICE_STATUS, labelOf, SALES_INVOICE_TYPES, type ArDetail } from '../api/finance'
import VerificationTable from '../components/VerificationTable.vue'

defineOptions({ name: 'FinReceivableDetail' })

/** 应收单详情（需求 12-02 3.2，T5）：确认 / 反确认 / 作废、开票登记；页签明细、核销记录、发票、日志 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ArDetail>()
const tab = ref('lines')

async function load() {
  d.value = await arApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.header.docNo)
}
onMounted(load)
const h = computed(() => d.value?.header)
const s = computed(() => h.value?.status)
const other = computed(() => h.value?.arType === 'OTHER')
const processed = computed(() => !!h.value && (Number(h.value.verifiedAmount) !== 0 || Number(h.value.invoicedAmount) !== 0))

const actions = computed<DocAction[]>(() => [
  { key: 'void', label: '作废', type: 'danger', permission: 'fin:receivable:unconfirm', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    handler: async (reason) => { await arApi.void(id.value, reason!); ElMessage.success('已作废'); load() } },
  { key: 'unconfirm', label: '反确认', permission: 'fin:receivable:unconfirm', visible: () => s.value === 'CONFIRMED' && !processed.value,
    reasonRequired: true, reasonTitle: '反确认原因', handler: async (reason) => { await arApi.unconfirm(id.value, reason!); ElMessage.success('已反确认'); load() } },
  { key: 'withdraw', label: '撤回', permission: 'fin:receivable:create-other', visible: () => s.value === 'PENDING',
    handler: async () => { await arApi.withdraw(id.value); ElMessage.success('已撤回'); load() } },
  { key: 'edit', label: '编辑', permission: 'fin:receivable:create-other', visible: () => s.value === 'DRAFT' && other.value,
    handler: () => router.push(`/finance/receivable/other/${id.value}/edit`) },
  { key: 'invoice', label: '开票登记', permission: 'fin:receivable:invoice',
    visible: () => s.value === 'CONFIRMED' && Number(h.value?.totalAmount) > 0 && Number(h.value?.invoicedAmount) < Number(h.value?.totalAmount),
    handler: () => router.push({ path: '/finance/receivable/invoice/new', query: { customerId: h.value!.customerId, receivableIds: id.value } }) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'fin:receivable:create-other', visible: () => s.value === 'DRAFT' && other.value,
    handler: async () => { const r = await arApi.submit(id.value); ElMessage.success(r.status === 'CONFIRMED' ? '已确认' : '已提交审批'); load() } },
  { key: 'confirm', label: '确认', type: 'primary', permission: 'fin:receivable:confirm', visible: () => s.value === 'DRAFT' && !other.value,
    handler: async () => { await arApi.confirm(id.value); ElMessage.success('已确认'); load() } }
])
const SOURCE_ROUTES: Record<string, string> = { SHP_SHIPMENT: '/shipping/shipment/', SAL_RETURN: '/sales/return/', QC_COMPLAINT: '/quality/complaint/' }
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="h?.docNo ?? '应收单'" :status="s" :status-map="AR_STATUS" :actions="actions" @back="router.push('/finance/receivable')">
        <template #actions-prefix>
          <ApprovalActions v-if="d && other" biz-type="FIN_OTHER_RECEIVABLE" :biz-id="id" @changed="load" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d && h">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="类型">{{ labelOf(AR_TYPES, h.arType) }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ h.customerName }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">
            <el-link v-if="h.sourceType && SOURCE_ROUTES[h.sourceType]" type="primary" underline="never" @click="router.push(SOURCE_ROUTES[h.sourceType] + h.sourceId)">{{ h.sourceNo }}</el-link>
            <span v-else>{{ h.sourceNo ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="业务日期">{{ h.bizDate }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">
            {{ h.currency }} / {{ Number(h.exchangeRate) === 0 ? '未维护（确认时取汇率）' : h.exchangeRate }}
          </el-descriptions-item>
          <el-descriptions-item label="不含税 / 税额">{{ formatAmount(d.amount) }} / {{ formatAmount(d.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="价税合计"><b :class="{ red: Number(h.totalAmount) < 0 }">{{ formatAmount(h.totalAmount) }}</b></el-descriptions-item>
          <el-descriptions-item label="本位币">{{ formatAmount(h.totalAmountBase) }}</el-descriptions-item>
          <el-descriptions-item label="已核销 / 未核销">{{ formatAmount(h.verifiedAmount) }} / {{ formatAmount(h.unverifiedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="已开票">{{ formatAmount(h.invoicedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="到期日">
            <span :class="{ red: h.overdueDays > 0 }">{{ h.dueDate ?? '-' }}<template v-if="h.overdueDays > 0">（逾期 {{ h.overdueDays }} 天）</template></span>
          </el-descriptions-item>
          <el-descriptions-item label="提单日期">{{ d.blDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDateTime(d.confirmedAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ h.ownerName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{ h.description ?? '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.voidReason" label="作废原因" :span="4">{{ d.voidReason }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="订单" width="150">
                <template #default="{ row }">
                  <el-link v-if="row.orderId" type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link>
                </template>
              </el-table-column>
              <el-table-column label="物料 / 说明" min-width="200">
                <template #default="{ row }">{{ row.materialCode ? `${row.materialCode} ${row.materialName ?? ''}` : row.description }}</template>
              </el-table-column>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column prop="priceInclTax" label="含税单价" width="100" align="right" />
              <el-table-column label="税率" width="70" align="right"><template #default="{ row }">{{ Number(row.taxRate) * 100 }}%</template></el-table-column>
              <el-table-column label="不含税" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
              <el-table-column label="税额" width="100" align="right"><template #default="{ row }">{{ formatAmount(row.taxAmount) }}</template></el-table-column>
              <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
              <el-table-column label="已开票数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.invoicedQty) }}</template></el-table-column>
              <el-table-column label="已开票金额" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.invoicedAmount) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`核销记录(${d.verifications.length})`" name="verify">
            <VerificationTable :data="d.verifications" doc-type="RECEIVABLE" :doc-id="id" unverify-permission="fin:receipt:unverify" @changed="load" />
          </el-tab-pane>
          <el-tab-pane :label="`发票记录(${d.invoices.length})`" name="invoices">
            <el-table :data="d.invoices">
              <el-table-column prop="docNo" label="登记号" width="150" />
              <el-table-column prop="invoiceNo" label="发票号码" width="150" />
              <el-table-column label="发票类型" width="110"><template #default="{ row }">{{ labelOf(SALES_INVOICE_TYPES, row.invoiceType) }}</template></el-table-column>
              <el-table-column prop="invoiceDate" label="开票日期" width="100" />
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="INVOICE_STATUS" /></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="other" label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="FIN_OTHER_RECEIVABLE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="FIN_RECEIVABLE" :biz-id="id" :status-map="AR_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="FIN_RECEIVABLE" :biz-id="id" :editable="s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.red { color: var(--erp-color-error); }
</style>
