<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import { CAPA_SOURCE, CAPA_STATUS, CAPA_STEPS, capaApi, type CapaDetail } from '../api/quality'

defineOptions({ name: 'QcCapaDetail' })

/**
 * CAPA / 8D 详情（需求 10-04 3.2，专用分步页面）：左侧 D1～D8 导航，右侧当前步骤表单。
 * D5 完成后待验证；D6 由品质主管填写验证数据并选择结果（有效继续 D7、D8，无效退回 D4）。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<CapaDetail>()
const step = ref(1)
const content = ref('')
const method = ref('')
const team = ref<string[]>([])
const d3Due = ref<string>()
const saving = ref(false)

async function load() {
  d.value = await capaApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  step.value = Math.min(d.value.currentStep, 8)
}
onMounted(load)
const field = (n: number): string | undefined => {
  const c = d.value
  if (!c) return undefined
  return [c.d1Team, c.d2Problem, c.d3Containment, c.d4RootCause, c.d5Actions, c.d6Implementation, c.d7Prevention, c.d8Summary][n - 1]
}
watch([step, d], () => {
  content.value = field(step.value) ?? ''
  method.value = d.value?.d4Method ?? ''
  team.value = (d.value?.teamMembers ?? []).map((m) => m.id)
  d3Due.value = d.value?.d3Due
})
const open = computed(() => d.value?.status === 'OPEN' || d.value?.status === 'VERIFYING')
const isCurrent = computed(() => step.value === d.value?.currentStep)
const canEditStep = computed(() => open.value && !!d.value && step.value <= d.value.currentStep && (step.value === 6 ? me.hasPermission('qc:capa:verify') : d.value.canEdit))
const stepState = (n: number) => {
  const cur = d.value?.currentStep ?? 1
  if (n < cur) return 'done'
  if (n === cur) return d.value?.overdue ? 'overdue' : 'current'
  return 'todo'
}
const body = () => ({ content: content.value, method: method.value || undefined, teamMembers: step.value === 1 ? team.value : undefined, d3Due: step.value === 3 ? d3Due.value : undefined })
async function saveStep() {
  saving.value = true
  try {
    await capaApi.saveStep(id.value, step.value, body())
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}
async function complete() {
  saving.value = true
  try {
    await capaApi.completeStep(id.value, step.value, body())
    ElMessage.success(`D${step.value} 已完成`)
    await load()
  } finally {
    saving.value = false
  }
}
async function verify(result: 'EFFECTIVE' | 'INEFFECTIVE') {
  if (!content.value.trim()) return ElMessage.warning('请填写验证数据')
  await capaApi.verify(id.value, result, content.value)
  ElMessage.success(result === 'EFFECTIVE' ? '验证有效，继续 D7' : '验证无效，已退回 D4')
  load()
}
async function closeCapa() {
  if (!content.value.trim()) return ElMessage.warning('请填写 D8 结案总结')
  await capaApi.close(id.value, content.value)
  ElMessage.success('已结案')
  load()
}
const actions = computed<DocAction[]>(() => [
  { key: 'cancel', label: '取消', permission: 'qc:capa:close', visible: () => open.value, reasonRequired: true, reasonTitle: '取消原因',
    handler: async (reason) => { await capaApi.cancel(id.value, reason!); load() } }
])
function openSource() {
  const c = d.value
  if (c?.source === 'NCR' && c.sourceId) router.push(`/quality/ncr/${c.sourceId}`)
  else if (c?.source === 'COMPLAINT' && c.sourceId) router.push(`/quality/complaint/${c.sourceId}`)
}
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? 'CAPA'" :status="d?.status" :status-map="CAPA_STATUS" :actions="actions" @back="router.push('/quality/capa')">
        <template #actions-prefix>
          <PrintButton v-if="d" biz-type="QC_CAPA" :ids="[id]" permission="qc:capa:query" label="打印 8D 报告" />
        </template>
      </DocPageHeader>
    </template>
    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="标题" :span="2">{{ d.title }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ CAPA_SOURCE[d.source] }}
            <el-link v-if="d.sourceId" type="primary" underline="never" @click="openSource">{{ d.sourceNo }}</el-link><span v-else>{{ d.sourceNo ?? '' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="负责人">{{ d.leaderName }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ d.materialCode ? `${d.materialCode} ${d.materialName}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户 / 供应商">{{ d.customerName || d.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="期限"><span :class="{ danger: d.overdue }">{{ d.dueDate }}</span></el-descriptions-item>
          <el-descriptions-item label="验证无效次数">{{ d.invalidCount }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <div class="steps-layout">
        <ErpPanel class="nav">
          <div v-for="(label, i) in CAPA_STEPS" :key="i" class="nav-item" :class="[stepState(i + 1), { active: step === i + 1 }]" @click="step = i + 1">
            <span class="mark"><el-icon v-if="stepState(i + 1) === 'done'"><Check /></el-icon><template v-else>{{ i + 1 }}</template></span>{{ label }}
          </div>
        </ErpPanel>
        <ErpPanel :title="CAPA_STEPS[step - 1]" class="body">
          <template v-if="step === 1">
            <el-form-item label="小组成员"><UserSelect v-model="team" multiple :disabled="!canEditStep" /></el-form-item>
          </template>
          <el-form-item v-if="step === 3" label="围堵期限"><el-date-picker v-model="d3Due" value-format="YYYY-MM-DD" :disabled="!canEditStep" /></el-form-item>
          <el-form-item v-if="step === 4" label="分析方法"><el-input v-model="method" placeholder="鱼骨图、5Why" maxlength="64" :disabled="!canEditStep" /></el-form-item>
          <el-input v-model="content" type="textarea" :rows="10" :disabled="!canEditStep"
                    :placeholder="step === 2 ? '5W2H：What / Why / Where / When / Who / How / How many' : step === 6 ? '实施情况与验证数据' : ''" />
          <div v-if="step === 6 && d.verifyHistory" class="muted history">{{ d.verifyHistory }}</div>
          <div v-if="d.d3DoneAt && step === 3" class="muted history">完成于 {{ formatDateTime(d.d3DoneAt, true) }}</div>
          <div v-if="open" class="ops">
            <el-button v-if="canEditStep" :loading="saving" @click="saveStep">保存</el-button>
            <el-button v-if="canEditStep && isCurrent && step !== 6 && step !== 8" v-perm="'qc:capa:update'" type="primary" :loading="saving" @click="complete">完成本步</el-button>
            <template v-if="step === 6 && d.status === 'VERIFYING'">
              <el-button v-perm="'qc:capa:verify'" type="danger" @click="verify('INEFFECTIVE')">验证无效（退回 D4）</el-button>
              <el-button v-perm="'qc:capa:verify'" type="primary" @click="verify('EFFECTIVE')">验证有效</el-button>
            </template>
            <el-button v-if="step === 8 && isCurrent" v-perm="'qc:capa:close'" type="primary" @click="closeCapa">结案</el-button>
          </div>
        </ErpPanel>
      </div>
      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="附件" lazy><AttachmentPanel biz-type="QC_CAPA" :biz-id="id" :editable="open" :category="`D${step}`" /></el-tab-pane>
          <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="QC_CAPA" :biz-id="id" :status-map="CAPA_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.steps-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: var(--erp-section-gap); }
.nav-item { display: flex; align-items: center; gap: var(--erp-space-2); padding: var(--erp-space-2) var(--erp-space-3); cursor: pointer; border-radius: var(--erp-radius-card); }
.nav-item.active { background: var(--erp-color-primary-bg); color: var(--erp-color-primary); }
.mark {
  display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%;
  border: 1px solid var(--erp-color-border); font-size: var(--erp-font-size-caption);
}
.done .mark { background: var(--erp-color-success); border-color: var(--erp-color-success); color: var(--erp-color-surface); }
.current .mark { border-color: var(--erp-color-primary); color: var(--erp-color-primary); }
.overdue .mark { border-color: var(--erp-color-error); color: var(--erp-color-error); }
.ops { display: flex; justify-content: flex-end; gap: var(--erp-space-2); margin-top: var(--erp-space-3); }
.muted { color: var(--erp-color-text-secondary); }
.history { margin-top: var(--erp-space-2); white-space: pre-wrap; font-size: var(--erp-font-size-caption); }
.danger { color: var(--erp-color-error); }
</style>
