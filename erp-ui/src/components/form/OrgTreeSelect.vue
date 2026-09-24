<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useBaseDataStore } from '@/stores/baseData'
import type { OrgNode } from '@/api/system'

/**
 * 组织/部门树选择（只含启用节点）。
 * - multiple：多选
 * - checkStrictly：勾选父节点不联动子节点（默认 true，可选任意层级，包括公司）
 * - onlyDept：只能选部门（公司节点不可选）
 */
const props = withDefaults(defineProps<{
  modelValue?: string | string[] | null
  multiple?: boolean
  checkStrictly?: boolean
  onlyDept?: boolean
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
  /** 排除的节点（及其下级），如编辑组织时排除自己 */
  excludeId?: string
}>(), { checkStrictly: true, placeholder: '请选择组织', clearable: true })
const emit = defineEmits<{ 'update:modelValue': [v: string | string[] | undefined]; change: [v: string | string[] | undefined] }>()

const store = useBaseDataStore()
onMounted(() => store.loadOrgTree())

interface TreeOption { value: string; label: string; disabled: boolean; children?: TreeOption[] }

function toOptions(nodes: OrgNode[]): TreeOption[] {
  return nodes
    .filter((n) => n.id !== props.excludeId)
    .map((n) => ({
      value: n.id,
      label: n.name,
      disabled: props.onlyDept && n.orgType === 'COMPANY',
      children: n.children?.length ? toOptions(n.children) : undefined
    }))
}

const data = computed(() => toOptions(store.orgTree.data ?? []))

function onChange(v: string | string[] | undefined) {
  const value = v === '' || v === null ? undefined : v
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <el-tree-select
    :model-value="modelValue ?? (multiple ? [] : undefined)"
    :data="data"
    :multiple="multiple"
    :check-strictly="checkStrictly"
    :show-checkbox="multiple"
    :collapse-tags="multiple"
    collapse-tags-tooltip
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    node-key="value"
    default-expand-all
    filterable
    @update:model-value="onChange"
  />
</template>
