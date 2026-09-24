<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http, upload } from '@/api/http'
import type { FileInfo } from '@/api/system'

/**
 * 附件上传：拖拽、多文件、大小/类型限制（系统参数 sys.file.max-size-mb、sys.file.allowed-ext）、上传进度。
 * - 已有单据：传 bizType + bizId，上传即绑定；
 * - 新建单据（尚无 ID）：不传 bizId，v-model 收集文件 ID，保存单据时由后端 FileApi.bind 绑定。
 */
const props = defineProps<{ bizType?: string; bizId?: string; category?: string; modelValue?: string[]; disabled?: boolean; multiple?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [ids: string[]]; uploaded: [file: FileInfo] }>()

interface Policy { maxSizeMb: number; allowedExt: string[] }
const policy = ref<Policy>({ maxSizeMb: 50, allowedExt: [] })
onMounted(async () => {
  try {
    policy.value = await http.get<Policy>('/system/files/policy', undefined, { silent: true })
  } catch {
    /* 使用默认值；后端上传时仍会校验 */
  }
})

interface Uploading { uid: number; name: string; percent: number; error?: string }
const uploading = ref<Uploading[]>([])
let uid = 0

function check(file: File): string | undefined {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (policy.value.allowedExt.length && !policy.value.allowedExt.includes(ext)) return `不支持的文件类型 .${ext}`
  if (file.size > policy.value.maxSizeMb * 1024 * 1024) return `文件大小不能超过 ${policy.value.maxSizeMb}MB`
  return undefined
}

async function handle(file: File) {
  const err = check(file)
  if (err) {
    ElMessage.warning(`${file.name}：${err}`)
    return
  }
  const item: Uploading = { uid: ++uid, name: file.name, percent: 0 }
  uploading.value.push(item)
  try {
    const fields: Record<string, string> = {}
    if (props.bizType) fields.bizType = props.bizType
    if (props.bizId) fields.bizId = props.bizId
    if (props.category) fields.category = props.category
    const info = await upload<FileInfo>('/system/files', file, fields, (p) => (item.percent = p))
    emit('uploaded', info)
    emit('update:modelValue', [...(props.modelValue ?? []), info.id])
    uploading.value = uploading.value.filter((u) => u.uid !== item.uid)
  } catch (e) {
    item.error = (e as Error).message
  }
}

function onChange(f: { raw?: File }) {
  if (f.raw) handle(f.raw)
}
</script>

<template>
  <div class="attachment-upload">
    <el-upload drag :multiple="multiple !== false" :auto-upload="false" :show-file-list="false" :disabled="disabled" :on-change="onChange">
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">单个文件不超过 {{ policy.maxSizeMb }}MB<template v-if="policy.allowedExt.length">；支持 {{ policy.allowedExt.join('、') }}</template></div>
      </template>
    </el-upload>
    <div v-for="u in uploading" :key="u.uid" class="progress">
      <span class="name">{{ u.name }}</span>
      <el-progress v-if="!u.error" :percentage="u.percent" :stroke-width="6" />
      <span v-else class="err">{{ u.error }}</span>
    </div>
  </div>
</template>

<style scoped>
.progress { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.progress .name { min-width: 160px; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.progress .el-progress { flex: 1; }
.err { color: var(--el-color-danger); font-size: 12px; }
</style>
