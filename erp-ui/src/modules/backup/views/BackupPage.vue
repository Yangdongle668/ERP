<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableColumn } from '@/components'
import { useUserStore } from '@/stores/user'
import { BACKUP_TYPE, RUN_STATUS, backupApi, formatBytes, type BackupInfo, type BackupRecord, type RestoreCheck, type RestoreLog } from '../api/backup'

defineOptions({ name: 'BackupRecordsPage' })

/** 系统备份（需求 14）：超级管理员全量备份数据库与附件，一键恢复 */
const router = useRouter()
const userStore = useUserStore()

const info = ref<BackupInfo>()
const list = ref<BackupRecord[]>([])
const total = ref(0)
const page = ref({ pageNo: 1, pageSize: 20 })
const loading = ref(false)
const logs = ref<RestoreLog[]>([])

const typeOptions = Object.entries(BACKUP_TYPE).map(([value, label]) => ({ value, label }))
const columns: TableColumn<BackupRecord>[] = [
  { prop: 'startedAt', label: '开始时间', width: 150, type: 'datetime' },
  { prop: 'fileName', label: '文件名', minWidth: 240 },
  { prop: 'backupType', label: '类型', width: 80, type: 'enum', options: typeOptions },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: RUN_STATUS },
  { prop: 'fileSize', label: '大小', width: 90, align: 'right', formatter: (r) => formatBytes(r.fileSize) },
  { key: 'counts', label: '表 / 行数', width: 130, align: 'right', formatter: (r) => (r.tableCount ? `${r.tableCount} / ${r.rowCount}` : '-') },
  { prop: 'attachmentCount', label: '附件数', width: 80, align: 'right', formatter: (r) => (r.includeFiles ? String(r.attachmentCount ?? 0) : '未包含') },
  { prop: 'remark', label: '说明', minWidth: 160, slot: true },
  { prop: 'operatorName', label: '操作人', width: 90 }
]
const logColumns: TableColumn<RestoreLog>[] = [
  { prop: 'startedAt', label: '开始时间', width: 150, type: 'datetime' },
  { prop: 'fileName', label: '恢复的备份', minWidth: 240 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: RUN_STATUS },
  { prop: 'phase', label: '阶段', width: 160 },
  { key: 'counts', label: '表 / 行数', width: 130, align: 'right', formatter: (r) => (r.tableCount ? `${r.tableCount} / ${r.rowCount}` : '-') },
  { prop: 'finishedAt', label: '结束时间', width: 150, type: 'datetime' },
  { prop: 'errorMsg', label: '说明', minWidth: 200 },
  { prop: 'operatorName', label: '操作人', width: 90 }
]

async function load() {
  loading.value = true
  try {
    const [i, p, l] = await Promise.all([backupApi.info(), backupApi.records(page.value), backupApi.restoreLogs()])
    info.value = i
    list.value = p.list
    total.value = p.total
    logs.value = l
  } finally {
    loading.value = false
  }
  schedulePoll()
}

/** 有进行中的备份时每 2 秒刷新一次 */
let pollTimer: number | undefined
function schedulePoll() {
  window.clearTimeout(pollTimer)
  if (list.value.some((r) => r.status === 'RUNNING')) pollTimer = window.setTimeout(load, 2000)
}

const busy = computed(() => !!info.value?.busy)

// ---------- 立即备份 ----------
const backupVisible = ref(false)
const backupForm = ref({ includeFiles: true, remark: '' })
const creating = ref(false)

function openBackup() {
  backupForm.value = { includeFiles: !!info.value?.filesSupported, remark: '' }
  backupVisible.value = true
}

async function createBackup() {
  creating.value = true
  try {
    await backupApi.create({ includeFiles: backupForm.value.includeFiles, remark: backupForm.value.remark.trim() || undefined })
    ElMessage.success('已开始备份，完成后可下载')
    backupVisible.value = false
    load()
  } finally {
    creating.value = false
  }
}

// ---------- 上传 ----------
const fileInput = ref<HTMLInputElement>()
const uploadPercent = ref<number>()

async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.warning('请选择本系统导出的 .zip 备份文件')
    return
  }
  uploadPercent.value = 0
  try {
    await backupApi.upload(file, (p) => (uploadPercent.value = p))
    ElMessage.success('上传成功，可在列表中恢复')
    load()
  } finally {
    uploadPercent.value = undefined
  }
}

async function remove(row: BackupRecord) {
  await backupApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

// ---------- 恢复 ----------
const restoreVisible = ref(false)
const checking = ref(false)
const check = ref<RestoreCheck>()
const confirmInput = ref('')
const restoring = ref(false)
const status = ref<RestoreLog | null>()
const finished = ref(false)

const versionRows = computed(() => {
  const c = check.value
  if (!c) return []
  const codes = [...new Set([...Object.keys(c.currentVersions), ...Object.keys(c.backupVersions)])].sort()
  return codes.map((code) => ({ code, backup: c.backupVersions[code] ?? '-', current: c.currentVersions[code] ?? '-', same: c.backupVersions[code] === c.currentVersions[code] }))
})
const canRestore = computed(() => !!check.value?.ok && confirmInput.value === check.value.confirmText && !restoring.value)

async function openRestore(row: BackupRecord) {
  check.value = undefined
  confirmInput.value = ''
  status.value = undefined
  finished.value = false
  restoreVisible.value = true
  checking.value = true
  try {
    check.value = await backupApi.check(row.id)
  } catch {
    restoreVisible.value = false
  } finally {
    checking.value = false
  }
}

async function startRestore() {
  if (!check.value || !canRestore.value) return
  const ok = await ElMessageBox.confirm('恢复期间系统暂停服务，所有用户将被退出登录。确定开始恢复吗？', '最后确认', {
    type: 'warning', confirmButtonText: '开始恢复', confirmButtonClass: 'el-button--danger'
  }).then(() => true).catch(() => false)
  if (!ok) return
  restoring.value = true
  try {
    await backupApi.restore(check.value.id, confirmInput.value)
    pollRestore()
  } catch {
    restoring.value = false
  }
}

let restoreTimer: number | undefined
async function pollRestore() {
  try {
    status.value = await backupApi.restoreStatus()
  } catch {
    // 恢复期间接口可能短暂不可用，继续轮询
  }
  if (!status.value || status.value.status === 'RUNNING') {
    restoreTimer = window.setTimeout(pollRestore, 1000)
    return
  }
  restoring.value = false
  finished.value = true
  if (status.value.status === 'SUCCESS') {
    await ElMessageBox.alert(
      `数据已恢复到备份「${status.value.fileName}」的状态${status.value.errorMsg ? `（${status.value.errorMsg}）` : ''}。请使用备份中的账号密码重新登录。`,
      '恢复成功', { type: 'success', confirmButtonText: '重新登录', showClose: false }
    ).catch(() => undefined)
    userStore.clear()
    router.replace('/login')
  } else {
    load()
  }
}

function beforeCloseRestore(done: () => void) {
  if (restoring.value) {
    ElMessage.warning('恢复进行中，请等待完成')
    return
  }
  done()
}

onMounted(async () => {
  await load()
  // 页面打开时如有进行中的恢复（如刷新页面），接着显示进度
  const s = await backupApi.restoreStatus().catch(() => null)
  if (s?.status === 'RUNNING') {
    restoreVisible.value = true
    restoring.value = true
    status.value = s
    pollRestore()
  }
})
onBeforeUnmount(() => {
  window.clearTimeout(pollTimer)
  window.clearTimeout(restoreTimer)
})
</script>

<template>
  <ErpPage description="备份全部业务数据（数据库与附件）到服务器备份目录，可下载保存；需要时一键恢复到备份时的状态。仅超级管理员可用">
    <template #actions>
      <el-button :disabled="uploadPercent !== undefined" icon="Upload" @click="fileInput?.click()">
        {{ uploadPercent !== undefined ? `上传中 ${uploadPercent}%` : '上传备份' }}
      </el-button>
      <el-button type="primary" icon="Backup" :disabled="busy" @click="openBackup">立即备份</el-button>
      <input ref="fileInput" type="file" accept=".zip" hidden @change="onFile" />
    </template>

    <ErpPanel title="备份设置">
      <el-descriptions v-if="info" :column="3" border>
        <el-descriptions-item label="备份目录"><span class="mono">{{ info.backupPath }}</span></el-descriptions-item>
        <el-descriptions-item label="可用空间">{{ formatBytes(info.usableBytes) }}</el-descriptions-item>
        <el-descriptions-item label="附件存储">
          {{ info.fileStorage }}
          <span v-if="!info.filesSupported" class="text-muted">（非本地存储，备份不含附件文件）</span>
        </el-descriptions-item>
        <el-descriptions-item label="自动备份">
          <ErpBadge :type="info.autoEnabled ? 'success' : 'info'">{{ info.autoEnabled ? '已开启' : '未开启' }}</ErpBadge>
          <span class="text-muted hint">每天 03:15，保留最近 {{ info.autoKeep }} 份；在「系统参数」中设置</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <ErpBadge v-if="info.maintenance" type="warning">维护中</ErpBadge>
          <ErpBadge v-else-if="info.busy" type="primary">{{ info.busy }}</ErpBadge>
          <ErpBadge v-else type="success">空闲</ErpBadge>
        </el-descriptions-item>
        <el-descriptions-item label="数据版本">{{ Object.keys(info.schemaVersions).length }} 个模块</el-descriptions-item>
      </el-descriptions>
      <el-skeleton v-else :rows="2" animated />
    </ErpPanel>

    <ErpPanel title="备份记录" flush>
      <ErpTable :columns="columns" :data="list" :loading="loading" :actions-width="160" storage-key="backup.records"
                empty-text="还没有备份，点击右上角「立即备份」" @refresh="load">
        <template #col-remark="{ row }">
          <span v-if="row.status === 'FAILED'" class="text-danger">{{ row.errorMsg }}</span>
          <span v-else-if="row.status === 'SUCCESS' && !row.restorable" class="text-warning">{{ row.mismatch }}</span>
          <span v-else>{{ row.remark || '-' }}</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '下载', permission: '*', visible: row.status === 'SUCCESS', handler: () => backupApi.download(row.id, row.fileName) },
            { label: '恢复', permission: '*', visible: row.status === 'SUCCESS' && row.restorable && !busy, handler: () => openRestore(row as BackupRecord) },
            { label: '删除', permission: '*', danger: true, visible: row.status !== 'RUNNING', confirm: `确定删除备份「${row.fileName}」吗？备份文件将一并删除，不可找回。`, handler: () => remove(row as BackupRecord) }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="page.pageNo" v-model:page-size="page.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <ErpPanel title="恢复记录" flush>
      <ErpTable :columns="logColumns" :data="logs" no-toolbar empty-text="还没有恢复过" />
    </ErpPanel>

    <el-dialog v-model="backupVisible" title="立即备份" width="480px">
      <el-form label-width="90px">
        <el-form-item label="包含附件">
          <el-checkbox v-model="backupForm.includeFiles" :disabled="!info?.filesSupported">同时备份上传的附件文件</el-checkbox>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="backupForm.remark" maxlength="200" show-word-limit placeholder="如：月结前备份" />
        </el-form-item>
      </el-form>
      <p class="text-muted">备份在后台进行，期间系统可正常使用；完成后记录状态变为「成功」。</p>
      <template #footer>
        <el-button @click="backupVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createBackup">开始备份</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="restoreVisible" title="一键恢复" width="640px" :close-on-click-modal="false" :before-close="beforeCloseRestore">
      <el-skeleton v-if="checking" :rows="4" animated />
      <template v-else-if="restoring || finished">
        <div class="progress">
          <p><b>{{ status?.fileName ?? check?.fileName }}</b></p>
          <p>
            <StatusTag :value="status?.status ?? 'RUNNING'" :map="RUN_STATUS" />
            <span class="hint">{{ status?.phase ?? '准备' }}</span>
          </p>
          <el-progress v-if="restoring" :percentage="100" :indeterminate="true" :show-text="false" />
          <el-alert v-if="status?.status === 'FAILED'" type="error" :closable="false" show-icon
                    :title="status.errorMsg || '恢复失败'" description="数据库已回滚到恢复前的状态；如附件已替换，可使用「恢复前」备份再次恢复。" />
          <p v-if="restoring" class="text-muted">恢复期间系统暂停服务，请勿关闭本页面。</p>
        </div>
      </template>
      <template v-else-if="check">
        <el-alert v-if="!check.ok" type="error" :closable="false" show-icon title="此备份不能恢复">
          <ul class="problems"><li v-for="p in check.problems" :key="p">{{ p }}</li></ul>
        </el-alert>
        <template v-else>
          <el-alert type="warning" :closable="false" show-icon title="恢复将覆盖当前全部数据">
            <ul class="problems">
              <li>当前所有业务数据、用户、权限、参数将替换为备份「{{ check.fileName }}」中的内容（{{ check.rowCount }} 行）</li>
              <li v-if="check.includeFiles && check.filesSupported">附件文件替换为备份中的 {{ check.attachmentCount }} 个文件</li>
              <li v-else>此备份不含附件，附件目录保持不变</li>
              <li>开始前自动备份当前数据（类型「恢复前」），恢复失败时数据库自动回滚</li>
              <li>恢复期间系统暂停服务，完成后所有用户需重新登录，密码以备份中的为准</li>
            </ul>
          </el-alert>
          <el-collapse class="versions">
            <el-collapse-item :title="`数据版本一致（${versionRows.length} 个模块）`">
              <el-table :data="versionRows" size="small" max-height="240">
                <el-table-column prop="code" label="模块" />
                <el-table-column prop="backup" label="备份版本" align="right" />
                <el-table-column prop="current" label="当前版本" align="right" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
          <el-form label-position="top" @submit.prevent="startRestore">
            <el-form-item :label="`请输入「${check.confirmText}」以继续`">
              <el-input v-model="confirmInput" :placeholder="check.confirmText" />
            </el-form-item>
          </el-form>
        </template>
      </template>
      <template #footer>
        <template v-if="finished || restoring">
          <el-button :disabled="restoring" @click="restoreVisible = false">关闭</el-button>
        </template>
        <template v-else>
          <el-button @click="restoreVisible = false">取消</el-button>
          <el-button type="danger" :disabled="!canRestore" @click="startRestore">开始恢复</el-button>
        </template>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.hint { margin-left: var(--erp-space-2); }
.problems { margin: var(--erp-space-1) 0 0; padding-left: var(--erp-space-4); }
.versions { margin: var(--erp-space-3) 0; }
.progress { display: flex; flex-direction: column; gap: var(--erp-space-3); }
.progress p { margin: 0; }
</style>
