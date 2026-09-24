<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { StatusMap, TableColumn } from '@/components'
import { useUserStore } from '@/stores/user'
import { describeCron } from '@/utils/cron'
import { formatDateTime, formatElapsed } from '@/utils/format'
import { jobApi, type JobLogRow, type JobRow } from '../api/job'

defineOptions({ name: 'SystemJobList' })

/** 定时任务（01-12 第 3 节）：各模块用 @ErpJob 声明，这里启停、修改 Cron、立即执行、查看执行日志 */
const store = useUserStore()
const canUpdate = computed(() => store.hasPermission('system:job:update'))
const list = ref<JobRow[]>([])
const loading = ref(false)
const keyword = ref('')

const RESULT_STATUS: StatusMap = {
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  RUNNING: { label: '执行中', type: 'primary' }
}

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return k ? list.value.filter((j) => j.code.toLowerCase().includes(k) || j.name.includes(k) || j.moduleName.includes(k)) : list.value
})

const columns: TableColumn<JobRow>[] = [
  { prop: 'name', label: '名称', minWidth: 170, slot: true },
  { prop: 'moduleName', label: '模块', width: 90 },
  { prop: 'cron', label: '执行计划', width: 170, slot: true },
  { prop: 'enabled', label: '启用', width: 70, slot: true },
  { prop: 'lastRunAt', label: '上次执行', width: 140, type: 'datetime' },
  { prop: 'lastResult', label: '上次结果', width: 90, slot: true },
  { prop: 'nextRunAt', label: '下次执行', width: 140, type: 'datetime' }
]

async function load() {
  loading.value = true
  try {
    list.value = await jobApi.list()
  } finally {
    loading.value = false
  }
}

async function toggle(j: JobRow, enabled: boolean) {
  if (!enabled) await ElMessageBox.confirm(`停用后「${j.name}」不再自动执行（仍可手动执行）。确定停用吗？`, '提示', { type: 'warning' })
  await (enabled ? jobApi.enable(j.code) : jobApi.disable(j.code))
  ElMessage.success(enabled ? '已启用' : '已停用')
  load()
}

async function run(j: JobRow) {
  await jobApi.run(j.code)
  ElMessage.success(`已开始执行「${j.name}」，结果见执行日志`)
  setTimeout(load, 1500)
}

// ---------- 编辑 Cron ----------
const cronVisible = ref(false)
const cronJob = ref<JobRow>()
const cron = ref('')
const preview = ref<string[]>([])
const previewError = ref('')
const saving = ref(false)
let timer = 0

function openCron(j: JobRow) {
  cronJob.value = j
  cron.value = j.cron
  cronVisible.value = true
  refreshPreview()
}

watch(cron, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(refreshPreview, 400)
})

async function refreshPreview() {
  previewError.value = ''
  if (!cron.value.trim()) {
    preview.value = []
    return
  }
  try {
    preview.value = await jobApi.preview(cron.value)
  } catch {
    preview.value = []
    previewError.value = 'Cron 表达式不正确'
  }
}

async function saveCron() {
  if (!cronJob.value) return
  saving.value = true
  try {
    await jobApi.updateCron(cronJob.value.code, cron.value)
    ElMessage.success('保存成功')
    cronVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function useDefault() {
  if (cronJob.value) cron.value = cronJob.value.defaultCron
}

// ---------- 执行日志 ----------
const logVisible = ref(false)
const logJob = ref<JobRow>()
const logs = ref<JobLogRow[]>([])
const logTotal = ref(0)
const logPage = ref({ pageNo: 1, pageSize: 20 })
const logLoading = ref(false)

function openLogs(j: JobRow) {
  logJob.value = j
  logPage.value = { pageNo: 1, pageSize: 20 }
  logVisible.value = true
  loadLogs()
}

async function loadLogs() {
  if (!logJob.value) return
  logLoading.value = true
  try {
    const r = await jobApi.logs(logJob.value.code, logPage.value.pageNo, logPage.value.pageSize)
    logs.value = r.list
    logTotal.value = Number(r.total) || 0
  } finally {
    logLoading.value = false
  }
}

const asJob = (r: unknown) => r as JobRow
const asLog = (r: unknown) => r as JobLogRow

onMounted(load)
</script>

<template>
  <ErpPage description="各模块声明的定时任务；多实例部署时同一任务同一时刻只在一个实例执行">
    <ErpPanel>
      <ErpTable :columns="columns" :data="filtered" :loading="loading" storage-key="system.job" :actions-width="220" @refresh="load">
        <template #toolbar>
          <el-input v-model="keyword" placeholder="搜索编码、名称或模块" clearable prefix-icon="Search" class="w240" />
        </template>
        <template #col-name="{ row }">
          <div class="stack">
            <span>{{ asJob(row).name }}</span>
            <span class="mono erp-text-caption">{{ asJob(row).code }}</span>
          </div>
        </template>
        <template #col-cron="{ row }">
          <div class="stack">
            <span>{{ describeCron(asJob(row).cron) || asJob(row).cron }}</span>
            <span class="mono erp-text-caption">{{ asJob(row).cron }}</span>
          </div>
        </template>
        <template #col-enabled="{ row }">
          <el-switch :model-value="asJob(row).enabled" :disabled="!canUpdate" @change="(v: string | number | boolean) => toggle(asJob(row), !!v)" />
        </template>
        <template #col-lastResult="{ row }">
          <ErpBadge v-if="asJob(row).running" type="primary">执行中</ErpBadge>
          <el-tooltip v-else-if="asJob(row).lastResult" :content="asJob(row).lastMessage || '-'" placement="top">
            <span><StatusTag :value="asJob(row).lastResult" :map="RESULT_STATUS" /></span>
          </el-tooltip>
          <span v-else class="text-muted">-</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '立即执行', permission: 'system:job:update', confirm: `确定立即执行「${asJob(row).name}」吗？`, handler: () => run(asJob(row)) },
            { label: '编辑 Cron', permission: 'system:job:update', handler: () => openCron(asJob(row)) },
            { label: '执行日志', handler: () => openLogs(asJob(row)) }
          ]" />
        </template>
      </ErpTable>
    </ErpPanel>

    <el-dialog v-model="cronVisible" :title="`编辑执行计划 - ${cronJob?.name ?? ''}`" width="560px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="Cron">
          <el-input v-model="cron" class="mono" placeholder="秒 分 时 日 月 周，如 0 0 2 * * ?" />
          <div class="form-tip">6 段：秒 分 时 日 月 周。默认：<span class="mono">{{ cronJob?.defaultCron }}</span>
            <el-button link type="primary" class="reset" @click="useDefault">恢复默认</el-button></div>
        </el-form-item>
        <el-form-item label="说明">
          <span v-if="previewError" class="text-danger">{{ previewError }}</span>
          <span v-else>{{ describeCron(cron) || '—' }}</span>
        </el-form-item>
        <el-form-item label="最近 5 次">
          <ol v-if="preview.length" class="preview num">
            <li v-for="t in preview" :key="t">{{ formatDateTime(t) }}</li>
          </ol>
          <span v-else class="text-muted">—</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cronVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!!previewError || !preview.length" @click="saveCron">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logVisible" :title="`执行日志 - ${logJob?.name ?? ''}`" size="760px" append-to-body>
      <el-table v-loading="logLoading" :data="logs">
        <el-table-column label="开始时间" width="160"><template #default="{ row }">{{ formatDateTime(asLog(row).startedAt) }}</template></el-table-column>
        <el-table-column label="耗时" width="90" align="right"><template #default="{ row }">{{ formatElapsed(asLog(row).durationMs) }}</template></el-table-column>
        <el-table-column label="结果" width="90"><template #default="{ row }"><StatusTag :value="asLog(row).result" :map="RESULT_STATUS" /></template></el-table-column>
        <el-table-column label="触发" width="150">
          <template #default="{ row }">{{ asLog(row).triggerType === 'MANUAL' ? `手动${asLog(row).operatorName ? '（' + asLog(row).operatorName + '）' : ''}` : '定时' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
        <template #empty><ErpEmpty description="暂无执行记录" compact /></template>
      </el-table>
      <ErpPagination v-model:page-no="logPage.pageNo" v-model:page-size="logPage.pageSize" :total="logTotal" @change="loadLogs" />
    </el-drawer>
  </ErpPage>
</template>

<style scoped>
.stack { display: flex; flex-direction: column; line-height: 18px; }
.preview { margin: 0; padding-left: 20px; line-height: 24px; }
.reset { margin-left: 8px; }
</style>
