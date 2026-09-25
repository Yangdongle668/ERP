<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { projectApi, PROJECT_TYPE_OPTIONS, PRIORITY_OPTIONS, type ProjectDetail, type ProjectSave } from '../api/project'

defineOptions({ name: 'EngProjectEdit' })

/** 研发项目编辑（需求 05-06 3.2，T4）：项目经理自动成为成员 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<ProjectDetail>()
const form = ref<ProjectSave>({ name: '', priority: 'MEDIUM', members: [] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  pmUserId: [{ required: true, message: '请选择项目经理', trigger: 'change' }],
  planStart: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  planEnd: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }, {
    validator: (_r, v, cb) => (v && form.value.planStart && v < form.value.planStart ? cb(new Error('计划结束日期不能早于开始日期')) : cb()), trigger: 'change'
  }]
}

onMounted(async () => {
  if (id.value) {
    const d = await projectApi.get(id.value)
    detail.value = d
    form.value = {
      name: d.name, projectType: d.projectType, customerId: d.customerId, productMaterialId: d.productMaterialId, pmUserId: d.pmUserId,
      priority: d.priority, planStart: d.planStart, planEnd: d.planEnd, description: d.description, version: d.version,
      members: d.members.filter((m) => m.userId !== d.pmUserId).map((m) => ({ userId: m.userId, role: m.role }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑 ${d.docNo}`)
  } else {
    form.value.pmUserId = me.user?.id
    form.value.planStart = today()
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/engineering/project/${id.value}` : '/engineering/project')
}

async function save() {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  const members = form.value.members.filter((m) => m.userId)
  if (new Set(members.map((m) => m.userId)).size !== members.length) return ElMessage.warning('成员重复')
  saving.value = true
  try {
    const data = { ...form.value, members, description: form.value.description?.trim() || undefined }
    const pid = id.value ? (await projectApi.update(id.value, data), id.value) : await projectApi.create(data)
    guard.markClean()
    ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/engineering/project/${pid}`)
  } finally {
    saving.value = false
  }
}
const title = computed(() => (detail.value ? `编辑 ${detail.value.docNo}` : '新建项目'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="项目名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="项目类型" prop="projectType">
              <el-select v-model="form.projectType"><el-option v-for="o in PROJECT_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="优先级">
              <el-radio-group v-model="form.priority"><el-radio v-for="o in PRIORITY_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="项目经理" prop="pmUserId"><UserSelect v-model="form.pmUserId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客户"><CustomerSelect v-model="form.customerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="产品"><MaterialSelect v-model="form.productMaterialId" :types="['SEMI_FINISHED', 'FINISHED']" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="计划开始" prop="planStart"><el-date-picker v-model="form.planStart" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="计划结束" prop="planEnd"><el-date-picker v-model="form.planEnd" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="项目描述"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>
    <ErpPanel title="成员">
      <template #extra><span class="text-muted">项目经理自动成为成员；任务负责人必须是项目成员</span></template>
      <el-table :data="form.members">
        <el-table-column label="成员" min-width="220"><template #default="{ row }"><UserSelect v-model="row.userId" /></template></el-table-column>
        <el-table-column label="角色" min-width="160"><template #default="{ row }"><el-input v-model="row.role" maxlength="32" placeholder="如 结构工程师" /></template></el-table-column>
        <el-table-column label="" width="56" align="center">
          <template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.members.splice($index, 1)" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="只有项目经理" /></template>
      </el-table>
      <el-button class="add" icon="Plus" @click="form.members.push({ userId: '' })">添加成员</el-button>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.add { margin-top: var(--erp-space-3); }
</style>
