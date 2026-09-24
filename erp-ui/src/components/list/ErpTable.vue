<script setup lang="ts" generic="T extends Record<string, any>">
import { computed, h, nextTick, onMounted, ref, watch, type VNode } from 'vue'
import type { TableInstance } from 'element-plus'
import type { TableColumn } from '../types'
import StatusTag from '../form/StatusTag.vue'
import DictTag from '../form/DictTag.vue'
import ErpEmpty from '../base/ErpEmpty.vue'
import ErpIconButton from '../base/ErpIconButton.vue'
import { useUserStore } from '@/stores/user'
import { useBaseDataStore } from '@/stores/baseData'
import {
  formatAmount, formatDate, formatDateTime, formatPercent, formatPrice, formatQty, formatRate, isNegative, orEmpty
} from '@/utils/format'

/**
 * 表格封装（UI 设计规范 T1 表格）：列配置、列设置（显示/隐藏、顺序，按用户 + 页面保存在本地）、
 * 数字格式与对齐、状态/字典标签、合计行、操作列、工具栏（左侧业务按钮，右侧导入导出 + 列设置 + 刷新）。
 *
 * 插槽：toolbar（左侧按钮）、toolbar-right（导入导出等 ErpIconButton）、col-<prop>（自定义单元格）、actions（操作列）、empty（空状态操作）
 *
 * 视觉（UI 设计规范 5.1 Table）：无斑马纹、只有横向分割线；表头浅灰；悬停整行高亮；首次加载显示骨架行，
 * 刷新时保留数据并显示遮罩；勾选后工具栏显示“已选 N 项”；密度可切换（默认 / 紧凑，全站记忆）。
 */
const props = withDefaults(defineProps<{
  columns: TableColumn<T>[]
  data: T[]
  loading?: boolean
  /** 显示勾选列（有批量操作时） */
  selection?: boolean
  rowKey?: string
  /** 列设置保存键，通常为页面路由名；不传则不提供列设置 */
  storageKey?: string
  /** 查询结果总计（后端返回），显示在合计行第二行 */
  totalSummary?: Record<string, string | number>
  actionsWidth?: number
  /** 隐藏工具栏 */
  noToolbar?: boolean
  height?: number | string
  maxHeight?: number | string
  border?: boolean
  /** 树形表格 */
  treeProps?: { children?: string; hasChildren?: string }
  defaultExpandAll?: boolean
  /** 空状态说明 */
  emptyText?: string
}>(), { rowKey: 'id', actionsWidth: 0, border: false, emptyText: '暂无数据' })

const emit = defineEmits<{
  'selection-change': [rows: T[]]
  'sort-change': [sort: { prop?: string; order?: 'asc' | 'desc' }]
  refresh: []
  'row-click': [row: T]
}>()

const tableRef = ref<TableInstance>()
const userStore = useUserStore()
const baseData = useBaseDataStore()
onMounted(() => {
  if (props.columns.some((c) => c.uomProp)) baseData.loadUoms()
  if (props.columns.some((c) => c.currencyProp)) baseData.loadCurrencies()
})

const colKey = (c: TableColumn<T>) => c.key ?? c.prop ?? c.label

// ---------- 列设置 ----------
interface ColSetting { key: string; visible: boolean }
const storeKey = computed(() => (props.storageKey ? `erp.cols.${userStore.user?.id ?? 'anon'}.${props.storageKey}` : ''))
const defaultSettings = (): ColSetting[] => props.columns.map((c) => ({ key: colKey(c), visible: !c.hidden }))
const settings = ref<ColSetting[]>(loadSettings())

function loadSettings(): ColSetting[] {
  const defaults = defaultSettings()
  if (!storeKey.value) return defaults
  try {
    const saved = JSON.parse(localStorage.getItem(storeKey.value) ?? 'null') as ColSetting[] | null
    if (!Array.isArray(saved)) return defaults
    // 以保存的顺序为准；新增的列追加到末尾，删除的列丢弃
    const known = new Map(defaults.map((d) => [d.key, d]))
    const result = saved.filter((s) => known.has(s.key))
    for (const d of defaults) if (!result.some((s) => s.key === d.key)) result.push(d)
    return result
  } catch {
    return defaults
  }
}

function saveSettings() {
  if (!storeKey.value) return
  try {
    localStorage.setItem(storeKey.value, JSON.stringify(settings.value))
  } catch {
    /* 存储不可用时仅本次有效 */
  }
}

function resetSettings() {
  settings.value = defaultSettings()
  if (storeKey.value) {
    try {
      localStorage.removeItem(storeKey.value)
    } catch {
      /* ignore */
    }
  }
}

function move(i: number, delta: number) {
  const j = i + delta
  if (j < 0 || j >= settings.value.length) return
  const arr = [...settings.value]
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  settings.value = arr
  saveSettings()
}

watch(() => props.columns.map(colKey).join(','), () => (settings.value = loadSettings()))

const visibleColumns = computed(() => {
  const byKey = new Map(props.columns.map((c) => [colKey(c), c]))
  return settings.value.filter((s) => s.visible && byKey.has(s.key)).map((s) => byKey.get(s.key)!)
})

const labelOf = (key: string) => props.columns.find((c) => colKey(c) === key)?.label ?? key

/** 当前显示的列（导出时使用：列与列设置一致） */
function getVisibleColumns() {
  return visibleColumns.value
}

// ---------- 格式 ----------
const NUMERIC = new Set(['qty', 'amount', 'price', 'rate', 'percent'])

function alignOf(c: TableColumn<T>) {
  if (c.align) return c.align
  // 数字右对齐（便于比较位数），其余左对齐（状态徽标、日期左对齐更整齐）
  if (c.type && NUMERIC.has(c.type)) return 'right'
  return 'left'
}

function valueOf(row: T, c: TableColumn<T>) {
  return c.prop ? row[c.prop] : undefined
}

function textOf(row: T, c: TableColumn<T>): string {
  if (c.formatter) return c.formatter(row)
  const v = valueOf(row, c)
  switch (c.type) {
    case 'qty': return formatQty(v, c.precision ?? (c.uomProp ? baseData.uomPrecision(row[c.uomProp]) : 4))
    case 'amount': return formatAmount(v, c.precision ?? (c.currencyProp ? baseData.currencyPrecision(row[c.currencyProp]) : 2))
    case 'price': return formatPrice(v)
    case 'rate': return formatRate(v)
    case 'percent': return formatPercent(v)
    case 'date': return formatDate(v)
    case 'datetime': return formatDateTime(v, true)
    case 'bool': return v === true ? '是' : v === false ? '否' : '-'
    case 'enum': return c.options?.find((o) => o.value === v)?.label ?? orEmpty(v)
    default: return orEmpty(v)
  }
}

function negative(row: T, c: TableColumn<T>) {
  return !!c.type && NUMERIC.has(c.type) && isNegative(valueOf(row, c))
}

// ---------- 合计 ----------
const hasSummary = computed(() => visibleColumns.value.some((c) => c.summary))

function summaryMethod({ columns }: { columns: { property?: string }[] }): (string | VNode)[] {
  return columns.map((col, index) => {
    if (index === 0) return props.totalSummary ? h('div', ['本页合计', h('div', { class: 'total' }, '总计')]) : '合计'
    const c = visibleColumns.value.find((x) => x.prop && x.prop === col.property)
    if (!c?.summary || !c.prop) return ''
    const sum = props.data.reduce((acc, row) => acc + (Number(row[c.prop!]) || 0), 0)
    const fmt = (v: unknown) => (c.type === 'qty' ? formatQty(v as number, c.precision ?? 4) : formatAmount(v as number, c.precision ?? 2))
    const total = props.totalSummary?.[c.prop]
    return total !== undefined ? h('div', [fmt(sum), h('div', { class: 'total' }, fmt(total))]) : fmt(sum)
  })
}

// ---------- 勾选 ----------
const selectedCount = ref(0)
function onSelection(rows: T[]) {
  selectedCount.value = rows.length
  emit('selection-change', rows)
}
function clearSelection() {
  tableRef.value?.clearSelection()
}

// ---------- 密度（全站记忆） ----------
const DENSITY_KEY = 'erp.table.density'
const compact = ref(readDensity())
function readDensity() {
  try {
    return localStorage.getItem(DENSITY_KEY) === 'compact'
  } catch {
    return false
  }
}
function toggleDensity() {
  compact.value = !compact.value
  try {
    localStorage.setItem(DENSITY_KEY, compact.value ? 'compact' : 'default')
  } catch {
    /* ignore */
  }
}

/** 首次加载（还没有数据）显示骨架行，之后刷新保留数据并显示遮罩 */
const skeleton = computed(() => props.loading && props.data.length === 0)

// ---------- 事件 ----------
function onSort({ prop, order }: { prop: string | null; order: string | null }) {
  emit('sort-change', { prop: order && prop ? prop : undefined, order: order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : undefined })
}

watch(() => props.data, () => nextTick(() => tableRef.value?.setScrollTop(0)))

defineExpose({ getVisibleColumns, clearSelection, table: tableRef })
</script>

<template>
  <div :class="['erp-table', { 'is-compact': compact }]">
    <div v-if="!noToolbar" class="erp-table__toolbar">
      <div class="left">
        <slot name="toolbar" />
        <span v-if="selection && selectedCount" class="selection-bar">
          已选 <b class="num">{{ selectedCount }}</b> 项<span class="dot">·</span><el-button link type="primary" @click="clearSelection">取消选择</el-button>
        </span>
      </div>
      <div class="right">
        <slot name="toolbar-right" />
        <el-popover v-if="storageKey" placement="bottom-end" :width="260" trigger="click">
          <template #reference>
            <span><ErpIconButton icon="Columns" tooltip="列设置" /></span>
          </template>
          <div class="col-settings">
            <div class="col-head">
              <span>显示列</span>
              <el-button link type="primary" @click="resetSettings">恢复默认</el-button>
            </div>
            <el-scrollbar max-height="360px">
              <div v-for="(s, i) in settings" :key="s.key" class="col-row">
                <el-checkbox v-model="s.visible" @change="saveSettings">{{ labelOf(s.key) }}</el-checkbox>
                <span class="ops">
                  <el-button link icon="Up" :disabled="i === 0" aria-label="上移" @click="move(i, -1)" />
                  <el-button link icon="Down" :disabled="i === settings.length - 1" aria-label="下移" @click="move(i, 1)" />
                </span>
              </div>
            </el-scrollbar>
          </div>
        </el-popover>
        <ErpIconButton icon="Operation" :tooltip="compact ? '切换为默认行高' : '切换为紧凑行高'" :active="compact" @click="toggleDensity" />
        <ErpIconButton icon="Refresh" tooltip="刷新" :loading="loading && !skeleton" @click="emit('refresh')" />
      </div>
    </div>

    <el-table
      ref="tableRef"
      v-loading="loading && !skeleton"
      :data="data"
      :row-key="rowKey"
      :border="border"
      :height="height"
      :max-height="maxHeight"
      :show-summary="hasSummary && !skeleton"
      :summary-method="summaryMethod"
      :tree-props="treeProps"
      :default-expand-all="defaultExpandAll"
      @selection-change="onSelection"
      @sort-change="onSort"
      @row-click="emit('row-click', $event)"
    >
      <el-table-column v-if="selection" type="selection" width="44" fixed="left" reserve-selection align="center" />
      <el-table-column
        v-for="c in visibleColumns"
        :key="colKey(c)"
        :prop="c.prop"
        :label="c.label"
        :width="c.width"
        :min-width="c.minWidth ?? (c.width ? undefined : 120)"
        :align="alignOf(c)"
        :header-align="alignOf(c)"
        :fixed="c.fixed"
        :sortable="c.sortable ? 'custom' : false"
        :show-overflow-tooltip="!c.noTooltip && c.type !== 'status' && c.type !== 'dict'"
      >
        <template #default="{ row }">
          <slot v-if="c.slot" :name="`col-${c.prop ?? c.key}`" :row="row" />
          <StatusTag v-else-if="c.type === 'status'" :value="valueOf(row, c)" :map="c.statusMap" />
          <DictTag v-else-if="c.type === 'dict'" :type="c.dictType!" :value="valueOf(row, c)" />
          <el-link v-else-if="c.type === 'link'" type="primary" underline="never" @click.stop="c.onClick?.(row)">{{ textOf(row, c) }}</el-link>
          <span v-else :class="{ num: c.type && NUMERIC.has(c.type), neg: negative(row, c), 'text-muted': textOf(row, c) === '-' }">{{ textOf(row, c) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="$slots.actions" label="操作" :width="actionsWidth || 180" fixed="right" align="left">
        <template #default="{ row }"><slot name="actions" :row="row" /></template>
      </el-table-column>
      <template #empty>
        <div v-if="skeleton" class="erp-table__skeleton">
          <el-skeleton v-for="i in 6" :key="i" animated :rows="0" :loading="true">
            <template #template><el-skeleton-item variant="text" :style="{ width: `${[92, 76, 88, 64, 84, 70][i - 1]}%` }" /></template>
          </el-skeleton>
        </div>
        <ErpEmpty v-else :description="emptyText"><slot name="empty" /></ErpEmpty>
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.erp-table { min-width: 0; }
.erp-table__toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 8px; min-height: 32px; }
.erp-table__toolbar .left, .erp-table__toolbar .right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.erp-table__toolbar .right { gap: 2px; }
.erp-table__toolbar :deep(.el-button + .el-button) { margin-left: 0; }
.selection-bar { display: inline-flex; align-items: center; gap: 4px; margin-left: 4px; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
.selection-bar b { color: var(--erp-color-text); font-weight: var(--erp-font-weight-medium); }
.selection-bar .dot { color: var(--erp-color-text-disabled); margin: 0 2px; }
.erp-table__skeleton { padding: 12px; display: flex; flex-direction: column; gap: 18px; text-align: left; }
.erp-table__skeleton :deep(.el-skeleton__item) { height: 14px; }
.erp-table.is-compact :deep(.el-table .el-table__cell) { padding: 4px 0; }
.num { font-variant-numeric: tabular-nums; }
.col-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; font-weight: var(--erp-font-weight-medium); }
.col-row { display: flex; justify-content: space-between; align-items: center; }
.col-row .ops { white-space: nowrap; }
:deep(.total) { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
</style>
