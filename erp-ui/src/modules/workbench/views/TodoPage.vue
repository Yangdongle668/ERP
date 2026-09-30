<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatDateTime } from '@/utils/format'
import {
  TODO_CATEGORY, TODO_PRIORITY, todoApi, WF_INSTANCE_STATUS, WF_TASK_STATUS, type MyInstance, type MyTask, type Todo
} from '../api/workbench'

defineOptions({ name: 'WbTodoPage' })

/**
 * 待办（需求 02-02）：我的待办（审批 + 业务任务，按优先级 → 期限 → 创建时间排序，审批可快捷通过 / 驳回、批量通过）、
 * 我已处理（审批流中我处理过的任务）、我发起的（审批中的可撤回）。
 */
const route = useRoute()
const router = useRouter()
const tab = ref<'pending' | 'done' | 'started'>('pending')

type Query = { category?: string; keyword?: string; overdue?: boolean; status?: string }
const pending = useListPage<Query, Todo>({
  api: (q) => todoApi.page({ ...q, status: 'PENDING' }),
  defaultQuery: () => ({ category: typeof route.query.category === 'string' ? route.query.category : undefined }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'category', label: '类别', type: 'select', options: [{ value: 'APPROVAL', label: '审批' }, { value: 'TASK', label: '任务' }] },
  { prop: 'keyword', label: '关键字', placeholder: '标题 / 单号' },
  { prop: 'overdue', label: '仅超期', type: 'select', options: [{ value: true as never, label: '是' }] }
]
const stay = (t: Todo) => {
  const ms = Date.now() - new Date(t.createdAt.replace(' ', 'T')).getTime()
  const h = Math.floor(ms / 3600000)
  return h >= 24 ? `${Math.floor(h / 24)} 天` : `${h} 小时`
}
const columns: TableColumn<Todo>[] = [
  { prop: 'category', label: '类别', width: 70, type: 'status', statusMap: TODO_CATEGORY },
  { prop: 'bizNo', label: '单号', width: 150, type: 'link', onClick: (r) => open(r) },
  { prop: 'title', label: '标题', minWidth: 240 },
  { prop: 'priority', label: '优先级', width: 70, type: 'status', statusMap: TODO_PRIORITY },
  { prop: 'createdAt', label: '创建时间', width: 150, type: 'datetime' },
  { prop: 'dueTime', label: '期限', width: 170, formatter: (r) => (r.dueTime ? formatDateTime(r.dueTime, true) + (r.overdue ? '（已超期）' : '') : '-') },
  { key: 'stay', label: '停留', width: 80, formatter: (r) => stay(r) }
]
function open(t: Todo) {
  router.push(t.link || t.route || '/workbench/home')
}
async function approve(t: Todo) {
  await todoApi.approve(t.taskId!)
  ElMessage.success('已通过')
  pending.load()
}
async function reject(t: Todo) {
  const { value } = await ElMessageBox.prompt('请填写驳回意见', '驳回', { inputValidator: (v) => (!!v && !!v.trim()) || '请填写驳回意见' })
  await todoApi.reject(t.taskId!, value)
  ElMessage.success('已驳回')
  pending.load()
}
async function markDone(t: Todo) {
  await todoApi.done(t.id)
  ElMessage.success('已标记完成')
  pending.load()
}
async function batchApprove() {
  const tasks = pending.selection.value.filter((t) => t.category === 'APPROVAL' && t.taskId)
  if (!tasks.length) return ElMessage.warning('请勾选审批类待办')
  const { value } = await ElMessageBox.prompt(`批量通过 ${tasks.length} 条审批，统一意见（可空）`, '批量通过', { inputValue: '同意' })
  const r = await todoApi.batchApprove(tasks.map((t) => t.taskId!), value)
  const failed = r.filter((x) => !x.success)
  if (failed.length) {
    ElNotification({ type: 'warning', title: `成功 ${r.length - failed.length} 条，失败 ${failed.length} 条`,
      message: failed.map((f) => `${f.bizNo ?? f.taskId}：${f.message}`).join('；'), duration: 10000 })
  } else ElMessage.success(`已通过 ${r.length} 条`)
  pending.load()
}

// 我已处理
const done = useListPage<object, MyTask>({ api: (q) => todoApi.myDone(q as never), immediate: false })
const doneColumns: TableColumn<MyTask>[] = [
  { prop: 'bizTypeName', label: '单据类型', width: 130 },
  { prop: 'bizNo', label: '单号', width: 150, type: 'link', onClick: (r) => r.detailRoute && router.push(r.detailRoute) },
  { prop: 'title', label: '标题', minWidth: 220 },
  { prop: 'nodeName', label: '节点', width: 130 },
  { prop: 'status', label: '我的处理', width: 90, type: 'status', statusMap: WF_TASK_STATUS },
  { prop: 'initiatorName', label: '发起人', width: 90 },
  { prop: 'handledAt', label: '处理时间', width: 150, type: 'datetime' }
]
// 我发起的
const started = useListPage<{ status?: string }, MyInstance>({ api: (q) => todoApi.myStarted(q as never), immediate: false })
const startedColumns: TableColumn<MyInstance>[] = [
  { prop: 'bizTypeName', label: '单据类型', width: 130 },
  { prop: 'bizNo', label: '单号', width: 150, type: 'link', onClick: (r) => r.detailRoute && router.push(r.detailRoute) },
  { prop: 'title', label: '标题', minWidth: 220 },
  { prop: 'startedAt', label: '发起时间', width: 150, type: 'datetime' },
  { prop: 'currentNodeName', label: '当前节点', width: 130 },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: WF_INSTANCE_STATUS }
]
async function withdraw(i: MyInstance) {
  await ElMessageBox.confirm(`撤回 ${i.bizNo ?? ''} 的审批？`, '撤回', { type: 'warning' })
  await todoApi.withdraw(i.id)
  ElMessage.success('已撤回')
  started.load()
}
watch(tab, (t) => {
  if (t === 'done') done.load()
  if (t === 'started') started.load()
  if (t === 'pending') pending.load()
})
</script>

<template>
  <ErpPage description="审批待办在单据中处理或在此快捷通过；任务类待办完成业务动作后自动消失，催料等提醒可手工标记完成">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane label="我的待办" name="pending" />
        <el-tab-pane label="我已处理" name="done" />
        <el-tab-pane label="我发起的" name="started" />
      </el-tabs>
    </ErpPanel>
    <ErpPanel v-if="tab === 'pending'">
      <template #filter>
        <ErpSearchForm v-model="pending.query" :fields="fields" :loading="pending.loading.value" @search="pending.search" @reset="pending.reset" />
      </template>
      <ErpTable :columns="columns" :data="pending.list.value" :loading="pending.loading.value" selection storage-key="wb.todo"
                @refresh="pending.load" @selection-change="pending.onSelectionChange">
        <template #toolbar><el-button type="primary" @click="batchApprove">批量通过</el-button></template>
        <template #actions="{ row }">
          <el-button link type="primary" @click="open(row as Todo)">处理</el-button>
          <template v-if="row.category === 'APPROVAL' && row.taskId">
            <el-button link type="primary" @click="approve(row as Todo)">通过</el-button>
            <el-button link type="danger" @click="reject(row as Todo)">驳回</el-button>
          </template>
          <el-button v-else-if="row.manual" link @click="markDone(row as Todo)">标记完成</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="pending.query.pageNo" v-model:page-size="pending.query.pageSize" :total="pending.total.value" @change="pending.load" />
    </ErpPanel>
    <ErpPanel v-else-if="tab === 'done'">
      <ErpTable :columns="doneColumns" :data="done.list.value" :loading="done.loading.value" storage-key="wb.todo.done" @refresh="done.load" />
      <ErpPagination v-model:page-no="done.query.pageNo" v-model:page-size="done.query.pageSize" :total="done.total.value" @change="done.load" />
    </ErpPanel>
    <ErpPanel v-else>
      <ErpTable :columns="startedColumns" :data="started.list.value" :loading="started.loading.value" storage-key="wb.todo.started" @refresh="started.load">
        <template #actions="{ row }">
          <el-button v-if="row.status === 'RUNNING'" link type="danger" @click="withdraw(row as MyInstance)">撤回</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="started.query.pageNo" v-model:page-size="started.query.pageSize" :total="started.total.value" @change="started.load" />
    </ErpPanel>
  </ErpPage>
</template>
