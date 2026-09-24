<script setup lang="ts">
import RemoteSelect from './RemoteSelect.vue'
import { systemCommonApi, type UserSimple } from '@/api/system'

/** 用户远程搜索（姓名/工号/用户名），显示“姓名（部门）”，只能选启用用户 */
defineProps<{ modelValue?: string | string[] | null; multiple?: boolean; placeholder?: string; disabled?: boolean; clearable?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: string | string[] | undefined]; select: [u: UserSimple | UserSimple[] | undefined] }>()

const search = (keyword: string) => systemCommonApi.userSearch(keyword)
const resolve = (ids: string[]) => systemCommonApi.userSearch('', ids)
const label = (u: UserSimple) => (u.deptName ? `${u.realName}（${u.deptName}）` : u.realName)
</script>

<template>
  <RemoteSelect
    :model-value="modelValue"
    :search="search"
    :resolve="resolve"
    :label="label"
    :multiple="multiple"
    :disabled="disabled"
    :clearable="clearable ?? true"
    :placeholder="placeholder ?? '姓名/工号/用户名'"
    @update:model-value="emit('update:modelValue', $event)"
    @select="emit('select', $event as UserSimple)"
  >
    <template #option="{ item }">
      <span>{{ item.realName }}</span>
      <span class="sub">{{ item.employeeNo || item.username }}<template v-if="item.deptName"> · {{ item.deptName }}</template></span>
    </template>
  </RemoteSelect>
</template>

<style scoped>
.sub { margin-left: 8px; color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
</style>
