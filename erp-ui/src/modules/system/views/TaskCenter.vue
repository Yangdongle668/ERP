<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SearchField, StatusMap, TableColumn } from '@/components'
import { download } from '@/api/http'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { elapsedBetween, formatElapsed, toDateString } from '@/utils/format'
import { TASK_TYPE_OPTIONS, taskApi, type TaskQuery, type TaskRow } from '../api/task'

defineOptions({ name: 'SystemTaskCenter' })

/** 任务中心（01-12 第 2 节）：后台导出、导入、运算任务的进度与结果；运行中的任务每 3 秒刷新 */
const store = useUserStore()
const canSeeAll = computed(() => store.hasPermission('system:task:all'))

const TASK_STATUS: StatusMap = {
  WAITING: { label: '等待中', type: 'warning' },
  RUNNING: { label: '运行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}

function lastDays(n: number): [string, string] {
  const from = new Date(Date.now() - (n - 1) * 86400000)
  return [`${toDateString(from)} 00:00:00`, `${toDateString(new Date())} 23:59:59`]
}

const { query, list, total, loading, load, search, reset } = useListPage<Omit<TaskQuery, 'pageNo' | 'pageSize'>, TaskRow>({
  api: (q) => taskApi.page(q as TaskQuery),
  defaultQuery: () => ({ time: lastDays(7), all: false })
})

const fields: SearchField[] = [
  { prop: 'taskType', label: '类型', type: 'select', options: TASK_TYPE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: Object.entries(TASK_STATUS).map(([value, s]) => ({ value, label: s.label })) },
  { prop: 'time', label: '提交时间', type: 'datetimerange' }
]

const columns = computed<TableColumn<TaskRow>[]>(() => [
  { prop: 'name', label: '任务名称', minWidth: 200 },
  { prop: 'taskType', label: '类型', width: 100, type: 'enum', options: TASK_TYPE_OPTIONS },
  { prop: 'status', label: '状态', width: 180, slot: true },
  ...(query.all ? [{ prop: 'submittedByName', label: '提交人', width: 100 } as TableColumn<TaskRow>] : []),
  { prop: 'createdAt', label: '提交时间', width: 150, type: 'datetime' },
  { prop: 'finishedAt', label: '完成时间', width: 150, type: 'datetime' },
  { key: 'elapsed', label: '耗时', width: 100, align: 'right', formatter: (r) => formatElapsed(elapsedBetween(r.startedAt, r.finishedAt)) },
  { prop: 'resultMessage', label: '结果说明', minWidth: 200, slot: true }
])

// ---------- 自动刷新 ----------
const active = ref(true)
let timer = 0
const hasPending = computed(() => list.value.some((t) => t.status === 'WAITING' || t.status === 'RUNNING'))
function schedule() {
  window.clearTimeout(timer)
  if (active.value && hasPending.value) timer = window.setTimeout(() => load(), 3000)
}
watch(list, schedule)
onMounted(schedule)
let firstActivation = true
onActivated(() => {
  active.value = true
  if (firstActivation) firstActivation = false // 首次由 useListPage 在挂载时加载
  else load()
})
onDeactivated(() => {
  active.value = false
  window.clearTimeout(timer)
})
onBeforeUnmount(() => window.clearTimeout(timer))

// ---------- 操作 ----------
async function downloadResult(t: TaskRow) {
  await download(`/system/files/${t.resultFileId}/download`, undefined, `${t.name}.xlsx`)
}

async function cancel(t: TaskRow) {
  await taskApi.cancel(t.id)
  ElMessage.success('已取消')
  load()
}

function showError(t: TaskRow) {
  ElMessageBox.alert(t.errorMessage || '未知错误', `任务失败：${t.name}`, { type: 'error', confirmButtonText: '知道了' })
}

const asTask = (r: unknown) => r as TaskRow
</script>

<template>
  <ErpPage description="后台执行的导出、导入与运算任务；结果文件保留 7 天，任务完成时会收到消息通知">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.task" :actions-width="140" empty-text="近 7 天没有后台任务" @refresh="load">
        <template v-if="canSeeAll" #toolbar>
          <el-checkbox v-model="query.all" @change="search">查看所有人的任务</el-checkbox>
        </template>
        <template #col-status="{ row }">
          <div v-if="asTask(row).status === 'RUNNING'" class="progress">
            <el-progress :percentage="asTask(row).progress" :stroke-width="6" />
          </div>
          <StatusTag v-else :value="asTask(row).status" :map="TASK_STATUS" />
        </template>
        <template #col-resultMessage="{ row }">
          <span v-if="asTask(row).status === 'FAILED'" class="text-danger">{{ asTask(row).errorMessage || '执行失败' }}</span>
          <span v-else>{{ asTask(row).resultMessage || '-' }}</span>
        </template>
        <template #actions="{ row }">
          <ErpBadge v-if="asTask(row).resultExpired" :dot="false">已过期</ErpBadge>
          <RowActions v-else :actions="[
            { label: '下载结果', visible: !!asTask(row).resultFileId, handler: () => downloadResult(asTask(row)) },
            { label: '取消', visible: asTask(row).status === 'WAITING', confirm: `确定取消任务「${asTask(row).name}」吗？`, handler: () => cancel(asTask(row)) },
            { label: '查看错误', visible: asTask(row).status === 'FAILED', handler: () => showError(asTask(row)) }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.progress { width: 150px; }
</style>
