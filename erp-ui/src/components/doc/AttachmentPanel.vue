<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { download, fetchBlob } from '@/api/http'
import { systemCommonApi, type FileInfo } from '@/api/system'
import AttachmentUpload from '../form/AttachmentUpload.vue'
import { formatDateTime } from '@/utils/format'

/**
 * 单据附件：列表（文件名、大小、上传人、时间）+ 下载、预览、删除 + 上传。
 * editable 由单据状态决定（通常草稿可编辑）；删除权限最终由后端 FileAccessChecker 判断。
 */
const props = defineProps<{ bizType: string; bizId?: string; editable?: boolean; category?: string }>()
const emit = defineEmits<{ change: [count: number] }>()
const files = ref<FileInfo[]>([])
const loading = ref(false)

async function load() {
  if (!props.bizId) return
  loading.value = true
  try {
    files.value = await systemCommonApi.files(props.bizType, props.bizId)
    emit('change', files.value.length)
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => props.bizId, load)

function size(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

const PREVIEWABLE = /\.(png|jpe?g|gif|pdf|txt)$/i

async function preview(f: FileInfo) {
  const blob = await fetchBlob(`/system/files/${f.id}/preview`)
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

async function remove(f: FileInfo) {
  await ElMessageBox.confirm(`确定删除附件「${f.fileName}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
  await systemCommonApi.deleteFile(f.id)
  ElMessage.success('删除成功')
  load()
}

const asFile = (row: unknown) => row as FileInfo

defineExpose({ reload: load })
</script>

<template>
  <div>
    <AttachmentUpload v-if="editable && bizId" :biz-type="bizType" :biz-id="bizId" :category="category" class="upload" @uploaded="load" />
    <el-table v-loading="loading" :data="files">
      <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column label="大小" width="100" align="right"><template #default="{ row }">{{ size(row.fileSize) }}</template></el-table-column>
      <el-table-column prop="createdByName" label="上传人" width="110" />
      <el-table-column label="上传时间" width="160" align="center"><template #default="{ row }">{{ formatDateTime(row.createdAt, true) }}</template></el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(`/system/files/${asFile(row).id}/download`, undefined, asFile(row).fileName)">下载</el-button>
          <el-button v-if="PREVIEWABLE.test(row.fileName)" link type="primary" @click="preview(asFile(row))">预览</el-button>
          <el-button v-if="editable" link type="danger" @click="remove(asFile(row))">删除</el-button>
        </template>
      </el-table-column>
      <template #empty><ErpEmpty description="暂无附件" compact /></template>
    </el-table>
  </div>
</template>

<style scoped>
.upload { margin-bottom: 12px; }
</style>
