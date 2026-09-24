<script setup lang="ts">
/**
 * 图标按钮（UI 设计规范 5.1 Button）：工具栏右侧的导入、导出、列设置、刷新等次要操作。32px 方形、无边框、悬停灰底，带提示。
 * 没有 permission 权限时不显示（不置灰）。
 * <ErpIconButton icon="Refresh" tooltip="刷新" @click="load" />
 */
import { useUserStore } from '@/stores/user'

defineOptions({ inheritAttrs: false })
defineProps<{ icon: string; tooltip?: string; loading?: boolean; disabled?: boolean; active?: boolean; permission?: string }>()
const store = useUserStore()
</script>

<template>
  <el-tooltip v-if="store.hasPermission(permission)" :content="tooltip" :disabled="!tooltip" placement="top" :show-after="300">
    <button
      v-bind="$attrs"
      type="button"
      :class="['erp-icon-btn', { 'is-active': active }]"
      :disabled="disabled || loading"
      :aria-label="tooltip"
    >
      <el-icon :class="{ 'is-loading': loading }"><component :is="loading ? 'Loading' : icon" /></el-icon>
    </button>
  </el-tooltip>
</template>

<style scoped>
.erp-icon-btn {
  display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; padding: 0; border: none;
  border-radius: var(--erp-radius-control); background: transparent; color: var(--erp-color-text-secondary); cursor: pointer;
  font-size: var(--erp-icon-size); transition: background-color var(--erp-duration-fast) var(--erp-ease), color var(--erp-duration-fast) var(--erp-ease);
}
.erp-icon-btn:hover:not(:disabled) { background: var(--erp-color-hover); color: var(--erp-color-text); }
.erp-icon-btn:active:not(:disabled) { background: var(--erp-color-active); }
.erp-icon-btn.is-active { color: var(--el-color-primary); background: var(--erp-color-primary-bg); }
.erp-icon-btn:focus-visible { outline: 2px solid var(--el-color-primary-light-5); outline-offset: 1px; }
.erp-icon-btn:disabled { color: var(--erp-color-text-disabled); cursor: not-allowed; }
</style>
