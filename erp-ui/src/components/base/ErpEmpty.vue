<script setup lang="ts">
/**
 * 空状态（UI 设计规范 5.1 Empty State）：线性图标 + 说明 + 可选操作。表格、列表、面板统一使用，不使用插画。
 * <ErpEmpty description="暂无数据"><el-button>新建</el-button></ErpEmpty>
 */
withDefaults(defineProps<{ description?: string; icon?: string; compact?: boolean }>(), { description: '暂无数据', icon: 'Empty' })
</script>

<template>
  <div :class="['erp-empty', { 'is-compact': compact }]">
    <el-icon class="erp-empty__icon"><component :is="icon" /></el-icon>
    <div class="erp-empty__text">{{ description }}</div>
    <div v-if="$slots.default" class="erp-empty__actions"><slot /></div>
  </div>
</template>

<style scoped>
.erp-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40px 16px; gap: 8px; line-height: 1.5; }
.erp-empty.is-compact { padding: 20px 16px; }
.erp-empty__icon { font-size: 32px; color: var(--erp-color-text-disabled); }
.erp-empty.is-compact .erp-empty__icon { font-size: 24px; }
.erp-empty__text { font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-tertiary); }
.erp-empty__actions { margin-top: 4px; display: flex; gap: 8px; }
</style>
