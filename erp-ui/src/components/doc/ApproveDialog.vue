<script setup lang="ts">
import { ref } from 'vue'
import type { ApproveResult } from '../types'

/**
 * 审批弹窗：通过 / 驳回，意见最多 500 字，驳回时必填。
 * open() 返回 { result, comment }，取消时为 undefined。
 */
const visible = ref(false)
const result = ref<'APPROVE' | 'REJECT'>('APPROVE')
const comment = ref('')
const error = ref('')
const title = ref('审批')
let resolver: ((v: ApproveResult | undefined) => void) | null = null

function open(opts: { title?: string; defaultResult?: 'APPROVE' | 'REJECT' } = {}): Promise<ApproveResult | undefined> {
  title.value = opts.title ?? '审批'
  result.value = opts.defaultResult ?? 'APPROVE'
  comment.value = ''
  error.value = ''
  visible.value = true
  return new Promise((resolve) => (resolver = resolve))
}

function confirm() {
  if (result.value === 'REJECT' && !comment.value.trim()) {
    error.value = '驳回时必须填写审批意见'
    return
  }
  visible.value = false
  resolver?.({ result: result.value, comment: comment.value.trim() })
  resolver = null
}

function cancel() {
  visible.value = false
  resolver?.(undefined)
  resolver = null
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="480px" :close-on-click-modal="false" append-to-body @close="cancel">
    <el-form label-width="80px">
      <el-form-item label="审批结果">
        <el-radio-group v-model="result">
          <el-radio value="APPROVE">通过</el-radio>
          <el-radio value="REJECT">驳回</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审批意见" :required="result === 'REJECT'" :error="error">
        <el-input v-model="comment" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="result === 'REJECT' ? '请输入驳回原因（必填）' : '选填'" @input="error = ''" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button :type="result === 'REJECT' ? 'danger' : 'primary'" @click="confirm">{{ result === 'REJECT' ? '驳回' : '通过' }}</el-button>
    </template>
  </el-dialog>
</template>
