<script setup lang="ts">
import { ref, watch } from 'vue'
import { refApi, type LocationBrief } from '@/api/refs'

/** 库位下拉，依赖所选仓库；仓库变化时清空并重新加载 */
const props = defineProps<{ modelValue?: string | null; warehouseId?: string | null; disabled?: boolean; clearable?: boolean; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined] }>()

const options = ref<LocationBrief[]>([])
watch(() => props.warehouseId, async (id, old) => {
  if (old !== undefined && id !== old) emit('update:modelValue', undefined)
  options.value = id ? await refApi.locationSimple(id).catch(() => []) : []
}, { immediate: true })
</script>

<template>
  <el-select
    :model-value="modelValue ?? undefined"
    :disabled="disabled || !warehouseId"
    :clearable="clearable ?? true"
    :placeholder="warehouseId ? (placeholder ?? '库位') : '请先选择仓库'"
    filterable
    @update:model-value="emit('update:modelValue', $event || undefined)"
  >
    <el-option v-for="l in options" :key="l.id" :value="l.id" :label="l.name ? `${l.code} ${l.name}` : l.code" />
  </el-select>
</template>
