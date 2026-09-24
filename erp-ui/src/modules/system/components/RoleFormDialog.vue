<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { DATA_SCOPE_OPTIONS, roleApi, type RoleSave } from '../api/role'

/** 角色表单（01-03 3.2，T2 中弹窗） */
const emit = defineEmits<{ saved: [] }>()
const visible = ref(false)
const saving = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInstance>()
const empty = (): RoleSave => ({ code: '', name: '', dataScope: 'SELF', customDeptIds: [], sort: 10 })
const form = ref<RoleSave>(empty())

const rules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_]{2,32}$/, message: '角色编码为 2～32 位字母、数字、下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }, { max: 32, message: '角色名称不能超过 32 个字', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
  customDeptIds: [{ validator: (_r, v: string[], cb) => (form.value.dataScope === 'CUSTOM' && !v?.length ? cb(new Error('请选择部门')) : cb()), trigger: 'change' }]
}

const title = computed(() => (editingId.value ? '编辑角色' : '新建角色'))

async function open(id?: string) {
  editingId.value = id
  if (id) {
    const d = await roleApi.get(id)
    form.value = { code: d.code, name: d.name, dataScope: d.dataScope, customDeptIds: d.customDeptIds, sort: d.sort, remark: d.remark, version: d.version }
  } else {
    form.value = empty()
  }
  visible.value = true
  formRef.value?.clearValidate()
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { ...form.value, code: form.value.code.toUpperCase(), customDeptIds: form.value.dataScope === 'CUSTOM' ? form.value.customDeptIds : [] }
    if (editingId.value) await roleApi.update(editingId.value, data)
    else await roleApi.create(data)
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
  <el-dialog v-model="visible" :title="title" width="640px" :close-on-click-modal="false" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="角色编码" prop="code">
        <el-input v-model="form.code" :disabled="!!editingId" maxlength="32" @input="form.code = String($event).toUpperCase()" />
      </el-form-item>
      <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" maxlength="32" /></el-form-item>
      <el-form-item label="数据范围" prop="dataScope">
        <el-select v-model="form.dataScope">
          <el-option v-for="o in DATA_SCOPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
        </el-select>
        <div class="form-tip">多个角色的数据范围取并集；主数据（物料、单位等）不受数据范围限制</div>
      </el-form-item>
      <el-form-item v-if="form.dataScope === 'CUSTOM'" label="自定义部门" prop="customDeptIds">
        <OrgTreeSelect v-model="form.customDeptIds" multiple placeholder="勾选父节点不会自动包含下级" />
      </el-form-item>
      <el-form-item label="排序" prop="sort"><el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>
