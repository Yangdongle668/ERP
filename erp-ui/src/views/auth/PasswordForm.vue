<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi, passwordChecks, type LoginResp, type PasswordPolicy } from '@/api/auth'
import { useUserStore } from '@/stores/user'

/** 修改密码表单：强制修改密码页和个人中心共用；实时显示密码规则检查项 */
const emit = defineEmits<{ changed: [resp: LoginResp] }>()
const store = useUserStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const policy = ref<PasswordPolicy>({ minLength: 8, complexity: 'LETTER_DIGIT', historyCount: 3 })
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

onMounted(async () => {
  const p = await authApi.passwordPolicy().catch(() => undefined)
  if (p) policy.value = p
})

const checks = computed(() => [
  ...passwordChecks(form.newPassword, policy.value, store.user?.username),
  { label: '两次输入一致', ok: !!form.newPassword && form.newPassword === form.confirm }
])

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (_r, v, cb) => (v !== form.newPassword ? cb(new Error('两次输入的密码不一致')) : cb()), trigger: 'blur' }
  ]
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const resp = await authApi.changePassword(form.oldPassword, form.newPassword)
    form.oldPassword = form.newPassword = form.confirm = ''
    formRef.value?.resetFields()
    emit('changed', resp)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="pwd-form" @submit.prevent="submit">
    <el-form-item label="原密码" prop="oldPassword"><el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
    <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
    <el-form-item label="确认新密码" prop="confirm"><el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" /></el-form-item>
    <el-form-item>
      <ul class="checks">
        <li v-for="c in checks" :key="c.label" :class="c.ok ? 'ok' : 'no'"><el-icon><component :is="c.ok ? 'Check' : 'Close'" /></el-icon>{{ c.label }}</li>
      </ul>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" native-type="submit" :loading="saving">确认修改</el-button>
      <slot name="extra-buttons" />
    </el-form-item>
  </el-form>
</template>

<style scoped>
.pwd-form { max-width: 480px; }
.checks { margin: 0; padding: 0; list-style: none; font-size: var(--erp-font-size-secondary); line-height: 1.8; }
.checks li { display: flex; align-items: center; gap: 6px; }
.ok { color: var(--el-color-success); }
.no { color: var(--erp-color-text-secondary); }
</style>
