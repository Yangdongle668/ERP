<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { http } from '@/api/http'
import { formatDateTime } from '@/utils/format'

/**
 * 审批记录时间线：显示单据全部审批实例的每个节点、审批人、结论、意见、时间。
 * 数据来自 GET /system/workflow/instances/by-biz（08-审批流）。
 */
interface WfTask { id: string; nodeName: string; assigneeName: string; status: string; comment?: string; finishedAt?: string; createdAt: string }
interface WfInstance { id: string; status: string; initiatorName: string; startedAt: string; finishedAt?: string; tasks: WfTask[] }
interface ByBiz { instances: WfInstance[]; myPendingTaskId?: string }

const props = defineProps<{ bizType: string; bizId?: string }>()
const emit = defineEmits<{ loaded: [data: ByBiz] }>()
const data = ref<ByBiz>({ instances: [] })
const loading = ref(false)

const TASK_STATUS: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '通过', type: 'success' },
  AUTO_PASSED: { label: '自动通过', type: 'success' },
  REJECTED: { label: '驳回', type: 'danger' },
  TRANSFERRED: { label: '已转交', type: 'info' },
  CANCELED: { label: '已取消', type: 'info' }
}
const INSTANCE_STATUS: Record<string, string> = {
  RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回', TERMINATED: '已终止'
}

async function load() {
  if (!props.bizId) return
  loading.value = true
  try {
    data.value = await http.get<ByBiz>('/system/workflow/instances/by-biz', { bizType: props.bizType, bizId: props.bizId }, { silent: true })
    emit('loaded', data.value)
  } catch {
    data.value = { instances: [] }
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => props.bizId, load)
defineExpose({ reload: load })
</script>

<template>
  <div v-loading="loading">
    <el-empty v-if="!data.instances.length" description="暂无审批记录" :image-size="60" />
    <div v-for="ins in data.instances" :key="ins.id" class="instance">
      <div class="ins-head">{{ ins.initiatorName }} 于 {{ formatDateTime(ins.startedAt) }} 提交审批 · {{ INSTANCE_STATUS[ins.status] ?? ins.status }}</div>
      <el-timeline>
        <el-timeline-item
          v-for="t in ins.tasks"
          :key="t.id"
          :type="TASK_STATUS[t.status]?.type ?? 'info'"
          :timestamp="formatDateTime(t.finishedAt ?? t.createdAt)"
          placement="top"
        >
          <b>{{ t.nodeName }}</b> · {{ t.assigneeName }}
          <el-tag size="small" :type="TASK_STATUS[t.status]?.type ?? 'info'" class="tag">{{ TASK_STATUS[t.status]?.label ?? t.status }}</el-tag>
          <div v-if="t.comment" class="comment">{{ t.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<style scoped>
.instance + .instance { margin-top: 16px; border-top: 1px dashed var(--el-border-color); padding-top: 12px; }
.ins-head { margin-bottom: 12px; color: var(--el-text-color-secondary); }
.tag { margin-left: 8px; }
.comment { margin-top: 4px; color: var(--el-text-color-regular); white-space: pre-wrap; }
</style>
