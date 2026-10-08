<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { fetchBlob } from '@/api/http'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { orgApi, type OrgNode, type OrgSave } from '../api/org'

/** 组织表单（01-01 3.2，T2 大弹窗双列）；公司类型显示“公司信息”分组 */
const emit = defineEmits<{ saved: [] }>()
const visible = ref(false)
const saving = ref(false)
const editingId = ref<string>()
const parentIsDept = ref(false)
const formRef = ref<FormInstance>()
const empty = (): OrgSave => ({ parentId: undefined, orgType: 'DEPT', code: '', name: '', sort: 10 })
const form = ref<OrgSave>(empty())
let snapshot = ''

const rules: FormRules = {
  orgType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  code: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{2,32}$/, message: '编码格式不正确，2～32 位字母、数字、- _', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }, { max: 64, message: '名称不能超过 64 个字', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

const isCompany = computed(() => form.value.orgType === 'COMPANY')

// ---------- 公司 Logo：系统左上角、登录页、打印单据抬头共用 ----------
const logoPreview = ref('')
const logoUploading = ref(false)
function setPreview(url: string) {
  if (logoPreview.value.startsWith('blob:')) URL.revokeObjectURL(logoPreview.value)
  logoPreview.value = url
}
async function loadLogo(fileId?: string) {
  setPreview('')
  if (!fileId) return
  const blob = await fetchBlob(`/system/files/${fileId}/preview`).catch(() => undefined)
  if (blob) setPreview(URL.createObjectURL(blob))
}
async function onLogoPick(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !editingId.value) return
  if (!/\.(png|svg|jpe?g)$/i.test(file.name)) return ElMessage.warning('Logo 只支持 PNG、SVG、JPG 图片')
  if (file.size > 512 * 1024) return ElMessage.warning('Logo 图片不能超过 512KB')
  logoUploading.value = true
  try {
    const id = await orgApi.uploadLogo(editingId.value, file)
    form.value.logoFileId = String(id)
    snapshot = JSON.stringify({ ...JSON.parse(snapshot), logoFileId: form.value.logoFileId })
    setPreview(URL.createObjectURL(file))
    ElMessage.success('Logo 已更新，刷新页面后左上角显示新 Logo')
    emit('saved')
  } finally {
    logoUploading.value = false
  }
}
async function removeLogo() {
  if (!editingId.value) return
  await ElMessageBox.confirm('删除公司 Logo？打印单据和系统左上角将不再显示 Logo。', '提示', { type: 'warning' })
  await orgApi.removeLogo(editingId.value)
  form.value.logoFileId = undefined
  snapshot = JSON.stringify({ ...JSON.parse(snapshot), logoFileId: undefined })
  setPreview('')
  ElMessage.success('已删除')
  emit('saved')
}
onBeforeUnmount(() => setPreview(''))
const title = computed(() => (editingId.value ? '编辑组织' : '新建组织'))

async function open(opts: { id?: string; parent?: OrgNode; orgType?: 'COMPANY' | 'DEPT' }) {
  editingId.value = opts.id
  if (opts.id) {
    const d = await orgApi.get(opts.id)
    form.value = {
      parentId: d.parentId, orgType: d.orgType, code: d.code, name: d.name, shortName: d.shortName, leaderUserId: d.leaderUserId,
      phone: d.phone, address: d.address, nameEn: d.nameEn, addressEn: d.addressEn, taxNo: d.taxNo, logoFileId: d.logoFileId,
      sort: d.sort, remark: d.remark, version: d.version
    }
    parentIsDept.value = false
    loadLogo(d.logoFileId)
  } else {
    setPreview('')
    form.value = empty()
    form.value.parentId = opts.parent?.id
    parentIsDept.value = opts.parent?.orgType === 'DEPT'
    form.value.orgType = opts.orgType ?? 'DEPT'
    const siblings = opts.parent?.children ?? []
    form.value.sort = siblings.reduce((m, s) => Math.max(m, s.sort), 0) + 10
  }
  snapshot = JSON.stringify(form.value)
  visible.value = true
  formRef.value?.clearValidate()
}

async function beforeClose(done: () => void) {
  if (JSON.stringify(form.value) !== snapshot) {
    const ok = await ElMessageBox.confirm('有未保存的修改，确定关闭吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) return
  }
  done()
}

async function save(andNew = false) {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { ...form.value, code: form.value.code.trim().toUpperCase() }
    if (editingId.value) await orgApi.update(editingId.value, data)
    else await orgApi.create(data)
    ElMessage.success('保存成功')
    emit('saved')
    if (andNew && !editingId.value) {
      const keep = { parentId: form.value.parentId, orgType: form.value.orgType, sort: form.value.sort + 10 }
      form.value = { ...empty(), ...keep }
      snapshot = JSON.stringify(form.value)
      formRef.value?.clearValidate()
    } else {
      visible.value = false
    }
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="960px" :close-on-click-modal="false" :before-close="beforeClose" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="上级组织" prop="parentId">
            <OrgTreeSelect v-model="form.parentId" :exclude-id="editingId" placeholder="为空表示顶级（公司）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="orgType">
            <el-radio-group v-model="form.orgType" :disabled="!!editingId">
              <el-radio value="COMPANY" :disabled="parentIsDept">公司</el-radio>
              <el-radio value="DEPT">部门</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="编码" prop="code">
            <el-input v-model="form.code" maxlength="32" @input="form.code = String($event).toUpperCase()" />
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="简称"><el-input v-model="form.shortName" maxlength="32" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="负责人"><UserSelect v-model="form.leaderUserId" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" maxlength="32" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="排序" prop="sort"><el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" /></el-form-item>
        </el-col>
      </el-row>
      <template v-if="isCompany">
        <el-divider content-position="left">公司信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="英文名称"><el-input v-model="form.nameEn" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税号"><el-input v-model="form.taxNo" maxlength="32" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="地址"><el-input v-model="form.address" maxlength="256" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="英文地址"><el-input v-model="form.addressEn" maxlength="256" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="公司 Logo">
              <div class="logo">
                <div class="logo__box">
                  <img v-if="logoPreview" :src="logoPreview" alt="Logo">
                  <span v-else class="text-muted">未上传</span>
                </div>
                <div class="logo__side">
                  <template v-if="editingId">
                    <label class="el-button" :class="{ 'is-disabled': logoUploading }">
                      <input type="file" accept=".png,.svg,.jpg,.jpeg,image/png,image/svg+xml,image/jpeg" hidden :disabled="logoUploading" @change="onLogoPick">
                      {{ form.logoFileId ? '更换 Logo' : '上传 Logo' }}
                    </label>
                    <el-button v-if="form.logoFileId" link type="danger" @click="removeLogo">删除</el-button>
                  </template>
                  <div class="text-muted logo__tip">
                    {{ editingId ? '' : '保存公司后再上传 Logo。' }}PNG / SVG / JPG，≤ 512KB，建议横版透明底、高度 ≥ 120px；
                    用于系统左上角、登录页和打印单据抬头（针式打印为黑白）
                  </div>
                </div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </template>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="beforeClose(() => (visible = false))">取消</el-button>
      <el-button v-if="!editingId" :loading="saving" @click="save(true)">保存并新建</el-button>
      <el-button type="primary" :loading="saving" @click="save()">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.logo { display: flex; align-items: flex-start; gap: var(--erp-space-4); }
.logo__box { width: 200px; height: 72px; display: flex; align-items: center; justify-content: center; border: 1px dashed var(--erp-color-border);
  border-radius: var(--erp-radius-control); background: var(--erp-color-bg); }
.logo__box img { max-width: 184px; max-height: 60px; object-fit: contain; }
.logo__side { display: flex; flex-direction: column; align-items: flex-start; gap: var(--erp-space-2); }
.logo__tip { font-size: var(--erp-font-size-caption); line-height: 1.5; }
</style>
