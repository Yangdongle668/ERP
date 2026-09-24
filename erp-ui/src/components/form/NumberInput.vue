<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * 数字输入框（QtyInput / AmountInput / PriceInput 的基础）：
 * 按精度限制小数位；默认禁止负数；右对齐；失焦时格式化为千分位显示；输出字符串（避免浮点误差）。
 */
const props = withDefaults(defineProps<{
  modelValue?: string | number | null
  precision?: number
  allowNegative?: boolean
  min?: number
  max?: number
  /** 失焦显示时去掉末尾 0（数量、单价） */
  trimZeros?: boolean
  /** 显示时至少保留的小数位（单价 2 位） */
  minDecimals?: number
  disabled?: boolean
  placeholder?: string
  /** 可编辑表格中使用：无边框 */
  borderless?: boolean
}>(), { precision: 2, allowNegative: false, trimZeros: false, minDecimals: 0 })

const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; change: [v: string | undefined]; enter: [] }>()

const focused = ref(false)
const text = ref('')

const display = computed(() => {
  if (focused.value) return text.value
  return format(props.modelValue)
})

function format(v: string | number | null | undefined): string {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  if (!Number.isFinite(n)) return String(v)
  let s = n.toFixed(props.precision)
  if (props.trimZeros && s.includes('.')) {
    let [i, d] = s.split('.')
    d = d.replace(/0+$/, '')
    if (d.length < props.minDecimals) d = d.padEnd(props.minDecimals, '0')
    s = d ? `${i}.${d}` : i
  }
  const [int, dec] = s.split('.')
  const sign = int.startsWith('-') ? '-' : ''
  const grouped = (sign ? int.slice(1) : int).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return sign + grouped + (dec !== undefined ? '.' + dec : '')
}

function onFocus() {
  focused.value = true
  text.value = props.modelValue === null || props.modelValue === undefined ? '' : String(props.modelValue)
}

/** 输入过滤：只允许数字、一个小数点（精度 > 0 时）、负号（允许负数时，且只能在开头） */
function onInput(raw: string) {
  let v = raw.replace(/[^\d.-]/g, '')
  if (!props.allowNegative) v = v.replace(/-/g, '')
  else v = v.replace(/(?!^)-/g, '')
  if (props.precision <= 0) v = v.replace(/\./g, '')
  else {
    const i = v.indexOf('.')
    if (i >= 0) v = v.slice(0, i + 1) + v.slice(i + 1).replace(/\./g, '').slice(0, props.precision)
  }
  text.value = v
}

function commit() {
  focused.value = false
  const v = text.value
  if (v === '' || v === '-' || v === '.') {
    update(undefined)
    return
  }
  let n = Number(v)
  if (!Number.isFinite(n)) {
    update(undefined)
    return
  }
  if (props.min !== undefined && n < props.min) n = props.min
  if (props.max !== undefined && n > props.max) n = props.max
  update(stripZeros(n.toFixed(props.precision)))
}

function stripZeros(s: string): string {
  return s.includes('.') ? s.replace(/\.?0+$/, '') : s
}

function update(v: string | undefined) {
  const old = props.modelValue === null || props.modelValue === undefined ? undefined : String(props.modelValue)
  if (old === v || (old !== undefined && v !== undefined && Number(old) === Number(v))) return
  emit('update:modelValue', v)
  emit('change', v)
}

function onEnter() {
  commit()
  emit('enter')
}
</script>

<template>
  <el-input
    :model-value="display"
    :disabled="disabled"
    :placeholder="placeholder"
    :class="['erp-number-input', { borderless }]"
    inputmode="decimal"
    @focus="onFocus"
    @update:model-value="onInput"
    @blur="commit"
    @keydown.enter="onEnter"
  />
</template>

<style scoped>
.erp-number-input :deep(input) { text-align: right; font-variant-numeric: tabular-nums; }
.borderless :deep(.el-input__wrapper) { box-shadow: none; background: transparent; }
.borderless :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--el-color-primary) inset; }
</style>
