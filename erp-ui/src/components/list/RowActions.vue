<script setup lang="ts">
import type { RowAction } from '../types'
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 表格操作列按钮（UI 设计规范 T1）：按权限和状态过滤，最多直接显示 3 个，其余放入“更多”下拉。
 * <RowActions :actions="[{ label: '编辑', permission: 'x:y:update', handler: () => edit(row) }, ...]" />
 */
const props = withDefaults(defineProps<{ actions: RowAction[]; max?: number }>(), { max: 3 })
const store = useUserStore()

const visible = computed(() => props.actions.filter((a) => a.visible !== false && store.hasPermission(a.permission)))
const direct = computed(() => (visible.value.length <= props.max ? visible.value : visible.value.slice(0, props.max - 1)))
const more = computed(() => (visible.value.length <= props.max ? [] : visible.value.slice(props.max - 1)))

async function run(a: RowAction) {
  if (a.confirm) {
    const ok = await ElMessageBox.confirm(a.confirm, '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) return
  }
  await a.handler()
}
</script>

<template>
  <span class="row-actions">
    <el-button v-for="a in direct" :key="a.label" link :type="a.danger ? 'danger' : 'primary'" @click.stop="run(a)">{{ a.label }}</el-button>
    <el-dropdown v-if="more.length" trigger="click" @command="(i: number) => run(more[i])">
      <el-button link type="primary" @click.stop>更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="(a, i) in more" :key="a.label" :command="i" :class="{ danger: a.danger }">{{ a.label }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </span>
</template>

<style scoped>
.row-actions { display: inline-flex; align-items: center; gap: 12px; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.danger { color: var(--el-color-danger); }
</style>
