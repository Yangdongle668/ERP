<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'

/**
 * 页面骨架（UI 设计规范 3.4 信息层级）：所有业务页面的根组件，保证每个页面的布局逻辑一致。
 *
 *   面包屑（12px）
 *   页面标题（20px / 600） + meta 插槽（状态徽标等）            actions 插槽（页面级主操作）
 *   描述（13px 次要文字）
 *   ─ 内容：默认插槽，子元素之间 20px 间距（通常是 ErpPanel）
 *
 * 单据页（T4/T5）用 header 插槽放 DocPageHeader；表单页（T3）用 back + sticky + actions。
 *
 * <ErpPage description="维护币别与每日汇率">
 *   <template #actions><el-button type="primary">新建</el-button></template>
 *   <ErpPanel>...</ErpPanel>
 * </ErpPage>
 */
const props = withDefaults(defineProps<{
  /** 默认取页签标题（页面设置的单号等）或菜单标题 */
  title?: string
  description?: string
  /** 显示返回按钮：true 时返回上一页；字符串为返回的路由 */
  back?: boolean | string
  /** 不显示面包屑 */
  noBreadcrumb?: boolean
  /** 内容区占满剩余高度（左右分栏、树形页面） */
  fill?: boolean
  /** 页头吸顶（表单页：保存按钮始终可见） */
  sticky?: boolean
  /** 自定义返回（如未保存确认）；未提供时返回上一页或 back 指定的路由 */
  onBack?: () => unknown
}>(), { back: false })

const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()

const pageTitle = computed(() => props.title ?? tabs.customTitles[tabKeyOf(route)] ?? String(route.meta.title ?? ''))
const crumbs = computed(() => [route.meta.module, route.meta.parentTitle].filter(Boolean).map(String))

function goBack() {
  if (props.onBack) props.onBack()
  else if (typeof props.back === 'string') router.push(props.back)
  else if (window.history.length > 1) router.back()
}
</script>

<template>
  <div :class="['erp-page', { 'is-fill': fill, 'is-sticky': sticky }]">
    <slot v-if="$slots.header" name="header" />
    <header v-else class="erp-page__header">
      <div class="erp-page__heading">
        <nav v-if="!noBreadcrumb && crumbs.length" class="erp-page__crumbs" aria-label="面包屑">
          <template v-for="c in crumbs" :key="c"><span>{{ c }}</span><span class="sep">/</span></template>
          <span class="current">{{ pageTitle }}</span>
        </nav>
        <div class="erp-page__title-row">
          <button v-if="back" type="button" class="erp-page__back" aria-label="返回" @click="goBack"><el-icon><ArrowLeft /></el-icon></button>
          <h1 class="erp-page__title">{{ pageTitle }}</h1>
          <slot name="meta" />
        </div>
        <p v-if="description || $slots.description" class="erp-page__desc"><slot name="description">{{ description }}</slot></p>
      </div>
      <div v-if="$slots.actions" class="erp-page__actions"><slot name="actions" /></div>
    </header>
    <div class="erp-page__body"><slot /></div>
  </div>
</template>

<style scoped>
.erp-page { display: flex; flex-direction: column; gap: var(--erp-space-4); min-width: 0; }
.erp-page.is-fill { height: 100%; }
.erp-page.is-sticky > .erp-page__header {
  position: sticky; top: calc(-1 * var(--erp-page-padding-y)); z-index: 10; background: var(--erp-color-bg);
  margin: calc(-1 * var(--erp-page-padding-y)) 0 0; padding: var(--erp-page-padding-y) 0 var(--erp-space-3);
}
.erp-page__header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--erp-space-4); flex-wrap: wrap; }
.erp-page__heading { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.erp-page__crumbs { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); line-height: 18px; display: flex; gap: 6px; }
.erp-page__crumbs .sep { color: var(--erp-color-text-disabled); }
.erp-page__crumbs .current { color: var(--erp-color-text-secondary); }
.erp-page__title-row { display: flex; align-items: center; gap: var(--erp-space-3); min-width: 0; }
.erp-page__title {
  margin: 0; font-size: var(--erp-font-size-page-title); font-weight: var(--erp-font-weight-semibold); line-height: 28px;
  color: var(--erp-color-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.erp-page__back {
  display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; margin-left: -4px; border: none;
  border-radius: var(--erp-radius-control); background: transparent; color: var(--erp-color-text-secondary); cursor: pointer; font-size: var(--erp-icon-size-lg);
  transition: background-color var(--erp-duration-fast) var(--erp-ease);
}
.erp-page__back:hover { background: var(--erp-color-hover); color: var(--erp-color-text); }
.erp-page__desc { margin: 0; font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); line-height: 20px; }
.erp-page__actions { display: flex; align-items: center; gap: var(--erp-space-2); flex-shrink: 0; }
.erp-page__actions :deep(.el-button + .el-button) { margin-left: 0; }
.erp-page__body { display: flex; flex-direction: column; gap: var(--erp-section-gap); min-width: 0; }
.erp-page.is-fill .erp-page__body { flex: 1; min-height: 0; }
</style>
