<script setup lang="ts">
import NumberInput from './NumberInput.vue'

/** 单价输入：最多 6 位小数，显示时去掉末尾 0 但至少 2 位；禁止负数 */
defineProps<{ modelValue?: string | number | null; precision?: number; disabled?: boolean; placeholder?: string; borderless?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; change: [v: string | undefined]; enter: [] }>()
</script>

<template>
  <NumberInput
    :model-value="modelValue" :precision="precision ?? 6" trim-zeros :min-decimals="2"
    :disabled="disabled" :placeholder="placeholder" :borderless="borderless"
    @update:model-value="emit('update:modelValue', $event)" @change="emit('change', $event)" @enter="emit('enter')"
  />
</template>
