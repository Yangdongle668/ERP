<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { download } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { stockApi, WAREHOUSE_TYPE_OPTIONS, type AgingResult, type SlowRow, type SummaryResult } from '../api/inventory'

defineOptions({ name: 'InvAnalysis' })

/** 库存报表（需求 08-08 2.3～2.5，T7）：收发存汇总、库龄分析、呆滞料 */
const me = useUserStore()
const canCost = computed(() => me.hasPermission('inv:stock:cost'))
const tab = ref('summary')

function ym(d = new Date()) {
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}`
}

// ==================== 收发存 ====================
const sq = reactive<{ periods: string[]; warehouseId?: string; keyword?: string; level: string; showIdle: boolean }>({
  periods: [ym(), ym()], level: 'MATERIAL', showIdle: false
})
const summary = ref<SummaryResult>()
const sLoading = ref(false)
const expandIn = ref(false)
const expandOut = ref(false)
function summaryParams() {
  return { periodFrom: sq.periods?.[0], periodTo: sq.periods?.[1], warehouseId: sq.warehouseId, keyword: sq.keyword || undefined, level: sq.level, showIdle: sq.showIdle }
}
async function loadSummary() {
  sLoading.value = true
  try {
    summary.value = await stockApi.summary(summaryParams())
  } finally {
    sLoading.value = false
  }
}
async function exportSummary() {
  const r = await download<{ async?: boolean }>('/inventory/reports/in-out-summary/export', summaryParams(), '收发存汇总.xlsx')
  if (r?.async) ElMessage.info('数据量较大，已转为后台导出，完成后可在任务中心下载')
}

// ==================== 库龄 ====================
const aq = reactive<{ asOf?: string; warehouseTypes: string[]; buckets: string }>({ warehouseTypes: [], buckets: '30,90,180,365' })
const aging = ref<AgingResult>()
const aLoading = ref(false)
async function loadAging() {
  aLoading.value = true
  try {
    aging.value = await stockApi.aging({ asOf: aq.asOf, warehouseTypes: aq.warehouseTypes.join(',') || undefined, buckets: aq.buckets })
  } finally {
    aLoading.value = false
  }
}
const agingMax = computed(() => Math.max(1, ...(aging.value?.bucketTotals ?? []).map(Number)))

// ==================== 呆滞料 ====================
const slowDays = ref<number>()
const slowRows = ref<SlowRow[]>([])
const slowLoading = ref(false)
async function loadSlow() {
  slowLoading.value = true
  try {
    slowRows.value = await stockApi.slow({ days: slowDays.value })
  } finally {
    slowLoading.value = false
  }
}

const loaded = new Set<string>()
function ensure(t: string) {
  if (loaded.has(t)) return
  loaded.add(t)
  if (t === 'summary') loadSummary()
  else if (t === 'aging') loadAging()
  else loadSlow()
}
watch(tab, ensure)
onMounted(() => ensure(tab.value))
</script>

<template>
  <ErpPage description="收发存数量来自流水实时汇总；出库成本在月末成本计算后显示">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="收发存汇总" name="summary">
          <el-form inline class="bar">
            <el-form-item label="期间">
              <el-date-picker v-model="sq.periods" type="monthrange" value-format="YYYYMM" :clearable="false" class="w240" />
            </el-form-item>
            <el-form-item label="仓库"><WarehouseSelect v-model="sq.warehouseId" placeholder="全部" class="w160" /></el-form-item>
            <el-form-item label="物料"><el-input v-model="sq.keyword" placeholder="编码前缀或名称" clearable class="w160" /></el-form-item>
            <el-form-item label="汇总层级">
              <el-radio-group v-model="sq.level"><el-radio-button value="MATERIAL">物料</el-radio-button><el-radio-button value="WAREHOUSE">物料+仓库</el-radio-button></el-radio-group>
            </el-form-item>
            <el-form-item label="显示无发生额"><el-switch v-model="sq.showIdle" /></el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" :loading="sLoading" @click="loadSummary">查询</el-button>
              <el-button v-perm="'inv:stock:export'" icon="Download" @click="exportSummary">导出</el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="summary && !summary.costCalculated" type="info" :closable="false" show-icon class="bar" title="本期出库成本在月末成本计算后显示" />
          <el-table v-loading="sLoading" :data="summary?.rows ?? []" border max-height="600" :row-class-name="({ row }) => (row.mismatch ? 'mismatch' : '')">
            <el-table-column prop="materialCode" label="物料编码" width="130" fixed />
            <el-table-column prop="materialName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="baseUom" label="单位" width="60" />
            <el-table-column v-if="sq.level === 'WAREHOUSE'" prop="warehouseName" label="仓库" width="100" />
            <el-table-column label="期初数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.openingQty) }}</span></template></el-table-column>
            <el-table-column v-if="canCost" label="期初金额" width="110" align="right"><template #default="{ row }"><span class="num">{{ row.openingAmount ? formatAmount(row.openingAmount) : '-' }}</span></template></el-table-column>
            <el-table-column align="center">
              <template #header><el-link underline="never" @click="expandIn = !expandIn">本期入库 {{ expandIn ? '收起' : '展开' }}</el-link></template>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.inQty) }}</span></template></el-table-column>
              <template v-if="expandIn">
                <el-table-column v-for="k in summary?.inKeys ?? []" :key="k.key" :label="k.label" width="90" align="right">
                  <template #default="{ row }"><span class="num">{{ row.inDetail[k.key] ? formatQty(row.inDetail[k.key]) : '' }}</span></template>
                </el-table-column>
              </template>
              <el-table-column v-if="canCost" label="金额" width="110" align="right"><template #default="{ row }"><span class="num">{{ row.inAmount ? formatAmount(row.inAmount) : '-' }}</span></template></el-table-column>
            </el-table-column>
            <el-table-column align="center">
              <template #header><el-link underline="never" @click="expandOut = !expandOut">本期出库 {{ expandOut ? '收起' : '展开' }}</el-link></template>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.outQty) }}</span></template></el-table-column>
              <template v-if="expandOut">
                <el-table-column v-for="k in summary?.outKeys ?? []" :key="k.key" :label="k.label" width="90" align="right">
                  <template #default="{ row }"><span class="num">{{ row.outDetail[k.key] ? formatQty(row.outDetail[k.key]) : '' }}</span></template>
                </el-table-column>
              </template>
              <el-table-column v-if="canCost" label="金额" width="110" align="right"><template #default="{ row }"><span class="num text-muted">{{ row.outAmount ? formatAmount(row.outAmount) : '-' }}</span></template></el-table-column>
            </el-table-column>
            <el-table-column label="期末数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.closingQty) }}</span></template></el-table-column>
            <el-table-column v-if="canCost" label="期末金额" width="110" align="right"><template #default="{ row }"><span class="num">{{ row.closingAmount ? formatAmount(row.closingAmount) : '-' }}</span></template></el-table-column>
            <template #empty><ErpEmpty compact description="所选期间没有收发" /></template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="库龄分析" name="aging">
          <el-form inline class="bar">
            <el-form-item label="截止日期"><el-date-picker v-model="aq.asOf" value-format="YYYY-MM-DD" placeholder="今天" /></el-form-item>
            <el-form-item label="仓库类型">
              <el-select v-model="aq.warehouseTypes" multiple collapse-tags placeholder="可用仓" class="w240">
                <el-option v-for="o in WAREHOUSE_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
              </el-select>
            </el-form-item>
            <el-form-item label="区间（天）"><el-input v-model="aq.buckets" class="w160" /></el-form-item>
            <el-form-item><el-button type="primary" icon="Search" :loading="aLoading" @click="loadAging">查询</el-button></el-form-item>
          </el-form>
          <div v-if="canCost && aging?.bucketTotals" class="bars">
            <div v-for="(label, i) in aging.bucketLabels" :key="label" class="bar-item">
              <span class="bar-label">{{ label }}</span>
              <el-progress :percentage="Math.round((Number(aging.bucketTotals[i]) / agingMax) * 100)" :stroke-width="12" :format="() => formatAmount(aging!.bucketTotals![i])" />
            </div>
          </div>
          <el-table v-loading="aLoading" :data="aging?.rows ?? []" max-height="560">
            <el-table-column prop="materialCode" label="物料编码" width="130" />
            <el-table-column prop="materialName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="materialSpec" label="规格" min-width="120" show-overflow-tooltip />
            <el-table-column label="现存量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
            <el-table-column v-for="(label, i) in aging?.bucketLabels ?? []" :key="label" :label="label" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ Number(row.bucketQty[i]) ? formatQty(row.bucketQty[i]) : '' }}</span></template>
            </el-table-column>
            <el-table-column label="最长库龄(天)" width="110" align="right"><template #default="{ row }"><span class="num">{{ row.maxDays }}</span></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="呆滞料" name="slow">
          <el-form inline class="bar">
            <el-form-item label="无出库天数 ≥"><el-input-number v-model="slowDays" :min="1" :max="3650" placeholder="参数值" controls-position="right" /></el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" :loading="slowLoading" @click="loadSlow">查询</el-button>
              <el-button v-perm="'inv:stock:export'" icon="Download" @click="download('/inventory/reports/slow-moving/export', { days: slowDays }, '呆滞料.xlsx')">导出</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="slowLoading" :data="slowRows" max-height="600">
            <el-table-column prop="materialCode" label="物料编码" width="130" />
            <el-table-column prop="materialName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="materialSpec" label="规格" min-width="130" show-overflow-tooltip />
            <el-table-column label="现存量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
            <el-table-column v-if="canCost" label="金额" width="110" align="right"><template #default="{ row }"><span class="num">{{ row.amount ? formatAmount(row.amount) : '-' }}</span></template></el-table-column>
            <el-table-column prop="lastInDate" label="最近入库" width="100" />
            <el-table-column prop="lastOutDate" label="最近出库" width="100" />
            <el-table-column label="无出库天数" width="100" align="right"><template #default="{ row }"><span class="num">{{ row.idleDays }}</span></template></el-table-column>
            <el-table-column label="BOM 引用" width="90" align="center">
              <template #default="{ row }"><span :class="{ 'text-warning': !row.bomUsed }">{{ row.bomUsed ? '是' : '否' }}</span></template>
            </el-table-column>
            <template #empty><ErpEmpty compact description="没有呆滞料" /></template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.bar { margin-bottom: var(--erp-space-3); }
.bars { display: grid; gap: var(--erp-space-2); max-width: 640px; margin-bottom: var(--erp-space-4); }
.bar-item { display: grid; grid-template-columns: 120px 1fr; align-items: center; gap: var(--erp-space-3); }
.bar-label { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-secondary); }
:deep(.mismatch) { background: var(--erp-color-error-bg); }
</style>
