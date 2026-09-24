<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { download, downloadPost, upload } from '@/api/http'

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
 */
interface ImportCheckResult {
  total: number
  errorCount: number
  columns: { key: string; label: string }[]
  rows: { rowNo: number; data: Record<string, string>; errors: string[]; action?: string }[]
}
interface ImportResult {
  success: number
  failed: number
  errors: { rowNo: number; message: string }[]
}

const props = withDefaults(defineProps<{ title?: string; base: string; allowPartial?: boolean; templateName?: string; params?: Record<string, string> }>(), { title: '导入' })
const emit = defineEmits<{ done: [result: ImportResult] }>()

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

const rowClass = ({ row }: { row: { errors: string[] } }) => (row.errors.length ? 'error-row' : '')

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="960px" :close-on-click-modal="false" append-to-body>
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
        <template v-if="result.errors.length" #extra>
          <el-table :data="result.errors" max-height="240" class="errors">
            <el-table-column prop="rowNo" label="行号" width="80" />
            <el-table-column prop="message" label="原因" />
          </el-table>
        </template>
      </el-result>
    </div>

    <template #footer>
      <template v-if="step === 2">
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
:deep(.error-row) { --el-table-tr-bg-color: var(--el-color-danger-light-9); }
</style>
