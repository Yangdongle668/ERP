<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useBaseDataStore } from '@/stores/baseData'
import type { Country } from '@/api/system'

/** 国家/地区下拉（ISO 3166-1 二位码），可按代码、中文、英文名搜索 */
defineProps<{ modelValue?: string | null; disabled?: boolean; clearable?: boolean; placeholder?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | undefined]; select: [c: Country | undefined] }>()

const store = useBaseDataStore()
onMounted(() => store.loadCountries())
const keyword = ref('')
const all = computed(() => store.countries.data ?? [])
const options = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return all.value
  return all.value.filter((c) => c.code.toLowerCase().includes(k) || c.nameCn.includes(k) || c.nameEn.toLowerCase().includes(k))
})

function onChange(v: string | undefined) {
  emit('update:modelValue', v || undefined)
  emit('select', all.value.find((c) => c.code === v))
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? undefined"
    :disabled="disabled"
    :clearable="clearable"
    :placeholder="placeholder ?? '国家/地区'"
    filterable
    :filter-method="(v: string) => (keyword = v)"
    @visible-change="(v: boolean) => !v && (keyword = '')"
    @update:model-value="onChange"
  >
    <el-option v-for="c in options" :key="c.code" :value="c.code" :label="`${c.code} ${c.nameCn}`">
      <span>{{ c.code }} {{ c.nameCn }}</span><span class="en">{{ c.nameEn }}</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.en { float: right; margin-left: 12px; color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
</style>
