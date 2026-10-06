<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { download, downloadPost, http, upload } from '@/api/http'

/**
 * 导入向导（UI 设计规范 5.4、9.1）：①下载模板 ②上传文件 ③校验预览（错误行标红，可下载错误报告）④确认导入 ⑤结果。
 * 只要有一行错误默认不允许导入；allowPartial 为 true 时可以只导入正确的行。
 *
 * 后端约定（同一资源下）：
 * - GET  {base}/import-template         下载模板
 * - POST {base}/import/check            校验，返回 ImportCheckResult
 * - POST {base}/import/check?report=true 返回错误报告文件（原文件末尾加“错误原因”列）
 * - POST {base}/import                  执行导入（partial=true 时只导入正确行），返回 ImportResult
 *
 * 导入选项（如“编码已存在时跳过/更新”）：放在 options 插槽中，取值通过 params 传入，
 * 校验和导入请求都会带上这些参数。
 *
 * history 为 true 时提供「导入记录」：每次导入为一个批次，可整批回滚（删除本批新增、恢复本批更新的数据）：
 * - GET  {base}/import/batches                 最近的导入批次
 * - POST {base}/import/batches/{id}/rollback   回滚
 */
interface ImportCheckResult {
  total: number
  errorCount: number
  columns: { key: string; label: string }[]
  rows: { rowNo: number; data: Record<string, string>; errors: string[]; action?: string }[]
}
interface ImportBatch {
  id: string
  fileName?: string
  totalCount: number
  createdCount: number
  updatedCount: number
  status: 'DONE' | 'ROLLED_BACK'
  createdAt: string
  rolledBackAt?: string
}
interface ImportResult {
  success: number
  failed: number
  errors: { rowNo: number; message: string }[]
}

const props = withDefaults(defineProps<{
  title?: string; base: string; allowPartial?: boolean; templateName?: string; params?: Record<string, string>
  /** 提供导入记录与回滚 */
  history?: boolean
}>(), { title: '导入' })
const emit = defineEmits<{ done: [result: ImportResult]; rollback: [] }>()

const visible = ref(false)
const step = ref(0)
const file = ref<File>()
const checking = ref(false)
const importing = ref(false)
const check = ref<ImportCheckResult>()
const result = ref<ImportResult>()
const onlyErrors = ref(false)

const MAX_ROWS = 5000

function open() {
  showHistory.value = false
  step.value = 0
  file.value = undefined
  check.value = undefined
  result.value = undefined
  onlyErrors.value = false
  visible.value = true
}

function downloadTemplate() {
  download(`${props.base}/import-template`, undefined, `${props.templateName ?? props.title}导入模板.xlsx`)
}

async function onFile(f: { raw?: File }) {
  if (!f.raw) return
  if (!/\.xlsx$/i.test(f.raw.name)) {
    ElMessage.warning('请上传 .xlsx 格式的文件')
    return
  }
  file.value = f.raw
  checking.value = true
  try {
    check.value = await upload<ImportCheckResult>(`${props.base}/import/check`, f.raw, { ...props.params })
    if (check.value.total > MAX_ROWS) {
      ElMessage.warning(`单次最多导入 ${MAX_ROWS} 行，请拆分文件`)
      check.value = undefined
      return
    }
    step.value = 2
  } finally {
    checking.value = false
  }
}

const previewRows = computed(() => (check.value?.rows ?? []).filter((r) => !onlyErrors.value || r.errors.length))
const canImport = computed(() => !!check.value && check.value.total > 0 && (check.value.errorCount === 0 || (props.allowPartial && check.value.errorCount < check.value.total)))

function downloadReport() {
  if (!file.value) return
  const form = new FormData()
  form.append('file', file.value)
  for (const [k, v] of Object.entries(props.params ?? {})) form.append(k, v)
  downloadPost(`${props.base}/import/check?report=true`, form, '导入错误报告.xlsx')
}

async function doImport() {
  if (!file.value) return
  importing.value = true
  try {
    const partial = !!check.value?.errorCount
    result.value = await upload<ImportResult>(`${props.base}/import`, file.value, { ...props.params, partial: String(partial) })
    step.value = 4
    emit('done', result.value)
  } finally {
    importing.value = false
  }
}

// ---------- 导入记录 / 回滚 ----------
const showHistory = ref(false)
const batches = ref<ImportBatch[]>([])
const loadingBatches = ref(false)
const rollingBack = ref<string>()

async function openHistory() {
  showHistory.value = true
  loadingBatches.value = true
  try {
    batches.value = await http.get<ImportBatch[]>(`${props.base}/import/batches`)
  } finally {
    loadingBatches.value = false
  }
}

async function rollback(b: ImportBatch) {
  const desc = [b.createdCount ? `删除新增的 ${b.createdCount} 条` : '', b.updatedCount ? `恢复更新的 ${b.updatedCount} 条为导入前的内容` : '']
    .filter(Boolean).join('，')
  const ok = await ElMessageBox.confirm(`确定回滚 ${b.createdAt} 的导入（${b.fileName ?? '-'}）吗？将${desc}。回滚后不能撤销。`, '回滚导入',
    { type: 'warning', confirmButtonText: '回滚', confirmButtonClass: 'el-button--danger' }).then(() => true).catch(() => false)
  if (!ok) return
  rollingBack.value = b.id
  try {
    await http.post(`${props.base}/import/batches/${b.id}/rollback`)
    ElMessage.success('已回滚')
    emit('rollback')
    openHistory()
  } finally {
    rollingBack.value = undefined
  }
}

const rowClass = ({ row }: { row: { errors: string[] } }) => (row.errors.length ? 'error-row' : '')

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="960px" :close-on-click-modal="false" append-to-body>
    <template v-if="showHistory">
      <div class="summary">
        <el-button link type="primary" icon="ArrowLeft" @click="showHistory = false">返回导入</el-button>
        <span class="muted">回滚：删除该批新增的数据，把该批更新的数据恢复为导入前的内容；数据导入后已被使用时不能回滚，须从最近的批次开始回滚。</span>
      </div>
      <el-table v-loading="loadingBatches" :data="batches" max-height="460">
        <el-table-column prop="createdAt" label="导入时间" width="170" />
        <el-table-column prop="fileName" label="文件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdCount" label="新增" width="80" align="right" />
        <el-table-column prop="updatedCount" label="更新" width="80" align="right" />
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <ErpBadge v-if="row.status === 'ROLLED_BACK'" type="info">已回滚 {{ row.rolledBackAt?.slice(5, 16) }}</ErpBadge>
            <ErpBadge v-else type="success">已导入</ErpBadge>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DONE'" link type="danger" :loading="rollingBack === row.id" @click="rollback(row as ImportBatch)">回滚</el-button>
          </template>
        </el-table-column>
        <template #empty><ErpEmpty description="还没有导入记录" compact /></template>
      </el-table>
    </template>
    <template v-else>
    <el-steps :active="step" finish-status="success" simple class="steps">
      <el-step title="下载模板" />
      <el-step title="上传文件" />
      <el-step title="校验预览" />
      <el-step title="确认导入" />
      <el-step title="导入结果" />
    </el-steps>

    <div v-if="step < 2" class="upload-step">
      <p>1. 下载模板，按模板格式填写数据（必填列名带 *，第二行为填写说明，导入时忽略）。
        <el-button link type="primary" icon="Download" @click="downloadTemplate(); step = 1">下载模板</el-button>
      </p>
      <div v-if="$slots.options" class="options"><slot name="options" /></div>
      <p>2. 上传填写好的文件（.xlsx，最多 {{ MAX_ROWS }} 行）。</p>
      <el-upload drag :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="onFile" :disabled="checking">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ checking ? '正在校验…' : '将文件拖到此处，或点击上传' }}</div>
      </el-upload>
    </div>

    <div v-else-if="step === 2 && check">
      <div class="summary">
        <span>共 {{ check.total }} 行，</span>
        <span :class="{ bad: check.errorCount }">错误 {{ check.errorCount }} 行</span>
        <el-checkbox v-if="check.errorCount" v-model="onlyErrors" class="only">只看错误行</el-checkbox>
        <el-button v-if="check.errorCount" link type="primary" icon="Download" @click="downloadReport">下载错误报告</el-button>
        <span v-if="check.errorCount && !allowPartial" class="bad">请修正错误后重新上传</span>
      </div>
      <el-table :data="previewRows" max-height="420" :row-class-name="rowClass">
        <el-table-column prop="rowNo" label="行号" width="70" align="center" fixed="left" />
        <el-table-column v-if="check.rows.some((r) => r.action)" prop="action" label="操作" min-width="90" show-overflow-tooltip />
        <el-table-column v-for="c in check.columns" :key="c.key" :label="c.label" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.data[c.key] }}</template>
        </el-table-column>
        <el-table-column label="错误原因" min-width="220" fixed="right">
          <template #default="{ row }"><span class="bad">{{ row.errors.join('；') }}</span></template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else-if="step === 4 && result" class="result">
      <el-result :icon="result.failed ? 'warning' : 'success'" :title="`成功 ${result.success} 条，失败 ${result.failed} 条`">
        <template v-if="history" #sub-title>如需恢复原样，可在「导入记录」中回滚本次导入</template>
        <template v-if="result.errors.length" #extra>
          <el-table :data="result.errors" max-height="240" class="errors">
            <el-table-column prop="rowNo" label="行号" width="80" />
            <el-table-column prop="message" label="原因" />
          </el-table>
        </template>
      </el-result>
    </div>

    </template>

    <template #footer>
      <el-button v-if="history && !showHistory && step < 2" link type="primary" icon="History" class="history-link" @click="openHistory">导入记录（回滚）</el-button>
      <el-button v-if="showHistory" @click="visible = false">关闭</el-button>
      <template v-else-if="step === 2">
        <el-button @click="step = 1">重新上传</el-button>
        <el-button type="primary" :disabled="!canImport" :loading="importing" @click="doImport">
          {{ check?.errorCount ? `只导入正确的 ${check.total - check.errorCount} 行` : '确认导入' }}
        </el-button>
      </template>
      <el-button v-else @click="visible = false">{{ step === 4 ? '完成' : '取消' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.options { margin: 0 0 12px; padding: 12px 16px; background: var(--erp-color-surface-subtle); border-radius: var(--erp-radius-control); }
.steps { margin-bottom: 16px; }
.summary { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.bad { color: var(--el-color-danger); }
.errors { width: 600px; }
.muted { color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-secondary); }
.history-link { float: left; }
:deep(.error-row) { --el-table-tr-bg-color: var(--el-color-danger-light-9); }
</style>
