<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http } from '@/api/http'
import type { SearchField, StatusMap, TableColumn, WfInstance } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatDuration, toDateString } from '@/utils/format'
import { INSTANCE_STATUS_OPTIONS, workflowApi, type BizType, type InstanceQuery, type MonitorRow } from '../api/workflow'

defineOptions({ name: 'SystemWorkflowInstanceList' })

/** 审批监控（需求 08 第 4.2 节）：查看进行中的审批、转交、终止；当前节点停留超过 24 小时橙色、72 小时红色 */
const router = useRouter()
const bizTypes = ref<BizType[]>([])
const reasonRef = ref<{ open: (o: { title: string; tip?: string }) => Promise<string | undefined> }>()

const INSTANCE_STATUS: StatusMap = {
  RUNNING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  WITHDRAWN: { label: '已撤回', type: 'info', plain: true },
  TERMINATED: { label: '已终止', type: 'danger' }
}

function last30Days(): [string, string] {
  return [`${toDateString(new Date(Date.now() - 29 * 86400000))} 00:00:00`, `${toDateString(new Date())} 23:59:59`]
}

const { query, list, total, loading, load, search, reset } = useListPage<Omit<InstanceQuery, 'pageNo' | 'pageSize'>, MonitorRow>({
  api: (q) => workflowApi.instances(q as InstanceQuery),
  defaultQuery: () => ({ status: 'RUNNING', started: last30Days() })
})

const fields: SearchField[] = [
  { prop: 'bizType', label: '单据类型', type: 'slot' },
  { prop: 'bizNo', label: '单号', placeholder: '单号前缀', upper: true },
  { prop: 'status', label: '状态', type: 'select', options: INSTANCE_STATUS_OPTIONS },
  { prop: 'initiatorId', label: '发起人', type: 'user' },
  { prop: 'assigneeId', label: '当前处理人', type: 'user' },
  { prop: 'started', label: '发起时间', type: 'datetimerange' }
]

const columns: TableColumn<MonitorRow>[] = [
  { prop: 'bizTypeName', label: '单据类型', width: 120 },
  { prop: 'bizNo', label: '单号', width: 160, type: 'link', onClick: (r) => r.detailRoute && router.push(r.detailRoute) },
  { prop: 'title', label: '标题', minWidth: 220 },
  { prop: 'initiatorName', label: '发起人', width: 100 },
  { prop: 'startedAt', label: '发起时间', width: 150, type: 'datetime' },
  { prop: 'currentNodeName', label: '当前节点', width: 120 },
  { prop: 'assigneeNames', label: '当前处理人', width: 160, formatter: (r) => r.assigneeNames.join('，') || '-' },
  { prop: 'stayMinutes', label: '停留时长', width: 100, align: 'right', slot: true },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: INSTANCE_STATUS }
]

function stayClass(m?: number) {
  if (m === undefined || m === null) return ''
  return m >= 72 * 60 ? 'text-danger' : m >= 24 * 60 ? 'text-warning' : ''
}

// ---------- 查看 ----------
const detailVisible = ref(false)
const detail = ref<WfInstance & { title?: string; variables?: Record<string, unknown>; bizType?: string }>()
async function view(r: MonitorRow) {
  detail.value = await http.get(`/system/workflow/instances/${r.id}`)
  detailVisible.value = true
}

const fieldName = (code: string) => bizTypes.value.find((t) => t.bizType === detail.value?.bizType)?.fields.find((f) => f.code === code)?.name ?? code

// ---------- 转交 ----------
const transferVisible = ref(false)
const transferRow = ref<MonitorRow>()
const transferForm = ref<{ taskId?: string; toUserId?: string; comment: string }>({ comment: '' })
function openTransfer(r: MonitorRow) {
  transferRow.value = r
  transferForm.value = { taskId: r.pendingTasks[0]?.taskId, comment: '' }
  transferVisible.value = true
}
async function doTransfer() {
  const f = transferForm.value
  if (!f.taskId || !f.toUserId) return ElMessage.warning('请选择待处理任务和新处理人')
  await workflowApi.transfer(f.taskId, f.toUserId, f.comment)
  ElMessage.success('已转交')
  transferVisible.value = false
  load()
}

// ---------- 终止 ----------
async function terminate(r: MonitorRow) {
  const reason = await reasonRef.value?.open({ title: '终止审批', tip: `终止后单据「${r.bizNo}」回到草稿，审批人的待办将被取消。` })
  if (!reason) return
  await workflowApi.terminate(r.id, reason)
  ElMessage.success('已终止')
  load()
}

const asRow = (r: unknown) => r as MonitorRow

onMounted(async () => {
  bizTypes.value = await workflowApi.bizTypes().catch(() => [])
})
</script>

<template>
  <ErpPage description="查看各单据的审批进度；可为审批人转交任务，或终止异常的审批">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-bizType>
            <el-select v-model="query.bizType" placeholder="全部" clearable filterable class="w200">
              <el-option-group v-for="m in [...new Set(bizTypes.map((t) => t.moduleName))]" :key="m" :label="m">
                <el-option v-for="t in bizTypes.filter((x) => x.moduleName === m)" :key="t.bizType" :value="t.bizType" :label="t.name" />
              </el-option-group>
            </el-select>
          </template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.workflow-instance" :actions-width="160" empty-text="没有符合条件的审批" @refresh="load">
        <template #col-stayMinutes="{ row }">
          <span :class="stayClass(asRow(row).stayMinutes)">{{ asRow(row).stayMinutes === undefined || asRow(row).stayMinutes === null ? '-' : formatDuration(asRow(row).stayMinutes) }}</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '查看', handler: () => view(asRow(row)) },
            { label: '转交', visible: asRow(row).status === 'RUNNING' && asRow(row).pendingTasks.length > 0, handler: () => openTransfer(asRow(row)) },
            { label: '终止', danger: true, visible: asRow(row).status === 'RUNNING', handler: () => terminate(asRow(row)) }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-drawer v-model="detailVisible" :title="detail?.title ?? '审批详情'" size="640px" append-to-body>
      <template v-if="detail">
        <div v-if="detail.variables && Object.keys(detail.variables).length" class="vars">
          <div class="group-title">发起时的条件字段</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-for="(v, k) in detail.variables" :key="k" :label="fieldName(String(k))">{{ v === true ? '是' : v === false ? '否' : v }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="group-title">审批记录</div>
        <ApprovalTimeline :instances="[detail]" />
      </template>
    </el-drawer>

    <el-dialog v-model="transferVisible" title="转交审批任务" width="480px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="待处理任务" required>
          <el-select v-model="transferForm.taskId">
            <el-option v-for="t in transferRow?.pendingTasks" :key="t.taskId" :value="t.taskId" :label="`${transferRow?.currentNodeName} · ${t.assigneeName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="新处理人" required><UserSelect v-model="transferForm.toUserId" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="transferForm.comment" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="doTransfer">确定</el-button>
      </template>
    </el-dialog>

    <ReasonDialog ref="reasonRef" />
  </ErpPage>
</template>

<style scoped>
.vars { margin-bottom: 20px; }
</style>
