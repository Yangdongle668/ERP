<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { SearchField } from '../types'
import DictSelect from '../form/DictSelect.vue'
import UserSelect from '../form/UserSelect.vue'
import OrgTreeSelect from '../form/OrgTreeSelect.vue'
import UomSelect from '../form/UomSelect.vue'
import CurrencySelect from '../form/CurrencySelect.vue'

/**
 * 查询区（UI 设计规范 T1）：
 * - 默认显示一行（宽度 ≥ 1600 显示 4 个条件，否则 3 个），其余点击“展开”显示；
 * - 输入框回车即查询；下拉、日期变更不自动查询；
 * - “重置”由父组件恢复默认条件并查询（useListPage.reset）。
 *
 * <ErpSearchForm v-model="query" :fields="fields" @search="search" @reset="reset" />
 * 自定义条件：field.type = 'slot'，插槽名 field-<prop>
 */
const props = defineProps<{ modelValue: Record<string, any>; fields: SearchField[]; loading?: boolean }>()
const emit = defineEmits<{ search: []; reset: [] }>()

const expanded = ref(false)
const wide = ref(window.innerWidth >= 1600)
const onResize = () => (wide.value = window.innerWidth >= 1600)
onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

const visibleCount = computed(() => (wide.value ? 4 : 3))
const collapsible = computed(() => props.fields.length > visibleCount.value)
const shown = computed(() => (expanded.value || !collapsible.value ? props.fields : props.fields.slice(0, visibleCount.value)))

const q = props.modelValue

function setUpper(f: SearchField, v: string) {
  q[f.prop] = f.upper ? v.toUpperCase() : v
}

function onEnter() {
  emit('search')
}
</script>

<template>
  <el-form class="erp-search-form" :model="modelValue" inline label-width="auto" @submit.prevent="onEnter">
    <el-form-item v-for="f in shown" :key="f.prop" :label="f.label">
      <slot v-if="f.type === 'slot'" :name="`field-${f.prop}`" :query="q" />
      <el-select v-else-if="f.type === 'select'" v-model="q[f.prop]" :multiple="f.multiple" :collapse-tags="f.multiple" :placeholder="f.placeholder ?? '全部'" :clearable="f.clearable !== false" class="w200">
        <el-option v-for="o in f.options" :key="String(o.value)" :value="o.value" :label="o.label" />
      </el-select>
      <DictSelect v-else-if="f.type === 'dict'" v-model="q[f.prop]" :type="f.dictType!" :multiple="f.multiple" :placeholder="f.placeholder ?? '全部'" class="w200" />
      <el-date-picker v-else-if="f.type === 'date'" v-model="q[f.prop]" type="date" value-format="YYYY-MM-DD" :placeholder="f.placeholder ?? '选择日期'" class="w200" />
      <el-date-picker v-else-if="f.type === 'daterange'" v-model="q[f.prop]" type="daterange" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="开始" end-placeholder="结束" class="w260" />
      <el-date-picker v-else-if="f.type === 'datetimerange'" v-model="q[f.prop]" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]" range-separator="~" start-placeholder="开始" end-placeholder="结束" class="w360" />
      <UserSelect v-else-if="f.type === 'user'" v-model="q[f.prop]" :multiple="f.multiple" class="w200" />
      <OrgTreeSelect v-else-if="f.type === 'org'" v-model="q[f.prop]" :multiple="f.multiple" class="w200" />
      <UomSelect v-else-if="f.type === 'uom'" v-model="q[f.prop]" clearable class="w200" />
      <CurrencySelect v-else-if="f.type === 'currency'" v-model="q[f.prop]" clearable class="w200" />
      <el-input-number v-else-if="f.type === 'number'" v-model="q[f.prop]" :controls="false" :placeholder="f.placeholder" class="w200" />
      <el-input v-else :model-value="q[f.prop]" :placeholder="f.placeholder ?? `请输入${f.label}`" clearable class="w200" @update:model-value="setUpper(f, $event)" @keyup.enter="onEnter" />
    </el-form-item>
    <el-form-item class="buttons">
      <el-button type="primary" icon="Search" :loading="loading" native-type="submit">查询</el-button>
      <el-button icon="Refresh" @click="emit('reset')">重置</el-button>
      <el-button v-if="collapsible" link type="primary" @click="expanded = !expanded">
        {{ expanded ? '收起' : '展开' }}<el-icon class="arrow"><component :is="expanded ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
      </el-button>
      <slot name="extra" />
    </el-form-item>
  </el-form>
</template>

<style scoped>
.erp-search-form { margin-bottom: 4px; }
.erp-search-form :deep(.el-form-item) { margin-right: 16px; margin-bottom: 12px; }
.w200 { width: 200px; }
.w260 { width: 260px; }
.w360 { width: 360px; }
.arrow { margin-left: 2px; }
</style>
