<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime, formatQty } from '@/utils/format'
import { DEDUCTION_STATUS, INVOICE_STATUS, labelOf, MATCH_STATUS, PURCHASE_INVOICE_TYPES, purchaseInvoiceApi, type PurchaseInvoiceDetail } from '../api/finance'

defineOptions({ name: 'FinPurchaseInvoiceDetail' })

/** 进项发票详情（需求 12-04 3.3）：匹配明细、差异确认（生成价差调整应付行）、认证抵扣、作废 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<PurchaseInvoiceDetail>()
const tab = ref('lines')

async function load() {
  d.value = await purchaseInvoiceApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.header.docNo)
}
onMounted(load)
const h = computed(() => d.value?.header)
const registered = computed(() => h.value?.status === 'REGISTERED')

const certifyDialog = ref(false)
const period = ref(new Date().toISOString().slice(0, 7).replace('-', ''))
async function certify() {
  await purchaseInvoiceApi.certify(id.value, period.value)
  certifyDialog.value = false
  ElMessage.success('已认证')
  load()
}
const actions = computed<DocAction[]>(() => [
  { key: 'void', label: '作废', type: 'danger', permission: 'fin:payable:invoice', visible: () => registered.value && h.value?.deductionStatus !== 'CERTIFIED',
    reasonRequired: true, reasonTitle: '作废原因', handler: async (reason) => { await purchaseInvoiceApi.void(id.value, reason!); ElMessage.success('已作废'); load() } },
  { key: 'certify', label: '认证抵扣', permission: 'fin:payable:invoice',
    visible: () => registered.value && h.value?.invoiceType === 'VAT_SPECIAL' && h.value?.deductionStatus !== 'CERTIFIED', handler: () => (certifyDialog.value = true) },
  { key: 'confirmDiff', label: '确认差异', type: 'primary', permission: 'fin:payable:confirm', visible: () => registered.value && h.value?.matchStatus === 'DIFF',
    confirm: '确认后视为已匹配，差异金额将生成价差调整应付行，确定吗？',
    handler: async () => { await purchaseInvoiceApi.confirmDiff(id.value); ElMessage.success('已确认差异'); load() } }
])
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="h?.docNo ?? '进项发票'" :status="h?.matchStatus" :status-map="MATCH_STATUS" :actions="actions" @back="router.push('/finance/payable/invoice')" />
    </template>
    <template v-if="d && h">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="供应商">{{ h.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ labelOf(PURCHASE_INVOICE_TYPES, h.invoiceType) }}</el-descriptions-item>
          <el-descriptions-item label="发票号码 / 代码">{{ h.invoiceNo }} / {{ h.invoiceCode ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ h.invoiceDate }}</el-descriptions-item>
          <el-descriptions-item label="价税合计">{{ h.currency }} {{ formatAmount(h.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="不含税 / 税额">{{ formatAmount(h.amount) }} / {{ formatAmount(h.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :value="h.status" :map="INVOICE_STATUS" /></el-descriptions-item>
          <el-descriptions-item label="认证">
            <StatusTag v-if="h.deductionStatus" :value="h.deductionStatus" :map="DEDUCTION_STATUS" /><span v-else>-</span>
            <span v-if="h.certifiedPeriod"> {{ h.certifiedPeriod }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="差异确认">{{ d.diffConfirmedByName ? `${d.diffConfirmedByName} ${formatDateTime(d.diffConfirmedAt, true)}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ h.createdByName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ h.remark ?? '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.voidReason" label="作废原因" :span="4">{{ d.voidReason }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`匹配明细(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="应付单" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/payable/${row.payableId}`)">{{ row.payableNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="sourceNo" label="来源单号" width="130" />
              <el-table-column label="物料" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column prop="apPrice" label="应付单价" width="100" align="right" />
              <el-table-column prop="invoicePrice" label="发票单价" width="100" align="right" />
              <el-table-column label="差异%" width="80" align="right">
                <template #default="{ row }"><span :class="{ red: row.overTolerance }">{{ Number(row.priceDiffPct) }}%</span></template>
              </el-table-column>
              <el-table-column label="发票金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
              <el-table-column label="冲应付" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.apAmount) }}</template></el-table-column>
              <el-table-column prop="diffReason" label="差异原因" min-width="140" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="FIN_PURCHASE_INVOICE" :biz-id="id" :status-map="MATCH_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="FIN_PURCHASE_INVOICE" :biz-id="id" :editable="registered" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="certifyDialog" title="认证抵扣" width="400px">
      <el-form label-width="90px">
        <el-form-item label="认证所属期"><el-input v-model="period" maxlength="6" placeholder="yyyyMM" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="certifyDialog = false">取消</el-button>
        <el-button type="primary" @click="certify">确定</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.red { color: var(--erp-color-error); }
</style>
