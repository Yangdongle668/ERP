<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { customerApi, followupApi, oppApi, type Contact, type FollowupRow, type FollowupSave, type OppRow } from '../api/crm'

/** 跟进表单（需求 03-04 3.2，T2 大弹窗）。open(row) 编辑，open(undefined, { customerId }) 新增并固定客户 */
const emit = defineEmits<{ saved: [] }>()
const visible = ref(false)
const saving = ref(false)
const editing = ref<FollowupRow>()
const fixedCustomer = ref(false)
const formRef = ref<FormInstance>()
const form = ref<FollowupSave>({ followupType: 'PHONE', followupAt: '', subject: '', content: '', fileIds: [] })
const contacts = ref<Contact[]>([])
const opps = ref<OppRow[]>([])

const pad = (n: number) => String(n).padStart(2, '0')
function nowText() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
const todayText = () => nowText().slice(0, 10)

const rules = computed<FormRules>(() => ({
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  followupType: [{ required: true, message: '请选择跟进方式', trigger: 'change' }],
  followupAt: [{ required: true, message: '请选择跟进时间', trigger: 'change' }, {
    validator: (_r, v, cb) => (v && v > nowText() ? cb(new Error('跟进时间不能晚于当前时间')) : cb()), trigger: 'change'
  }],
  subject: [{ required: true, message: '请填写主题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写内容', trigger: 'blur' }],
  nextFollowupAt: [{ validator: (_r, v, cb) => (v && v < todayText() ? cb(new Error('下次跟进日期不能早于今天')) : cb()), trigger: 'change' }]
}))

async function loadRefs(customerId?: string) {
  contacts.value = []
  opps.value = []
  if (!customerId) return
  const [d, o] = await Promise.all([customerApi.get(customerId).catch(() => undefined), oppApi.page({ customerId, pageNo: 1, pageSize: 100 }).catch(() => undefined)])
  contacts.value = d?.contacts.filter((c) => c.status !== 'LEFT') ?? []
  opps.value = o?.list ?? []
}

async function onCustomer(v?: string | string[]) {
  form.value.contactId = undefined
  form.value.opportunityId = undefined
  await loadRefs(typeof v === 'string' ? v : undefined)
}

async function open(row?: FollowupRow, preset: { customerId?: string; opportunityId?: string } = {}) {
  editing.value = row
  fixedCustomer.value = !!row || !!preset.customerId
  form.value = row
    ? { customerId: row.customerId, contactId: row.contactId, opportunityId: row.opportunityId, followupType: row.followupType, followupAt: row.followupAt,
        subject: row.subject, content: row.content, nextFollowupAt: row.nextFollowupAt, nextPlan: row.nextPlan, fileIds: [], version: row.version }
    : { customerId: preset.customerId, opportunityId: preset.opportunityId, followupType: 'PHONE', followupAt: nowText(), subject: '', content: '', fileIds: [] }
  visible.value = true
  await loadRefs(form.value.customerId)
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { ...form.value, subject: form.value.subject.trim(), content: form.value.content.trim(), nextPlan: form.value.nextPlan?.trim() || undefined }
    if (editing.value) await followupApi.update(editing.value.id, data)
    else await followupApi.create(data)
    ElMessage.success('保存成功')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="editing ? '编辑跟进' : '新增跟进'" width="760px" :close-on-click-modal="false" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="客户" prop="customerId">
            <CustomerSelect v-model="form.customerId" :disabled="fixedCustomer" @update:model-value="onCustomer" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系人">
            <el-select v-model="form.contactId" clearable placeholder="该客户联系人" class="w-full">
              <el-option v-for="c in contacts" :key="c.id" :value="c.id!" :label="c.title ? `${c.name}（${c.title}）` : c.name" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="跟进方式" prop="followupType"><DictSelect v-model="form.followupType" type="crm_followup_type" :clearable="false" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="跟进时间" prop="followupAt">
            <el-date-picker v-model="form.followupAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联商机">
            <el-select v-model="form.opportunityId" clearable placeholder="该客户进行中的商机" class="w-full">
              <el-option v-for="o in opps" :key="o.id" :value="o.id" :label="o.name" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24"><el-form-item label="主题" prop="subject"><el-input v-model="form.subject" maxlength="128" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="6" maxlength="4000" show-word-limit /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="下次跟进" prop="nextFollowupAt"><el-date-picker v-model="form.nextFollowupAt" type="date" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="下次计划"><el-input v-model="form.nextPlan" maxlength="512" /></el-form-item></el-col>
        <el-col :span="24">
          <el-form-item label="附件">
            <AttachmentUpload v-if="!editing" v-model="form.fileIds" biz-type="CRM_FOLLOWUP" multiple />
            <AttachmentPanel v-else biz-type="CRM_FOLLOWUP" :biz-id="editing.id" editable />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>
