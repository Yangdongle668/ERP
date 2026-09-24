<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import { formatDateTime } from '@/utils/format'

/**
 * 待办铃铛：角标显示待办数量（>99 显示 99+），每 60 秒刷新；点击显示最近 10 条待办，底部“查看全部”。
 * 数据来自工作台模块（02-工作台 / 02-待办与审批）；接口未就绪时只显示铃铛。
 */
interface Todo { id: string; title: string; createdAt: string; link?: string }

const router = useRouter()
const count = ref(0)
const list = ref<Todo[]>([])
let timer = 0

async function refreshCount() {
  try {
    count.value = await http.get<number>('/workbench/todos/count', undefined, { silent: true })
  } catch {
    count.value = 0
  }
}

async function loadList(visible: boolean) {
  if (!visible) return
  try {
    const page = await http.get<{ list: Todo[] }>('/workbench/todos', { status: 'PENDING', pageNo: 1, pageSize: 10 }, { silent: true })
    list.value = page.list
  } catch {
    list.value = []
  }
}

function open(t: Todo) {
  router.push(t.link || '/workbench/todo')
}

onMounted(() => {
  refreshCount()
  timer = window.setInterval(refreshCount, 60000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadList(true)">
    <template #reference>
      <button type="button" class="bell" aria-label="待办">
        <el-badge :value="count" :max="99" :hidden="!count"><el-icon><Bell /></el-icon></el-badge>
      </button>
    </template>
    <div class="todo-head">待办</div>
    <ErpEmpty v-if="!list.length" description="暂无待办" compact />
    <div v-for="t in list" :key="t.id" class="todo" @click="open(t)">
      <div class="title">{{ t.title }}</div>
      <div class="time">{{ formatDateTime(t.createdAt, true) }}</div>
    </div>
    <div class="all"><el-button link type="primary" @click="router.push('/workbench/todo')">查看全部</el-button></div>
  </el-popover>
</template>

<style scoped>
.bell {
  display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: none; padding: 0; cursor: pointer;
  border-radius: var(--erp-radius-control); background: transparent; color: var(--erp-color-text-secondary); font-size: var(--erp-icon-size-lg);
  transition: background-color var(--erp-duration-fast) var(--erp-ease);
}
.bell:hover { background: var(--erp-color-hover); color: var(--erp-color-text); }
.bell :deep(.el-badge) { display: inline-flex; }
.todo-head { font-size: var(--erp-font-size-body); font-weight: var(--erp-font-weight-semibold); padding: 0 4px 8px; border-bottom: 1px solid var(--erp-color-border); }
.todo { padding: 8px 4px; border-bottom: 1px solid var(--erp-color-border-light); cursor: pointer; border-radius: var(--erp-radius-xs); }
.todo:hover { background: var(--erp-color-hover); }
.todo .title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.todo .time { font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.all { text-align: center; margin-top: 8px; }
</style>
