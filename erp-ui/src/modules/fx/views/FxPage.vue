<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableColumn } from '@/components'
import { formatDateTime, formatRate } from '@/utils/format'
import { FX_PAIRS, fxApi, type FxDailyRow, type FxMonthlyRow, type FxStatus } from '../api/fx'

defineOptions({ name: 'FxRatesPage' })

/**
 * 汇率看板（需求 16-实时汇率）：中国银行现汇买入价最新报价、轮询状态、日平均与月平均汇率。
 * 汇率由后台自动获取并写入「系统管理 / 币别汇率」，无需人工维护。
 */
const status = ref<FxStatus>()
const daily = ref<FxDailyRow[]>([])
const monthly = ref<FxMonthlyRow[]>([])
const loading = ref(false)
const refreshing = ref(false)
const dailyPair = ref<string>('USD_CNY')

const pairOptions = FX_PAIRS.map((p) => ({ value: p.value, label: p.label }))
const dailyColumns: TableColumn<FxDailyRow>[] = [
  { prop: 'rateDate', label: '日期', width: 110, type: 'date' },
  { prop: 'pair', label: '汇率对', width: 130, type: 'enum', options: pairOptions },
  { prop: 'avgRate', label: '日平均', width: 110, type: 'rate' },
  { prop: 'minRate', label: '最低', width: 110, type: 'rate' },
  { prop: 'maxRate', label: '最高', width: 110, type: 'rate' },
  { prop: 'sampleCount', label: '报价笔数', width: 90, align: 'right' },
  { prop: 'finalized', label: '状态', width: 90, align: 'center', formatter: (r) => (r.finalized ? '已结算' : '当日进行中') }
]
const monthlyColumns: TableColumn<FxMonthlyRow>[] = [
  { prop: 'rateMonth', label: '月份', width: 100 },
  { prop: 'pair', label: '汇率对', width: 130, type: 'enum', options: pairOptions },
  { prop: 'avgRate', label: '月平均', width: 110, type: 'rate' },
  { prop: 'dayCount', label: '天数', width: 80, align: 'right' }
]

async function load() {
  loading.value = true
  try {
    const [s, d, m] = await Promise.all([fxApi.status(), fxApi.daily({ pair: dailyPair.value || undefined }), fxApi.monthly()])
    status.value = s
    daily.value = d
    monthly.value = m
  } finally {
    loading.value = false
  }
}

async function loadDaily() {
  daily.value = await fxApi.daily({ pair: dailyPair.value || undefined })
}

async function refresh() {
  refreshing.value = true
  try {
    status.value = await fxApi.refresh()
    ElMessage.success('已获取最新汇率')
    loadDaily()
  } finally {
    refreshing.value = false
  }
}

const health = computed(() => {
  const s = status.value
  if (!s) return undefined
  if (!s.enabled) return { type: 'info' as const, text: '已关闭自动获取（系统参数 fx.enabled）' }
  if (s.consecutiveFailures > 0) return { type: 'danger' as const, text: `连续失败 ${s.consecutiveFailures} 次，退避重试中` }
  if (!s.lastSuccessAt) return { type: 'warning' as const, text: '尚未取得汇率' }
  return { type: 'success' as const, text: '正常' }
})

/** 每分钟刷新状态（下次获取时间、是否过期） */
let timer: number | undefined
onMounted(() => {
  load()
  timer = window.setInterval(() => fxApi.status().then((s) => (status.value = s)).catch(() => undefined), 60000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <ErpPage description="后台每 15 分钟获取中国银行现汇买入价，失败时 1、2、4…60 分钟退避重试；当天结束后结算日平均汇率、月份结束后结算月平均汇率，自动写入系统汇率表（来源“自动”），数据保存 3 年">
    <template #actions>
      <el-button v-perm="'fx:rate:refresh'" type="primary" icon="Refresh" :loading="refreshing" @click="refresh">立即获取</el-button>
    </template>

    <div class="quotes">
      <ErpPanel v-for="q in status?.quotes ?? []" :key="q.pair" :title="q.label">
        <template #extra><ErpBadge v-if="q.stale && q.rate" type="warning">超过 15 分钟未更新</ErpBadge></template>
        <div class="rate num">{{ q.rate ? formatRate(q.rate) : '-' }}</div>
        <div class="meta">
          <span>今日平均 <b class="num">{{ q.todayAverage ? formatRate(q.todayAverage) : '-' }}</b></span>
          <span class="text-muted">银行发布 {{ formatDateTime(q.publishTime, true) || '-' }}</span>
          <span class="text-muted">取得 {{ formatDateTime(q.fetchedAt, true) || '-' }}</span>
        </div>
      </ErpPanel>
    </div>

    <ErpPanel title="获取状态">
      <el-descriptions v-if="status" :column="3" border>
        <el-descriptions-item label="状态"><ErpBadge v-if="health" :type="health.type">{{ health.text }}</ErpBadge></el-descriptions-item>
        <el-descriptions-item label="上次成功">{{ formatDateTime(status.lastSuccessAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次获取">{{ status.polling ? formatDateTime(status.nextRunAt) || '-' : '未启动' }}</el-descriptions-item>
        <el-descriptions-item label="推送目标">
          {{ status.pushTarget ? '系统汇率表（USD、EUR 日汇率 / 月末汇率）' : '本位币不是人民币，只在本模块保存' }}
        </el-descriptions-item>
        <el-descriptions-item label="上次尝试">{{ formatDateTime(status.lastAttemptAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息"><span :class="{ 'text-danger': status.lastError }">{{ status.lastError || '-' }}</span></el-descriptions-item>
      </el-descriptions>
      <el-skeleton v-else :rows="2" animated />
    </ErpPanel>

    <ErpPanel title="日平均汇率" description="当天各次报价的平均值；当天结束后（00:05）结算" flush>
      <template #extra>
        <el-select v-model="dailyPair" clearable placeholder="全部汇率对" class="pair-select" @change="loadDaily">
          <el-option v-for="p in FX_PAIRS" :key="p.value" v-bind="p" />
        </el-select>
      </template>
      <ErpTable :columns="dailyColumns" :data="daily" :loading="loading" no-toolbar :max-height="420" empty-text="近 30 天没有汇率数据" />
    </ErpPanel>

    <ErpPanel title="月平均汇率" description="当月各日平均汇率的平均值；月份结束后结算，USD、EUR 写入系统月末汇率" flush>
      <ErpTable :columns="monthlyColumns" :data="monthly" :loading="loading" no-toolbar :max-height="420" empty-text="还没有月平均汇率" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.quotes { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--erp-space-4); }
.rate { font-size: var(--erp-font-size-metric); font-weight: var(--erp-font-weight-semibold); }
.meta { display: flex; flex-direction: column; gap: var(--erp-space-1); margin-top: var(--erp-space-2); font-size: var(--erp-font-size-secondary); }
.pair-select { width: 180px; }
</style>
