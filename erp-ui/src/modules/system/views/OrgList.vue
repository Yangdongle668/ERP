<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useBaseDataStore } from '@/stores/baseData'
import { orgApi, type OrgNode } from '../api/org'
import OrgFormDialog from '../components/OrgFormDialog.vue'

defineOptions({ name: 'SystemOrgList' })

/** 组织架构（01-01，T6 树形表格） */
const router = useRouter()
const baseData = useBaseDataStore()
const query = reactive<{ keyword?: string; status?: string }>({})
const list = ref<OrgNode[]>([])
const loading = ref(false)
const expandAll = ref(true)
const tableKey = ref(0)
const formRef = ref<InstanceType<typeof OrgFormDialog>>()

const fields: SearchField[] = [
  { prop: 'keyword', label: '名称/编码' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]

const columns: TableColumn<OrgNode>[] = [
  { prop: 'name', label: '名称', minWidth: 280, slot: true },
  { prop: 'code', label: '编码', width: 140 },
  { prop: 'orgType', label: '类型', width: 80, type: 'enum', options: [{ value: 'COMPANY', label: '公司' }, { value: 'DEPT', label: '部门' }], align: 'center' },
  { prop: 'leaderName', label: '负责人', width: 120 },
  { prop: 'userCount', label: '人数', width: 80, align: 'right', slot: true },
  { prop: 'sort', label: '排序', width: 70, align: 'right' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS }
]

async function load() {
  loading.value = true
  try {
    list.value = await orgApi.tree({ ...query })
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = undefined
  query.status = undefined
  load()
}

function toggleExpand() {
  expandAll.value = !expandAll.value
  tableKey.value++
}

async function saved() {
  await load()
  baseData.loadOrgTree(true)
}

async function changeStatus(row: OrgNode, action: 'enable' | 'disable') {
  if (action === 'disable') {
    await ElMessageBox.confirm(`停用后组织「${row.name}」不能在新单据中使用，已有单据不受影响。确定停用吗？`, '提示', { type: 'warning' })
  }
  await orgApi[action](row.id)
  ElMessage.success(action === 'enable' ? '已启用' : '已停用')
  saved()
}

async function remove(row: OrgNode) {
  await orgApi.remove(row.id)
  ElMessage.success('删除成功')
  saved()
}

const hasCompany = computed(() => list.value.length > 0)
const asOrg = (r: unknown) => r as OrgNode

onMounted(load)
</script>

<template>
  <el-card>
    <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="load" @reset="reset" />
    <ErpTable
      :key="tableKey"
      :columns="columns"
      :data="list"
      :loading="loading"
      :tree-props="{ children: 'children' }"
      :default-expand-all="expandAll"
      :actions-width="220"
      storage-key="system.org"
      @refresh="load"
    >
      <template #toolbar>
        <el-button v-perm="'system:org:create'" type="primary" icon="Plus" @click="formRef?.open({ orgType: 'COMPANY' })">新建公司</el-button>
        <el-button :icon="expandAll ? 'Fold' : 'Expand'" @click="toggleExpand">{{ expandAll ? '收起全部' : '展开全部' }}</el-button>
      </template>
      <template #col-name="{ row }">
        <el-icon class="org-icon"><component :is="asOrg(row).orgType === 'COMPANY' ? 'OfficeBuilding' : 'Folder'" /></el-icon>{{ asOrg(row).name }}
      </template>
      <template #col-userCount="{ row }">
        <el-link v-if="asOrg(row).userCount" type="primary" underline="never" @click="router.push({ path: '/system/user', query: { deptId: asOrg(row).id } })">{{ asOrg(row).userCount }}</el-link>
        <span v-else>0</span>
      </template>
      <template #actions="{ row }">
        <RowActions
          :actions="[
            { label: '新增下级', permission: 'system:org:create', visible: asOrg(row).status === 'ENABLED', handler: () => formRef?.open({ parent: asOrg(row) }) },
            { label: '编辑', permission: 'system:org:update', handler: () => formRef?.open({ id: asOrg(row).id }) },
            { label: '停用', permission: 'system:org:update', visible: asOrg(row).status === 'ENABLED', handler: () => changeStatus(asOrg(row), 'disable') },
            { label: '启用', permission: 'system:org:update', visible: asOrg(row).status === 'DISABLED', handler: () => changeStatus(asOrg(row), 'enable') },
            { label: '删除', permission: 'system:org:delete', danger: true, confirm: `确定删除组织「${asOrg(row).code} ${asOrg(row).name}」吗？删除后不可恢复。`, handler: () => remove(asOrg(row)) }
          ]"
        />
      </template>
    </ErpTable>
    <el-empty v-if="!loading && !hasCompany" description="暂无组织" />
  </el-card>
  <OrgFormDialog ref="formRef" @saved="saved" />
</template>

<style scoped>
.org-icon { margin-right: 4px; vertical-align: -2px; color: var(--el-color-primary); }
</style>
