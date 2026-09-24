<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useDictStore } from '@/stores/dict'

/** 字典单选组：选项少（≤5）时使用 */
const props = defineProps<{ type: string; modelValue?: string | null; disabled?: boolean; fillDefault?: boolean; button?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: string]; change: [v: string] }>()
const store = useDictStore()
const items = computed(() => store.items(props.type))

onMounted(async () => {
  await store.load()
  if (props.fillDefault && !props.modelValue) {
    const d = store.defaultValue(props.type)
    if (d) emit('update:modelValue', d)
  }
})

function onChange(v: string | number | boolean | undefined) {
  emit('update:modelValue', String(v))
  emit('change', String(v))
}
</script>

<template>
  <el-radio-group :model-value="modelValue ?? undefined" :disabled="disabled" @update:model-value="onChange">
    <template v-if="button">
      <el-radio-button v-for="i in items" :key="i.value" :value="i.value">{{ i.label }}</el-radio-button>
    </template>
    <template v-else>
      <el-radio v-for="i in items" :key="i.value" :value="i.value">{{ i.label }}</el-radio>
    </template>
  </el-radio-group>
</template>
