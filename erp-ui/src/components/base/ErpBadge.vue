<script setup lang="ts">
import type { TagColor } from '../types'

/**
 * 状态徽标（UI 设计规范 5.1 Badge、7.3 状态颜色）：圆点 + 文字，浅底色。
 * plain：终态/停用类状态，只显示灰色圆点与次要文字，降低视觉权重。
 * <ErpBadge type="success">启用</ErpBadge>
 */
withDefaults(defineProps<{ type?: TagColor; plain?: boolean; dot?: boolean }>(), { type: 'info', plain: false, dot: true })
</script>

<template>
  <span :class="['erp-badge', `erp-badge--${type || 'info'}`, { 'is-plain': plain }]">
    <i v-if="dot" class="erp-badge__dot" />
    <span class="erp-badge__text"><slot /></span>
  </span>
</template>

<style scoped>
.erp-badge {
  display: inline-flex; align-items: center; gap: 6px; height: 22px; padding: 0 8px; border-radius: var(--erp-radius-xs);
  font-size: var(--erp-font-size-caption); line-height: 22px; white-space: nowrap; vertical-align: middle;
  color: var(--badge-color); background: var(--badge-bg);
}
.erp-badge__dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.erp-badge--primary { --badge-color: var(--el-color-primary); --badge-bg: var(--erp-color-primary-bg); }
.erp-badge--success { --badge-color: var(--el-color-success); --badge-bg: var(--erp-color-success-bg); }
.erp-badge--warning { --badge-color: var(--el-color-warning); --badge-bg: var(--erp-color-warning-bg); }
.erp-badge--danger { --badge-color: var(--el-color-danger); --badge-bg: var(--erp-color-error-bg); }
.erp-badge--info, .erp-badge--, .erp-badge--default { --badge-color: var(--erp-color-text-secondary); --badge-bg: var(--erp-color-hover); }
.erp-badge.is-plain { background: transparent; padding: 0; color: var(--erp-color-text-tertiary); }
.erp-badge.is-plain .erp-badge__dot { background: var(--erp-color-text-disabled); }
</style>
