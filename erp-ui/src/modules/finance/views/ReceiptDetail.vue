<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime } from '@/utils/format'
import { CASH_STATUS, labelOf, RECEIPT_TYPES, receiptApi, type ReceiptDetail } from '../api/finance'
import VerificationTable from '../components/VerificationTable.vue'

defineOptions({ name: 'FinReceiptDetail' })

/** 收款单详情（需求 12-03，T5）：确认 / 反确认（有核销记录时先反核销）、核销 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ReceiptDetail>()
const tab = ref('verify')

async function load() {
  d.value = await receiptApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.header.docNo)
}
onMounted(load)
const h = computed(() => d.value?.header)
const s = computed(() => h.value?.status)

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', type: 'danger', permission: 'fin:receipt:delete', visible: () => s.value === 'DRAFT', confirm: '确定删除该收款单吗？',
    handler: async () => { await receiptApi.remove(id.value); ElMessage.success('已删除'); tabs.remove([tabKeyOf(route)]); router.push('/finance/receipt') } },
  { key: 'unconfirm', label: '反确认', permission: 'fin:receipt:unconfirm', visible: () => s.value === 'CONFIRMED', reasonRequired: true, reasonTitle: '反确认原因',
    handler: async (reason) => { await receiptApi.unconfirm(id.value, reason!); ElMessage.success('已反确认'); load() } },
  { key: 'edit', label: '编辑', permission: 'fin:receipt:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/finance/receipt/${id.value}/edit`) },
  { key: 'verify', label: '核销', permission: 'fin:receipt:verify', visible: () => s.value === 'CONFIRMED' && Number(h.value?.unallocatedAmount) !== 0,
    handler: () => router.push({ path: '/finance/receipt/verify', query: { customerId: h.value!.customerId, currency: h.value!.currency, receiptId: id.value } }) },
  { key: 'confirm', label: '确认', type: 'primary', permission: 'fin:receipt:confirm', visible: () => s.value === 'DRAFT',
    handler: async () => { await receiptApi.confirm(id.value); ElMessage.success('已确认'); load() } }
])
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="h?.docNo ?? '收款单'" :status="s" :status-map="CASH_STATUS" :actions="actions" @back="router.push('/finance/receipt')" />
    </template>
    <template v-if="d && h">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="客户">{{ h.customerName }}</el-descriptions-item>
          <el-descriptions-item label="收款类型">{{ labelOf(RECEIPT_TYPES, h.receiptType) }}</el-descriptions-item>
          <el-descriptions-item label="收款账户">{{ h.bankAccountName }}</el-descriptions-item>
          <el-descriptions-item label="结算方式"><DictTag type="sys_settlement_method" :value="h.settlementMethod" /></el-descriptions-item>
          <el-descriptions-item label="到账日期">{{ h.receiptDate }}</el-descriptions-item>
          <el-descriptions-item label="币别 / 汇率">{{ h.currency }} / {{ h.exchangeRate }}</el-descriptions-item>
          <el-descriptions-item label="到账金额">{{ formatAmount(h.amount) }}</el-descriptions-item>
          <el-descriptions-item label="手续费">{{ formatAmount(h.bankFee) }}</el-descriptions-item>
          <el-descriptions-item label="本位币">{{ formatAmount(h.amountBase) }}</el-descriptions-item>
          <el-descriptions-item label="已核销 / 未核销">{{ formatAmount(h.allocatedAmount) }} / {{ formatAmount(h.unallocatedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="销售订单">
            <el-link v-if="h.orderId" type="primary" underline="never" @click="router.push(`/sales/order/${h.orderId}`)">{{ h.orderNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="银行流水号">{{ h.bankRefNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="付款方">{{ h.payerName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDateTime(d.confirmedAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出纳">{{ h.ownerName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ h.remark ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`核销记录(${d.verifications.length})`" name="verify">
            <VerificationTable :data="d.verifications" doc-type="RECEIPT" :doc-id="id" unverify-permission="fin:receipt:unverify" @changed="load" />
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="FIN_RECEIPT" :biz-id="id" :status-map="CASH_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="FIN_RECEIPT" :biz-id="id" :editable="s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>
