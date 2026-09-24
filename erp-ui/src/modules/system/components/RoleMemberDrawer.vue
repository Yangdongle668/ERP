<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ENABLE_STATUS } from '@/components'
import { roleApi, type RoleMember, type RoleRow } from '../api/role'

/** 角色成员抽屉（01-03 3.4）：添加（UserSelect 多选）、移除（用户至少保留一个角色） */
const emit = defineEmits<{ changed: [] }>()
const visible = ref(false)
const role = ref<RoleRow>()
const members = ref<RoleMember[]>([])
const loading = ref(false)
const adding = ref<string[]>([])

async function open(r: RoleRow) {
  role.value = r
  adding.value = []
  visible.value = true
  await load()
}

async function load() {
  loading.value = true
  try {
    members.value = await roleApi.members(role.value!.id)
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!adding.value.length) return
  await roleApi.addMembers(role.value!.id, adding.value)
  ElMessage.success('已添加')
  adding.value = []
  await load()
  emit('changed')
}

async function remove(m: RoleMember) {
  await roleApi.removeMember(role.value!.id, m.userId)
  ElMessage.success('已移除')
  await load()
  emit('changed')
}

defineExpose({ open })
</script>

<template>
  <el-drawer v-model="visible" :title="`角色成员 - ${role?.name ?? ''}`" size="720px" append-to-body>
    <div v-if="!role?.builtin" v-perm="'system:role:update'" class="bar">
      <UserSelect v-model="adding" multiple placeholder="选择要添加的用户" class="users" />
      <el-button type="primary" icon="Plus" :disabled="!adding.length" @click="add">添加成员</el-button>
    </div>
    <el-table v-loading="loading" :data="members" border>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="deptName" label="主部门" min-width="160" />
      <el-table-column label="状态" width="80" align="center"><template #default="{ row }"><StatusTag :value="row.status" :map="ENABLE_STATUS" /></template></el-table-column>
      <el-table-column v-if="!role?.builtin" label="操作" width="90">
        <template #default="{ row }">
          <el-popconfirm title="确定移除该成员吗？" @confirm="remove(row as RoleMember)">
            <template #reference><el-button v-perm="'system:role:update'" link type="danger">移除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无成员" :image-size="60" /></template>
    </el-table>
  </el-drawer>
</template>

<style scoped>
.bar { display: flex; gap: 8px; margin-bottom: 12px; }
.users { flex: 1; }
</style>
