<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchBlob, http } from '@/api/http'

/**
 * 打印按钮（UI 设计规范 9.3，模板管理见 01-系统管理 / 09-打印模板）：
 * 选择模板（默认模板在最前）→ 新窗口预览（PDF）→ 打印或另存。ids 为多个时批量打印。
 */
interface TemplateBrief { id: string; name: string; isDefault: boolean; lang?: string }

const props = defineProps<{ bizType: string; ids: string[] | (() => string[]); permission?: string; label?: string }>()
const templates = ref<TemplateBrief[]>([])
const loading = ref(false)

function currentIds() {
  return typeof props.ids === 'function' ? props.ids() : props.ids
}

async function loadTemplates(visible: boolean) {
  if (!visible || templates.value.length) return
  try {
    const list = await http.get<TemplateBrief[]>('/system/print-templates/simple', { bizType: props.bizType }, { silent: true })
    templates.value = [...list].sort((a, b) => Number(b.isDefault) - Number(a.isDefault))
  } catch {
    templates.value = []
  }
}

async function print(templateId?: string) {
  const ids = currentIds()
  if (!ids.length) {
    ElMessage.warning('请先勾选数据')
    return
  }
  loading.value = true
  // 先打开窗口，避免浏览器拦截异步弹窗
  const win = window.open('', '_blank')
  try {
    const blob = await fetchBlob('/system/print/render', { bizType: props.bizType, templateId, ids: ids.join(',') })
    const url = URL.createObjectURL(blob)
    if (win) win.location.href = url
    else window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 120000)
  } catch {
    win?.close()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dropdown v-perm="permission" split-button :loading="loading" @click="print(templates[0]?.id)" @visible-change="loadTemplates" @command="print">
    {{ label ?? '打印' }}
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="t in templates" :key="t.id" :command="t.id">{{ t.name }}<span v-if="t.isDefault" class="def">（默认）</span></el-dropdown-item>
        <el-dropdown-item v-if="!templates.length" disabled>使用默认模板</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.def { color: var(--erp-color-text-secondary); }
</style>
