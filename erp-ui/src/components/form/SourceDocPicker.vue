<script setup lang="ts" generic="L extends Record<string, any>">
import { reactive, ref, shallowRef } from 'vue'
import type { PageResult } from '@/api/http'
import type { SearchField, TableColumn } from '../types'
import ErpSearchForm from '../list/ErpSearchForm.vue'
import ErpTable from '../list/ErpTable.vue'
import ErpPagination from '../list/ErpPagination.vue'

/**
 * 上游选单弹窗（超大弹窗 1200px，UI 设计规范 T4）：上方查询条件，下方上游单据“行”列表（单号、物料、数量、已执行、剩余），
 * 多选后带入。数量默认取上游剩余可执行数量，由调用方在带入时处理。
 *
 * const lines = await pickerRef.value.open(fixedQuery)   // fixedQuery 如 { customerId }，取消返回 []
 */
const props = withDefaults(defineProps<{
  title: string
  api: (query: Record<string, any>) => Promise<PageResult<L>>
  columns: TableColumn<L>[]
  searchFields?: SearchField[]
  rowKey?: string
  /** 已在明细中的上游行 ID，不能再选 */
  excludeKeys?: string[]
}>(), { rowKey: 'id', searchFields: () => [] })

const visible = ref(false)
const query = reactive<Record<string, any>>({ pageNo: 1, pageSize: 20 })
let fixed: Record<string, any> = {}
const list = shallowRef<L[]>([])
const total = ref(0)
const loading = ref(false)
const selected = shallowRef<L[]>([])
let resolver: ((v: L[]) => void) | null = null

function open(fixedQuery: Record<string, any> = {}): Promise<L[]> {
  fixed = fixedQuery
  for (const k of Object.keys(query)) delete query[k]
  Object.assign(query, { pageNo: 1, pageSize: 20 })
  selected.value = []
  visible.value = true
  load()
  return new Promise((resolve) => (resolver = resolve))
}

async function load() {
  loading.value = true
  try {
    const page = await props.api({ ...query, ...fixed })
    const exclude = new Set(props.excludeKeys ?? [])
    list.value = page.list.filter((l) => !exclude.has(String(l[props.rowKey])))
    total.value = Number(page.total) || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNo = 1
  load()
}

function reset() {
  for (const k of Object.keys(query)) delete query[k]
  Object.assign(query, { pageNo: 1, pageSize: 20 })
  load()
}

function finish(rows: L[]) {
  visible.value = false
  resolver?.(rows)
  resolver = null
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="1200px" :close-on-click-modal="false" append-to-body top="5vh" @close="finish([])">
    <ErpSearchForm v-if="searchFields.length" v-model="query" :fields="searchFields" :loading="loading" @search="search" @reset="reset" />
    <ErpTable :columns="columns" :data="list" :loading="loading" selection :row-key="rowKey" no-toolbar max-height="480" @selection-change="selected = $event" />
    <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    <template #footer>
      <span class="count">已选 {{ selected.length }} 行</span>
      <el-button @click="finish([])">取消</el-button>
      <el-button type="primary" :disabled="!selected.length" @click="finish(selected)">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.count { margin-right: 12px; color: var(--erp-color-text-secondary); }
</style>
