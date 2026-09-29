<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime } from '@/utils/format'
import { SCAR_STATUS, scarApi, type ScarDetail } from '../api/quality'

defineOptions({ name: 'QcScarDetail' })

/** SCAR 详情（需求 10-06 第 3 节，T5 变体）：发出（打印 / 导出给供应商）、登记回复（须上传回复文件）、验证、结案 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ScarDetail>()
async function load() {
  d.value = await scarApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(load)
const s = computed(() => d.value?.status)
const replyVisible = ref(false)
const reply = ref<{ content?: string; repliedAt?: string; fileIds: string[] }>({ fileIds: [] })
async function saveReply() {
  if (!reply.value.content?.trim()) return ElMessage.warning('请填写供应商回复摘要')
  if (!reply.value.fileIds.length) return ElMessage.warning('请上传供应商的回复文件')
  await scarApi.reply(id.value, reply.value)
  replyVisible.value = false
  ElMessage.success('已登记回复')
  load()
}
async function verify(result: 'EFFECTIVE' | 'INEFFECTIVE') {
  const { value } = await ElMessageBox.prompt(result === 'EFFECTIVE' ? '验证说明（可空）' : '无效说明（将退回已发出并要求重新回复）', result === 'EFFECTIVE' ? '验证有效' : '验证无效')
  await scarApi.verify(id.value, result, value?.trim() || undefined)
  load()
}
const actions = computed<DocAction[]>(() => [
  { key: 'cancel', label: '取消', permission: 'qc:scar:close', visible: () => !['CLOSED', 'CANCELED'].includes(s.value ?? ''), reasonRequired: true,
    reasonTitle: '取消原因', handler: async (reason) => { await scarApi.cancel(id.value, reason!); load() } },
  { key: 'send', label: '发出', type: 'primary', permission: 'qc:scar:send', visible: () => s.value === 'DRAFT',
    handler: async () => { await scarApi.send(id.value); ElMessage.success('已发出，可打印 SCAR 发给供应商'); load() } },
  { key: 'reply', label: '登记回复', type: 'primary', permission: 'qc:scar:update', visible: () => s.value === 'SENT',
    handler: () => { reply.value = { fileIds: [] }; replyVisible.value = true } },
  { key: 'startVerify', label: '开始验证', type: 'primary', permission: 'qc:scar:verify', visible: () => s.value === 'REPLIED', handler: async () => {
    const { value } = await ElMessageBox.prompt('验证方式（如：后续 3 批加严检验）', '开始验证', { inputValidator: (v) => (v?.trim() ? true : '请填写验证方式') })
    await scarApi.startVerify(id.value, value.trim())
    load()
  } },
  { key: 'invalid', label: '验证无效', permission: 'qc:scar:verify', visible: () => s.value === 'VERIFYING', handler: () => verify('INEFFECTIVE') },
  { key: 'valid', label: '验证有效', type: 'primary', permission: 'qc:scar:verify', visible: () => s.value === 'VERIFYING', handler: () => verify('EFFECTIVE') }
])
const steps = [
  { status: 'DRAFT', label: '草稿' }, { status: 'SENT', label: '已发出' }, { status: 'REPLIED', label: '已回复' }, { status: 'VERIFYING', label: '验证中' },
  { status: 'CLOSED', label: '已结案' }
]
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? 'SCAR'" :status="d?.status" :status-map="SCAR_STATUS" :actions="actions" @back="router.push('/quality/scar')">
        <template #actions-prefix><PrintButton v-if="d && s !== 'CANCELED'" biz-type="QC_SCAR" :ids="[id]" permission="qc:scar:query" /></template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CANCELED: `已取消：${d.cancelReason ?? ''}` }" />
        <el-descriptions :column="4">
          <el-descriptions-item label="供应商">{{ d.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ d.materialCode }} {{ d.materialName }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ d.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="NCR">
            <el-link v-if="d.ncrId" type="primary" underline="never" @click="router.push(`/quality/ncr/${d.ncrId}`)">{{ d.ncrNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="发出时间">{{ formatDateTime(d.sentAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="回复期限"><span :class="{ danger: d.replyOverdue }">{{ d.replyDueDate || '-' }}</span></el-descriptions-item>
          <el-descriptions-item label="回复时间">{{ formatDateTime(d.repliedAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="验证无效次数">{{ d.invalidCount }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="4"><span class="pre">{{ d.problemDescription }}</span></el-descriptions-item>
          <el-descriptions-item label="要求" :span="4"><span class="pre">{{ d.requirement }}</span></el-descriptions-item>
          <el-descriptions-item label="供应商回复" :span="4"><span class="pre">{{ d.replyContent || '-' }}</span></el-descriptions-item>
          <el-descriptions-item label="验证方式" :span="2">{{ d.verifyPlan || '-' }}</el-descriptions-item>
          <el-descriptions-item label="SQE">{{ d.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="结案时间">{{ formatDateTime(d.closedAt, true) }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="附件" lazy><AttachmentPanel biz-type="QC_SCAR" :biz-id="id" :editable="s !== 'CLOSED' && s !== 'CANCELED'" /></el-tab-pane>
          <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="QC_SCAR" :biz-id="id" :status-map="SCAR_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="replyVisible" title="登记供应商回复" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="回复摘要" required><el-input v-model="reply.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="回复时间"><el-date-picker v-model="reply.repliedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="默认当前时间" /></el-form-item>
        <el-form-item label="8D 报告" required><AttachmentUpload v-model="reply.fileIds" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="replyVisible = false">取消</el-button><el-button type="primary" @click="saveReply">保存</el-button></template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.pre { white-space: pre-wrap; }
.danger { color: var(--erp-color-error); }
</style>
