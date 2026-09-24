<script setup lang="ts">
import { ref } from 'vue'
import { loadAvailable, printDocuments, type Available } from '@/utils/print/printDocs'
import { useUserStore } from '@/stores/user'

/**
 * 打印按钮（需求 01-09 第 3.3 节、UI 设计规范 9.3）：主按钮用当前语言的默认模板打印；下拉列出该单据类型的全部启用模板。
 * 详情页传单个 ID；列表页传勾选的 ID（函数形式），批量打印最多 50 张，每张另起一页。
 *
 * <PrintButton biz-type="SAL_ORDER" :ids="[id]" permission="sal:order:print" />
 */
const props = defineProps<{ bizType: string; ids: string[] | (() => string[]); permission?: string; label?: string }>()
const store = useUserStore()
const available = ref<Available>()
const loading = ref(false)

const currentIds = () => (typeof props.ids === 'function' ? props.ids() : props.ids)

async function loadTemplates(visible: boolean) {
  if (!visible || available.value) return
  available.value = await loadAvailable(props.bizType).catch(() => undefined)
}

async function print(templateId?: string) {
  loading.value = true
  try {
    await printDocuments({ bizType: props.bizType, ids: currentIds(), templateId, language: store.user?.language === 'en' ? 'en' : 'zh-CN', available: available.value })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dropdown v-if="store.hasPermission(permission)" split-button trigger="click" :disabled="loading" @click="print()" @visible-change="loadTemplates" @command="print">
    <el-icon v-if="loading" class="is-loading"><Loading /></el-icon><el-icon v-else><Printer /></el-icon><span class="label">{{ label ?? '打印' }}</span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="t in available?.templates ?? []" :key="t.id" :command="t.id">
          {{ t.name }}<span class="meta">{{ t.language === 'en' ? 'English' : '中文' }}{{ t.isDefault ? ' · 默认' : '' }}</span>
        </el-dropdown-item>
        <el-dropdown-item v-if="available && !available.templates.length" disabled>没有可用的打印模板</el-dropdown-item>
        <el-dropdown-item v-if="!available" disabled>加载中…</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.label { margin-left: 4px; }
.meta { margin-left: 12px; font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
</style>
