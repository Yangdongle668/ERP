<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { DATA_SCOPE_OPTIONS, roleApi, type RoleRow } from '../api/role'
import RoleFormDialog from '../components/RoleFormDialog.vue'
import RolePermissionDrawer from '../components/RolePermissionDrawer.vue'
import RoleMemberDrawer from '../components/RoleMemberDrawer.vue'
import type { PageParam } from '@/api/http'

defineOptions({ name: 'SystemRoleList' })

/** 角色列表（01-03 3.1） */
type RoleQuery = PageParam & { keyword?: string; status?: string }
const { query, list, total, loading, load, search, reset } = useListPage<RoleQuery, RoleRow>({ api: roleApi.page })
const formRef = ref<InstanceType<typeof RoleFormDialog>>()
const permRef = ref<InstanceType<typeof RolePermissionDrawer>>()
const memberRef = ref<InstanceType<typeof RoleMemberDrawer>>()

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]

const columns: TableColumn<RoleRow>[] = [
  { prop: 'code', label: '角色编码', width: 160 },
  { prop: 'name', label: '角色名称', width: 180, slot: true },
  { prop: 'dataScope', label: '数据范围', width: 130, type: 'enum', options: DATA_SCOPE_OPTIONS },
  { prop: 'userCount', label: '用户数', width: 80, align: 'right', slot: true },
  { prop: 'sort', label: '排序', width: 70, align: 'right' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS },
  { prop: 'remark', label: '备注', minWidth: 160 }
]

async function changeStatus(row: RoleRow, action: 'enable' | 'disable') {
  if (action === 'disable') {
    await ElMessageBox.confirm(`停用后拥有角色「${row.name}」的用户将失去该角色的权限。确定停用吗？`, '提示', { type: 'warning' })
  }
  await roleApi[action](row.id)
  ElMessage.success(action === 'enable' ? '已启用' : '已停用')
  load()
}

async function copy(row: RoleRow) {
  const id = await roleApi.copy(row.id)
  ElMessage.success('已复制，请修改名称和编码')
  await load()
  formRef.value?.open(id)
}

async function remove(row: RoleRow) {
  await roleApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

const asRole = (r: unknown) => r as RoleRow
</script>

<template>
  <ErpPage description="角色决定用户可以使用的菜单、按钮与数据范围；内置角色不可修改">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.role" :actions-width="240" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'system:role:create'" type="primary" icon="Plus" @click="formRef?.open()">新建角色</el-button>
        </template>
        <template #col-name="{ row }">
          <span class="name-cell">{{ asRole(row).name }}<ErpBadge v-if="asRole(row).builtin" :dot="false">内置</ErpBadge></span>
        </template>
        <template #col-userCount="{ row }">
          <el-link type="primary" underline="never" @click="memberRef?.open(asRole(row))">{{ asRole(row).userCount }}</el-link>
        </template>
        <template #actions="{ row }">
          <RowActions
            :actions="[
              { label: '编辑', permission: 'system:role:update', visible: !asRole(row).builtin, handler: () => formRef?.open(asRole(row).id) },
              { label: '功能权限', permission: 'system:role:grant', visible: !asRole(row).builtin, handler: () => permRef?.open(asRole(row)) },
              { label: '成员', permission: 'system:role:query', handler: () => memberRef?.open(asRole(row)) },
              { label: '复制', permission: 'system:role:create', handler: () => copy(asRole(row)) },
              { label: '停用', permission: 'system:role:update', visible: !asRole(row).builtin && asRole(row).status === 'ENABLED', handler: () => changeStatus(asRole(row), 'disable') },
              { label: '启用', permission: 'system:role:update', visible: !asRole(row).builtin && asRole(row).status === 'DISABLED', handler: () => changeStatus(asRole(row), 'enable') },
              { label: '删除', permission: 'system:role:delete', danger: true, visible: !asRole(row).builtin, confirm: `确定删除角色「${asRole(row).code} ${asRole(row).name}」吗？删除后不可恢复。`, handler: () => remove(asRole(row)) }
            ]"
          />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
    <RoleFormDialog ref="formRef" @saved="load" />
    <RolePermissionDrawer ref="permRef" />
    <RoleMemberDrawer ref="memberRef" @changed="load" />
  </ErpPage>
</template>

<style scoped>
.name-cell { display: inline-flex; align-items: center; gap: 8px; }
</style>
