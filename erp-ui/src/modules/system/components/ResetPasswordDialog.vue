<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, type UserRow } from '../api/user'

/** 重置密码（01-02 3.3）：随机生成时弹窗显示一次新密码，可复制 */
const visible = ref(false)
const saving = ref(false)
const user = ref<UserRow>()
const form = reactive({ mode: 'RANDOM' as 'RANDOM' | 'MANUAL', password: '', mustChangePassword: true })
const result = ref<string>()

function open(u: UserRow) {
  user.value = u
  Object.assign(form, { mode: 'RANDOM', password: '', mustChangePassword: true })
  result.value = undefined
  visible.value = true
}

async function submit() {
  if (form.mode === 'MANUAL' && !form.password) {
    ElMessage.warning('请输入新密码')
    return
  }
  saving.value = true
  try {
    const r = await userApi.resetPassword(user.value!.id, { ...form, password: form.mode === 'MANUAL' ? form.password : undefined })
    ElMessage.success('密码已重置，该用户需要重新登录')
    if (r.password) result.value = r.password
    else visible.value = false
  } finally {
    saving.value = false
  }
}

function copy() {
  navigator.clipboard?.writeText(result.value ?? '').then(() => ElMessage.success('已复制'))
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="`重置密码 - ${user?.realName ?? ''}`" width="480px" :close-on-click-modal="false" append-to-body>
    <template v-if="!result">
      <el-form label-width="120px">
        <el-form-item label="新密码方式">
          <el-radio-group v-model="form.mode">
            <el-radio value="RANDOM">随机生成</el-radio>
            <el-radio value="MANUAL">手工输入</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'MANUAL'" label="新密码" required>
          <el-input v-model="form.password" type="password" show-password maxlength="64" autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="下次登录修改密码"><el-switch v-model="form.mustChangePassword" /></el-form-item>
      </el-form>
    </template>
    <el-result v-else icon="success" title="密码已重置" sub-title="新密码只显示这一次，请复制后告知用户">
      <template #extra>
        <div class="pwd">{{ result }}</div>
        <el-button type="primary" icon="CopyDocument" @click="copy">复制</el-button>
      </template>
    </el-result>
    <template #footer>
      <template v-if="!result">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
      <el-button v-else @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.pwd { font-family: var(--erp-font-family-mono); font-size: var(--erp-font-size-page-title); margin-bottom: 12px; letter-spacing: 1px; }
</style>
