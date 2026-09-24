<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PasswordForm from '../auth/PasswordForm.vue'
import { authApi, type LoginResp } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { orEmpty } from '@/utils/format'

/** 个人中心（01-13 3.3）：基本信息（可改手机号、邮箱、语言）、修改密码 */
const store = useUserStore()
const route = useRoute()
const tab = ref(route.query.tab === 'password' ? 'password' : 'info')
watch(() => route.query.tab, (t) => (tab.value = t === 'password' ? 'password' : 'info'))

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ mobile: store.user?.mobile ?? '', email: store.user?.email ?? '', language: store.user?.language ?? 'zh-CN' })
const rules: FormRules = {
  mobile: [{ pattern: /^(1\d{10}|\+\d{6,20})$/, message: '手机号格式不正确，应为 11 位手机号或 + 开头的国际号码', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await authApi.updateProfile({ mobile: form.mobile || undefined, email: form.email || undefined, language: form.language })
    await store.loadUser()
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

function onPasswordChanged(resp: LoginResp) {
  // 其他会话已失效，当前会话使用新签发的令牌继续
  store.applyLogin(resp)
  ElMessage.success('密码修改成功，其他设备上的登录已失效')
}
</script>

<template>
  <el-card>
    <el-tabs v-model="tab">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ store.user?.realName }}</el-descriptions-item>
          <el-descriptions-item label="工号">{{ orEmpty(store.user?.employeeNo) }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ orEmpty(store.user?.deptName) }}</el-descriptions-item>
          <el-descriptions-item label="岗位"><DictTag type="sys_position" :value="store.user?.position" plain /></el-descriptions-item>
          <el-descriptions-item label="角色">{{ store.user?.roleNames?.join('、') || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="form">
          <el-form-item label="手机号" prop="mobile"><el-input v-model="form.mobile" maxlength="20" /></el-form-item>
          <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="128" /></el-form-item>
          <el-form-item label="界面语言">
            <el-radio-group v-model="form.language">
              <el-radio value="zh-CN">中文</el-radio>
              <el-radio value="en">English</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item><el-button type="primary" :loading="saving" @click="save">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="修改密码" name="password">
        <PasswordForm @changed="onPasswordChanged" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<style scoped>
.desc { max-width: 800px; margin-bottom: 20px; }
.form { max-width: 480px; }
</style>
