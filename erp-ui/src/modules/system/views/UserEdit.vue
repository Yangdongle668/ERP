<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { userApi, type UserSave } from '../api/user'
import { roleApi, type RoleSimple } from '../api/role'

/** 新建/编辑用户（01-02 3.2，T3 独立表单页） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const roles = ref<RoleSimple[]>([])
const isAdminUser = ref(false)
const passwordMode = ref<'PARAM' | 'MANUAL'>('PARAM')
const empty = (): UserSave => ({
  username: '', realName: '', gender: 'UNKNOWN', language: 'zh-CN', partDeptIds: [], roleIds: [],
  deptId: typeof route.query.deptId === 'string' ? route.query.deptId : undefined, mustChangePassword: true
})
const form = ref<UserSave>(empty())
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9._-]{3,31}$/, message: '用户名格式不正确，4～32 位，字母开头，只能包含字母、数字、. _ -', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  mobile: [{ pattern: /^(1\d{10}|\+\d{6,20})$/, message: '手机号格式不正确，应为 11 位手机号或 + 开头的国际号码', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择主部门', trigger: 'change' }],
  roleIds: [{ type: 'array', required: true, min: 1, message: '请至少分配一个角色', trigger: 'change' }]
}

/** 只有超级管理员能看到并分配“超级管理员”角色（SYS-USR-R06） */
const visibleRoles = computed(() => roles.value.filter((r) => r.code !== 'SUPER_ADMIN' || me.hasPermission('*')))

onMounted(async () => {
  roles.value = await roleApi.simple()
  if (id.value) {
    const d = await userApi.get(id.value)
    isAdminUser.value = d.admin
    form.value = {
      username: d.username, realName: d.realName, employeeNo: d.employeeNo, gender: d.gender, mobile: d.mobile, email: d.email,
      position: d.position, language: d.language, remark: d.remark, deptId: d.deptId, partDeptIds: d.partDeptIds,
      superiorUserId: d.superiorUserId, roleIds: d.roleIds, version: d.version
    }
    tabs.setTitle(tabKeyOf(route), `编辑用户 ${d.username}`)
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push('/system/user')
}

async function save(andNew = false) {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (id.value && me.user?.id === id.value && !me.hasPermission('*')) {
    const ok = await ElMessageBox.confirm('修改后你可能失去用户管理权限，确定吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) return
  }
  saving.value = true
  try {
    const data = { ...form.value, username: form.value.username.trim().toLowerCase(), password: passwordMode.value === 'MANUAL' ? form.value.password : undefined }
    if (id.value) {
      await userApi.update(id.value, data)
      ElMessage.success('保存成功')
      guard.markClean()
      await back()
    } else {
      const r = await userApi.create(data)
      guard.markClean()
      await ElMessageBox.alert(`用户已创建。初始密码：<b style="font-family:monospace;font-size:16px">${r.initPassword}</b><br/>该密码只显示这一次，请告知用户。`,
        '保存成功', { dangerouslyUseHTMLString: true, confirmButtonText: '复制并关闭', callback: () => navigator.clipboard?.writeText(r.initPassword) })
      if (andNew) {
        form.value = { ...empty(), deptId: form.value.deptId, roleIds: form.value.roleIds }
        guard.markClean()
        formRef.value?.clearValidate()
      } else {
        await back()
      }
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="id ? `编辑用户 ${form.username}` : '新建用户'" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button v-if="!id" :loading="saving" @click="save(true)">保存并新建</el-button>
      <el-button type="primary" :loading="saving" @click="save()">保存</el-button>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="erp-stack">
      <ErpPanel title="基本信息">
        <el-row :gutter="16">
          <el-col :xl="8" :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="!!id" maxlength="32" autocomplete="off" @input="form.username = String($event).toLowerCase()" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="32" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="工号" prop="employeeNo"><el-input v-model="form.employeeNo" maxlength="32" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="form.gender">
                <el-radio value="MALE">男</el-radio><el-radio value="FEMALE">女</el-radio><el-radio value="UNKNOWN">未知</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="手机号" prop="mobile"><el-input v-model="form.mobile" maxlength="21" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="岗位"><DictSelect v-model="form.position" type="sys_position" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="语言">
              <el-select v-model="form.language"><el-option value="zh-CN" label="中文" /><el-option value="en" label="English" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
      <ErpPanel title="组织与权限">
        <el-row :gutter="16">
          <el-col :xl="8" :span="12"><el-form-item label="主部门" prop="deptId"><OrgTreeSelect v-model="form.deptId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="兼职部门"><OrgTreeSelect v-model="form.partDeptIds" multiple placeholder="最多 10 个" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="直属上级"><UserSelect v-model="form.superiorUserId" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="角色" prop="roleIds">
              <el-checkbox-group v-model="form.roleIds" :disabled="isAdminUser && !me.hasPermission('*')">
                <el-checkbox v-for="r in visibleRoles" :key="r.id" :value="r.id">{{ r.name }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
          <template v-if="!id">
            <el-col :xl="8" :span="12">
              <el-form-item label="初始密码">
                <el-radio-group v-model="passwordMode">
                  <el-radio value="PARAM">系统生成</el-radio>
                  <el-radio value="MANUAL">手工输入</el-radio>
                </el-radio-group>
                <div class="form-tip">系统生成：取参数“用户初始密码”，为空时随机生成；保存后显示一次</div>
              </el-form-item>
            </el-col>
            <el-col v-if="passwordMode === 'MANUAL'" :xl="8" :span="12">
              <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password maxlength="64" autocomplete="new-password" /></el-form-item>
            </el-col>
            <el-col :xl="8" :span="12">
              <el-form-item label="下次登录改密"><el-switch v-model="form.mustChangePassword" /></el-form-item>
            </el-col>
          </template>
        </el-row>
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>
