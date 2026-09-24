<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { refApi, type WarehouseBrief } from '@/api/refs'

/**
 * 仓库下拉：只列当前用户有权限的仓库（onlyMine，默认 true）。
 * types 按仓库类型过滤，如 ['RAW','FPC','ELEC']；onlyAvailable 只列可用仓（排除不良品仓、待检仓、退货仓）。
 */
const props = withDefaults(defineProps<{
  modelValue?: string | null
  types?: string[]
  onlyAvailable?: boolean
  onlyMine?: boolean
  disabled?: boolean
  clearable?: boolean
  placeholder?: string
}>(), { onlyMine: true, clearable: true })
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; select: [w: WarehouseBrief | undefined] }>()

const options = ref<WarehouseBrief[]>([])
async function load() {
  const list = await refApi.warehouseSimple({ types: props.types?.join(','), onlyMine: props.onlyMine }).catch(() => [])
  options.value = props.onlyAvailable ? list.filter((w) => w.available) : list
}
onMounted(load)
watch(() => [props.types?.join(','), props.onlyAvailable, props.onlyMine], load)

function onChange(v: string | undefined) {
  emit('update:modelValue', v || undefined)
  emit('select', options.value.find((w) => w.id === v))
}
</script>

<template>
  <el-select :model-value="modelValue ?? undefined" :disabled="disabled" :clearable="clearable" :placeholder="placeholder ?? '仓库'" filterable @update:model-value="onChange">
    <el-option v-for="w in options" :key="w.id" :value="w.id" :label="`${w.code} ${w.name}`" />
  </el-select>
</template>
