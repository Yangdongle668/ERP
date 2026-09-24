<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { download } from '@/api/http'
import { bomApi, BOM_STATUS, BOM_STATUS_OPTIONS, PARENT_TYPES, type BomQuery, type BomRow } from '../api/bom'

defineOptions({ name: 'EngBomList' })

/** BOM 列表（需求 05-03 4.1，T1） */
const router = useRouter()
const importRef = ref<{ open: () => void }>()

type Query = Omit<BomQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[] }

const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, BomRow>({
  api: (q) => bomApi.page(toParams(q) as BomQuery),
  defaultQuery: () => ({ statuses: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'], defaultOnly: true }),
  refreshOnActivated: true
})

function toParams(q: Query) {
  const { statuses, ...rest } = q
  return { ...rest, statuses: statuses?.length ? statuses.join(',') : undefined, defaultOnly: rest.defaultOnly || undefined }
}

const fields: SearchField[] = [
  { prop: 'materialId', label: '父件', type: 'slot' },
  { prop: 'keyword', label: '父件编码/名称', placeholder: '编码前缀或名称' },
  { prop: 'statuses', label: '状态', type: 'select', options: BOM_STATUS_OPTIONS, multiple: true },
  { prop: 'defaultOnly', label: '仅默认版本', type: 'slot' },
  { prop: 'componentId', label: '包含子件', type: 'slot' }
]

const columns: TableColumn<BomRow>[] = [
  { prop: 'materialCode', label: '父件编码', width: 130, type: 'link', onClick: (r) => router.push(`/engineering/bom/${r.id}`) },
  { prop: 'materialName', label: '父件名称', minWidth: 180 },
  { prop: 'materialSpec', label: '规格', minWidth: 180 },
  { prop: 'version', label: '版本', width: 110, slot: true },
  { prop: 'baseQty', label: '基数', width: 70, type: 'qty' },
  { prop: 'lineCount', label: '子件数', width: 70, align: 'right' },
  { prop: 'description', label: '说明', minWidth: 160 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: BOM_STATUS },
  { prop: 'updatedAt', label: '更新人/时间', width: 170, slot: true }
]

async function newVersion(r: BomRow) {
  const id = await bomApi.newVersion(r.id)
  ElMessage.success('已复制为新版本，请填写版本说明')
  router.push(`/engineering/bom/${id}/edit`)
}

async function act(r: BomRow, action: 'setDefault' | 'disable' | 'remove') {
  if (action === 'disable') await ElMessageBox.confirm(`停用后 ${r.docNo} 不能恢复，需要时可通过“新建版本”复制。确定停用吗？`, '停用 BOM', { type: 'warning' })
  await bomApi[action](r.id)
  ElMessage.success(action === 'remove' ? '删除成功' : action === 'setDefault' ? '已设为默认版本' : '已停用')
  load()
}

// ---------- 导出 ----------
async function exportBom(mode: 'SINGLE' | 'MULTI') {
  const ids = selection.value.map((r) => r.id)
  if (mode === 'MULTI' && !ids.length) return ElMessage.warning('多级展开导出请先勾选 BOM')
  const r = await download<{ async?: boolean }>('/engineering/boms/export', { ...toParams({ ...query }), pageNo: undefined, pageSize: undefined, mode, ids: ids.join(',') || undefined },
    `${mode === 'MULTI' ? 'BOM多级展开' : 'BOM'}.xlsx`)
  if (r?.async) ElMessage.info('数据量较大，已转为后台导出，完成后可在任务中心下载')
}

// ---------- 导入 ----------
const importSubmit = ref(false)
const importParams = computed(() => ({ submit: String(importSubmit.value) }))

const asRow = (r: unknown) => r as BomRow
</script>

<template>
  <ErpPage description="父件由哪些子件、各多少组成；每个父件可有多个版本，同一时间只有一个默认版本被 MRP、生产和成本使用">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" :types="PARENT_TYPES" placeholder="全部" class="w200" /></template>
          <template #field-defaultOnly><el-switch v-model="query.defaultOnly" /></template>
          <template #field-componentId><MaterialSelect v-model="query.componentId" placeholder="全部" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="eng.bom" :actions-width="200"
                @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:bom:create'" type="primary" icon="Plus" @click="router.push('/engineering/bom/new')">新建 BOM</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Upload" tooltip="导入" permission="eng:bom:import" @click="importRef?.open()" />
          <el-dropdown v-perm="'eng:bom:export'" trigger="click" @command="exportBom">
            <ErpIconButton icon="Download" tooltip="导出" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="SINGLE">导出单层{{ selection.length ? `（勾选 ${selection.length} 个）` : '（按查询条件）' }}</el-dropdown-item>
                <el-dropdown-item command="MULTI">导出多级展开（勾选行）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template #col-version="{ row }">
          <span class="ver">V{{ asRow(row).version }}<ErpBadge v-if="asRow(row).isDefault" type="success">默认</ErpBadge></span>
        </template>
        <template #col-updatedAt="{ row }">
          <span>{{ asRow(row).updatedByName ?? '-' }}</span> <span class="text-muted">{{ asRow(row).updatedAt?.slice(0, 16) }}</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:bom:update', visible: asRow(row).status === 'DRAFT', handler: () => router.push(`/engineering/bom/${asRow(row).id}/edit`) },
            { label: '新建版本', permission: 'eng:bom:create', visible: asRow(row).status === 'APPROVED' || asRow(row).status === 'CLOSED', handler: () => newVersion(asRow(row)) },
            { label: '设为默认', permission: 'eng:bom:set-default', visible: asRow(row).status === 'APPROVED' && !asRow(row).isDefault, handler: () => act(asRow(row), 'setDefault') },
            { label: '停用', permission: 'eng:bom:disable', visible: asRow(row).status === 'APPROVED' && !asRow(row).isDefault, handler: () => act(asRow(row), 'disable') },
            { label: '删除', permission: 'eng:bom:delete', danger: true, visible: asRow(row).status === 'DRAFT', confirm: `确定删除 ${asRow(row).docNo} 吗？删除后不可恢复。`, handler: () => act(asRow(row), 'remove') }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:bom:create'" icon="Plus" @click="router.push('/engineering/bom/new')">新建 BOM</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <ImportDialog ref="importRef" title="导入 BOM" base="/engineering/boms" template-name="BOM" :params="importParams" allow-partial @done="load">
      <template #options>
        <div class="import-tip">同一父件编码的连续行组成一个 BOM，导入为该父件的新草稿版本。</div>
        <el-checkbox v-perm="'eng:bom:submit'" v-model="importSubmit">导入后提交审核</el-checkbox>
      </template>
    </ImportDialog>
  </ErpPage>
</template>

<style scoped>
.ver { display: inline-flex; align-items: center; gap: var(--erp-space-2); }
.import-tip { margin-bottom: var(--erp-space-2); color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
</style>
