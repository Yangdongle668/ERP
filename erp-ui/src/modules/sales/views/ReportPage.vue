<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatAmount, formatDateTime, formatQty, today, toDateString } from '@/utils/format'
import {
  DOC_STATUS, joinList, LINE_STATUS, num, optionsOf, reportApi,
  type CustomerRankRow, type OpenOrderRow, type OrderLineReportRow, type Performance, type QuoteSuccess, type TraceStep
} from '../api/sales'

defineOptions({ name: 'SalReportPage' })

/** 销售报表（需求 04-08，T7）：订单明细、未交订单、执行跟踪、业务员业绩、报价成功率、客户排行 */
const router = useRouter()
const tab = ref<'lines' | 'open' | 'perf' | 'quote' | 'rank'>('open')
const updatedAt = ref(`${today()} ${new Date().toTimeString().slice(0, 5)}`)
const pct = (v?: string | null) => (v == null ? '-' : `${(Number(v) * 100).toFixed(1)}%`)
function yearStart(): [string, string] {
  const d = new Date()
  return [`${d.getFullYear()}-01-01`, toDateString(d)]
}

// ---------- 订单明细 ----------
type LQuery = { docNo?: string; customerId?: string; ownerId?: string; materialId?: string; statuses?: string[]; dates?: [string, string]; required?: [string, string] }
const toLineParams = (q: LQuery) => {
  const { statuses, dates, required, ...rest } = q
  return { ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1], requiredFrom: required?.[0], requiredTo: required?.[1] }
}
const lines = useListPage<LQuery, OrderLineReportRow>({ api: (q) => reportApi.orderLines(toLineParams(q) as never), defaultQuery: () => ({ dates: yearStart() }) })
const lineQuery = lines.query
const lineFields: SearchField[] = [
  { prop: 'docNo', label: '订单号/PO', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(DOC_STATUS), multiple: true },
  { prop: 'dates', label: '下单日期', type: 'daterange' },
  { prop: 'required', label: '要求交期', type: 'daterange' }
]
const lineColumns: TableColumn<OrderLineReportRow>[] = [
  { prop: 'docNo', label: '订单号', width: 150, type: 'link', onClick: (r) => router.push(`/sales/order/${r.orderId}`) },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'docDate', label: '下单日期', width: 110, type: 'date' },
  { prop: 'customerName', label: '客户', width: 130 },
  { prop: 'customerPoNo', label: '客户PO', width: 120, hidden: true },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'qty', label: '数量', width: 90, type: 'qty' },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'price', label: '单价', width: 100, type: 'price' },
  { prop: 'totalAmount', label: '价税合计', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'totalAmountBase', label: '本位币', width: 120, type: 'amount', summary: true },
  { prop: 'requiredDate', label: '要求交期', width: 110, type: 'date' },
  { prop: 'shippedQty', label: '已出货', width: 90, type: 'qty' },
  { prop: 'openQty', label: '未出货', width: 90, type: 'qty' },
  { prop: 'invoicedQty', label: '已开票', width: 90, type: 'qty' },
  { prop: 'lineStatus', label: '行状态', width: 80, type: 'status', statusMap: LINE_STATUS },
  { prop: 'ownerName', label: '业务员', width: 90 }
]

// ---------- 未交订单 ----------
type OQuery = { customerId?: string; ownerId?: string; materialId?: string; orderNo?: string; dueWithinDays?: number }
const open = useListPage<OQuery, OpenOrderRow>({ api: (q) => reportApi.openOrders(q as never) })
const openQuery = open.query
const openFields: SearchField[] = [
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'dueWithinDays', label: '距交期', type: 'select', options: [{ value: 0, label: '今天及已过期' }, { value: 3, label: '3 天内' }, { value: 7, label: '7 天内' }, { value: 30, label: '30 天内' }] }
]
const openColumns: TableColumn<OpenOrderRow>[] = [
  { prop: 'orderNo', label: '订单号', width: 150, type: 'link', onClick: (r) => router.push(`/sales/order/${r.orderId}`) },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'customerName', label: '客户', width: 130 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'orderQty', label: '订单', width: 90, type: 'qty' },
  { prop: 'shippedQty', label: '已出货', width: 90, type: 'qty' },
  { prop: 'openQty', label: '未出货', width: 90, type: 'qty' },
  { prop: 'availableQty', label: '可用库存', width: 100, slot: true },
  { prop: 'wipQty', label: '在制', width: 80, type: 'qty' },
  { prop: 'requiredDate', label: '要求交期', width: 110, type: 'date' },
  { prop: 'promisedDate', label: '承诺交期', width: 110, type: 'date' },
  { prop: 'daysToDue', label: '距交期', width: 90, slot: true },
  { prop: 'ownerName', label: '业务员', width: 90 }
]
const asOpen = (r: unknown) => r as OpenOrderRow

// ---------- 执行跟踪 ----------
const traceVisible = ref(false)
const trace = ref<{ orderNo: string; lineNo: number; materialCode: string; materialName: string; qty: string; steps: TraceStep[] }>()
async function showTrace(lineId: string) {
  trace.value = await reportApi.trace(lineId)
  traceVisible.value = true
}

// ---------- 业绩 / 报价成功率 / 客户排行 ----------
const period = ref<[string, string]>(yearStart())
const perfOwner = ref<string>()
const perf = ref<Performance>()
const quote = ref<QuoteSuccess>()
const groupBy = ref('OWNER')
const rank = ref<CustomerRankRow[]>()
const loading = ref(false)
const periodParams = () => ({ from: period.value?.[0], to: period.value?.[1], ownerId: perfOwner.value, groupBy: groupBy.value })
async function run<T>(f: () => Promise<T>, set: (v: T) => void) {
  loading.value = true
  try {
    set(await f())
    updatedAt.value = `${today()} ${new Date().toTimeString().slice(0, 5)}`
  } finally {
    loading.value = false
  }
}
const loadPerf = () => run(() => reportApi.performance(periodParams()), (v) => (perf.value = v))
const loadQuote = () => run(() => reportApi.quoteSuccess(periodParams()), (v) => (quote.value = v))
const loadRank = () => run(() => reportApi.customerRanking(periodParams()), (v) => (rank.value = v))
const maxTrend = computed(() => Math.max(1, ...(perf.value?.trend ?? []).map((t) => Math.max(num(t.orderAmount), num(t.shipAmount), num(t.receiptAmount)))))

function onTab(t: string | number) {
  if (t === 'lines' && !lines.list.value.length) lines.load()
  if (t === 'open' && !open.list.value.length) open.load()
  if (t === 'perf' && !perf.value) loadPerf()
  if (t === 'quote' && !quote.value) loadQuote()
  if (t === 'rank' && !rank.value) loadRank()
}
onTab('open')
</script>

<template>
  <ErpPage description="订单明细、未交订单、业务员业绩、报价成功率与客户排行；金额统计口径为本位币不含税">
    <template #meta><span class="text-muted">数据更新于 {{ updatedAt }}</span></template>
    <ErpPanel flush>
      <el-tabs v-model="tab" class="list-tabs" @tab-change="onTab">
        <el-tab-pane label="未交订单" name="open">
          <ErpSearchForm v-model="openQuery" :fields="openFields" :loading="open.loading.value" @search="open.search" @reset="open.reset">
            <template #field-customerId><CustomerSelect v-model="openQuery.customerId" /></template>
            <template #field-materialId><MaterialSelect v-model="openQuery.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="openColumns" :data="open.list.value" :loading="open.loading.value" storage-key="sal.report.open" :actions-width="80" @refresh="open.load">
            <template #toolbar-right>
              <ExportButton url="/sales/reports/open-orders/export" :params="() => ({ ...openQuery })" filename="未交订单" permission="sales:report:export" />
            </template>
            <template #col-availableQty="{ row }">
              <span :class="{ 'text-danger': num(asOpen(row).availableQty) < num(asOpen(row).openQty) }">{{ formatQty(asOpen(row).availableQty) }}</span>
            </template>
            <template #col-daysToDue="{ row }">
              <span :class="{ 'text-danger': asOpen(row).daysToDue < 0, 'text-warning': asOpen(row).daysToDue >= 0 && asOpen(row).daysToDue <= 3 }">
                {{ asOpen(row).daysToDue < 0 ? `逾期 ${-asOpen(row).daysToDue} 天` : `${asOpen(row).daysToDue} 天` }}
              </span>
            </template>
            <template #actions="{ row }"><el-button link type="primary" @click="showTrace(asOpen(row).orderLineId)">跟踪</el-button></template>
          </ErpTable>
          <ErpPagination v-model:page-no="openQuery.pageNo" v-model:page-size="openQuery.pageSize" :total="open.total.value" @change="open.load" />
        </el-tab-pane>

        <el-tab-pane label="订单明细" name="lines">
          <ErpSearchForm v-model="lineQuery" :fields="lineFields" :loading="lines.loading.value" @search="lines.search" @reset="lines.reset">
            <template #field-customerId><CustomerSelect v-model="lineQuery.customerId" /></template>
            <template #field-materialId><MaterialSelect v-model="lineQuery.materialId" /></template>
          </ErpSearchForm>
          <ErpTable :columns="lineColumns" :data="lines.list.value" :loading="lines.loading.value" storage-key="sal.report.lines" @refresh="lines.load">
            <template #toolbar-right>
              <ExportButton url="/sales/reports/order-lines/export" :params="() => toLineParams({ ...lineQuery })" filename="销售订单明细" permission="sales:report:export" />
            </template>
          </ErpTable>
          <ErpPagination v-model:page-no="lineQuery.pageNo" v-model:page-size="lineQuery.pageSize" :total="lines.total.value" @change="lines.load" />
        </el-tab-pane>

        <el-tab-pane label="业务员业绩" name="perf">
          <div class="bar">
            <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" />
            <UserSelect v-model="perfOwner" placeholder="全部业务员" clearable class="w160" />
            <el-button type="primary" icon="Search" :loading="loading" @click="loadPerf">查询</el-button>
            <span class="erp-spacer" />
            <ExportButton url="/sales/reports/performance/export" :params="periodParams" filename="业务员业绩" permission="sales:report:export" />
          </div>
          <template v-if="perf">
            <el-table :data="perf.rows">
              <el-table-column prop="ownerName" label="业务员" width="120" />
              <el-table-column prop="deptName" label="部门" width="140" />
              <el-table-column :label="`接单额(${perf.baseCurrency})`" width="150" align="right"><template #default="{ row }">{{ formatAmount(row.orderAmount) }}</template></el-table-column>
              <el-table-column :label="`出货额(${perf.baseCurrency})`" width="150" align="right"><template #default="{ row }">{{ formatAmount(row.shipAmount) }}</template></el-table-column>
              <el-table-column :label="`回款额(${perf.baseCurrency})`" width="150" align="right"><template #default="{ row }">{{ formatAmount(row.receiptAmount) }}</template></el-table-column>
              <el-table-column prop="orderCount" label="订单数" width="90" align="right" />
              <el-table-column prop="newCustomers" label="新客户" width="90" align="right" />
              <template #empty><ErpEmpty compact /></template>
            </el-table>
            <div class="group-title">月度趋势</div>
            <div class="trend">
              <div v-for="t in perf.trend" :key="t.month" class="trend-row">
                <span class="trend-month">{{ t.month }}</span>
                <div class="trend-bars">
                  <el-progress :percentage="Math.round((num(t.orderAmount) / maxTrend) * 100)" :stroke-width="8" :format="() => `接单 ${formatAmount(t.orderAmount)}`" />
                  <el-progress :percentage="Math.round((num(t.shipAmount) / maxTrend) * 100)" status="success" :stroke-width="8" :format="() => `出货 ${formatAmount(t.shipAmount)}`" />
                  <el-progress :percentage="Math.round((num(t.receiptAmount) / maxTrend) * 100)" status="warning" :stroke-width="8" :format="() => `回款 ${formatAmount(t.receiptAmount)}`" />
                </div>
              </div>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="报价成功率" name="quote">
          <div class="bar">
            <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" />
            <el-radio-group v-model="groupBy" @change="loadQuote">
              <el-radio-button value="OWNER">按业务员</el-radio-button>
              <el-radio-button value="CUSTOMER">按客户</el-radio-button>
              <el-radio-button value="CATEGORY">按物料类别</el-radio-button>
            </el-radio-group>
            <el-button type="primary" icon="Search" :loading="loading" @click="loadQuote">查询</el-button>
          </div>
          <template v-if="quote">
            <p class="text-muted">报价 {{ quote.quoteCount }} 单，成交 {{ quote.wonCount }} 单，成功率 <strong>{{ pct(quote.successRate) }}</strong></p>
            <el-table :data="quote.rows">
              <el-table-column prop="groupName" label="分组" min-width="160" />
              <el-table-column prop="quoteCount" label="报价数" width="90" align="right" />
              <el-table-column prop="wonCount" label="成交" width="80" align="right" />
              <el-table-column prop="lostCount" label="未成交" width="80" align="right" />
              <el-table-column label="成功率" width="100" align="right"><template #default="{ row }">{{ pct(row.successRate) }}</template></el-table-column>
              <el-table-column label="平均成交周期(天)" width="140" align="right"><template #default="{ row }">{{ row.avgCycleDays ?? '-' }}</template></el-table-column>
              <template #empty><ErpEmpty compact /></template>
            </el-table>
            <div v-if="quote.lostReasons.length" class="group-title">未成交原因</div>
            <div class="top">
              <div v-for="r in quote.lostReasons" :key="r.name" class="top-row">
                <span class="top-name">{{ r.label }}</span>
                <el-progress :percentage="Math.round((num(r.value) / Math.max(1, ...quote.lostReasons.map((x) => num(x.value)))) * 100)" :stroke-width="10"
                             :format="() => `${r.value} 单`" class="top-bar" />
              </div>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="客户排行" name="rank">
          <div class="bar">
            <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" />
            <el-button type="primary" icon="Search" :loading="loading" @click="loadRank">查询</el-button>
            <span class="erp-spacer" />
            <ExportButton url="/sales/reports/customer-ranking/export" :params="periodParams" filename="客户排行" permission="sales:report:export" />
          </div>
          <el-table v-if="rank" :data="rank">
            <el-table-column prop="rank" label="排名" width="70" />
            <el-table-column label="客户" min-width="200"><template #default="{ row }">{{ row.customerCode }} {{ row.customerName }}</template></el-table-column>
            <el-table-column label="接单额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.orderAmount) }}</template></el-table-column>
            <el-table-column label="出货额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.shipAmount) }}</template></el-table-column>
            <el-table-column label="占比" width="90" align="right"><template #default="{ row }">{{ pct(row.share) }}</template></el-table-column>
            <el-table-column label="去年同期" width="140" align="right"><template #default="{ row }">{{ row.lastYearAmount ? formatAmount(row.lastYearAmount) : '-' }}</template></el-table-column>
            <el-table-column label="同比" width="90" align="right">
              <template #default="{ row }"><span :class="{ 'text-danger': num(row.growth) < 0, 'text-success': num(row.growth) > 0 }">{{ pct(row.growth) }}</span></template>
            </el-table-column>
            <el-table-column prop="abcClass" label="ABC" width="70" align="center" />
            <template #empty><ErpEmpty compact /></template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="traceVisible" :title="trace ? `执行跟踪 - ${trace.orderNo} 行 ${trace.lineNo} ${trace.materialCode}` : '执行跟踪'" width="720px" append-to-body>
      <el-timeline v-if="trace">
        <el-timeline-item v-for="(st, i) in trace.steps" :key="i" :timestamp="st.time ? formatDateTime(st.time, true) : st.date" :type="st.date || st.time ? 'primary' : undefined">
          <strong>{{ st.label }}</strong>
          <span v-if="st.docNo" class="trace-doc">{{ st.docNo }}</span>
          <span v-if="st.qty" class="trace-doc">数量 {{ formatQty(st.qty) }}</span>
          <div v-if="st.remark" class="text-muted">{{ st.remark }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.list-tabs { padding: 0 var(--erp-space-5) var(--erp-space-4); }
.bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-2); margin: var(--erp-space-3) 0; }
.w160 { width: 160px; }
.group-title { margin: var(--erp-space-4) 0 var(--erp-space-2); }
.trend { display: flex; flex-direction: column; gap: var(--erp-space-3); }
.trend-row { display: flex; align-items: center; gap: var(--erp-space-3); }
.trend-month { width: 80px; flex-shrink: 0; color: var(--erp-color-text-secondary); }
.trend-bars { flex: 1; display: flex; flex-direction: column; gap: var(--erp-space-1); }
.top { display: flex; flex-direction: column; gap: var(--erp-space-2); }
.top-row { display: flex; align-items: center; gap: var(--erp-space-3); }
.top-name { width: 160px; flex-shrink: 0; }
.top-bar { flex: 1; }
.trace-doc { margin-left: var(--erp-space-2); color: var(--erp-color-text-secondary); }
</style>
