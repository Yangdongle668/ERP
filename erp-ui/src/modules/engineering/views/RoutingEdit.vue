<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { BOM_STATUS } from '../api/bom'
import { routingApi, workCenterApi, type RoutingDetail, type RoutingSave, type StepRow, type WorkCenterSimple } from '../api/routing'

defineOptions({ name: 'EngRoutingEdit' })

/** 工艺路线编辑（需求 05-04 3.3，T4）：工序号按 10 递增；最后一道工序必须是报工点（审核时校验） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<RoutingDetail>()
const workCenters = ref<WorkCenterSimple[]>([])

interface Form { materialId?: string; description?: string; remark?: string; steps: StepRow[] }
const form = ref<Form>({ steps: [] })
const guard = useLeaveGuard(() => form.value)
const rules: FormRules = { materialId: [{ required: true, message: '请选择物料', trigger: 'change' }] }

function addStep() {
  const max = Math.max(0, ...form.value.steps.map((s) => s.seq ?? 0))
  form.value.steps.push({ seq: max + 10, setupMinutes: '0', isReportPoint: true, isInspectionPoint: false, isOutsourced: false })
}
function onWc(s: StepRow) {
  const wc = workCenters.value.find((w) => w.id === s.workCenterId)
  s.wcType = wc?.wcType
  if (wc?.wcType === 'OUTSOURCE') s.isOutsourced = true
}

onMounted(async () => {
  workCenters.value = await workCenterApi.simple()
  if (id.value) {
    const d = await routingApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的工艺路线可以修改')
      router.replace(`/engineering/routing/${id.value}`)
      return
    }
    detail.value = d
    form.value = { materialId: d.materialId, description: d.description, remark: d.remark, steps: d.steps.map((s) => ({ ...s })) }
    tabs.setTitle(tabKeyOf(route), `编辑工艺路线 ${d.docNo}`)
  } else {
    if (typeof route.query.materialId === 'string') form.value.materialId = route.query.materialId
    addStep()
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/engineering/routing/${id.value}` : '/engineering/routing')
}

function validateSteps(): boolean {
  const seqs = new Set<number>()
  for (const [i, s] of form.value.steps.entries()) {
    const n = i + 1
    if (!s.seq || s.seq < 1) return warn(`第 ${n} 行：请填写工序号`)
    if (seqs.has(s.seq)) return warn(`工序号 ${s.seq} 重复`)
    seqs.add(s.seq)
    if (!s.operation) return warn(`第 ${n} 行：请选择工序`)
    if (!s.workCenterId) return warn(`第 ${n} 行：请选择工作中心`)
    if (!(Number(s.runSeconds) > 0)) return warn(`第 ${n} 行标准工时必须大于 0`)
    if (s.isOutsourced && s.wcType && s.wcType !== 'OUTSOURCE') return warn(`第 ${n} 行：委外工序必须选择委外类型的工作中心`)
  }
  return true
}
function warn(msg: string) {
  ElMessage.warning(msg)
  return false
}

async function save(approve: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!validateSteps()) return
  saving.value = true
  try {
    const f = form.value
    const data: RoutingSave = {
      materialId: f.materialId!, description: f.description?.trim() || undefined, remark: f.remark?.trim() || undefined, rowVersion: detail.value?.rowVersion,
      steps: f.steps.map((s) => ({ seq: s.seq, operation: s.operation, workCenterId: s.workCenterId, setupMinutes: s.setupMinutes || '0', runSeconds: s.runSeconds,
        isReportPoint: !!s.isReportPoint, isInspectionPoint: !!s.isInspectionPoint, isOutsourced: !!s.isOutsourced, remark: s.remark?.trim() || undefined }))
    }
    const r = id.value ? await routingApi.update(id.value, data) : await routingApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: 'BOM 工序引用', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (approve) {
      try {
        await routingApi.approve(r.id)
        ElMessage.success('已保存并审核')
      } catch {
        if (!id.value) router.replace(`/engineering/routing/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/engineering/routing/${r.id}`)
  } finally {
    saving.value = false
  }
}

const totalRun = computed(() => form.value.steps.reduce((a, s) => a + (Number(s.runSeconds) || 0), 0))
const title = computed(() => (detail.value ? `编辑工艺路线 ${detail.value.docNo}` : '新建工艺路线'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="BOM_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('eng:routing:approve')" type="primary" :loading="saving" @click="save(true)">保存并审核</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="物料" prop="materialId">
              <MaterialSelect v-if="!id" v-model="form.materialId" :types="['SEMI_FINISHED', 'FINISHED']" placeholder="半成品或成品" />
              <span v-else class="mono">{{ detail?.materialCode }} {{ detail?.materialName }}</span>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="版本说明"><el-input v-model="form.description" maxlength="256" /></el-form-item></el-col>
          <el-col :xl="8" :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="工序">
      <template #extra><span class="text-muted">合计标准工时 {{ totalRun }} 秒/件；最后一道工序必须是报工点</span></template>
      <el-table :data="form.steps">
        <el-table-column label="工序号" width="100">
          <template #default="{ row }"><el-input-number v-model="row.seq" :min="1" :precision="0" controls-position="right" class="w80" /></template>
        </el-table-column>
        <el-table-column label="工序" min-width="140"><template #default="{ row }"><DictSelect v-model="row.operation" type="eng_operation" /></template></el-table-column>
        <el-table-column label="工作中心" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.workCenterId" filterable @change="onWc(row)">
              <el-option v-for="w in workCenters" :key="w.id" :value="w.id" :label="`${w.code} ${w.name}`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="准备时间(分)" width="120"><template #default="{ row }"><NumberInput v-model="row.setupMinutes" :precision="2" trim-zeros /></template></el-table-column>
        <el-table-column label="标准工时(秒/件)" width="130"><template #default="{ row }"><NumberInput v-model="row.runSeconds" :precision="2" trim-zeros /></template></el-table-column>
        <el-table-column label="报工点" width="70" align="center"><template #default="{ row }"><el-checkbox v-model="row.isReportPoint" /></template></el-table-column>
        <el-table-column label="检验点" width="70" align="center"><template #default="{ row }"><el-checkbox v-model="row.isInspectionPoint" /></template></el-table-column>
        <el-table-column label="委外" width="60" align="center"><template #default="{ row }"><el-checkbox v-model="row.isOutsourced" /></template></el-table-column>
        <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="56" align="center">
          <template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.steps.splice($index, 1)" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="请添加工序" /></template>
      </el-table>
      <el-button class="add" icon="Plus" @click="addStep">添加工序</el-button>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.add { margin-top: var(--erp-space-3); }
.w80 { width: 80px; }
</style>
