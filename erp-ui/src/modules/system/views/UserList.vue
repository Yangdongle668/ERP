<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useBaseDataStore } from '@/stores/baseData'
import { useUserStore } from '@/stores/user'
import type { OrgNode } from '@/api/system'
import { userApi, userQueryParams, type UserQuery, type UserRow } from '../api/user'
import { roleApi, type RoleSimple } from '../api/role'
import ResetPasswordDialog from '../components/ResetPasswordDialog.vue'

defineOptions({ name: 'SystemUserList' })

/** 用户列表（01-02 3.1）：左侧组织树筛选（含下级），右侧列表 */
const router = useRouter()
const route = useRoute()
const baseData = useBaseDataStore()
const userStore = useUserStore()
const roles = ref<RoleSimple[]>([])
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const importRef = ref<{ open: () => void }>()
const resetRef = ref<InstanceType<typeof ResetPasswordDialog>>()
const orgKeyword = ref('')

const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<UserQuery, UserRow>({
  api: userApi.page,
  defaultQuery: () => ({ status: 'ENABLED', deptId: typeof route.query.deptId === 'string' ? route.query.deptId : undefined } as UserQuery)
})

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '用户名/姓名/工号/手机号' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] },
  { prop: 'roleId', label: '角色', type: 'slot' },
  { prop: 'position', label: '岗位', type: 'dict', dictType: 'sys_position' },
  { prop: 'lastLogin', label: '最近登录', type: 'daterange' }
]

const columns: TableColumn<UserRow>[] = [
  { prop: 'username', label: '用户名', width: 130, type: 'link', onClick: (r) => router.push(`/system/user/${r.id}/edit`) },
  { prop: 'realName', label: '姓名', width: 100 },
  { prop: 'employeeNo', label: '工号', width: 100 },
  { prop: 'deptName', label: '主部门', width: 160 },
  { prop: 'roleNames', label: '角色', minWidth: 200, formatter: (r) => r.roleNames.join('，') || '-' },
  { prop: 'position', label: '岗位', width: 100, type: 'dict', dictType: 'sys_position' },
  { prop: 'mobile', label: '手机号', width: 120 },
  { prop: 'status', label: '状态', width: 110, slot: true, align: 'center' },
  { prop: 'lastLoginAt', label: '最近登录', width: 150, type: 'datetime', sortable: true },
  { prop: 'createdAt', label: '创建时间', width: 150, type: 'datetime', sortable: true, hidden: true }
]

onMounted(async () => {
  roles.value = await roleApi.simple().catch(() => [])
  baseData.loadOrgTree()
})

function onNode(node: OrgNode) {
  query.deptId = query.deptId === node.id ? undefined : node.id
  search()
}

function onSort(s: { prop?: string; order?: string }) {
  query.sortField = s.prop
  query.sortOrder = s.order
  search()
}

const filterNode = (value: string, data: Record<string, any>) => !value || String(data.name).includes(value)
const treeRef = ref<{ filter: (v: string) => void }>()

async function changeStatus(row: UserRow, action: 'enable' | 'disable') {
  if (action === 'disable') {
    await ElMessageBox.confirm(`停用后用户「${row.realName}」将立即下线且不能登录。确定停用吗？`, '提示', { type: 'warning' })
  }
  await userApi[action](row.id)
  ElMessage.success(action === 'enable' ? '已启用' : '已停用')
  load()
}

async function batchDisable() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选数据')
    return
  }
  await ElMessageBox.confirm(`确定停用选中的 ${selection.value.length} 个用户吗？`, '提示', { type: 'warning' })
  const results = await userApi.batchDisable(selection.value.map((u) => u.id))
  const failed = results.filter((r) => !r.success)
  if (!failed.length) ElMessage.success(`操作成功 ${results.length} 条`)
  else {
    await ElMessageBox.alert(failed.map((f) => `${f.name}：${f.message}`).join('<br/>'), `成功 ${results.length - failed.length} 条，失败 ${failed.length} 条`,
      { dangerouslyUseHTMLString: true, type: 'warning' })
  }
  load()
}

async function unlock(row: UserRow) {
  await userApi.unlock(row.id)
  ElMessage.success('已解锁')
  load()
}

async function kick(row: UserRow) {
  await userApi.kick(row.id)
  ElMessage.success('已强制下线')
}

const asUser = (r: unknown) => r as UserRow
const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const isSelf = (row: UserRow) => row.id === userStore.user?.id
</script>

<template>
  <div class="user-page">
    <el-card class="org-panel">
      <el-input v-model="orgKeyword" placeholder="搜索部门" clearable prefix-icon="Search" @input="treeRef?.filter(orgKeyword)" />
      <el-scrollbar class="tree-scroll">
        <el-tree
          ref="treeRef"
          :data="baseData.orgTree.data ?? []"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          :current-node-key="query.deptId"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          @node-click="onNode"
        />
      </el-scrollbar>
    </el-card>
    <el-card class="main">
      <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
        <template #field-roleId>
          <el-select v-model="query.roleId" placeholder="全部" clearable class="w200">
            <el-option v-for="r in roles" :key="r.id" :value="r.id" :label="r.name" />
          </el-select>
        </template>
      </ErpSearchForm>
      <ErpTable
        ref="tableRef"
        :columns="columns"
        :data="list"
        :loading="loading"
        selection
        storage-key="system.user"
        :actions-width="230"
        @selection-change="onSelectionChange"
        @sort-change="onSort"
        @refresh="load"
      >
        <template #toolbar>
          <el-button v-perm="'system:user:create'" type="primary" icon="Plus" @click="router.push({ path: '/system/user/new', query: { deptId: query.deptId } })">新建</el-button>
          <el-tooltip :disabled="!!selection.length" content="请先勾选数据" placement="top">
            <span><el-button v-perm="'system:user:update'" :disabled="!selection.length" @click="batchDisable">批量停用</el-button></span>
          </el-tooltip>
        </template>
        <template #toolbar-right>
          <el-tooltip content="导入" placement="top"><el-button v-perm="'system:user:import'" icon="Upload" circle @click="importRef?.open()" /></el-tooltip>
          <ExportButton url="/system/users/export" :params="() => userQueryParams({ ...query })" :columns="exportColumns" filename="用户" permission="system:user:export" />
        </template>
        <template #col-status="{ row }">
          <StatusTag :value="asUser(row).status" :map="ENABLE_STATUS" />
          <el-tag v-if="asUser(row).locked" type="warning" class="locked">已锁定</el-tag>
        </template>
        <template #actions="{ row }">
          <RowActions
            :actions="[
              { label: '编辑', permission: 'system:user:update', handler: () => router.push(`/system/user/${asUser(row).id}/edit`) },
              { label: '重置密码', permission: 'system:user:reset-password', handler: () => resetRef?.open(asUser(row)) },
              { label: '停用', permission: 'system:user:update', visible: asUser(row).status === 'ENABLED' && !asUser(row).admin && !isSelf(asUser(row)), handler: () => changeStatus(asUser(row), 'disable') },
              { label: '启用', permission: 'system:user:update', visible: asUser(row).status === 'DISABLED', handler: () => changeStatus(asUser(row), 'enable') },
              { label: '解锁', permission: 'system:user:update', visible: asUser(row).locked, handler: () => unlock(asUser(row)) },
              { label: '强制下线', permission: 'system:user:update', visible: asUser(row).status === 'ENABLED' && !isSelf(asUser(row)), confirm: '强制下线后该用户需要重新登录，确定吗？', handler: () => kick(asUser(row)) }
            ]"
          />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </el-card>
    <ImportDialog ref="importRef" title="导入用户" base="/system/users" template-name="用户" @done="load" />
    <ResetPasswordDialog ref="resetRef" />
  </div>
</template>

<style scoped>
.user-page { display: flex; gap: 12px; align-items: flex-start; }
.org-panel { width: 240px; flex-shrink: 0; }
.org-panel + .main { margin-top: 0; }
.tree-scroll { height: calc(100vh - 220px); margin-top: 8px; }
.main { flex: 1; min-width: 0; }
.w200 { width: 200px; }
.locked { margin-left: 4px; }
</style>
