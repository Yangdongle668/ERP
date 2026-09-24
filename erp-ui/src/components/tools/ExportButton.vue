<script setup lang="ts">
import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import { useRouter } from 'vue-router'
import { download } from '@/api/http'
import ErpIconButton from '../base/ErpIconButton.vue'

/**
 * 导出按钮（UI 设计规范 9.2）：导出内容与当前查询条件一致，列与列设置一致（columns 传当前显示列的字段名）。
 * 后端结果 ≤ 同步导出上限时直接返回文件；超过时返回 { async: true }，提示到任务中心下载。
 */
const props = defineProps<{
  url: string
  params?: object | (() => object)
  columns?: () => string[]
  filename?: string
  permission?: string
  label?: string
}>()

const router = useRouter()
const loading = ref(false)

async function run() {
  loading.value = true
  try {
    const base = typeof props.params === 'function' ? props.params() : props.params ?? {}
    const q: Record<string, unknown> = { ...base }
    delete q.pageNo
    delete q.pageSize
    if (props.columns) q.columns = props.columns().join(',')
    const r = await download<{ async?: boolean }>(props.url, q, `${props.filename ?? '导出'}.xlsx`)
    if (r?.async) {
      const n = ElNotification({
        type: 'info',
        title: '已转为后台导出',
        message: '数据量较大，完成后会收到消息通知。点击此处前往任务中心查看进度。',
        duration: 6000,
        onClick: () => {
          n.close()
          router.push('/system/task')
        }
      })
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <ErpIconButton icon="Download" :tooltip="label ?? '导出'" :permission="permission" :loading="loading" @click="run" />
</template>
