<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { projectApi, type ProjectRow } from '../api/project'
import { MAKE_METHOD_OPTIONS, sampleApi, SAMPLE_STATUS, SAMPLE_TYPE_OPTIONS, type SampleDetail, type SampleSave } from '../api/sample'

defineOptions({ name: 'EngSampleEdit' })

/** 样品单编辑（需求 05-07 3.2，T4）：客户样必须选择客户；要求日期不早于今天 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<SampleDetail>()
const form = ref<SampleSave>({ sampleType: 'CUSTOMER', makeMethod: 'PRODUCE', purpose: '', fileIds: [] })
const guard = useLeaveGuard(() => form.value)
const uom = ref<string>()
const projects = ref<ProjectRow[]>([])

const rules = computed<FormRules>(() => ({
  sampleType: [{ required: true, message: '请选择样品类型', trigger: 'change' }],
  customerId: form.value.sampleType === 'CUSTOMER' ? [{ required: true, message: '客户样请选择客户', trigger: 'change' }] : [],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  qty: [{ required: true, message: '请输入数量', trigger: 'blur' }, { validator: (_r, v, cb) => (Number(v) > 0 ? cb() : cb(new Error('数量必须大于 0'))), trigger: 'blur' }],
  requiredDate: [{ required: true, message: '请选择要求日期', trigger: 'change' }, {
    validator: (_r, v, cb) => (v && v < today() ? cb(new Error('要求日期不能早于今天')) : cb()), trigger: 'change'
  }],
  purpose: [{ required: true, message: '请填写用途', trigger: 'blur' }]
}))

function onMaterial(m?: MaterialBrief | MaterialBrief[]) {
  uom.value = (Array.isArray(m) ? m[0] : m)?.baseUom
}

onMounted(async () => {
  projects.value = (await projectApi.page({ statuses: 'PLANNING,IN_PROGRESS,ON_HOLD', pageNo: 1, pageSize: 200 }).catch(() => ({ list: [] as ProjectRow[] }))).list
  if (id.value) {
    const d = await sampleApi.get(id.value)
    if (d.sampleStatus !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的样品单可以修改')
      router.replace(`/engineering/sample/${id.value}`)
      return
    }
    detail.value = d
    uom.value = d.uom
    form.value = {
      sampleType: d.sampleType, customerId: d.customerId, projectId: d.projectId, materialId: d.materialId, customerPartNo: d.customerPartNo, qty: d.qty,
      requiredDate: d.requiredDate, makeMethod: d.makeMethod, purpose: d.purpose, requirements: d.requirements, shipAddress: d.shipAddress, fileIds: [], version: d.version
    }
    tabs.setTitle(tabKeyOf(route), `编辑 ${d.docNo}`)
  } else if (typeof route.query.projectId === 'string') {
    form.value.projectId = route.query.projectId
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/engineering/sample/${id.value}` : '/engineering/sample')
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const f = form.value
    const data: SampleSave = { ...f, customerPartNo: f.customerPartNo?.trim() || undefined, purpose: f.purpose.trim() }
    const sid = id.value ? (await sampleApi.update(id.value, data), id.value) : await sampleApi.create(data)
    guard.markClean()
    if (submit) {
      try {
        const st = await sampleApi.submit(sid)
        ElMessage.success(st === 'APPROVED' ? '提交成功，已审批' : '已提交审批')
      } catch {
        if (!id.value) router.replace(`/engineering/sample/${sid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/engineering/sample/${sid}`)
  } finally {
    saving.value = false
  }
}
const title = computed(() => (detail.value ? `编辑 ${detail.value.docNo}` : '新建样品单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.sampleStatus" :map="SAMPLE_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('eng:sample:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>
    <ErpPanel title="样品信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="样品类型" prop="sampleType">
              <el-radio-group v-model="form.sampleType"><el-radio v-for="o in SAMPLE_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="研发项目">
              <el-select v-model="form.projectId" clearable filterable placeholder="可选">
                <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="`${p.docNo} ${p.name}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="物料" prop="materialId"><MaterialSelect v-model="form.materialId" @select="onMaterial" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客户料号"><el-input v-model="form.customerPartNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="数量" prop="qty"><QtyInput v-model="form.qty" :uom="uom" /><span class="uom">{{ uom }}</span></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="要求日期" prop="requiredDate"><el-date-picker v-model="form.requiredDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="制作方式">
              <el-radio-group v-model="form.makeMethod"><el-radio v-for="o in MAKE_METHOD_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="用途" prop="purpose"><el-input v-model="form.purpose" maxlength="512" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="特殊要求"><el-input v-model="form.requirements" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="寄送地址"><el-input v-model="form.shipAddress" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>
    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="ENG_SAMPLE" multiple />
      <AttachmentPanel v-else biz-type="ENG_SAMPLE" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.uom { margin-left: var(--erp-space-2); color: var(--erp-color-text-secondary); }
</style>
