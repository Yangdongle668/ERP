<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '@/api/http'
import ApproveDialog from './ApproveDialog.vue'
import UserSelect from '../form/UserSelect.vue'
import type { ByBiz } from '../types'

/**
 * 单据详情页的审批操作（需求 08 第 4.3 节，所有业务单据通用）：放在 DocPageHeader 的 actions-prefix 插槽。
 * - 当前用户有待处理任务：[通过]（主按钮）[驳回] [转交]
 * - 当前用户是发起人且审批中：[撤回]
 * 操作成功后触发 changed，页面重新加载单据（审批结果已通过 ApprovalCompletedEvent 更新单据状态）。
 *
 * <ApprovalActions biz-type="SAL_ORDER" :biz-id="id" @changed="reload" />
 */
const props = defineProps<{ bizType: string; bizId?: string }>()
const emit = defineEmits<{ changed: [] }>()
const state = ref<ByBiz>({ instances: [] })
const running = ref('')
const approveRef = ref<InstanceType<typeof ApproveDialog>>()

async function load() {
  if (!props.bizId) return
  state.value = await http.get<ByBiz>('/system/workflow/instances/by-biz', { bizType: props.bizType, bizId: props.bizId }, { silent: true })
    .catch(() => ({ instances: [] }))
}
onMounted(load)
watch(() => props.bizId, load)

async function act(key: string, fn: () => Promise<unknown>, message: string) {
  running.value = key
  try {
    await fn()
    ElMessage.success(message)
    await load()
    emit('changed')
  } finally {
    running.value = ''
  }
}

async function decide(defaultResult: 'APPROVE' | 'REJECT') {
  const r = await approveRef.value?.open({ title: defaultResult === 'APPROVE' ? '审批通过' : '驳回', defaultResult })
  if (!r) return
  const task = state.value.myPendingTaskId
  const path = r.result === 'APPROVE' ? 'approve' : 'reject'
  await act(path, () => http.post(`/system/workflow/tasks/${task}/${path}`, { comment: r.comment }), r.result === 'APPROVE' ? '已通过' : '已驳回')
}

async function withdraw() {
  await ElMessageBox.confirm('撤回后单据回到草稿，可修改后重新提交。确定撤回吗？', '撤回审批', { type: 'warning' })
  await act('withdraw', () => http.post(`/system/workflow/instances/${state.value.runningInstanceId}/withdraw`), '已撤回')
}

// ---------- 转交 ----------
const transferVisible = ref(false)
const transferTo = ref<string>()
const transferComment = ref('')

async function transfer() {
  if (!transferTo.value) {
    ElMessage.warning('请选择转交对象')
    return
  }
  await act('transfer', () => http.post(`/system/workflow/tasks/${state.value.myPendingTaskId}/transfer`,
    { toUserId: transferTo.value, comment: transferComment.value }), '已转交')
  transferVisible.value = false
}

defineExpose({ reload: load })
</script>

<template>
  <template v-if="state.myPendingTaskId">
    <el-button :loading="running === 'transfer'" :disabled="!!running" @click="transferTo = undefined; transferComment = ''; transferVisible = true">转交</el-button>
    <el-button :loading="running === 'reject'" :disabled="!!running" @click="decide('REJECT')">驳回</el-button>
    <el-button type="primary" :loading="running === 'approve'" :disabled="!!running" @click="decide('APPROVE')">通过</el-button>
  </template>
  <el-button v-else-if="state.canWithdraw" :loading="running === 'withdraw'" @click="withdraw">撤回</el-button>
  <ApproveDialog ref="approveRef" />
  <el-dialog v-model="transferVisible" title="转交审批" width="480px" append-to-body :close-on-click-modal="false">
    <el-form label-width="80px">
      <el-form-item label="转交给" required><UserSelect v-model="transferTo" /></el-form-item>
      <el-form-item label="说明"><el-input v-model="transferComment" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="transferVisible = false">取消</el-button>
      <el-button type="primary" :loading="running === 'transfer'" @click="transfer">确定</el-button>
    </template>
  </el-dialog>
</template>
