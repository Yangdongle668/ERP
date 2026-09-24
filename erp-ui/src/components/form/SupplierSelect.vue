<script setup lang="ts">
import RemoteSelect from './RemoteSelect.vue'
import { refApi, type SupplierBrief } from '@/api/refs'

/**
 * 供应商远程搜索（编码/名称/简称）。statuses 限制供应商状态，如下单只能选合格供应商 ['QUALIFIED']。
 * 选中后 select 事件带出供应商默认币别、付款条件、税率、采购员。
 */
const props = defineProps<{ modelValue?: string | string[] | null; multiple?: boolean; statuses?: string[]; disabled?: boolean; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | string[] | undefined]; select: [c: SupplierBrief | SupplierBrief[] | undefined] }>()

const search = (keyword: string) => refApi.supplierSearch({ keyword, statuses: props.statuses?.join(',') })
const resolve = (ids: string[]) => refApi.supplierSearch({ ids: ids.join(',') })
const label = (c: SupplierBrief) => `${c.code} ${c.shortName || c.name}`
</script>

<template>
  <RemoteSelect
    :model-value="modelValue"
    :search="search"
    :resolve="resolve"
    :label="label"
    :multiple="multiple"
    :disabled="disabled"
    :placeholder="placeholder ?? '供应商编码/名称/简称'"
    @update:model-value="emit('update:modelValue', $event)"
    @select="emit('select', $event as SupplierBrief)"
  >
    <template #option="{ item }">
      <span class="code">{{ item.code }}</span><span>{{ item.name }}</span>
    </template>
  </RemoteSelect>
</template>

<style scoped>
.code { display: inline-block; min-width: 90px; }
</style>
