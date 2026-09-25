<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { systemCommonApi, type PaymentTermSimple } from '@/api/system'

/** 销售用付款条件下拉（CRM 模块内使用） */
defineProps<{ modelValue?: string | null; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined] }>()
const options = ref<PaymentTermSimple[]>([])
onMounted(async () => {
  options.value = await systemCommonApi.paymentTermSimple('SALES').catch(() => [])
})
</script>

<template>
  <el-select :model-value="modelValue ?? undefined" :disabled="disabled" filterable clearable placeholder="请选择付款条件" class="w-full"
             @update:model-value="emit('update:modelValue', $event || undefined)">
    <el-option v-for="t in options" :key="t.id" :value="t.id" :label="`${t.code} ${t.name}`" />
  </el-select>
</template>
