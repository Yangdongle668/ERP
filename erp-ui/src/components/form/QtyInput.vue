<script setup lang="ts">
import { computed, onMounted } from 'vue'
import NumberInput from './NumberInput.vue'
import { useBaseDataStore } from '@/stores/baseData'

/** 数量输入：小数位按单位精度（传 uom 或 precision），默认禁止负数 */
const props = defineProps<{ modelValue?: string | number | null; uom?: string; precision?: number; allowNegative?: boolean; min?: number; max?: number; disabled?: boolean; placeholder?: string; borderless?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; change: [v: string | undefined]; enter: [] }>()
const store = useBaseDataStore()
onMounted(() => store.loadUoms())
const p = computed(() => props.precision ?? (props.uom ? store.uomPrecision(props.uom) : 4))
</script>

<template>
  <NumberInput
    :model-value="modelValue" :precision="p" trim-zeros :allow-negative="allowNegative" :min="min" :max="max"
    :disabled="disabled" :placeholder="placeholder" :borderless="borderless"
    @update:model-value="emit('update:modelValue', $event)" @change="emit('change', $event)" @enter="emit('enter')"
  />
</template>
