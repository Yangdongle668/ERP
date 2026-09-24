<script setup lang="ts">
import RemoteSelect from './RemoteSelect.vue'
import { refApi, type MaterialBrief } from '@/api/refs'

/**
 * 物料远程搜索：按编码前缀/名称/规格搜索，只列启用物料，下拉显示“编码 名称 规格 单位”。
 * types 可限制物料类型，如 ['FINISHED','SEMI_FINISHED']。
 */
const props = defineProps<{ modelValue?: string | string[] | null; multiple?: boolean; types?: string[]; disabled?: boolean; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | string[] | undefined]; select: [m: MaterialBrief | MaterialBrief[] | undefined] }>()

const search = (keyword: string) => refApi.materialSearch({ keyword, types: props.types?.join(',') })
const resolve = (ids: string[]) => refApi.materialSearch({ ids: ids.join(','), status: '' })
const label = (m: MaterialBrief) => `${m.code} ${m.name}`
</script>

<template>
  <RemoteSelect
    :model-value="modelValue"
    :search="search"
    :resolve="resolve"
    :label="label"
    :multiple="multiple"
    :disabled="disabled"
    :placeholder="placeholder ?? '物料编码/名称/规格'"
    @update:model-value="emit('update:modelValue', $event)"
    @select="emit('select', $event as MaterialBrief)"
  >
    <template #option="{ item }">
      <span class="code">{{ item.code }}</span>
      <span>{{ item.name }}</span>
      <span class="sub">{{ item.spec }}</span>
      <span class="uom">{{ item.baseUom }}</span>
    </template>
  </RemoteSelect>
</template>

<style scoped>
.code { display: inline-block; min-width: 110px; font-variant-numeric: tabular-nums; }
.sub { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.uom { float: right; margin-left: 12px; color: var(--el-text-color-secondary); }
</style>
