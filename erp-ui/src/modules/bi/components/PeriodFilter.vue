<script setup lang="ts">
import { COMPARES, type CompareCode, PERIODS, type PeriodCode } from '../api/bi'
import { formatDateTime } from '@/utils/format'

/** 期间 + 对比方式（BI-DSH-R01），右侧显示“数据更新于”（BI-DATA-R04） */
defineProps<{ updatedAt?: string | null }>()
const period = defineModel<PeriodCode>('period', { required: true })
const compare = defineModel<CompareCode>('compare', { required: true })
const custom = defineModel<[string, string] | null>('custom', { default: null })
const emit = defineEmits<{ change: [] }>()
</script>

<template>
  <div class="bi-period">
    <el-radio-group v-model="period" @change="period !== 'CUSTOM' && emit('change')">
      <el-radio-button v-for="p in PERIODS" :key="p.value" :value="p.value">{{ p.label }}</el-radio-button>
    </el-radio-group>
    <el-date-picker v-if="period === 'CUSTOM'" v-model="custom" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
                    start-placeholder="开始日期" end-placeholder="结束日期" class="bi-period__range" @change="emit('change')" />
    <el-select v-model="compare" class="bi-period__compare" @change="emit('change')">
      <el-option v-for="c in COMPARES" :key="c.value" :value="c.value" :label="c.label" />
    </el-select>
    <slot />
    <span class="bi-period__updated">数据更新于 {{ updatedAt ? formatDateTime(updatedAt, true) : '-' }}</span>
  </div>
</template>

<style scoped>
.bi-period { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-3); }
.bi-period__range { width: 260px; }
.bi-period__compare { width: 96px; }
.bi-period__updated { margin-left: auto; color: var(--erp-color-text-tertiary); font-size: var(--erp-font-size-caption); }
</style>
