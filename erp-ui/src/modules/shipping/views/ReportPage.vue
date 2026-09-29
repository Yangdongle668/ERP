<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatAmount, formatQty } from '@/utils/format'
import { NOTICE_STATUS, reportApi, type DetailReport, type ExportStatRow, type OnTimeReport, type PendingRow } from '../api/shipping'

defineOptions({ name: 'ShpReportPage' })

/** 出货报表（需求 11-06，T7）：待出货清单、出货明细、出货准时率、出口统计 */
const router = useRouter()
const tab = ref('pending')
const iso = (d: Date) => d.toISOString().slice(0, 10)
const now = new Date()
const q = ref<{ customerId?: string; materialId?: string; ownerId?: string; days?: number; dates: [string, string]; groupBy: string }>({
  dates: [iso(new Date(now.getFullYear(), now.getMonth(), 1)), iso(now)], groupBy: 'CUSTOMER'
})
const loading = ref(false)
const pending = ref<PendingRow[]>([])
const details = ref<DetailReport>()
const onTime = ref<OnTimeReport>()
const stats = ref<ExportStatRow[]>([])

const params = () => ({ customerId: q.value.customerId, materialId: q.value.materialId, ownerId: q.value.ownerId, days: q.value.days,
  dateFrom: q.value.dates?.[0], dateTo: q.value.dates?.[1], groupBy: q.value.groupBy })
async function load() {
  loading.value = true
  try {
    if (tab.value === 'pending') pending.value = await reportApi.pending(params())
    else if (tab.value === 'details') details.value = await reportApi.details(params())
    else if (tab.value === 'onTime') onTime.value = await reportApi.onTime(params())
    else stats.value = await reportApi.exportStats(params())
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(tab, load)
const exportUrl = () => ({ pending: '/shipping/reports/pending/export', details: '/shipping/reports/details/export', onTime: '/shipping/reports/on-time/export',
  stats: '/shipping/reports/export-stats/export' })[tab.value] ?? ''
const rateText = (r?: string) => (r === undefined || r === null ? '-' : `${r}%`)
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <el-form inline class="filters">
        <el-form-item label="客户"><CustomerSelect v-model="q.customerId" /></el-form-item>
        <el-form-item label="物料"><MaterialSelect v-model="q.materialId" /></el-form-item>
        <el-form-item label="业务员"><UserSelect v-model="q.ownerId" /></el-form-item>
        <el-form-item v-if="tab === 'pending'" label="未来天数"><el-input-number v-model="q.days" :min="1" :max="90" placeholder="14" controls-position="right" /></el-form-item>
        <el-form-item v-else label="出货日期"><el-date-picker v-model="q.dates" type="daterange" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item v-if="tab === 'details'" label="汇总">
          <el-select v-model="q.groupBy" class="group">
            <el-option value="CUSTOMER" label="按客户" /><el-option value="MATERIAL" label="按物料" /><el-option value="MONTH" label="按月份" />
            <el-option value="OWNER" label="按业务员" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="load">查询</el-button>
          <ExportButton :url="exportUrl()" :params="params" permission="shp:report:export" />
        </el-form-item>
      </el-form>
    </ErpPanel>
    <ErpPanel flush>
      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane label="待出货清单" name="pending">
          <el-table v-loading="loading" :data="pending">
            <el-table-column prop="customerName" label="客户" min-width="130" />
            <el-table-column label="订单" width="170">
              <template #default="{ row }">
                <el-link type="primary" underline="never" @click="router.push(`/sales/order/${row.orderId}`)">{{ row.orderNo }}</el-link> 行 {{ row.lineNo }}
              </template>
            </el-table-column>
            <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
            <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
            <el-table-column label="交期" width="110"><template #default="{ row }"><span :class="{ late: row.overdue }">{{ row.dueDate }}</span></template></el-table-column>
            <el-table-column label="出货通知" width="160">
              <template #default="{ row }">
                <el-link v-if="row.noticeId" type="primary" underline="never" @click="router.push(`/shipping/notice/${row.noticeId}`)">{{ row.noticeNo }}</el-link>
                <ErpBadge v-else type="warning" :dot="false">未通知</ErpBadge>
              </template>
            </el-table-column>
            <el-table-column label="通知状态" width="100">
              <template #default="{ row }"><StatusTag v-if="row.noticeStatus" :value="row.noticeStatus" :map="NOTICE_STATUS" /></template>
            </el-table-column>
            <el-table-column label="可用库存" width="100" align="right"><template #default="{ row }">{{ formatQty(row.availableQty) }}</template></el-table-column>
            <el-table-column prop="ownerName" label="业务员" width="90" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="出货明细" name="details">
          <template v-if="details">
            <el-table :data="details.summary" class="gap-b">
              <el-table-column prop="label" label="汇总" min-width="200" />
              <el-table-column prop="lines" label="行数" width="80" align="right" />
              <el-table-column label="数量" width="120" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="金额（本位币）" width="140" align="right"><template #default="{ row }">{{ row.amountBase === undefined ? '***' : formatAmount(row.amountBase) }}</template></el-table-column>
            </el-table>
            <el-table v-loading="loading" :data="details.rows" max-height="520">
              <el-table-column prop="shipDate" label="出货日期" width="100" />
              <el-table-column label="出货单" width="150">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/shipping/shipment/${row.shipmentId}`)">{{ row.shipmentNo }}</el-link></template>
              </el-table-column>
              <el-table-column prop="customerName" label="客户" min-width="120" />
              <el-table-column prop="orderNo" label="订单号" width="150" />
              <el-table-column prop="customerPoNo" label="客户 PO" width="110" />
              <el-table-column label="物料" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="batchNo" label="批次" width="120" />
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="金额" width="130" align="right">
                <template #default="{ row }">{{ row.amount === undefined ? '***' : `${row.currency} ${formatAmount(row.amount)}` }}</template>
              </el-table-column>
              <el-table-column prop="blNo" label="提单号" width="120" />
              <el-table-column prop="ownerName" label="业务员" width="90" />
            </el-table>
          </template>
        </el-tab-pane>
        <el-tab-pane label="出货准时率" name="onTime">
          <template v-if="onTime">
            <div class="cards">
              <div class="card"><span>完成出货的订单行</span><strong class="num">{{ onTime.total }}</strong></div>
              <div class="card"><span>按期</span><strong class="num">{{ onTime.onTime }}</strong></div>
              <div class="card"><span>准时率</span><strong class="num">{{ rateText(onTime.rate) }}</strong></div>
            </div>
            <el-row :gutter="16">
              <el-col v-for="g in [{ t: '按客户', d: onTime.byCustomer }, { t: '按业务员', d: onTime.byOwner }, { t: '按产品', d: onTime.byMaterial }]" :key="g.t" :span="8">
                <el-table :data="g.d" max-height="300">
                  <el-table-column prop="label" :label="g.t" min-width="140" />
                  <el-table-column label="按期 / 总数" width="100" align="right"><template #default="{ row }">{{ row.onTime }} / {{ row.total }}</template></el-table-column>
                  <el-table-column label="准时率" width="80" align="right"><template #default="{ row }">{{ rateText(row.rate) }}</template></el-table-column>
                </el-table>
              </el-col>
            </el-row>
            <h4 class="sub">延期明细</h4>
            <el-table :data="onTime.delays">
              <el-table-column label="订单" width="170"><template #default="{ row }">{{ row.orderNo }} 行 {{ row.lineNo }}</template></el-table-column>
              <el-table-column prop="customerName" label="客户" min-width="120" />
              <el-table-column label="物料" min-width="160"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="dueDate" label="交期" width="100" />
              <el-table-column prop="firstShipDate" label="首次出货" width="100" />
              <el-table-column prop="delayDays" label="延期(天)" width="90" align="right" />
              <el-table-column prop="ownerName" label="业务员" width="90" />
            </el-table>
          </template>
        </el-tab-pane>
        <el-tab-pane label="出口统计" name="stats">
          <el-table v-loading="loading" :data="stats">
            <el-table-column prop="month" label="月份" width="100" />
            <el-table-column prop="country" label="目的国" width="100" />
            <el-table-column prop="hsCode" label="HS 编码" width="140" />
            <el-table-column prop="shipments" label="出货单数" width="100" align="right" />
            <el-table-column label="数量" width="120" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
            <el-table-column label="金额（本位币）" min-width="140" align="right">
              <template #default="{ row }">{{ row.amountBase === undefined ? '***' : formatAmount(row.amountBase) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.filters :deep(.el-form-item) { margin-bottom: 0; }
.group { width: 120px; }
.late { color: var(--erp-color-error); }
.gap-b { margin-bottom: var(--erp-space-4); }
.sub { margin: var(--erp-space-4) 0 var(--erp-space-2); font-size: var(--erp-font-size-section-title); font-weight: var(--erp-font-weight-semibold); }
.cards { display: flex; gap: var(--erp-space-4); margin-bottom: var(--erp-space-4); }
.card {
  flex: 1; display: flex; justify-content: space-between; align-items: center; padding: var(--erp-space-4) var(--erp-space-5);
  background: var(--erp-color-surface); border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-card);
}
.card strong { font-size: var(--erp-font-size-metric); }
</style>
