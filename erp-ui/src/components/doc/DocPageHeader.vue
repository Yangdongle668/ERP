<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { DocAction, StatusMap } from '../types'
import StatusTag from '../form/StatusTag.vue'
import ReasonDialog from './ReasonDialog.vue'
import { useUserStore } from '@/stores/user'

/**
 * 单据页头（UI 设计规范 T4/T5）：返回、标题、状态标签、按钮区（吸顶）。
 * 按钮按权限和 visible() 显示；confirm 二次确认；reasonRequired 打开原因弹窗；执行中 loading 防重复提交。
 * 按钮顺序：数组顺序从左到右，主操作放最后并设 type=primary。
 */
const props = defineProps<{
  title: string
  status?: string
  statusMap?: StatusMap
  actions?: DocAction[]
  /** 返回前的确认（如未保存提醒），返回 false 取消 */
  beforeBack?: () => Promise<boolean> | boolean
}>()
const emit = defineEmits<{ back: [] }>()

const store = useUserStore()
const reasonRef = ref<InstanceType<typeof ReasonDialog>>()
const running = ref('')

const visibleActions = computed(() => (props.actions ?? []).filter((a) => store.hasPermission(a.permission) && (a.visible ? a.visible() : true)))

async function run(a: DocAction) {
  if (running.value) return
  let reason: string | undefined
  if (a.reasonRequired) {
    reason = await reasonRef.value?.open({ title: a.reasonTitle ?? `${a.label}原因`, tip: a.confirm, options: a.reasonOptions })
    if (!reason) return
  } else if (a.confirm) {
    const ok = await ElMessageBox.confirm(a.confirm, '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) return
  }
  running.value = a.key
  try {
    await a.handler(reason)
  } finally {
    running.value = ''
  }
}

async function back() {
  if (props.beforeBack && !(await props.beforeBack())) return
  emit('back')
}
</script>

<template>
  <div class="doc-page-header">
    <div class="left">
      <el-button link icon="ArrowLeft" class="back" @click="back" />
      <span class="title">{{ title }}</span>
      <StatusTag v-if="status" :value="status" :map="statusMap" />
      <slot name="extra" />
    </div>
    <div class="right">
      <slot name="actions-prefix" />
      <el-button
        v-for="a in visibleActions"
        :key="a.key"
        :type="a.type === 'primary' ? 'primary' : a.type === 'danger' ? 'danger' : 'default'"
        :plain="a.type === 'danger'"
        :loading="running === a.key"
        :disabled="!!running && running !== a.key"
        @click="run(a)"
      >{{ a.label }}</el-button>
      <slot name="actions-suffix" />
    </div>
    <ReasonDialog ref="reasonRef" />
  </div>
</template>

<style scoped>
.doc-page-header {
  position: sticky; top: -16px; z-index: 10; display: flex; justify-content: space-between; align-items: center;
  margin: -16px -16px 12px; padding: 12px 16px; background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-lighter);
}
.left, .right { display: flex; align-items: center; gap: 8px; }
.right :deep(.el-button + .el-button) { margin-left: 0; }
.back { font-size: 18px; }
.title { font-size: 18px; font-weight: 600; }
</style>
