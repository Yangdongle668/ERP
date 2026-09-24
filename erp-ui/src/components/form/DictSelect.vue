<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useDictStore } from '@/stores/dict'

/**
 * 字典下拉：只列启用项；当前值为已停用项时仍显示其标签（不可再选）。
 * fillDefault 为 true 且当前无值时自动选中字典的默认项（新建表单使用）。
 */
const props = withDefaults(defineProps<{
  type: string
  modelValue?: string | string[] | null
  multiple?: boolean
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  fillDefault?: boolean
}>(), { clearable: true, placeholder: '请选择' })
const emit = defineEmits<{ 'update:modelValue': [v: string | string[] | undefined]; change: [v: string | string[] | undefined] }>()

const store = useDictStore()
const options = computed(() => {
  const enabled = store.items(props.type)
  const selected = Array.isArray(props.modelValue) ? props.modelValue : props.modelValue ? [props.modelValue] : []
  const extra = selected
    .filter((v) => !enabled.some((i) => i.value === v))
    .map((v) => ({ value: v, label: store.label(props.type, v), disabled: true }))
  return [...enabled.map((i) => ({ value: i.value, label: i.label, disabled: false })), ...extra]
})

onMounted(async () => {
  await store.load()
  if (props.fillDefault && !props.multiple && !props.modelValue) {
    const d = store.defaultValue(props.type)
    if (d) emit('update:modelValue', d)
  }
})

function onChange(v: string | string[] | undefined) {
  emit('update:modelValue', v === '' ? undefined : v)
  emit('change', v)
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? (multiple ? [] : undefined)"
    :multiple="multiple"
    :collapse-tags="multiple"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    filterable
    @update:model-value="onChange"
  >
    <el-option v-for="o in options" :key="o.value" :value="o.value" :label="o.label" :disabled="o.disabled" />
  </el-select>
</template>
