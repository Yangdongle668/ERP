<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { refApi, type BatchBrief } from '@/api/refs'
import { formatDate, formatQty } from '@/utils/format'

/**
 * 批次选择：按物料 + 仓库列出可用批次（批次号、可用数量、生产日期、到期日）。
 * rule 为 FIFO（按入库日期）或 FEFO（按到期日），排在第一位的是推荐批次；冻结批次不可选。
 */
const props = withDefaults(defineProps<{
  modelValue?: string | null
  materialId?: string | null
  warehouseId?: string | null
  rule?: 'FIFO' | 'FEFO'
  precision?: number
  disabled?: boolean
}>(), { rule: 'FIFO', precision: 4 })
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; select: [b: BatchBrief | undefined] }>()

const batches = ref<BatchBrief[]>([])
watch(() => [props.materialId, props.warehouseId], async () => {
  batches.value = props.materialId && props.warehouseId
    ? await refApi.batchAvailable(props.materialId, props.warehouseId).catch(() => [])
    : []
}, { immediate: true })

const sorted = computed(() => {
  const key = (b: BatchBrief) => (props.rule === 'FEFO' ? b.expiryDate || '9999-12-31' : b.inDate || b.productionDate || '')
  return [...batches.value].sort((a, b) => key(a).localeCompare(key(b)))
})

function onChange(v: string | undefined) {
  emit('update:modelValue', v || undefined)
  emit('select', batches.value.find((b) => b.batchNo === v))
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? undefined"
    :disabled="disabled || !materialId || !warehouseId"
    :placeholder="materialId && warehouseId ? '批次' : '请先选择物料和仓库'"
    filterable
    clearable
    popper-class="batch-select-popper"
    @update:model-value="onChange"
  >
    <el-option v-for="(b, i) in sorted" :key="b.batchNo" :value="b.batchNo" :label="b.batchNo" :disabled="b.frozen">
      <span class="no">{{ b.batchNo }}<ErpBadge v-if="i === 0" type="success" :dot="false" class="tag">推荐</ErpBadge><ErpBadge v-if="b.frozen" type="danger" :dot="false" class="tag">冻结</ErpBadge></span>
      <span class="meta">可用 {{ formatQty(b.availableQty, precision) }} · 生产 {{ formatDate(b.productionDate) }} · 到期 {{ formatDate(b.expiryDate) }}</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.no { display: inline-block; min-width: 160px; }
.tag { margin-left: 4px; }
.meta { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
</style>
