<script setup lang="ts">
import { ref, watch } from 'vue'

/**
 * 原因弹窗（作废、关闭、反审核等）：原因必填，可选常用原因后手工补充，最多 500 字。
 * 通过 open() 打开，返回 Promise<string | undefined>（取消时为 undefined）。
 */
const props = withDefaults(defineProps<{ title?: string; tip?: string; options?: string[] }>(), { title: '填写原因' })

const visible = ref(false)
const reason = ref('')
const picked = ref<string>()
const error = ref('')
let resolver: ((v: string | undefined) => void) | null = null
const tipText = ref('')
const titleText = ref('')
const optionList = ref<string[]>([])

function open(opts: { title?: string; tip?: string; options?: string[] } = {}): Promise<string | undefined> {
  titleText.value = opts.title ?? props.title
  tipText.value = opts.tip ?? props.tip ?? ''
  optionList.value = opts.options ?? props.options ?? []
  reason.value = ''
  picked.value = undefined
  error.value = ''
  visible.value = true
  return new Promise((resolve) => (resolver = resolve))
}

watch(picked, (p) => {
  if (p && !reason.value.startsWith(p)) reason.value = reason.value ? `${p}；${reason.value}` : p
})

function confirm() {
  const r = reason.value.trim()
  if (!r) {
    error.value = '请输入原因'
    return
  }
  visible.value = false
  resolver?.(r)
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
  <el-dialog v-model="visible" :title="titleText" width="480px" :close-on-click-modal="false" append-to-body @close="cancel">
    <p v-if="tipText" class="tip">{{ tipText }}</p>
    <el-select v-if="optionList.length" v-model="picked" placeholder="常用原因" clearable class="pick">
      <el-option v-for="o in optionList" :key="o" :value="o" :label="o" />
    </el-select>
    <el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请输入原因（必填）" @input="error = ''" />
    <div v-if="error" class="err">{{ error }}</div>
    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip { margin: 0 0 12px; color: var(--el-color-warning); }
.pick { width: 100%; margin-bottom: 8px; }
.err { color: var(--el-color-danger); font-size: 12px; margin-top: 4px; }
</style>
