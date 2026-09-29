<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime } from '@/utils/format'
import { CASH_STATUS, labelOf, paymentApi, REQUEST_TYPES, type PaymentDetail } from '../api/finance'
import VerificationTable from '../components/VerificationTable.vue'

defineOptions({ name: 'FinPaymentDetail' })

/** 付款单详情（需求 12-05 3.2，T5）：确认（自动核销应付）、反确认（反核销后回到草稿）、预付冲应付 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<PaymentDetail>()
const tab = ref('verify')

async function load() {
  d.value = await paymentApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.header.docNo)
}
onMounted(load)
const h = computed(() => d.value?.header)
const s = computed(() => h.value?.status)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', type: 'danger', permission: 'fin:payment:create', visible: () => s.value === 'DRAFT', confirm: '确定删除该付款单吗？',
    handler: async () => { await paymentApi.remove(id.value); ElMessage.success('已删除'); tabs.remove([tabKeyOf(route)]); router.push('/finance/payment') } },
  { key: 'unconfirm', label: '反确认', permission: 'fin:payment:confirm', visible: () => s.value === 'CONFIRMED', reasonRequired: true, reasonTitle: '反确认原因',
    handler: async (reason) => { await paymentApi.unconfirm(id.value, reason!); ElMessage.success('已反确认'); load() } },
  { key: 'edit', label: '编辑', permission: 'fin:payment:create', visible: () => s.value === 'DRAFT', handler: () => router.push(`/finance/payment/${id.value}/edit`) },
  { key: 'verify', label: '冲应付', permission: 'fin:payment:verify',
    visible: () => s.value === 'CONFIRMED' && Number(h.value?.amount) > Number(h.value?.allocatedAmount),
    handler: () => router.push({ path: '/finance/payment/verify', query: { supplierId: h.value!.supplierId, currency: h.value!.currency, paymentId: id.value } }) },
  { key: 'confirm', label: '确认付款', type: 'primary', permission: 'fin:payment:confirm', visible: () => s.value === 'DRAFT',
    handler: async () => { await paymentApi.confirm(id.value); ElMessage.success('已确认付款'); load() } }
])
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="h?.docNo ?? '付款单'" :status="s" :status-map="CASH_STATUS" :actions="actions" @back="router.push('/finance/payment')" />
    </template>
    <template v-if="d && h">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="付款申请">
            <el-link type="primary" underline="never" @click="router.push(`/finance/payment/request/${h.requestId}`)">{{ h.requestNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ labelOf(REQUEST_TYPES, h.requestType) }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ h.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="付款账户">{{ h.bankAccountName }}</el-descriptions-item>
          <el-descriptions-item label="结算方式"><DictTag type="sys_settlement_method" :value="h.settlementMethod" /></el-descriptions-item>
          <el-descriptions-item label="付款日期">{{ h.payDate }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ h.currency }} / {{ h.exchangeRate }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatAmount(h.amount) }}</el-descriptions-item>
          <el-descriptions-item label="手续费">{{ formatAmount(h.bankFee) }}</el-descriptions-item>
          <el-descriptions-item label="本位币">{{ formatAmount(h.amountBase) }}</el-descriptions-item>
          <el-descriptions-item label="已核销">{{ formatAmount(h.allocatedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="银行流水号">{{ h.bankRefNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDateTime(d.confirmedAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出纳">{{ h.ownerName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ h.remark ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`核销记录(${d.verifications.length})`" name="verify">
            <VerificationTable :data="d.verifications" doc-type="PAYMENT" :doc-id="id" unverify-permission="fin:payment:verify" @changed="load" />
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="FIN_PAYMENT" :biz-id="id" :status-map="CASH_STATUS" /></el-tab-pane>
          <el-tab-pane label="回单" name="files" lazy><AttachmentPanel biz-type="FIN_PAYMENT" :biz-id="id" :editable="s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>
