<script setup lang="ts">
import { computed } from 'vue'

/**
 * 状态步骤条：显示单据标准流程，当前状态高亮。
 * 当前状态不在标准流程中（已关闭、已作废）时显示提示条。
 * <DocSteps :steps="[{status:'DRAFT',label:'草稿'},...]" :current="doc.status" :terminal="{ CLOSED: '已关闭：客户取消' }" />
 */
const props = defineProps<{
  steps: { status: string; label: string }[]
  current?: string
  /** 终止状态 → 提示文字 */
  terminal?: Record<string, string>
}>()

const index = computed(() => props.steps.findIndex((s) => s.status === props.current))
const terminalText = computed(() => (props.current && props.terminal ? props.terminal[props.current] : undefined))
</script>

<template>
  <el-alert v-if="terminalText" :title="terminalText" type="info" :closable="false" show-icon class="doc-steps" />
  <el-steps v-else :active="index" finish-status="success" process-status="process" align-center class="doc-steps">
    <el-step v-for="s in steps" :key="s.status" :title="s.label" />
  </el-steps>
</template>

<style scoped>
.doc-steps { margin-bottom: 12px; }
</style>
