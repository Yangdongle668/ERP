<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { formatDateTime, formatQty, today, toDateString } from '@/utils/format'
import { num, pct, shortageApi, type ShortageLine, type ShortageMaterial, type ShortageOrder, type SnapshotRow } from '../api/pmc'

defineOptions({ name: 'PmcShortagePage' })

/**
 * 缺料分析（需求 06-06）：生产订单按 优先级 → 计划开工 依次分配可用库存，缺料匹配在途得到预计齐套日期；
 * 订单视图 / 物料视图，推送催料给采购员。分析只读取数据，不占用库存。
 */
const route = useRoute()
const router = useRouter()
const addDays = (d: string, n: number) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return toDateString(x)
}
const form = ref<{ deptId?: string; statuses: string[]; range: [string, string] }>({ statuses: ['RELEASED', 'IN_PROGRESS', 'PLANNED'], range: [addDays(today(), -30), addDays(today(), 14)] })
const snapshotNo = ref<string | undefined>(typeof route.query.snapshotNo === 'string' ? route.query.snapshotNo : undefined)
const view = ref<'ORDER' | 'MATERIAL'>('ORDER')
const orders = ref<ShortageOrder[]>([])
const materials = ref<ShortageMaterial[]>([])
const snapshots = ref<SnapshotRow[]>([])
const lines = ref<Record<string, ShortageLine[]>>({})
const loading = ref(false)
const analyzing = ref(false)
const selected = ref<ShortageMaterial[]>([])

async function load() {
  loading.value = true
  try {
    snapshots.value = await shortageApi.snapshots()
    if (!snapshotNo.value) snapshotNo.value = snapshots.value[0]?.snapshotNo
    if (!snapshotNo.value) return
    lines.value = {}
    ;[orders.value, materials.value] = await Promise.all([shortageApi.orders(snapshotNo.value), shortageApi.materials(snapshotNo.value)])
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function analyze() {
  analyzing.value = true
  try {
    const r = await shortageApi.analyze({ deptId: form.value.deptId, statuses: form.value.statuses, planStartFrom: form.value.range[0], planStartTo: form.value.range[1] })
    if (!r.snapshotNo) return ElMessage.info('没有符合条件的生产订单')
    ElMessage.success(`已分析 ${r.orderCount} 张订单，缺料 ${r.shortOrderCount} 张、物料 ${r.shortMaterialCount} 种`)
    snapshotNo.value = r.snapshotNo
    load()
  } finally {
    analyzing.value = false
  }
}
async function expand(x: unknown, expanded: unknown) {
  const row = x as ShortageOrder
  const open = Array.isArray(expanded) ? expanded.includes(x) : Boolean(expanded)
  if (!open || lines.value[row.prodOrderId]) return
  lines.value[row.prodOrderId] = await shortageApi.lines(snapshotNo.value, row.prodOrderId)
}
const rateColor = (v: string) => (num(v) >= 1 ? 'success' : num(v) >= 0.8 ? 'warning' : 'exception')

async function push() {
  if (!selected.value.length) return ElMessage.warning('请勾选物料')
  const r = await shortageApi.push(snapshotNo.value, selected.value.map((m) => m.componentId))
  if (r.errors.length) ElNotification({ type: 'warning', title: `已推送 ${r.success} 个物料`, message: r.errors.join('；'), duration: 8000 })
  else ElMessage.success(`已推送 ${r.success} 个物料给采购员`)
}
const supplyBrief = (x: unknown) => (x as ShortageMaterial).supplies.map((x) => `${x.docNo ?? ''} ${x.date}`).join('；') || '-'
const supplyText = (s: { docNo?: string; date: string; qty: string }[]) => s.map((x) => `${x.docNo ?? ''} ${x.date} × ${formatQty(x.qty)}`).join('\n')
</script>

<template>
  <ErpPage description="按开工先后分配可用库存，计算齐套率、可开工数量与预计齐套日期；物料视图用于采购统一催料">
    <ErpPanel>
      <el-form inline @submit.prevent>
        <el-form-item label="车间"><OrgTreeSelect v-model="form.deptId" only-dept class="w-select" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.statuses" multiple class="w-status">
            <el-option value="RELEASED" label="已下达" /><el-option value="IN_PROGRESS" label="生产中" /><el-option value="PLANNED" label="已计划" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开工"><el-date-picker v-model="form.range" type="daterange" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="analyzing" @click="analyze">开始分析</el-button></el-form-item>
      </el-form>
      <div class="bar">
        <el-radio-group v-model="view">
          <el-radio-button value="ORDER">订单视图</el-radio-button>
          <el-radio-button value="MATERIAL">物料视图</el-radio-button>
        </el-radio-group>
        <el-select v-model="snapshotNo" class="snap" placeholder="历史快照" @change="load">
          <el-option v-for="s in snapshots" :key="s.snapshotNo" :value="s.snapshotNo"
                     :label="`${formatDateTime(s.createdAt, true)}（${s.orderCount} 单，缺料 ${s.shortOrderCount}）`" />
        </el-select>
        <el-button v-if="view === 'MATERIAL'" v-perm="'pmc:shortage:push'" type="primary" @click="push">推送催料</el-button>
        <ExportButton url="/pmc/shortages/export" :params="() => ({ snapshotNo })" filename="缺料明细" permission="pmc:shortage:query" />
      </div>

      <el-table v-if="view === 'ORDER'" v-loading="loading" :data="orders" row-key="prodOrderId" max-height="640" @expand-change="expand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="(lines[row.prodOrderId] ?? []).filter((l) => num(l.shortageQty) > 0)" class="inner">
              <el-table-column label="子件" min-width="180"><template #default="{ row: l }">{{ l.componentCode }} {{ l.componentName }}</template></el-table-column>
              <el-table-column label="未领" width="90" align="right"><template #default="{ row: l }">{{ formatQty(l.unissuedQty) }}</template></el-table-column>
              <el-table-column label="可分配" width="90" align="right"><template #default="{ row: l }">{{ formatQty(l.allocatedQty) }}</template></el-table-column>
              <el-table-column label="缺料" width="90" align="right"><template #default="{ row: l }"><span class="text-danger">{{ formatQty(l.shortageQty) }}</span></template></el-table-column>
              <el-table-column label="在途" min-width="160">
                <template #default="{ row: l }">
                  <el-tooltip v-if="l.supplies.length" :content="supplyText(l.supplies)"><span>{{ l.supplies.length }} 笔</span></el-tooltip><span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="预计齐套" width="110"><template #default="{ row: l }">{{ l.etaDate ?? '-' }}</template></el-table-column>
              <el-table-column label="无供应" width="90" align="right">
                <template #default="{ row: l }"><span :class="{ 'text-danger': num(l.noSupplyQty) > 0 }">{{ formatQty(l.noSupplyQty) }}</span></template>
              </el-table-column>
              <el-table-column prop="buyerName" label="采购员" width="90" />
              <template #empty><ErpEmpty compact description="该订单齐套" /></template>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="70" align="center" />
        <el-table-column label="生产订单" width="150">
          <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/production/prod-order/${row.prodOrderId}`)">{{ row.prodOrderNo }}</el-link></template>
        </el-table-column>
        <el-table-column label="产品" min-width="160"><template #default="{ row }">{{ row.productCode }} {{ row.productName }}</template></el-table-column>
        <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
        <el-table-column prop="planStart" label="计划开工" width="105" />
        <el-table-column label="行齐套率" width="140">
          <template #default="{ row }"><el-progress :percentage="Math.round(num(row.lineKitRate) * 100)" :status="rateColor(row.lineKitRate)" :stroke-width="6" /></template>
        </el-table-column>
        <el-table-column label="数量齐套" width="80" align="right"><template #default="{ row }">{{ pct(row.qtyKitRate) }}</template></el-table-column>
        <el-table-column label="可开工" width="80" align="right"><template #default="{ row }">{{ formatQty(row.kitableQty) }}</template></el-table-column>
        <el-table-column label="缺料行" width="70" align="right"><template #default="{ row }">{{ row.shortLineCount }}</template></el-table-column>
        <el-table-column label="预计齐套" width="120">
          <template #default="{ row }">
            <span v-if="row.hasNoSupply" class="text-danger">无供应</span>
            <span v-else :class="{ 'text-danger': row.etaLate }">{{ row.etaDate ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="销售订单 / 交期" width="170"><template #default="{ row }">{{ row.salesOrderNo ?? '-' }} {{ row.customerDate ?? '' }}</template></el-table-column>
        <template #empty><ErpEmpty compact description="点击“开始分析”" /></template>
      </el-table>

      <el-table v-else v-loading="loading" :data="materials" row-key="componentId" max-height="640" @selection-change="(v: ShortageMaterial[]) => (selected = v)">
        <el-table-column type="selection" width="44" />
        <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.componentCode }} {{ row.componentName }}</template></el-table-column>
        <el-table-column label="总缺料" width="100" align="right"><template #default="{ row }"><span class="text-danger">{{ formatQty(row.shortageQty) }}</span></template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column label="影响订单" width="160"><template #default="{ row }"><el-tooltip :content="row.orderNos.join('、')"><span>{{ row.orderCount }} 张</span></el-tooltip></template></el-table-column>
        <el-table-column prop="firstNeedDate" label="最早需要" width="105" />
        <el-table-column label="在途" min-width="200">
          <template #default="{ row }"><span class="text-muted">{{ supplyBrief(row) }}</span></template>
        </el-table-column>
        <el-table-column label="无供应" width="90" align="right">
          <template #default="{ row }"><span :class="{ 'text-danger': num(row.noSupplyQty) > 0 }">{{ formatQty(row.noSupplyQty) }}</span></template>
        </el-table-column>
        <el-table-column prop="buyerName" label="采购员" width="90" />
        <template #empty><ErpEmpty compact description="没有缺料" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
.w-select { width: 200px; }
.w-status { width: 240px; }
.snap { width: 300px; }
.inner { margin: 0 var(--erp-space-8); }
</style>
