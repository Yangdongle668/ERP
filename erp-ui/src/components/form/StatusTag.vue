<script setup lang="ts">
import { computed } from 'vue'
import type { StatusMap } from '../types'
import { COMMON_STATUS } from '../status'
import ErpBadge from '../base/ErpBadge.vue'

/**
 * 状态标签（UI 设计规范 7.3）：以 ErpBadge 显示。map 未提供时使用通用状态（启用/停用/草稿/已审核…）。
 * <StatusTag :value="row.status" :map="SO_STATUS" />
 */
const props = defineProps<{ value?: string | null; map?: StatusMap }>()

const info = computed(() => {
  if (!props.value) return undefined
  return (props.map ?? COMMON_STATUS)[props.value] ?? COMMON_STATUS[props.value]
})
</script>

<template>
  <ErpBadge v-if="info" :type="info.type" :plain="info.plain">{{ info.label }}</ErpBadge>
  <span v-else-if="value">{{ value }}</span>
  <span v-else class="text-muted">-</span>
</template>
