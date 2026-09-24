<script setup lang="ts">
/**
 * 内容面板（UI 设计规范 3.4）：白底、1px 边框、8px 圆角、无阴影。只用于有独立信息边界的内容：
 * 一个列表页通常只有一个面板（filter 插槽放查询条件，默认插槽放工具栏 + 表格 + 分页），不要在面板里再套卡片。
 *
 * <ErpPanel title="汇率" description="1 外币 = x 本位币">
 *   <template #extra><el-button>批量录入</el-button></template>
 *   <template #filter><ErpSearchForm ... /></template>
 *   <ErpTable ... />
 * </ErpPanel>
 */
defineProps<{
  title?: string
  description?: string
  /** 内容区无内边距（树、导航列表、整幅表格） */
  flush?: boolean
}>()
</script>

<template>
  <section :class="['erp-panel', { 'is-flush': flush }]">
    <header v-if="title || $slots.title || $slots.extra" class="erp-panel__header">
      <div class="erp-panel__heading">
        <h2 class="erp-panel__title"><slot name="title">{{ title }}</slot></h2>
        <span v-if="description" class="erp-panel__desc">{{ description }}</span>
      </div>
      <div v-if="$slots.extra" class="erp-panel__extra"><slot name="extra" /></div>
    </header>
    <div v-if="$slots.filter" class="erp-panel__filter"><slot name="filter" /></div>
    <div class="erp-panel__body"><slot /></div>
    <footer v-if="$slots.footer" class="erp-panel__footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.erp-panel { background: var(--erp-color-surface); border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-card); min-width: 0; }
.erp-panel__header { display: flex; align-items: center; justify-content: space-between; gap: var(--erp-space-3); padding: 14px 20px 0; min-height: 46px; }
.erp-panel__heading { display: flex; align-items: baseline; gap: var(--erp-space-2); min-width: 0; }
.erp-panel__title { margin: 0; font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); line-height: 24px; color: var(--erp-color-text); }
.erp-panel__desc { font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-tertiary); }
.erp-panel__extra { display: flex; align-items: center; gap: var(--erp-space-2); }
.erp-panel__extra :deep(.el-button + .el-button) { margin-left: 0; }
.erp-panel__filter { padding: 16px 20px 0; border-bottom: 1px solid var(--erp-color-border); }
.erp-panel__body { padding: 16px 20px 20px; }
.erp-panel.is-flush > .erp-panel__body { padding: 0; }
.erp-panel__footer { padding: 12px 20px; border-top: 1px solid var(--erp-color-border); display: flex; justify-content: flex-end; gap: var(--erp-space-2); }
</style>
