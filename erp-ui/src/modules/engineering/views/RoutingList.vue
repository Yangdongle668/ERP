<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { BOM_STATUS, BOM_STATUS_OPTIONS } from '../api/bom'
import { routingApi, type RoutingQuery, type RoutingRow } from '../api/routing'

defineOptions({ name: 'EngRoutingList' })

/** 工艺路线列表（需求 05-04 3.2，T1）：状态同 BOM，CLOSED 显示为“停用” */
const router = useRouter()
type Query = Omit<RoutingQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, RoutingRow>({
  api: (q) => routingApi.page(toParams(q) as RoutingQuery),
  defaultQuery: () => ({ statuses: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'], defaultOnly: true }),
  refreshOnActivated: true
})
function toParams(q: Query) {
  const { statuses, ...rest } = q
  return { ...rest, statuses: statuses?.length ? statuses.join(',') : undefined, defaultOnly: rest.defaultOnly || undefined }
}

const fields: SearchField[] = [
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'keyword', label: '编码/名称' },
  { prop: 'statuses', label: '状态', type: 'select', options: BOM_STATUS_OPTIONS, multiple: true },
  { prop: 'defaultOnly', label: '仅默认版本', type: 'slot' }
]
const columns: TableColumn<RoutingRow>[] = [
  { prop: 'materialCode', label: '物料编码', width: 130, type: 'link', onClick: (r) => router.push(`/engineering/routing/${r.id}`) },
  { prop: 'materialName', label: '名称', minWidth: 160 },
  { prop: 'materialSpec', label: '规格', minWidth: 160 },
  { prop: 'version', label: '版本', width: 110, slot: true },
  { prop: 'stepCount', label: '工序数', width: 70, align: 'right' },
  { prop: 'totalRunSeconds', label: '标准工时(秒)', width: 110, type: 'qty', precision: 2 },
  { prop: 'description', label: '说明', minWidth: 140 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: BOM_STATUS },
  { prop: 'updatedAt', label: '更新时间', width: 150, type: 'datetime' }
]

async function newVersion(r: RoutingRow) {
  const id = await routingApi.newVersion(r.id)
  ElMessage.success('已复制为新版本')
  router.push(`/engineering/routing/${id}/edit`)
}
async function act(r: RoutingRow, action: 'approve' | 'setDefault' | 'disable' | 'remove') {
  if (action === 'disable') await ElMessageBox.confirm(`停用后 ${r.docNo} 不能恢复。确定停用吗？`, '停用工艺路线', { type: 'warning' })
  await routingApi[action](r.id)
  ElMessage.success({ approve: '已审核', setDefault: '已设为默认版本', disable: '已停用', remove: '删除成功' }[action])
  load()
}
const asRow = (r: unknown) => r as RoutingRow
</script>

<template>
  <ErpPage description="产品按什么工序、在哪个工作中心、用多少工时生产；每个物料可有多个版本，默认版本用于新建生产订单">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" :types="['SEMI_FINISHED', 'FINISHED']" placeholder="全部" class="w200" /></template>
          <template #field-defaultOnly><el-switch v-model="query.defaultOnly" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.routing" :actions-width="200" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:routing:create'" type="primary" icon="Plus" @click="router.push('/engineering/routing/new')">新建工艺路线</el-button>
        </template>
        <template #col-version="{ row }">
          <span class="ver">V{{ asRow(row).version }}<ErpBadge v-if="asRow(row).isDefault" type="success">默认</ErpBadge></span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:routing:update', visible: asRow(row).status === 'DRAFT', handler: () => router.push(`/engineering/routing/${asRow(row).id}/edit`) },
            { label: '审核', permission: 'eng:routing:approve', visible: asRow(row).status === 'DRAFT', handler: () => act(asRow(row), 'approve') },
            { label: '新建版本', permission: 'eng:routing:create', visible: asRow(row).status === 'APPROVED' || asRow(row).status === 'CLOSED', handler: () => newVersion(asRow(row)) },
            { label: '设为默认', permission: 'eng:routing:set-default', visible: asRow(row).status === 'APPROVED' && !asRow(row).isDefault, handler: () => act(asRow(row), 'setDefault') },
            { label: '停用', permission: 'eng:routing:approve', visible: asRow(row).status === 'APPROVED' && !asRow(row).isDefault, handler: () => act(asRow(row), 'disable') },
            { label: '删除', permission: 'eng:routing:delete', danger: true, visible: asRow(row).status === 'DRAFT', confirm: `确定删除 ${asRow(row).docNo} 吗？`, handler: () => act(asRow(row), 'remove') }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.ver { display: inline-flex; align-items: center; gap: var(--erp-space-2); }
</style>
