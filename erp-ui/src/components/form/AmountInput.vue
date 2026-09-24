<script setup lang="ts">
import { computed, onMounted } from 'vue'
import NumberInput from './NumberInput.vue'
import { useBaseDataStore } from '@/stores/baseData'

/** 金额输入：小数位按币别精度（传 currency 或 precision，默认 2），默认禁止负数 */
const props = defineProps<{ modelValue?: string | number | null; currency?: string; precision?: number; allowNegative?: boolean; min?: number; max?: number; disabled?: boolean; placeholder?: string; borderless?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; change: [v: string | undefined]; enter: [] }>()
const store = useBaseDataStore()
onMounted(() => store.loadCurrencies())
const p = computed(() => props.precision ?? (props.currency ? store.currencyPrecision(props.currency) : 2))
</script>

<template>
  <NumberInput
    :model-value="modelValue" :precision="p" :allow-negative="allowNegative" :min="min" :max="max"
    :disabled="disabled" :placeholder="placeholder" :borderless="borderless"
    @update:model-value="emit('update:modelValue', $event)" @change="emit('change', $event)" @enter="emit('enter')"
  />
</template>
