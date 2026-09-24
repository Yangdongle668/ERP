<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { fetchBlob } from '@/api/http'
import { formatQty } from '@/utils/format'
import { categoryApi, type CategorySimple } from '../api/category'
import {
  materialApi, MATERIAL_STATUS, MATERIAL_STATUS_OPTIONS, MATERIAL_TYPE_OPTIONS, SOURCE_TYPE_OPTIONS, TRACKING_OPTIONS,
  type BatchResult, type Material, type MaterialQuery, type MaterialSettings
} from '../api/material'

defineOptions({ name: 'EngMaterialList' })

/**
 * 物料列表（需求 05-02 3.1，T1 + 左侧类别树）：前端列表页的参考实现。
 * ErpPage → erp-split（左侧 ErpPanel 树 + 右侧 ErpPanel：filter 插槽放 ErpSearchForm → ErpTable → ErpPagination）。
 * 样式只用公共组件与 token，不在页面里写颜色和字号。
 */
const route = useRoute()
const router = useRouter()
const settings = ref<MaterialSettings>({ enableApproval: false, manualCodeAllowed: true, duplicateCheck: 'WARN', canViewCost: false })
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const importRef = ref<{ open: () => void }>()

type Query = Omit<MaterialQuery, 'pageNo' | 'pageSize' | 'types'> & { types?: string[]; created?: [string, string] }

const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, Material>({
  api: (q) => materialApi.page(toParams(q) as MaterialQuery),
  defaultQuery: () => ({ categoryId: typeof route.query.categoryId === 'string' ? route.query.categoryId : undefined }),
  refreshOnActivated: true
})

function toParams(q: Query) {
  const { types, created, ...rest } = q
  return { ...rest, types: types?.length ? types.join(',') : undefined, createdFrom: created?.[0], createdTo: created?.[1] }
}

const fields: SearchField[] = [
  { prop: 'code', label: '编码', placeholder: '编码前缀', upper: true },
  { prop: 'name', label: '名称/规格', placeholder: '名称或规格关键字' },
  { prop: 'types', label: '物料类型', type: 'select', options: MATERIAL_TYPE_OPTIONS, multiple: true },
  { prop: 'status', label: '状态', type: 'select', options: MATERIAL_STATUS_OPTIONS },
  { prop: 'mpn', label: '制造商料号' },
  { prop: 'sourceType', label: '取得方式', type: 'select', options: SOURCE_TYPE_OPTIONS },
  { prop: 'buyerId', label: '采购员', type: 'user' },
  { prop: 'tracking', label: '库存管理', type: 'select', options: TRACKING_OPTIONS },
  { prop: 'created', label: '创建时间', type: 'datetimerange' }
]

const columns = computed<TableColumn<Material>[]>(() => [
  { prop: 'imageFileId', label: '图片', width: 60, align: 'center', slot: true, hidden: true },
  { prop: 'code', label: '编码', width: 130, type: 'link', sortable: true, onClick: (r) => router.push(`/engineering/material/${r.id}`) },
  { prop: 'name', label: '名称', minWidth: 180, sortable: true },
  { prop: 'spec', label: '规格', minWidth: 200 },
  { prop: 'categoryName', label: '类别', width: 120 },
  { prop: 'materialType', label: '类型', width: 80, type: 'enum', options: MATERIAL_TYPE_OPTIONS },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'sourceType', label: '取得方式', width: 80, type: 'enum', options: SOURCE_TYPE_OPTIONS },
  { prop: 'mpn', label: '制造商料号', width: 140, hidden: true },
  { prop: 'brand', label: '品牌', width: 100, hidden: true },
  { prop: 'tracking', label: '库存管理', width: 80, type: 'enum', options: TRACKING_OPTIONS, hidden: true },
  ...(settings.value.canViewCost ? [{ prop: 'standardCost', label: '标准成本', width: 100, type: 'price', hidden: true } as TableColumn<Material>] : []),
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: MATERIAL_STATUS },
  { prop: 'updatedAt', label: '更新时间', width: 150, type: 'datetime', sortable: true }
])

function onSort(s: { prop?: string; order?: 'asc' | 'desc' }) {
  query.sortField = s.prop
  query.sortOrder = s.order
  search()
}

// ---------- 类别树 ----------
const tree = ref<CategorySimple[]>([])
const treeKeyword = ref('')
const treeRef = ref<{ filter: (v: string) => void; setCurrentKey: (k?: string) => void }>()
const filterNode = (v: string, data: CategorySimple) => !v || data.name.includes(v) || data.code.includes(v.toUpperCase())
const selectedCategory = ref<CategorySimple>()
const filterFn = (v: string, d: unknown) => filterNode(v, d as CategorySimple)
const onNodeClick = (d: unknown) => onNode(d as CategorySimple)

function onNode(n: CategorySimple) {
  if (query.categoryId === n.id) {
    query.categoryId = undefined
    selectedCategory.value = undefined
    treeRef.value?.setCurrentKey(undefined)
  } else {
    query.categoryId = n.id
    selectedCategory.value = n
  }
  search()
}

function clearCategory() {
  query.categoryId = undefined
  selectedCategory.value = undefined
  treeRef.value?.setCurrentKey(undefined)
  search()
}

// ---------- 操作 ----------
function create() {
  const c = selectedCategory.value
  router.push({ path: '/engineering/material/new', query: c?.leaf ? { categoryId: c.id } : {} })
}

async function enable(row: Material) {
  const status = await materialApi.enable(row.id)
  ElMessage.success(status === 'PENDING' ? '已提交启用审批' : '已启用')
  load()
}

/** 停用前提示引用情况（R08，不阻止） */
async function disable(row: Material) {
  const r = await materialApi.references(row.id)
  const parts: string[] = []
  if (Number(r.stockQty) > 0) parts.push(`当前有库存 ${formatQty(r.stockQty)}`)
  if (r.openDocCount) parts.push(`未完成单据 ${r.openDocCount} 张`)
  if (r.bomCount) parts.push(`被 ${r.bomCount} 个 BOM 使用`)
  const prefix = parts.length ? `该物料${parts.join('，')}。` : ''
  await ElMessageBox.confirm(`${prefix}停用后物料「${row.code}」不能在新单据中使用，确定停用吗？`, '停用物料', { type: 'warning' })
  await materialApi.disable(row.id)
  ElMessage.success('已停用')
  load()
}

async function remove(row: Material) {
  await materialApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function batch(action: 'enable' | 'disable') {
  const rows = selection.value.filter((r) => (action === 'enable' ? r.status === 'DRAFT' || r.status === 'DISABLED' : r.status === 'ENABLED'))
  if (!rows.length) return ElMessage.warning(action === 'enable' ? '请勾选草稿或停用的物料' : '请勾选启用的物料')
  if (action === 'disable') {
    await ElMessageBox.confirm(`确定停用选中的 ${rows.length} 个物料吗？停用后不能在新单据中使用。`, '批量停用', { type: 'warning' })
  }
  const r: BatchResult = action === 'enable' ? await materialApi.batchEnable(rows.map((x) => x.id)) : await materialApi.batchDisable(rows.map((x) => x.id))
  if (r.failures.length) {
    await ElMessageBox.alert(
      `<p>成功 ${r.success} 个，失败 ${r.failures.length} 个：</p><ul>${r.failures.map((f) => `<li>${f.code}：${f.message}</li>`).join('')}</ul>`,
      '批量操作结果', { dangerouslyUseHTMLString: true })
  } else {
    ElMessage.success(`已${action === 'enable' ? '启用' : '停用'} ${r.success} 个物料`)
  }
  load()
}

// ---------- 导入 ----------
const importMode = ref<'SKIP' | 'UPDATE'>('SKIP')
const importEnable = ref(false)
const importParams = computed(() => ({ mode: importMode.value, enable: String(importEnable.value) }))

// ---------- 图片 ----------
async function preview(id: string) {
  const url = URL.createObjectURL(await fetchBlob(`/system/files/${id}/preview`))
  window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))
const asMaterial = (row: unknown) => row as Material
const enableLabel = (r: Material) => (settings.value.enableApproval && r.status === 'DRAFT' ? '提交启用' : '启用')

onMounted(async () => {
  settings.value = await materialApi.settings().catch(() => settings.value)
  tree.value = await categoryApi.simpleTree().catch(() => [])
})
</script>

<template>
  <ErpPage description="物料主数据：基本信息、单位换算、计划、采购、库存、质量、财务属性；草稿可删除，启用后才能在单据中使用">
    <div class="erp-split">
      <ErpPanel title="物料类别" class="tree-panel" flush>
        <template #extra>
          <el-button v-if="query.categoryId" link type="primary" @click="clearCategory">全部</el-button>
        </template>
        <div class="tree-search">
          <el-input v-model="treeKeyword" placeholder="搜索类别" clearable prefix-icon="Search" @input="treeRef?.filter(treeKeyword)" />
        </div>
        <el-scrollbar class="tree-scroll">
          <el-tree
            ref="treeRef"
            :data="tree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            :current-node-key="query.categoryId"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            :filter-node-method="filterFn"
            @node-click="onNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node"><span>{{ data.name }}</span><span class="tree-code">{{ data.code }}</span></span>
            </template>
          </el-tree>
        </el-scrollbar>
      </ErpPanel>

      <ErpPanel class="erp-split-main">
        <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
        <ErpTable
          ref="tableRef"
          :columns="columns"
          :data="list"
          :loading="loading"
          selection
          storage-key="eng.material"
          :actions-width="180"
          @selection-change="onSelectionChange"
          @sort-change="onSort"
          @refresh="load"
        >
          <template #toolbar>
            <el-button v-perm="'eng:material:create'" type="primary" icon="Plus" @click="create">新建物料</el-button>
            <el-button v-perm="'eng:material:enable'" :disabled="!selection.length" @click="batch('enable')">批量启用</el-button>
            <el-button v-perm="'eng:material:disable'" :disabled="!selection.length" @click="batch('disable')">批量停用</el-button>
          </template>
          <template #toolbar-right>
            <ErpIconButton icon="Upload" tooltip="导入" permission="eng:material:import" @click="importRef?.open()" />
            <ExportButton url="/engineering/materials/export" :params="() => toParams({ ...query })" :columns="exportColumns" filename="物料" permission="eng:material:export" />
          </template>
          <template #col-imageFileId="{ row }">
            <ErpIconButton v-if="asMaterial(row).imageFileId" icon="Picture" tooltip="查看图片" @click="preview(asMaterial(row).imageFileId!)" />
          </template>
          <template #actions="{ row }">
            <RowActions :actions="[
              { label: '编辑', permission: 'eng:material:update', visible: asMaterial(row).status !== 'PENDING', handler: () => router.push(`/engineering/material/${asMaterial(row).id}/edit`) },
              { label: enableLabel(asMaterial(row)), permission: 'eng:material:enable', visible: asMaterial(row).status === 'DRAFT' || asMaterial(row).status === 'DISABLED', handler: () => enable(asMaterial(row)) },
              { label: '停用', permission: 'eng:material:disable', visible: asMaterial(row).status === 'ENABLED', handler: () => disable(asMaterial(row)) },
              { label: '复制新建', permission: 'eng:material:create', handler: () => router.push({ path: '/engineering/material/new', query: { from: asMaterial(row).id } }) },
              { label: '删除', permission: 'eng:material:delete', danger: true, visible: asMaterial(row).status === 'DRAFT', confirm: `确定删除物料「${asMaterial(row).code} ${asMaterial(row).name}」吗？删除后不可恢复。`, handler: () => remove(asMaterial(row)) }
            ]" />
          </template>
          <template #empty>
            <el-button v-perm="'eng:material:create'" icon="Plus" @click="create">新建物料</el-button>
          </template>
        </ErpTable>
        <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
      </ErpPanel>
    </div>

    <ImportDialog ref="importRef" title="导入物料" base="/engineering/materials" template-name="物料" :params="importParams" allow-partial @done="load">
      <template #options>
        <el-form label-width="120px" class="import-options">
          <el-form-item label="编码已存在时">
            <el-radio-group v-model="importMode">
              <el-radio value="SKIP">跳过</el-radio>
              <el-radio value="UPDATE">更新（只更新文件中非空的列）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="!settings.enableApproval" v-perm="'eng:material:enable'" label="导入后">
            <el-checkbox v-model="importEnable">直接启用</el-checkbox>
          </el-form-item>
        </el-form>
      </template>
    </ImportDialog>
  </ErpPage>
</template>

<style scoped>
.tree-panel { width: 240px; flex-shrink: 0; }
.tree-search { padding: var(--erp-space-3) var(--erp-space-3) var(--erp-space-2); }
.tree-scroll { height: calc(100vh - 290px); min-height: 240px; padding: 0 var(--erp-space-2) var(--erp-space-3); }
.tree-node { display: inline-flex; gap: var(--erp-space-2); align-items: baseline; min-width: 0; }
.tree-code { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.import-options :deep(.el-form-item) { margin-bottom: 0; }
</style>
