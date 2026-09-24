<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useBaseDataStore } from '@/stores/baseData'
import { systemCommonApi, type CurrencySimple } from '@/api/system'
import { today } from '@/utils/format'

/**
 * 币别下拉（启用币别）。
 * - excludeBase：排除本位币（汇率录入用）
 * - rateDate：传入单据日期时，选择币别后自动取该日期的日汇率，通过 rate 事件带出（本位币为 1）；
 *   未维护汇率时提示后端返回的信息，rate 事件值为 undefined
 */
const props = defineProps<{ modelValue?: string | null; excludeBase?: boolean; rateDate?: string; disabled?: boolean; clearable?: boolean; placeholder?: string }>()
const emit = defineEmits<{
  'update:modelValue': [v: string | undefined]
  select: [c: CurrencySimple | undefined]
  rate: [rate: string | undefined, effectiveDate?: string]
}>()

const store = useBaseDataStore()
onMounted(() => store.loadCurrencies())

const options = computed(() => (store.currencies.data ?? []).filter((c) => !(props.excludeBase && c.base)))

async function onChange(v: string | undefined) {
  emit('update:modelValue', v || undefined)
  const cur = options.value.find((c) => c.code === v)
  emit('select', cur)
  if (props.rateDate !== undefined && cur) {
    if (cur.base) {
      emit('rate', '1')
      return
    }
    try {
      const r = await systemCommonApi.rateLookup(cur.code, props.rateDate || today())
      emit('rate', r.rate, r.effectiveDate)
    } catch (e) {
      emit('rate', undefined)
      const { ElMessage } = await import('element-plus')
      ElMessage.warning((e as Error).message)
    }
  }
}
</script>

<template>
  <el-select :model-value="modelValue ?? undefined" :disabled="disabled" :clearable="clearable" :placeholder="placeholder ?? '币别'" filterable @update:model-value="onChange">
    <el-option v-for="c in options" :key="c.code" :value="c.code" :label="`${c.code} ${c.name}`" />
  </el-select>
</template>
