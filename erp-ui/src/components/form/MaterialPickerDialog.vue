<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TableInstance } from 'element-plus'
import { refApi, type CategoryNode, type MaterialBrief } from '@/api/refs'

/**
 * 物料批量选择弹窗（大弹窗 960px）：左侧物料类别树，右侧物料列表（多选、分页），底部显示已选物料。
 * const picked = await pickerRef.value.open({ keyword: 'FPC', types: ['RAW'] })   // 取消返回 []
 */
const visible = ref(false)
const multiple = ref(true)
const keyword = ref('')
const categoryId = ref<string>()
const types = ref<string[]>()
const tree = ref<CategoryNode[]>([])
const list = ref<MaterialBrief[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const loading = ref(false)
const selected = ref<MaterialBrief[]>([])
const tableRef = ref<TableInstance>()
let resolver: ((v: MaterialBrief[]) => void) | null = null
let syncing = false

function open(opts: { keyword?: string; types?: string[]; multiple?: boolean } = {}): Promise<MaterialBrief[]> {
  keyword.value = opts.keyword ?? ''
  types.value = opts.types
  multiple.value = opts.multiple !== false
  categoryId.value = undefined
  selected.value = []
  pageNo.value = 1
  visible.value = true
  if (!tree.value.length) refApi.categoryTree().then((t) => (tree.value = t)).catch(() => undefined)
  load()
  return new Promise((resolve) => (resolver = resolve))
}

async function load() {
  loading.value = true
  try {
    const page = await refApi.materialPage({
      keyword: keyword.value || undefined, categoryId: categoryId.value, types: types.value?.join(','),
      pageNo: pageNo.value, pageSize
    })
    list.value = page.list
    total.value = Number(page.total) || 0
    syncSelection()
  } finally {
    loading.value = false
  }
}

/** 翻页后恢复本页已选行的勾选状态 */
function syncSelection() {
  syncing = true
  requestAnimationFrame(() => {
    for (const row of list.value) tableRef.value?.toggleRowSelection(row, selected.value.some((s) => s.id === row.id))
    syncing = false
  })
}

function onSelect(rows: MaterialBrief[]) {
  if (syncing) return
  const pageIds = new Set(list.value.map((r) => r.id))
  const others = selected.value.filter((s) => !pageIds.has(s.id))
  selected.value = [...others, ...rows]
}

function onRowClick(row: MaterialBrief) {
  if (!multiple.value) {
    finish([row])
    return
  }
  tableRef.value?.toggleRowSelection(row)
}

function removeSelected(m: MaterialBrief) {
  selected.value = selected.value.filter((s) => s.id !== m.id)
  const row = list.value.find((r) => r.id === m.id)
  if (row) tableRef.value?.toggleRowSelection(row, false)
}

function search() {
  pageNo.value = 1
  load()
}

function onNode(node: CategoryNode) {
  categoryId.value = node.id === categoryId.value ? undefined : node.id
  search()
}

function finish(rows: MaterialBrief[]) {
  visible.value = false
  resolver?.(rows)
  resolver = null
}

const title = computed(() => (multiple.value ? '选择物料（可多选）' : '选择物料'))

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="960px" :close-on-click-modal="false" append-to-body @close="finish([])">
    <div class="picker">
      <div class="tree">
        <div class="tree-title">物料类别</div>
        <el-scrollbar height="420px">
          <el-tree :data="tree" :props="{ label: 'name', children: 'children' }" node-key="id" highlight-current default-expand-all :expand-on-click-node="false" @node-click="onNode" />
        </el-scrollbar>
      </div>
      <div class="main">
        <div class="bar">
          <el-input v-model="keyword" placeholder="编码/名称/规格，回车查询" clearable @keyup.enter="search" @clear="search" />
          <el-button type="primary" icon="Search" @click="search">查询</el-button>
        </div>
        <el-table ref="tableRef" v-loading="loading" :data="list" row-key="id" height="360" @selection-change="onSelect" @row-click="onRowClick">
          <el-table-column v-if="multiple" type="selection" width="44" />
          <el-table-column prop="code" label="编码" width="140" />
          <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="160" show-overflow-tooltip />
          <el-table-column prop="baseUom" label="单位" width="70" align="center" />
          <template #empty><ErpEmpty description="暂无数据" compact /></template>
        </el-table>
        <el-pagination v-model:current-page="pageNo" :page-size="pageSize" :total="total" layout="total, prev, pager, next" size="small" class="pager" @current-change="load" />
      </div>
    </div>
    <div v-if="multiple" class="selected">
      <span class="label">已选 {{ selected.length }} 项：</span>
      <el-tag v-for="m in selected" :key="m.id" closable class="tag" @close="removeSelected(m)">{{ m.code }} {{ m.name }}</el-tag>
    </div>
    <template #footer>
      <el-button @click="finish([])">取消</el-button>
      <el-button v-if="multiple" type="primary" :disabled="!selected.length" @click="finish(selected)">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.picker { display: flex; gap: 12px; }
.tree { width: 220px; border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-xs); padding: 8px; }
.tree-title { font-weight: var(--erp-font-weight-semibold); margin-bottom: 8px; }
.main { flex: 1; min-width: 0; }
.bar { display: flex; gap: 8px; margin-bottom: 8px; }
.pager { margin-top: 8px; justify-content: flex-end; }
.selected { margin-top: 12px; max-height: 80px; overflow: auto; }
.selected .label { color: var(--erp-color-text-secondary); }
.tag { margin: 2px 4px 2px 0; }
</style>
