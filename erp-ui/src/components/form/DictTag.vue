<script setup lang="ts">
import { computed } from 'vue'
import { tagTypeOf, useDictStore } from '@/stores/dict'

/** 字典标签：显示字典项标签，颜色取字典项配置；停用项仍显示标签（历史数据） */
const props = defineProps<{ type: string; value?: string | null; plain?: boolean }>()
const store = useDictStore()
store.load()
const item = computed(() => store.item(props.type, props.value))
</script>

<template>
  <template v-if="item">
    <span v-if="plain || item.tagType === 'DEFAULT'">{{ item.label }}</span>
    <el-tag v-else :type="tagTypeOf(item.tagType) || undefined" disable-transitions>{{ item.label }}</el-tag>
  </template>
  <span v-else>{{ value || '-' }}</span>
</template>
