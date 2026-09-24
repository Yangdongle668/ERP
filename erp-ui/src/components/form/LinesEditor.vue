<script setup lang="ts" generic="R extends Record<string, any>">
import { computed, nextTick, reactive, ref, shallowRef, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { LineColumn } from '../types'
import NumberInput from './NumberInput.vue'
import DictSelect from './DictSelect.vue'
import MaterialPickerDialog from './MaterialPickerDialog.vue'
import { refApi, type MaterialBrief } from '@/api/refs'
import { useBaseDataStore } from '@/stores/baseData'
import { formatAmount, formatQty } from '@/utils/format'

/**
 * 可编辑明细表格（UI 设计规范 T4 “明细行”的全部行为）：
 * - 物料单元格：输入编码回车/失焦 → 精确匹配直接带出；否则弹出物料选择器（onMaterial 回调带出名称、规格、单位、单价等）
 * - 键盘：Enter/Tab 下一个可编辑单元格，最后一行最后一格回车自动新增一行；↑↓ 同列上下移动；Ctrl+Delete 删除当前行
 * - 行操作：插入行（当前行上方）、复制行、删除；勾选后“删除选中”
 * - 底部合计（summary 列）
 * - validate()：忽略空行（物料为空），逐格校验必填/格式，错误单元格标红并悬停显示原因，滚动到第一处错误；
 *   没有有效行时提示“请至少添加一行明细”
 *
 * <LinesEditor v-model="form.lines" :columns="cols" :new-row="() => ({ qty: undefined })" :on-material="fill" />
 */
const props = withDefaults(defineProps<{
  columns: LineColumn<R>[]
  newRow: () => R
  /** 物料带出：设置行上的名称、规格、单位、默认单价等 */
  onMaterial?: (row: R, material: MaterialBrief | undefined) => void | Promise<void>
  /** 物料列对应的 ID 字段 */
  materialIdProp?: string
  disabled?: boolean
  /** 只读时隐藏的操作列、工具栏 */
  hideToolbar?: boolean
  maxHeight?: number | string
  /** 物料选择器可选类型 */
  materialTypes?: string[]
}>(), { materialIdProp: 'materialId', maxHeight: 520 })

const model = defineModel<R[]>({ required: true })
/** defineModel 的泛型数组在模板中会被解包，这里统一按 R[] 使用 */
const rows = computed<R[]>({ get: () => model.value as R[], set: (v) => (model.value = v) })

const baseData = useBaseDataStore()
baseData.loadUoms()
const pickerRef = ref<InstanceType<typeof MaterialPickerDialog>>()
const wrapRef = ref<HTMLElement>()
const selection = shallowRef([]) as Ref<R[]>

// ---------- 行标识与错误（不写入行对象，避免影响保存数据和未保存判断） ----------
let seq = 0
const keys = new WeakMap<object, number>()
function keyOf(row: R): number {
  let k = keys.get(row)
  if (k === undefined) {
    k = ++seq
    keys.set(row, k)
  }
  return k
}
const errors = reactive(new Map<string, string>())
const errKey = (row: R, prop: string) => `${keyOf(row)}:${prop}`
function errorOf(row: R, prop: string) {
  return errors.get(errKey(row, prop))
}
function clearError(row: R, prop: string) {
  errors.delete(errKey(row, prop))
}

const materialCol = computed(() => props.columns.find((c) => c.type === 'material'))
const editableCols = computed(() => props.columns.filter((c) => c.type !== 'readonly'))

function isEditable(row: R, c: LineColumn<R>) {
  return !props.disabled && c.type !== 'readonly' && (!c.editable || c.editable(row))
}

function isEmptyRow(row: R) {
  const mc = materialCol.value
  if (mc) return !row[props.materialIdProp] && !row[mc.prop]
  return editableCols.value.every((c) => row[c.prop] === undefined || row[c.prop] === null || row[c.prop] === '')
}

// ---------- 行操作 ----------
function addRow(focus = true) {
  rows.value.push(props.newRow())
  if (focus) nextTick(() => focusCell(rows.value.length - 1, 0))
}

function insertAbove(index: number) {
  rows.value.splice(index, 0, props.newRow())
  nextTick(() => focusCell(index, 0))
}

function copyRow(index: number) {
  const src = rows.value[index]
  const copy = { ...JSON.parse(JSON.stringify(src)) } as R
  delete (copy as Record<string, unknown>).id
  delete (copy as Record<string, unknown>).sourceLineId
  rows.value.splice(index + 1, 0, copy)
}

function removeRow(index: number) {
  const [row] = rows.value.splice(index, 1)
  if (row) for (const c of props.columns) clearError(row, c.prop)
}

function removeSelected() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选数据')
    return
  }
  const set = new Set(selection.value)
  rows.value = rows.value.filter((r) => !set.has(r))
  selection.value = []
}

// ---------- 物料 ----------
const materialText = reactive(new Map<number, string>())

function materialInput(row: R) {
  const mc = materialCol.value!
  return materialText.get(keyOf(row)) ?? (row[mc.prop] as string) ?? ''
}

async function applyMaterial(row: R, m: MaterialBrief | undefined) {
  const mc = materialCol.value!
  const r = row as Record<string, unknown>
  r[props.materialIdProp] = m?.id
  r[mc.prop] = m?.code
  materialText.delete(keyOf(row))
  clearError(row, mc.prop)
  await props.onMaterial?.(row, m)
}

async function resolveMaterial(row: R, index: number, fromEnter: boolean) {
  const mc = materialCol.value!
  const text = (materialText.get(keyOf(row)) ?? '').trim().toUpperCase()
  if (!materialText.has(keyOf(row))) {
    if (fromEnter) moveNext(index, 0)
    return
  }
  if (!text) {
    await applyMaterial(row, undefined)
    return
  }
  if (text === row[mc.prop]) {
    materialText.delete(keyOf(row))
    if (fromEnter) moveNext(index, 0)
    return
  }
  const exact = await refApi.materialByCode(text).catch(() => null)
  if (exact && (!props.materialTypes || props.materialTypes.includes(exact.materialType))) {
    await applyMaterial(row, exact)
    if (fromEnter) moveNext(index, 0)
    return
  }
  const picked = await pickerRef.value!.open({ keyword: text, types: props.materialTypes, multiple: false })
  if (picked.length) {
    await applyMaterial(row, picked[0])
    nextTick(() => moveNext(index, 0))
  } else {
    materialText.delete(keyOf(row))
  }
}

async function openPicker(row: R, index: number) {
  const picked = await pickerRef.value!.open({ keyword: materialInput(row), types: props.materialTypes, multiple: false })
  if (picked.length) {
    await applyMaterial(row, picked[0])
    nextTick(() => moveNext(index, 0))
  }
}

/** 批量添加物料：先填充末尾空行，再追加 */
async function batchAdd() {
  const picked = await pickerRef.value!.open({ types: props.materialTypes, multiple: true })
  for (const m of picked) {
    let target = rows.value.find((r) => isEmptyRow(r))
    if (!target) {
      rows.value.push(props.newRow())
      target = rows.value[rows.value.length - 1]
    }
    await applyMaterial(target, m)
  }
}

// ---------- 键盘导航 ----------
function focusCell(rowIndex: number, colIndex: number) {
  const el = wrapRef.value?.querySelector<HTMLElement>(`[data-cell="${rowIndex}-${colIndex}"] input, [data-cell="${rowIndex}-${colIndex}"] textarea`)
  el?.focus()
  if (el instanceof HTMLInputElement) el.select?.()
}

function editableIndexes(rowIndex: number): number[] {
  const row = rows.value[rowIndex]
  return props.columns.map((c, i) => (row && isEditable(row, c) ? i : -1)).filter((i) => i >= 0)
}

function moveNext(rowIndex: number, colIndex: number) {
  const idx = editableIndexes(rowIndex)
  const next = idx.find((i) => i > colIndex)
  if (next !== undefined) {
    focusCell(rowIndex, next)
    return
  }
  if (rowIndex === rows.value.length - 1) {
    if (isEmptyRow(rows.value[rowIndex])) return
    addRow(false)
  }
  nextTick(() => focusCell(rowIndex + 1, editableIndexes(rowIndex + 1)[0] ?? 0))
}

function onKeydown(e: KeyboardEvent) {
  const cell = (e.target as HTMLElement).closest<HTMLElement>('[data-cell]')
  if (!cell) return
  const [r, c] = cell.dataset.cell!.split('-').map(Number)
  const col = props.columns[c]
  if (e.key === 'Delete' && e.ctrlKey) {
    e.preventDefault()
    removeRow(r)
    nextTick(() => focusCell(Math.min(r, rows.value.length - 1), c))
    return
  }
  if (e.key === 'Tab' && !e.shiftKey) {
    e.preventDefault()
    if (col.type === 'material') resolveMaterial(rows.value[r], r, true)
    else moveNext(r, c)
    return
  }
  if (e.key === 'Enter' && col.type !== 'select' && col.type !== 'dict' && col.type !== 'date') {
    e.preventDefault()
    if (col.type !== 'material') moveNext(r, c) // 物料列由自身的回车处理
    return
  }
  if ((e.key === 'ArrowUp' || e.key === 'ArrowDown') && col.type !== 'select' && col.type !== 'dict') {
    e.preventDefault()
    const target = e.key === 'ArrowUp' ? r - 1 : r + 1
    if (target >= 0 && target < rows.value.length) focusCell(target, c)
  }
}

// ---------- 校验 ----------
function isBlank(v: unknown) {
  return v === undefined || v === null || v === ''
}

function validate(): boolean {
  errors.clear()
  const valid = rows.value.filter((r) => !isEmptyRow(r))
  if (!valid.length) {
    ElMessage.warning('请至少添加一行明细')
    return false
  }
  let first: { r: number; c: number } | undefined
  rows.value.forEach((row, ri) => {
    if (isEmptyRow(row)) return
    props.columns.forEach((c, ci) => {
      if (c.type === 'readonly' || (c.editable && !c.editable(row))) return
      const v = row[c.type === 'material' ? props.materialIdProp : c.prop]
      let msg: string | undefined
      if (c.required && isBlank(v)) {
        msg = ['select', 'dict', 'date', 'material'].includes(c.type) ? `请选择${c.label}` : `请输入${c.label}`
      } else if (!isBlank(v) && ['qty', 'price', 'amount', 'number'].includes(c.type)) {
        const n = Number(v)
        if (c.min !== undefined && n < c.min) msg = `${c.label}不能小于 ${c.min}`
        else if (c.required && c.type === 'qty' && c.min === undefined && n <= 0) msg = `${c.label}必须大于 0`
        else if (!c.allowNegative && n < 0) msg = `${c.label}不能为负数`
      }
      if (!msg && c.validate) msg = c.validate(v, row)
      if (msg) {
        errors.set(errKey(row, c.prop), msg)
        first ??= { r: ri, c: ci }
      }
    })
  })
  if (first) {
    const f = first
    nextTick(() => {
      wrapRef.value?.querySelector(`[data-cell="${f.r}-${f.c}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    })
    ElMessage.warning('明细有错误，请检查标红的单元格')
    return false
  }
  return true
}

/** 有效行（去掉空行） */
function validRows(): R[] {
  return rows.value.filter((r) => !isEmptyRow(r))
}

// ---------- 合计 ----------
const hasSummary = computed(() => props.columns.some((c) => c.summary))
function summaryMethod({ columns }: { columns: { property?: string }[] }) {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    const c = props.columns.find((x) => x.prop === col.property)
    if (!c?.summary) return ''
    const sum = rows.value.reduce((acc, r) => acc + (Number(r[c.prop]) || 0), 0)
    return c.type === 'qty' ? formatQty(sum, c.precision ?? 4) : formatAmount(sum, c.precision ?? 2)
  })
}

function qtyPrecision(row: R, c: LineColumn<R>) {
  return c.precision ?? (c.uomProp ? baseData.uomPrecision(row[c.uomProp]) : 4)
}

function readonlyText(row: R, c: LineColumn<R>) {
  if (c.formatter) return c.formatter(row)
  const v = row[c.prop]
  return v === undefined || v === null || v === '' ? '' : String(v)
}

function setValue(row: R, prop: string, v: unknown) {
  ;(row as Record<string, unknown>)[prop] = v
  clearError(row, prop)
}

defineExpose({ validate, validRows, addRow, batchAdd })
</script>

<template>
  <div ref="wrapRef" class="lines-editor" @keydown="onKeydown">
    <div v-if="!disabled && !hideToolbar" class="toolbar">
      <el-button icon="Plus" @click="addRow()">添加行</el-button>
      <el-button v-if="materialCol" icon="Files" @click="batchAdd">批量添加物料</el-button>
      <slot name="toolbar" />
      <el-button icon="Delete" :disabled="!selection.length" @click="removeSelected">删除选中</el-button>
    </div>
    <el-table
      :data="(rows as any[])"
      :row-key="(r: R) => String(keyOf(r))"
      border
      :max-height="maxHeight"
      :show-summary="hasSummary"
      :summary-method="summaryMethod"
      class="lines-table"
      @selection-change="selection = $event"
    >
      <el-table-column v-if="!disabled && !hideToolbar" type="selection" width="40" fixed="left" />
      <el-table-column label="行" width="50" align="center" fixed="left">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column
        v-for="(c, ci) in columns"
        :key="c.prop"
        :prop="c.prop"
        :width="c.width"
        :min-width="c.minWidth ?? (c.width ? undefined : 120)"
        :align="['qty', 'price', 'amount', 'number'].includes(c.type) ? 'right' : 'left'"
        :header-align="['qty', 'price', 'amount', 'number'].includes(c.type) ? 'right' : 'left'"
      >
        <template #header><span :class="{ required: c.required && !disabled }">{{ c.label }}</span></template>
        <template #default="{ row, $index }">
          <el-tooltip :disabled="!errorOf(row, c.prop)" :content="errorOf(row, c.prop)" placement="top" effect="dark">
            <div :data-cell="`${$index}-${ci}`" :class="['cell', { 'has-error': errorOf(row, c.prop) }]">
              <template v-if="!isEditable(row, c)">
                <span v-if="c.type === 'qty'">{{ formatQty(row[c.prop], qtyPrecision(row, c)) === '-' ? '' : formatQty(row[c.prop], qtyPrecision(row, c)) }}</span>
                <span v-else-if="c.type === 'amount'">{{ row[c.prop] === undefined || row[c.prop] === null ? '' : formatAmount(row[c.prop], c.precision ?? 2) }}</span>
                <span v-else class="ro">{{ readonlyText(row, c) }}</span>
              </template>
              <el-input
                v-else-if="c.type === 'material'"
                :model-value="materialInput(row)"
                placeholder="编码，回车"
                class="borderless"
                @update:model-value="materialText.set(keyOf(row), String($event).toUpperCase())"
                @keydown.enter.prevent="resolveMaterial(row, $index, true)"
                @blur="resolveMaterial(row, $index, false)"
              >
                <template #suffix><el-icon class="search" @mousedown.prevent="openPicker(row, $index)"><Search /></el-icon></template>
              </el-input>
              <NumberInput
                v-else-if="c.type === 'qty'"
                :model-value="row[c.prop]" :precision="qtyPrecision(row, c)" trim-zeros :allow-negative="c.allowNegative" borderless
                @update:model-value="setValue(row, c.prop, $event)"
              />
              <NumberInput
                v-else-if="c.type === 'price'"
                :model-value="row[c.prop]" :precision="c.precision ?? 6" trim-zeros :min-decimals="2" borderless
                @update:model-value="setValue(row, c.prop, $event)"
              />
              <NumberInput
                v-else-if="c.type === 'amount' || c.type === 'number'"
                :model-value="row[c.prop]" :precision="c.precision ?? 2" :trim-zeros="c.type === 'number'" :allow-negative="c.allowNegative" borderless
                @update:model-value="setValue(row, c.prop, $event)"
              />
              <el-date-picker
                v-else-if="c.type === 'date'"
                :model-value="row[c.prop]" type="date" value-format="YYYY-MM-DD" :clearable="false" class="borderless date"
                @update:model-value="setValue(row, c.prop, $event)"
              />
              <el-select
                v-else-if="c.type === 'select'"
                :model-value="row[c.prop]" filterable class="borderless"
                @update:model-value="setValue(row, c.prop, $event)"
              >
                <el-option v-for="o in c.options" :key="String(o.value)" :value="o.value" :label="o.label" />
              </el-select>
              <DictSelect
                v-else-if="c.type === 'dict'"
                :model-value="row[c.prop]" :type="c.dictType!" :clearable="false" class="borderless"
                @update:model-value="setValue(row, c.prop, $event)"
              />
              <el-input v-else :model-value="row[c.prop]" class="borderless" @update:model-value="setValue(row, c.prop, $event)" />
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column v-if="!disabled" label="" width="96" fixed="right" align="center">
        <template #default="{ $index }">
          <el-tooltip content="在上方插入行" placement="top"><el-button link icon="Top" @click="insertAbove($index)" /></el-tooltip>
          <el-tooltip content="复制行" placement="top"><el-button link icon="CopyDocument" @click="copyRow($index)" /></el-tooltip>
          <el-tooltip content="删除行（Ctrl+Delete）" placement="top"><el-button link type="danger" icon="Delete" @click="removeRow($index)" /></el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <MaterialPickerDialog ref="pickerRef" />
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.toolbar :deep(.el-button + .el-button) { margin-left: 0; }
.lines-table :deep(.el-table__cell) { padding: 2px 0; }
.lines-table :deep(.cell) { padding: 0 4px; }
.cell { min-height: 32px; display: flex; align-items: center; border-radius: var(--erp-radius-xs); }
.cell > * { width: 100%; }
.cell .ro { padding: 0 7px; }
.has-error { box-shadow: 0 0 0 1px var(--el-color-danger) inset; background: var(--el-color-danger-light-9); }
.borderless :deep(.el-input__wrapper), .borderless :deep(.el-select__wrapper) { box-shadow: none; background: transparent; }
.borderless :deep(.el-input__wrapper.is-focus), .borderless :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px var(--el-color-primary) inset; }
.date { width: 100%; }
.search { cursor: pointer; }
.required::before { content: '*'; color: var(--el-color-danger); margin-right: 2px; }
</style>
