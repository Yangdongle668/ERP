<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney, formatQty } from '@/utils/format'
import { CAPA_STATUS, COMPLAINT_STATUS, DOC_STATUS, HANDLING, SEVERITY, complaintApi, labelOf, type ComplaintDetail } from '../api/quality'

defineOptions({ name: 'QcComplaintDetail' })

/** 客诉详情（需求 10-05 3.3，T5）：开始分析、记录回复、登记处理结果、生成 NCR / CAPA、结案（审批 QC_COMPLAINT_CLOSE） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ComplaintDetail>()
async function load() {
  d.value = await complaintApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(load)
const s = computed(() => d.value?.status)
const open = computed(() => ['OPEN', 'ANALYZING', 'REPLIED'].includes(s.value ?? ''))

const replyVisible = ref(false)
const reply = ref<{ content?: string; rootCause?: string; repliedAt?: string; fileIds: string[] }>({ fileIds: [] })
function openReply() {
  reply.value = { content: d.value?.replyContent, rootCause: d.value?.rootCause, fileIds: [] }
  replyVisible.value = true
}
async function saveReply() {
  if (!reply.value.content?.trim()) return ElMessage.warning('请填写回复内容')
  await complaintApi.reply(id.value, reply.value)
  replyVisible.value = false
  ElMessage.success('已记录回复')
  load()
}
const handVisible = ref(false)
const hand = ref<{ handling?: string; claimAmount?: string; agreedAmount?: string; currency?: string; remark?: string }>({})
function openHandling() {
  const c = d.value!
  hand.value = { handling: c.handling ?? 'NONE', claimAmount: c.claimAmount, agreedAmount: c.agreedAmount, currency: c.currency ?? 'USD', remark: c.handlingRemark }
  handVisible.value = true
}
async function saveHandling() {
  await complaintApi.handling(id.value, hand.value)
  handVisible.value = false
  ElMessage.success('已登记处理结果')
  load()
}

const actions = computed<DocAction[]>(() => [
  { key: 'cancel', label: '取消', permission: 'qc:complaint:close', visible: () => open.value, reasonRequired: true, reasonTitle: '取消原因',
    handler: async (reason) => { await complaintApi.cancel(id.value, reason!); load() } },
  { key: 'edit', label: '编辑', permission: 'qc:complaint:update', visible: () => open.value, handler: () => router.push(`/quality/complaint/${id.value}/edit`) },
  { key: 'ncr', label: '生成 NCR', permission: 'qc:ncr:create', visible: () => open.value && !d.value?.ncrId && !!d.value?.materialId,
    handler: async () => { const n = await complaintApi.createNcr(id.value); ElMessage.success('已生成 NCR'); router.push(`/quality/ncr/${n}`) } },
  { key: 'capa', label: '生成 CAPA', permission: 'qc:capa:create', visible: () => open.value && !d.value?.capaId,
    handler: async () => { const c = await complaintApi.createCapa(id.value); ElMessage.success('已生成 CAPA'); router.push(`/quality/capa/${c}`) } },
  { key: 'return', label: '生成销售退货', permission: 'sales:return:create', visible: () => open.value,
    handler: () => router.push({ path: '/sales/return/new', query: { customerId: d.value?.customerId, complaintNo: d.value?.docNo } }) },
  { key: 'start', label: '开始分析', permission: 'qc:complaint:update', visible: () => s.value === 'OPEN',
    handler: async () => { await complaintApi.start(id.value); load() } },
  { key: 'handling', label: '登记处理结果', permission: 'qc:complaint:update', visible: () => open.value, handler: openHandling },
  { key: 'reply', label: '记录回复', permission: 'qc:complaint:reply', visible: () => open.value, handler: openReply },
  { key: 'close', label: '结案', type: 'primary', permission: 'qc:complaint:close', visible: () => s.value === 'REPLIED', handler: async () => {
    if (d.value?.closeMissing.length) return ElMessage.warning(`结案前需要：${d.value.closeMissing.join('、')}`)
    await complaintApi.close(id.value)
    ElMessage.success('已提交结案')
    load()
  } }
])
const steps = [
  { status: 'OPEN', label: '新建' }, { status: 'ANALYZING', label: '分析中' }, { status: 'REPLIED', label: '已回复' },
  { status: 'CLOSING', label: '结案审批' }, { status: 'CLOSED', label: '已结案' }
]
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '客诉'" :status="d?.status" :status-map="COMPLAINT_STATUS" :actions="actions" @back="router.push('/quality/complaint')">
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="QC_COMPLAINT_CLOSE" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CANCELED: `已取消：${d.cancelReason ?? ''}` }" />
        <el-alert v-if="open && d.closeMissing.length" type="info" :closable="false" show-icon :title="`结案前需要：${d.closeMissing.join('、')}`" class="gap" />
        <el-descriptions :column="4">
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ d.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型"><DictTag type="qc_complaint_type" :value="d.complaintType" /></el-descriptions-item>
          <el-descriptions-item label="严重度"><StatusTag :value="d.severity" :map="SEVERITY" /></el-descriptions-item>
          <el-descriptions-item label="物料">{{ d.materialCode ? `${d.materialCode} ${d.materialName}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户料号">{{ d.customerPartNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="订单 / 出货单">{{ d.orderNo || '-' }} / {{ d.shipmentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ d.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="投诉数量">{{ formatQty(d.complaintQty) }}</el-descriptions-item>
          <el-descriptions-item label="收到时间">{{ formatDateTime(d.receivedAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="回复期限"><span :class="{ danger: d.replyOverdue }">{{ d.replyDueDate }}</span></el-descriptions-item>
          <el-descriptions-item label="负责 QE / 业务员">{{ d.qeName }} / {{ d.salesOwnerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="4"><span class="pre">{{ d.description }}</span></el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel title="分析与处理">
        <el-descriptions :column="3">
          <el-descriptions-item label="原因分析" :span="3"><span class="pre">{{ d.rootCause || '-' }}</span></el-descriptions-item>
          <el-descriptions-item label="回复内容" :span="2"><span class="pre">{{ d.replyContent || '-' }}</span></el-descriptions-item>
          <el-descriptions-item label="首次回复">{{ formatDateTime(d.repliedAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="处理方式">{{ labelOf(HANDLING, d.handling) }}</el-descriptions-item>
          <el-descriptions-item label="索赔 / 同意赔偿">{{ formatMoney(d.claimAmount, d.currency) }} / {{ formatMoney(d.agreedAmount, d.currency) }} {{ d.currency ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="处理说明">{{ d.handlingRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="NCR">
            <el-link v-if="d.ncrId" type="primary" underline="never" @click="router.push(`/quality/ncr/${d.ncrId}`)">{{ d.ncrNo }}</el-link>
            <StatusTag v-if="d.ncrStatus" :value="d.ncrStatus" :map="DOC_STATUS" /><span v-if="!d.ncrId">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="CAPA">
            <el-link v-if="d.capaId" type="primary" underline="never" @click="router.push(`/quality/capa/${d.capaId}`)">{{ d.capaNo }}</el-link>
            <StatusTag v-if="d.capaStatus" :value="d.capaStatus" :map="CAPA_STATUS" /><span v-if="!d.capaId">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="结案时间">{{ formatDateTime(d.closedAt, true) }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="处理时间线" lazy><OperationLogTable biz-type="QC_COMPLAINT" :biz-id="id" :status-map="COMPLAINT_STATUS" /></el-tab-pane>
          <el-tab-pane label="审批记录" lazy><ApprovalTimeline biz-type="QC_COMPLAINT_CLOSE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="附件" lazy><AttachmentPanel biz-type="QC_COMPLAINT" :biz-id="id" :editable="open" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="replyVisible" title="记录回复" width="600px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <el-form-item label="回复内容" required><el-input v-model="reply.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="原因分析"><el-input v-model="reply.rootCause" type="textarea" :rows="3" placeholder="简要原因（详细分析在 CAPA）" /></el-form-item>
        <el-form-item label="回复时间"><el-date-picker v-model="reply.repliedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="默认当前时间" /></el-form-item>
        <el-form-item label="回复文件"><AttachmentUpload v-model="reply.fileIds" biz-type="QC_COMPLAINT" :biz-id="id" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="replyVisible = false">取消</el-button><el-button type="primary" @click="saveReply">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="handVisible" title="登记处理结果" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="处理方式" required>
          <el-select v-model="hand.handling"><el-option v-for="o in HANDLING" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
        </el-form-item>
        <el-form-item label="币别"><CurrencySelect v-model="hand.currency" /></el-form-item>
        <el-form-item label="客户索赔金额"><AmountInput v-model="hand.claimAmount" /></el-form-item>
        <el-form-item label="同意赔偿金额"><AmountInput v-model="hand.agreedAmount" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="hand.remark" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="handVisible = false">取消</el-button><el-button type="primary" @click="saveHandling">保存</el-button></template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.pre { white-space: pre-wrap; }
.gap { margin-bottom: var(--erp-space-3); }
.danger { color: var(--erp-color-error); }
</style>
