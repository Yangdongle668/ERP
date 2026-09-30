<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { forecastApi, type ForecastResult, type ForecastSuggestion } from '../api/bi'
import { formatQty } from '@/utils/format'

defineOptions({ name: 'BiForecast' })

/** 销售预测建议（需求 13-04 2.4）：基于最近 24 个月出货数量的趋势 / 季节预测，勾选物料后一键生成销售预测草稿 */
const router = useRouter()
const months = ref(3)
const loading = ref(false)
const generating = ref(false)
const result = ref<ForecastResult>()
const selected = ref<ForecastSuggestion[]>([])
const keyword = ref('')

async function load() {
  loading.value = true
  selected.value = []
  try {
    result.value = await forecastApi.suggestions(months.value)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const periods = computed(() => {
  const r = result.value
  if (!r) return []
  const out: string[] = []
  let y = Number(r.startPeriod.slice(0, 4))
  let m = Number(r.startPeriod.slice(4))
  for (let i = 0; i < r.months; i++) {
    out.push(`${y}${String(m).padStart(2, '0')}`)
    if (++m > 12) { m = 1; y++ }
  }
  return out
})
const rows = computed(() => (result.value?.suggestions ?? []).filter((s) => !keyword.value || s.materialLabel.includes(keyword.value)))
const asRow = (r: unknown) => r as ForecastSuggestion
const label = (p: string) => `${p.slice(0, 4)}-${p.slice(4)}`
const last12 = (s: ForecastSuggestion) => s.history.map((h) => Number(h.qty))
const methodText = (m: string) => (m === 'TREND_SEASONAL' ? '趋势 × 季节' : '线性趋势')

/** 迷你趋势线：最近 12 个月出货数量 */
function spark(s: ForecastSuggestion): string {
  const v = last12(s)
  const max = Math.max(...v, 1)
  const w = 96
  const h = 24
  return v.map((x, i) => `${(i / Math.max(v.length - 1, 1)) * w},${h - (x / max) * (h - 2) - 1}`).join(' ')
}

async function generate() {
  if (!selected.value.length) return ElMessage.warning('请先勾选要生成预测的物料')
  await ElMessageBox.confirm(`将为 ${selected.value.length} 个物料生成销售预测草稿（${label(periods.value[0])} ～ ${label(periods.value[periods.value.length - 1])}），生成后请业务 / 计划员核对再发布。`, '生成销售预测草稿',
    { confirmButtonText: '生成', cancelButtonText: '取消' })
  generating.value = true
  try {
    const r = await forecastApi.generate(selected.value.map((s) => s.materialId), months.value)
    ElMessage.success(`已生成销售预测草稿（${r.materialCount} 个物料）`)
    router.push(`/sales/forecast/${r.forecastId}`)
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <ErpPage description="按物料最近 24 个月的出货数量，用线性趋势（历史满 24 个月时叠加季节指数）预测本月起的需求；历史出货不足 12 个月的物料不给建议。建议仅供参考，生成的是草稿">
    <ErpPanel>
      <template #filter>
        <div class="fc-toolbar">
          <el-input v-model="keyword" placeholder="物料编码 / 名称" clearable prefix-icon="Search" class="fc-toolbar__kw" />
          <el-radio-group v-model="months" @change="load">
            <el-radio-button :value="3">3 个月</el-radio-button>
            <el-radio-button :value="6">6 个月</el-radio-button>
          </el-radio-group>
          <el-button v-perm="'sales:forecast:create'" type="primary" :disabled="!selected.length" :loading="generating" @click="generate">生成销售预测草稿</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" row-key="materialId" @selection-change="(v: ForecastSuggestion[]) => (selected = v)">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="materialLabel" label="物料" min-width="220" show-overflow-tooltip />
        <el-table-column label="最近 12 个月" width="130">
          <template #default="{ row }"><svg width="96" height="24" class="fc-spark" aria-hidden="true"><polyline :points="spark(asRow(row))" fill="none" stroke="currentColor" stroke-width="1.5" /></svg></template>
        </el-table-column>
        <el-table-column v-for="p in periods" :key="p" :label="label(p)" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ formatQty(asRow(row).forecast[p]) }}</span></template>
        </el-table-column>
        <el-table-column label="模型" width="110"><template #default="{ row }">{{ methodText(asRow(row).method) }}</template></el-table-column>
        <el-table-column label="回测误差" width="100" align="right">
          <template #default="{ row }">{{ asRow(row).mape == null ? '-' : `${asRow(row).mape?.toFixed(1)}%` }}</template>
        </el-table-column>
        <el-table-column label="历史月数" width="90" align="right"><template #default="{ row }">{{ row.historyMonths }}</template></el-table-column>
        <template #empty><ErpEmpty compact description="没有历史出货满 12 个月的物料" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.fc-toolbar { display: flex; align-items: center; gap: var(--erp-space-3); }
.fc-toolbar__kw { width: 220px; }
.fc-spark { color: var(--erp-color-primary); vertical-align: middle; }
</style>
