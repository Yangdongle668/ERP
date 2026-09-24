<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { http } from '@/api/http'
import type { ByBiz, StatusMap, WfInstance } from '../types'
import StatusTag from '../form/StatusTag.vue'
import ErpEmpty from '../base/ErpEmpty.vue'
import { formatDateTime } from '@/utils/format'

/**
 * 审批记录（需求 08 第 4.3 节）：单据全部审批实例（新的在前），每个实例按时间列出：
 * 发起 → 每个任务（节点、处理人、结论、意见、时间；自动通过显示原因；转交显示去向）→ 结果。
 * 数据来自 GET /system/workflow/instances/by-biz；也可直接传入 instances（监控页查看）。
 */
const props = defineProps<{ bizType?: string; bizId?: string; instances?: WfInstance[] }>()
const emit = defineEmits<{ loaded: [data: ByBiz] }>()
const data = ref<ByBiz>({ instances: props.instances ?? [] })
const loading = ref(false)

const TASK_STATUS: StatusMap = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '通过', type: 'success' },
  AUTO_PASSED: { label: '自动通过', type: 'success' },
  REJECTED: { label: '驳回', type: 'danger' },
  TRANSFERRED: { label: '已转交', type: 'info' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
const INSTANCE_STATUS: StatusMap = {
  RUNNING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  WITHDRAWN: { label: '已撤回', type: 'info', plain: true },
  TERMINATED: { label: '已终止', type: 'danger' }
}
const DOT: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  PENDING: 'warning', APPROVED: 'success', AUTO_PASSED: 'success', REJECTED: 'danger', TRANSFERRED: 'info', CANCELED: 'info'
}

async function load() {
  if (props.instances) {
    data.value = { instances: props.instances }
    return
  }
  if (!props.bizId || !props.bizType) return
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
watch(() => [props.bizId, props.instances], load)
defineExpose({ reload: load })
</script>

<template>
  <div v-loading="loading" class="approval-timeline">
    <ErpEmpty v-if="!data.instances.length" description="暂无审批记录" compact />
    <section v-for="ins in data.instances" :key="ins.id" class="instance">
      <header class="ins-head">
        <StatusTag :value="ins.status" :map="INSTANCE_STATUS" />
        <span class="ins-meta">{{ ins.initiatorName }} 于 {{ formatDateTime(ins.startedAt, true) }} 提交</span>
        <span v-if="ins.finishedAt" class="ins-meta">· 结束于 {{ formatDateTime(ins.finishedAt, true) }}</span>
      </header>
      <el-timeline>
        <el-timeline-item v-for="t in ins.tasks" :key="t.id" :type="DOT[t.status] ?? 'info'" :timestamp="formatDateTime(t.finishedAt ?? t.createdAt, true)" placement="top">
          <div class="task-line">
            <span class="node">{{ t.nodeName }}</span>
            <span class="assignee">{{ t.assigneeName }}</span>
            <StatusTag :value="t.status" :map="TASK_STATUS" />
          </div>
          <div v-if="t.autoReason" class="note">{{ t.status === 'AUTO_PASSED' ? '自动通过：' : '' }}{{ t.autoReason }}</div>
          <div v-if="t.transferToName" class="note">转交给 {{ t.transferToName }}<template v-if="t.handledByName">（由 {{ t.handledByName }} 操作）</template></div>
          <div v-if="t.comment" class="comment">{{ t.comment }}</div>
        </el-timeline-item>
      </el-timeline>
      <div v-if="ins.resultComment" class="result">{{ ins.status === 'TERMINATED' ? '终止原因' : '意见' }}：{{ ins.resultComment }}</div>
    </section>
  </div>
</template>

<style scoped>
.instance + .instance { margin-top: 20px; border-top: 1px solid var(--erp-color-border); padding-top: 16px; }
.ins-head { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.ins-meta { font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
.task-line { display: flex; align-items: center; gap: 8px; }
.node { font-weight: var(--erp-font-weight-medium); }
.assignee { color: var(--erp-color-text-secondary); }
.note { margin-top: 4px; font-size: var(--erp-font-size-caption); color: var(--erp-color-text-tertiary); }
.comment {
  margin-top: 6px; padding: 6px 10px; border-radius: var(--erp-radius-control); background: var(--erp-color-surface-subtle);
  white-space: pre-wrap; font-size: var(--erp-font-size-secondary);
}
.result { font-size: var(--erp-font-size-secondary); color: var(--erp-color-text-secondary); }
</style>
