<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useBaseDataStore } from '@/stores/baseData'
import type { UomSimple } from '@/api/system'

/**
 * 计量单位下拉（启用单位）。codes 限制可选单位（如物料的基本单位 + 已配置的辅助单位）；category 限制类别。
 */
const props = defineProps<{ modelValue?: string | null; codes?: string[]; category?: string; disabled?: boolean; clearable?: boolean; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; select: [u: UomSimple | undefined] }>()

const store = useBaseDataStore()
onMounted(() => store.loadUoms())

const options = computed(() =>
  (store.uoms.data ?? []).filter((u) => (!props.codes || props.codes.includes(u.code)) && (!props.category || u.category === props.category))
)

function onChange(v: string | undefined) {
  emit('update:modelValue', v || undefined)
  emit('select', options.value.find((u) => u.code === v))
}
</script>

<template>
  <el-select :model-value="modelValue ?? undefined" :disabled="disabled" :clearable="clearable" :placeholder="placeholder ?? '单位'" filterable @update:model-value="onChange">
    <el-option v-for="u in options" :key="u.code" :value="u.code" :label="`${u.code} ${u.name}`" />
  </el-select>
</template>
