<script setup lang="ts">
import { prodOrderApi, searchOrders, type ProdOrderRow } from '../api/production'

/** 生产订单远程搜索（单号），statuses 为 prodStatus 逗号分隔，默认已下达、生产中 */
const props = withDefaults(defineProps<{ modelValue?: string | null; statuses?: string; disabled?: boolean }>(), { statuses: 'RELEASED,IN_PROGRESS' })
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; select: [o: ProdOrderRow | undefined] }>()

const search = (keyword: string) => searchOrders(keyword, props.statuses)
async function resolve(ids: string[]): Promise<ProdOrderRow[]> {
  const list = await Promise.all(ids.map((id) => prodOrderApi.get(id).catch(() => undefined)))
  return list.filter((d) => d !== undefined).map((d) => ({ ...d, progress: '0', overdue: false, docDate: d.createdAt.slice(0, 10) }) as unknown as ProdOrderRow)
}
const label = (o: ProdOrderRow) => `${o.docNo} ${o.materialCode} ${o.materialName}`
</script>

<template>
  <RemoteSelect
    :model-value="modelValue" :search="search" :resolve="resolve" :label="label" :disabled="disabled" placeholder="生产订单号"
    @update:model-value="emit('update:modelValue', $event as string | undefined)" @select="emit('select', $event as ProdOrderRow | undefined)"
  >
    <template #option="{ item }">
      <span class="code">{{ item.docNo }}</span><span>{{ item.materialCode }} {{ item.materialName }}</span>
    </template>
  </RemoteSelect>
</template>

<style scoped>
.code { display: inline-block; min-width: 150px; margin-right: var(--erp-space-2); color: var(--erp-color-text-secondary); }
</style>
