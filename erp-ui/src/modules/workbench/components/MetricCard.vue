<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { formatAmount } from '@/utils/format'
import { homeApi, type CardData, type CardVO } from '../api/workbench'
import TrendChart from './TrendChart.vue'

/** 看板卡片：骨架屏 → 数据；单个卡片失败显示“加载失败，点击重试”（WB-HOME-R02） */
const props = defineProps<{ card: CardVO }>()
const emit = defineEmits<{ open: [route: string] }>()
const data = ref<CardData>()
const loading = ref(true)
const failed = ref(false)

async function load(refresh = false) {
  loading.value = true
  failed.value = false
  try {
    data.value = await homeApi.cardData(props.card.code, refresh)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(() => load())
defineExpose({ load })

function display(d: CardData) {
  if (d.value === undefined || d.value === null) return '-'
  if (d.unit === 'PERCENT') return `${d.value}%`
  if (d.unit === 'COUNT') return String(Number(d.value))
  const v = Number(d.value)
  return Math.abs(v) >= 10000 ? `${formatAmount(v / 10000, 1)} 万` : formatAmount(v)
}
function trendClass(d: CardData) {
  const up = Number(d.changePct) > 0
  return up !== d.costLike ? 'good' : 'bad'
}
const hhmm = (s?: string) => (s ? s.slice(11, 16) : '')
</script>

<template>
  <div class="card" :class="{ chart: card.type === 'CHART', link: !!card.route }" @click="card.route && !failed && emit('open', card.route)">
    <div class="name">{{ card.name }}</div>
    <el-skeleton v-if="loading" :rows="1" animated />
    <div v-else-if="failed" class="failed" @click.stop="load(true)">加载失败，点击重试</div>
    <template v-else-if="data">
      <TrendChart v-if="card.type === 'CHART'" :points="data.series" />
      <template v-else>
        <div class="value">{{ display(data) }}</div>
        <div class="meta">
          <span v-if="data.changePct !== undefined && data.changePct !== null" :class="trendClass(data)">
            {{ Number(data.changePct) >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(data.changePct)) }}% {{ data.compareLabel }}
          </span>
          <span v-if="data.subText" class="sub">{{ data.subText }}</span>
        </div>
      </template>
      <div class="time">更新于 {{ hhmm(data.updatedAt) }}</div>
    </template>
  </div>
</template>

<style scoped>
.card {
  background: var(--erp-color-surface); border: 1px solid var(--erp-color-border); border-radius: var(--erp-radius-card);
  padding: var(--erp-space-4); min-height: 120px; display: flex; flex-direction: column; gap: var(--erp-space-1);
}
.card.link { cursor: pointer; }
.card.link:hover { border-color: var(--erp-color-primary); }
.card.chart { grid-column: 1 / -1; }
.name { color: var(--erp-color-text-secondary); }
.value { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); }
.meta { display: flex; gap: var(--erp-space-2); flex-wrap: wrap; font-size: var(--erp-font-size-caption); }
.good { color: var(--erp-color-success); }
.bad { color: var(--erp-color-error); }
.sub { color: var(--erp-color-text-secondary); }
.time { margin-top: auto; text-align: right; color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.failed { color: var(--erp-color-error); cursor: pointer; }
</style>
