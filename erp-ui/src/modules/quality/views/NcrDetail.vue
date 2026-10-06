<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatQty } from '@/utils/format'
import {
  CAPA_STATUS, DISPOSITION, DOC_STATUS, INSP_RESULT, NCR_SOURCE, SCAR_STATUS, SEVERITY, ncrApi, type DispositionRow, type NcrDetail
} from '../api/quality'

defineOptions({ name: 'QcNcrDetail' })

/** NCR 详情（需求 10-03 3.3，T5）：MRB 审批、处置执行（后续单据）、CAPA / SCAR、关闭 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<NcrDetail>()
const tab = ref('disp')

async function load() {
  d.value = await ncrApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
onMounted(load)
const s = computed(() => d.value?.status)
const hasDisp = (k: string) => (d.value?.dispositions ?? []).some((x) => x.disposition === k && !x.followDocNo)
const allDone = computed(() => (d.value?.dispositions ?? []).every((x) => x.done))

const actions = computed<DocAction[]>(() => [
  { key: 'void', label: '作废', permission: 'qc:ncr:void', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    handler: async (reason) => { await ncrApi.void(id.value, reason!); ElMessage.success('已作废'); load() } },
  { key: 'capa', label: '生成 CAPA', permission: 'qc:capa:create', visible: () => s.value !== 'VOIDED' && !d.value?.capaId,
    handler: async () => { const c = await ncrApi.createCapa(id.value); ElMessage.success('已生成 CAPA'); router.push(`/quality/capa/${c}`) } },
  { key: 'scar', label: '生成 SCAR', permission: 'qc:scar:create', visible: () => s.value !== 'VOIDED' && !d.value?.scarId && !!d.value?.supplierId,
    handler: async () => { const c = await ncrApi.createScar(id.value); ElMessage.success('已生成 SCAR'); router.push(`/quality/scar/${c}`) } },
  { key: 'return', label: '通知采购退货', permission: 'qc:ncr:update', visible: () => s.value === 'APPROVED' && hasDisp('RETURN'),
    handler: async () => { ElMessage.success(await ncrApi.purchaseReturn(id.value)); load() } },
  { key: 'rework', label: '生成返工订单', permission: 'qc:ncr:update', visible: () => s.value === 'APPROVED' && hasDisp('REWORK'),
    handler: async () => { ElMessage.success(await ncrApi.reworkOrder(id.value)); load() } },
  { key: 'scrap', label: '生成报废出库', permission: 'qc:ncr:update', visible: () => s.value === 'APPROVED' && hasDisp('SCRAP'),
    handler: async () => { ElMessage.success(await ncrApi.scrapOut(id.value)); load() } },
  { key: 'downgrade', label: '生成降级转换', permission: 'qc:ncr:update', visible: () => s.value === 'APPROVED' && hasDisp('DOWNGRADE'),
    handler: async () => { ElMessage.success(await ncrApi.downgrade(id.value)); load() } },
  { key: 'close', label: '关闭', permission: 'qc:ncr:close', visible: () => s.value === 'APPROVED' && allDone.value, handler: close },
  { key: 'edit', label: '编辑', permission: 'qc:ncr:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/quality/ncr/${id.value}/edit`) },
  { key: 'submit', label: '提交 MRB', type: 'primary', permission: 'qc:ncr:submit', visible: () => s.value === 'DRAFT',
    handler: async () => { await ncrApi.submit(id.value); ElMessage.success('已提交 MRB'); load() } }
])
async function close() {
  let unfreeze = false
  if (d.value?.batchFrozen) {
    unfreeze = await ElMessageBox.confirm(`批次 ${d.value.batchNo} 已被本 NCR 冻结，关闭时是否解冻？`, '关闭 NCR', {
      confirmButtonText: '解冻并关闭', cancelButtonText: '保持冻结', distinguishCancelAndClose: true
    }).then(() => true).catch((a) => (a === 'cancel' ? false : Promise.reject(a)))
  }
  await ncrApi.close(id.value, unfreeze)
  ElMessage.success('已关闭')
  load()
}
async function done(x: DispositionRow) {
  const { value } = await ElMessageBox.prompt('后续单据号（采购退货单、返工订单、报废出库单等，可空）', `${DISPOSITION[x.disposition]} 执行完成`, { inputValue: x.followDocNo ?? '' })
  await ncrApi.done(id.value, x.id!, value?.trim() || undefined)
  load()
}
const steps = [
  { status: 'DRAFT', label: '草稿' }, { status: 'PENDING_APPROVAL', label: 'MRB 会签' }, { status: 'APPROVED', label: '处置执行' }, { status: 'CLOSED', label: '已关闭' }
]
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? 'NCR'" :status="d?.status" :status-map="DOC_STATUS" :actions="actions" @back="router.push('/quality/ncr')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="QC_NCR" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && s !== 'VOIDED'" biz-type="QC_NCR" :ids="[id]" permission="qc:ncr:print" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ VOIDED: '已作废' }" />
        <el-descriptions :column="4">
          <el-descriptions-item label="来源">{{ NCR_SOURCE[d.source] }}
            <el-link v-if="d.inspectionId" type="primary" underline="never" @click="router.push(`/quality/inspection/${d.inspectionId}`)">{{ d.inspectionNo }}</el-link>
            <span v-else>{{ d.sourceNo ?? '' }}</span>
            <StatusTag v-if="d.inspectionResult" :value="d.inspectionResult" :map="INSP_RESULT" />
          </el-descriptions-item>
          <el-descriptions-item label="物料">{{ d.materialCode }} {{ d.materialName }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ d.batchNo || '-' }}<ErpBadge v-if="d.batchFrozen" type="danger" :dot="false" class="gap-l">已冻结</ErpBadge></el-descriptions-item>
          <el-descriptions-item label="不合格数量">{{ formatQty(d.ncrQty) }} {{ d.baseUom ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="严重度"><StatusTag :value="d.severity" :map="SEVERITY" /></el-descriptions-item>
          <el-descriptions-item label="责任"><DictTag type="qc_ncr_responsibility" :value="d.responsibility" /></el-descriptions-item>
          <el-descriptions-item label="供应商 / 客户">{{ d.supplierName || d.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="缺陷代码">{{ d.defectCodes.join('、') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="不合格描述" :span="4"><span class="pre">{{ d.defectDescription }}</span></el-descriptions-item>
          <el-descriptions-item label="围堵措施" :span="4"><span class="pre">{{ d.containment || '-' }}</span></el-descriptions-item>
          <el-descriptions-item label="CAPA">
            <template v-if="d.capaId"><el-link type="primary" underline="never" @click="router.push(`/quality/capa/${d.capaId}`)">{{ d.capaNo }}</el-link>
              <StatusTag :value="d.capaStatus" :map="CAPA_STATUS" /></template>
            <span v-else>{{ d.capaRequired ? '需要（未生成）' : '不需要' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="SCAR">
            <template v-if="d.scarId"><el-link type="primary" underline="never" @click="router.push(`/quality/scar/${d.scarId}`)">{{ d.scarNo }}</el-link>
              <StatusTag :value="d.scarStatus" :map="SCAR_STATUS" /></template>
            <span v-else>{{ d.scarRequired ? '需要（未生成）' : '不需要' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="客诉">
            <el-link v-if="d.complaintId" type="primary" underline="never" @click="router.push(`/quality/complaint/${d.complaintId}`)">{{ d.complaintNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="QE">{{ d.ownerName }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel flush>
        <el-tabs v-model="tab" class="detail-tabs">
          <el-tab-pane :label="`处置执行(${d.dispositions.length})`" name="disp">
            <el-table :data="d.dispositions">
              <el-table-column label="处置" width="110"><template #default="{ row }">{{ DISPOSITION[row.disposition] }}</template></el-table-column>
              <el-table-column label="数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="说明" min-width="200">
                <template #default="{ row }">
                  <div v-if="row.targetMaterialId">降级为 {{ row.targetMaterialCode }} {{ row.targetMaterialName }}</div>
                  <div>{{ row.remark }}</div>
                </template>
              </el-table-column>
              <el-table-column label="后续单据" prop="followDocNo" min-width="200" />
              <el-table-column label="完成" width="160">
                <template #default="{ row }">{{ row.done ? `已完成 ${formatDateTime(row.doneAt, true)}` : '未完成' }}</template>
              </el-table-column>
              <el-table-column label="" width="100">
                <template #default="{ row }">
                  <el-button v-if="s === 'APPROVED' && !row.done" v-perm="'qc:ncr:update'" link type="primary" @click="done(row as DispositionRow)">标记完成</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="QC_NCR" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="QC_NCR" :biz-id="id" :status-map="DOC_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="QC_NCR" :biz-id="id" :editable="s !== 'CLOSED' && s !== 'VOIDED'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.pre { white-space: pre-wrap; }
.gap-l { margin-left: var(--erp-space-2); }
</style>
