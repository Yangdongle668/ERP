<script setup lang="ts" generic="T extends Record<string, any>">
import { computed, ref, shallowRef, watch } from 'vue'

/**
 * 远程搜索下拉的基础组件（UserSelect、MaterialSelect、CustomerSelect、SupplierSelect 基于它实现）。
 *
 * - search(keyword)：远程搜索，最多返回 20 条
 * - resolve(ids)：编辑已有数据时，根据当前值取回显示文字（否则下拉框只能显示 ID）
 * - 选中后触发 select 事件并带出完整对象，页面据此带出默认值（如客户的币别、付款条件）
 */
const props = withDefaults(defineProps<{
  modelValue?: string | string[] | null
  search: (keyword: string) => Promise<T[]>
  resolve?: (ids: string[]) => Promise<T[]>
  valueKey?: string
  label: (item: T) => string
  multiple?: boolean
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  /** 打开下拉时即加载（keyword 为空），默认 true */
  loadOnFocus?: boolean
}>(), { valueKey: 'id', placeholder: '输入关键字搜索', clearable: true, loadOnFocus: true })

const emit = defineEmits<{
  'update:modelValue': [v: string | string[] | undefined]
  select: [item: T | T[] | undefined]
}>()

const options = shallowRef<T[]>([])
/** 已选项缓存：保证选中项在重新搜索后仍能显示文字 */
const known = new Map<string, T>()
const loading = ref(false)
let seq = 0

const idOf = (item: T) => String(item[props.valueKey])

const displayOptions = computed(() => {
  const list = [...options.value]
  const ids = new Set(list.map(idOf))
  for (const v of selectedIds.value) {
    const item = known.get(v)
    if (item && !ids.has(v)) list.unshift(item)
  }
  return list
})

const selectedIds = computed<string[]>(() =>
  Array.isArray(props.modelValue) ? props.modelValue : props.modelValue ? [props.modelValue] : []
)

async function remoteSearch(keyword: string) {
  const my = ++seq
  loading.value = true
  try {
    const list = await props.search(keyword ?? '')
    if (my !== seq) return
    options.value = list
    for (const item of list) known.set(idOf(item), item)
  } finally {
    if (my === seq) loading.value = false
  }
}

function onVisible(visible: boolean) {
  if (visible && props.loadOnFocus && !options.value.length) remoteSearch('')
}

watch(selectedIds, async (ids) => {
  const missing = ids.filter((id) => !known.has(id))
  if (missing.length && props.resolve) {
    const items = await props.resolve(missing).catch(() => [] as T[])
    for (const item of items) known.set(idOf(item), item)
    options.value = [...options.value]
  }
}, { immediate: true })

function onChange(v: string | string[] | undefined) {
  const value = v === '' ? undefined : v
  emit('update:modelValue', value)
  if (Array.isArray(value)) emit('select', value.map((id) => known.get(id)).filter(Boolean) as T[])
  else emit('select', value ? known.get(value) : undefined)
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? (multiple ? [] : undefined)"
    :multiple="multiple"
    :collapse-tags="multiple"
    collapse-tags-tooltip
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :loading="loading"
    filterable
    remote
    :remote-method="remoteSearch"
    remote-show-suffix
    @visible-change="onVisible"
    @update:model-value="onChange"
  >
    <el-option v-for="item in displayOptions" :key="idOf(item)" :value="idOf(item)" :label="label(item)">
      <slot name="option" :item="item">{{ label(item) }}</slot>
    </el-option>
  </el-select>
</template>
