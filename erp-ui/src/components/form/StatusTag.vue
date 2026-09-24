<script setup lang="ts">
import { computed } from 'vue'
import type { StatusMap } from '../types'
import { COMMON_STATUS } from '../status'

/**
 * 状态标签（UI 设计规范 7.3）。map 未提供时使用通用状态（启用/停用/草稿/已审核…）。
 * <StatusTag :value="row.status" :map="SO_STATUS" />
 */
const props = defineProps<{ value?: string | null; map?: StatusMap }>()

const info = computed(() => {
  if (!props.value) return undefined
  return (props.map ?? COMMON_STATUS)[props.value] ?? COMMON_STATUS[props.value]
})
</script>

<template>
  <el-tag v-if="info" :type="info.type || undefined" :effect="info.plain ? 'plain' : 'light'" disable-transitions>{{ info.label }}</el-tag>
  <span v-else-if="value">{{ value }}</span>
  <span v-else>-</span>
</template>
