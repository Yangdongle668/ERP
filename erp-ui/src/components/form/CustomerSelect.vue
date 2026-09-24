<script setup lang="ts">
import RemoteSelect from './RemoteSelect.vue'
import { refApi, type CustomerBrief } from '@/api/refs'

/**
 * 客户远程搜索（编码/名称/简称），按数据权限过滤。statuses 限制客户状态，如下单只能选正式客户 ['ACTIVE']。
 * 选中后 select 事件带出客户默认币别、付款条件、贸易条款、税率、业务员。
 */
const props = defineProps<{ modelValue?: string | string[] | null; multiple?: boolean; statuses?: string[]; disabled?: boolean; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | string[] | undefined]; select: [c: CustomerBrief | CustomerBrief[] | undefined] }>()

const search = (keyword: string) => refApi.customerSearch({ keyword, statuses: props.statuses?.join(',') })
const resolve = (ids: string[]) => refApi.customerSearch({ ids: ids.join(',') })
const label = (c: CustomerBrief) => `${c.code} ${c.shortName || c.name}`
</script>

<template>
  <RemoteSelect
    :model-value="modelValue"
    :search="search"
    :resolve="resolve"
    :label="label"
    :multiple="multiple"
    :disabled="disabled"
    :placeholder="placeholder ?? '客户编码/名称/简称'"
    @update:model-value="emit('update:modelValue', $event)"
    @select="emit('select', $event as CustomerBrief)"
  >
    <template #option="{ item }">
      <span class="code">{{ item.code }}</span><span>{{ item.name }}</span>
    </template>
  </RemoteSelect>
</template>

<style scoped>
.code { display: inline-block; min-width: 90px; }
</style>
