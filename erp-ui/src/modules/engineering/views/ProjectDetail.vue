<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { today } from '@/utils/format'
import {
  projectApi, PROJECT_STATUS, PROJECT_TYPE_OPTIONS, PRIORITY_OPTIONS, STAGE_OPTIONS, TASK_STATUS,
  type ProjectDetail, type RelatedRow, type TaskRow, type TaskSave
} from '../api/project'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngProjectDetail' })

/** 研发项目详情（需求 05-06 3.3，T5）：阶段推进、任务（按阶段分组）、成员、关联单据、操作日志 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ProjectDetail>()
const activeTab = ref('tasks')

async function load() {
  d.value = await projectApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const st = computed(() => d.value?.projectStatus)
const active = computed(() => st.value === 'PLANNING' || st.value === 'IN_PROGRESS')

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

/** 推进阶段（R04）：当前阶段有未完成任务时提示 */
const nextStage = computed(() => STAGE_OPTIONS[STAGE_OPTIONS.findIndex((s) => s.value === d.value?.stage) + 1])
async function advance() {
  const next = nextStage.value
  if (!next || !d.value) return
  const undone = d.value.undoneInStage
  await ElMessageBox.confirm(undone ? `当前阶段还有 ${undone} 个未完成任务，确定推进到“${next.label}”吗？` : `确定推进到“${next.label}”阶段吗？`, '推进阶段',
    { type: undone ? 'warning' : 'info' })
  await run(projectApi.stage(id.value, next.value), `已推进到${next.label}`)
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'eng:project:delete', visible: () => st.value === 'PLANNING', confirm: `确定删除 ${d.value?.docNo} 吗？`,
    handler: async () => {
      await projectApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/engineering/project')
    } },
  { key: 'cancel', label: '取消项目', permission: 'eng:project:close', visible: () => active.value || st.value === 'ON_HOLD', reasonRequired: true, reasonTitle: '取消原因',
    handler: (reason) => run(projectApi.cancel(id.value, reason!), '已取消') },
  { key: 'hold', label: '暂停', visible: () => !!d.value?.canManage && active.value, handler: () => run(projectApi.hold(id.value), '已暂停') },
  { key: 'resume', label: '恢复', visible: () => !!d.value?.canManage && st.value === 'ON_HOLD', handler: () => run(projectApi.resume(id.value), '已恢复') },
  { key: 'complete', label: '完成项目', permission: 'eng:project:close', visible: () => active.value,
    confirm: '完成后项目不能再修改。确定完成吗？', handler: () => run(projectApi.complete(id.value), '项目已完成') },
  { key: 'edit', label: '编辑', visible: () => !!d.value?.canManage && (active.value || st.value === 'ON_HOLD'), handler: () => router.push(`/engineering/project/${id.value}/edit`) },
  { key: 'stage', label: nextStage.value ? `推进到${nextStage.value.label}` : '推进阶段', type: 'primary',
    visible: () => !!d.value?.canManage && active.value && !!nextStage.value, handler: advance }
])

// ---------- 任务 ----------
const taskVisible = ref(false)
const taskEditing = ref<TaskRow>()
const taskFormRef = ref<FormInstance>()
const taskForm = ref<TaskSave>({ name: '' })
const taskRules: FormRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  planStart: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  planEnd: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }]
}
function openTask(t?: TaskRow) {
  taskEditing.value = t
  taskForm.value = t
    ? { stage: t.stage, name: t.name, ownerId: t.ownerId, planStart: t.planStart, planEnd: t.planEnd, deliverable: t.deliverable, weight: t.weight, remark: t.remark }
    : { stage: d.value?.stage, name: '', planStart: today(), planEnd: today(), weight: 1 }
  taskVisible.value = true
}
async function saveTask() {
  if (!(await taskFormRef.value?.validate().catch(() => false))) return
  if (taskEditing.value) await projectApi.updateTask(id.value, taskEditing.value.id, taskForm.value)
  else await projectApi.createTask(id.value, taskForm.value)
  ElMessage.success('保存成功')
  taskVisible.value = false
  load()
}
async function removeTask(t: TaskRow) {
  await ElMessageBox.confirm(`确定删除任务“${t.name}”吗？`, '删除任务', { type: 'warning' })
  await run(projectApi.removeTask(id.value, t.id), '删除成功')
}
async function setStatus(t: TaskRow, status: string) {
  await run(projectApi.taskStatus(id.value, t.id, status), '已更新')
}
const NEXT_STATUS: Record<string, { value: string; label: string }[]> = {
  TODO: [{ value: 'DOING', label: '开始' }, { value: 'DONE', label: '完成' }, { value: 'CANCELED', label: '取消' }],
  DOING: [{ value: 'DONE', label: '完成' }, { value: 'TODO', label: '退回未开始' }, { value: 'CANCELED', label: '取消' }],
  DONE: [{ value: 'DOING', label: '重新打开' }],
  CANCELED: [{ value: 'TODO', label: '恢复' }]
}
const grouped = computed(() => STAGE_OPTIONS.map((s) => ({ ...s, tasks: d.value?.tasks.filter((t) => t.stage === s.value) ?? [] })).filter((g) => g.tasks.length))
const stageSteps = STAGE_OPTIONS.map((s) => ({ status: s.value, label: s.label }))

function openRelated(r: RelatedRow) {
  const path = { SAMPLE: 'sample', BOM: 'bom', ECN: 'ecn', CERT: 'cert' }[r.docType]
  if (path === 'cert') router.push('/engineering/cert')
  else if (path) router.push(`/engineering/${path}/${r.id}`)
}
const RELATED_TYPE: Record<string, string> = { SAMPLE: '样品单', BOM: 'BOM', ECN: 'ECN', CERT: '认证' }

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.docNo} ${d.name}` : '研发项目'" :status="d?.projectStatus" :status-map="PROJECT_STATUS" :actions="actions"
                     @back="router.push('/engineering/project')" />
    </template>
    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="stageSteps" :current="d.stage" />
        <el-descriptions :column="3" class="head">
          <el-descriptions-item label="类型">{{ labelOf(PROJECT_TYPE_OPTIONS, d.projectType) }}</el-descriptions-item>
          <el-descriptions-item label="项目经理">{{ d.pmName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ labelOf(PRIORITY_OPTIONS, d.priority) }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ d.customerName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品">
            <el-link v-if="d.productMaterialId" type="primary" underline="never" @click="router.push(`/engineering/material/${d.productMaterialId}`)">{{ d.productCode }}</el-link>
            <span v-else>-</span> {{ d.productName ?? '' }}
          </el-descriptions-item>
          <el-descriptions-item label="进度"><el-progress :percentage="Math.round(Number(d.progressPct) * 100)" :stroke-width="8" class="progress" /></el-descriptions-item>
          <el-descriptions-item label="计划">{{ d.planStart }} ～ {{ d.planEnd }}</el-descriptions-item>
          <el-descriptions-item label="实际">{{ d.actualStart ?? '-' }} ～ {{ d.actualEnd ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="未完成任务">{{ d.undoneTotal }}（当前阶段 {{ d.undoneInStage }}）</el-descriptions-item>
          <el-descriptions-item v-if="d.cancelReason" label="取消原因" :span="3">{{ d.cancelReason }}</el-descriptions-item>
          <el-descriptions-item label="项目描述" :span="3"><span class="pre">{{ d.description || '-' }}</span></el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`任务(${d.tasks.length})`" name="tasks">
            <div v-if="d.canManage && active" class="bar"><el-button type="primary" icon="Plus" @click="openTask()">新建任务</el-button></div>
            <template v-for="g in grouped" :key="g.value">
              <div class="group-title">{{ g.label }}（{{ g.tasks.length }}）</div>
              <el-table :data="g.tasks" :row-class-name="({ row }) => ((row as TaskRow).overdue ? 'overdue' : '')">
                <el-table-column prop="name" label="任务" min-width="180" show-overflow-tooltip />
                <el-table-column prop="ownerName" label="负责人" width="100" />
                <el-table-column label="计划" width="200"><template #default="{ row }">{{ row.planStart }} ～ {{ row.planEnd }}</template></el-table-column>
                <el-table-column prop="actualEnd" label="实际完成" width="110" />
                <el-table-column prop="weight" label="权重" width="60" align="right" />
                <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.taskStatus" :map="TASK_STATUS" /></template></el-table-column>
                <el-table-column prop="deliverable" label="交付物" min-width="140" show-overflow-tooltip />
                <el-table-column label="" width="280">
                  <template #default="{ row }">
                    <template v-if="active && (row as TaskRow).canUpdate">
                      <el-button v-for="o in NEXT_STATUS[row.taskStatus] ?? []" :key="o.value" link type="primary" @click="setStatus(row as TaskRow, o.value)">{{ o.label }}</el-button>
                    </template>
                    <template v-if="d.canManage && active">
                      <el-button link type="primary" @click="openTask(row as TaskRow)">编辑</el-button>
                      <el-button link type="danger" @click="removeTask(row as TaskRow)">删除</el-button>
                    </template>
                  </template>
                </el-table-column>
              </el-table>
            </template>
            <ErpEmpty v-if="!d.tasks.length" compact description="还没有任务" />
          </el-tab-pane>
          <el-tab-pane :label="`成员(${d.members.length})`" name="members">
            <el-table :data="d.members">
              <el-table-column prop="name" label="姓名" width="140" />
              <el-table-column prop="deptName" label="部门" width="160" />
              <el-table-column prop="role" label="角色" min-width="160" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related">
            <el-table :data="d.related">
              <el-table-column label="类型" width="100"><template #default="{ row }">{{ RELATED_TYPE[row.docType] ?? row.docType }}</template></el-table-column>
              <el-table-column label="单号" width="180">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="openRelated(row as RelatedRow)">{{ row.docNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="120" />
              <template #empty><ErpEmpty compact description="没有关联的样品单、BOM、ECN 或认证" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="ENG_PROJECT" :biz-id="id" :status-map="PROJECT_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="taskVisible" :title="taskEditing ? '编辑任务' : '新建任务'" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="24"><el-form-item label="任务名称" prop="name"><el-input v-model="taskForm.name" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="阶段">
              <el-select v-model="taskForm.stage"><el-option v-for="o in STAGE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人" prop="ownerId">
              <el-select v-model="taskForm.ownerId" placeholder="项目成员">
                <el-option v-for="m in d?.members ?? []" :key="m.userId" :value="m.userId" :label="m.name ?? m.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="计划开始" prop="planStart"><el-date-picker v-model="taskForm.planStart" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划结束" prop="planEnd"><el-date-picker v-model="taskForm.planEnd" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="权重"><el-input-number v-model="taskForm.weight" :min="1" :max="10" :precision="0" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="交付物"><el-input v-model="taskForm.deliverable" maxlength="256" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="taskForm.remark" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="taskVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.pre { white-space: pre-wrap; }
.progress { width: 200px; }
.bar { margin-bottom: var(--erp-space-3); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
:deep(.overdue) { color: var(--erp-color-error); }
</style>
