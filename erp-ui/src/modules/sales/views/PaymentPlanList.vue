<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatDateTime, formatMoney } from '@/utils/format'
import { BASE_EVENT_OPTIONS, joinList, labelOf, optionsOf, PLAN_STATUS, paymentPlanApi, type PaymentPlanRow } from '../api/sales'

defineOptions({ name: 'SalPaymentPlanList' })

/** 回款跟踪（需求 04-07，T1）：按订单回款计划跟踪到期、逾期，记录催款 */
const router = useRouter()
const tableRef = ref<{ getVisibleColumns: () => TableColumn[] }>()
const summary = ref<{ dueThisMonth: string; overdue: string; receivedThisMonth: string; baseCurrency: string }>()

type Query = { customerId?: string; orderNo?: string; ownerId?: string; statuses?: string[]; due?: [string, string]; overdueOnly?: boolean }
const toParams = (q: Query) => {
  const { statuses, due, ...rest } = q
  return { ...rest, statuses: joinList(statuses), dueFrom: due?.[0], dueTo: due?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, PaymentPlanRow>({
  api: (q) => paymentPlanApi.page(toParams(q) as never),
  refreshOnActivated: true
})
async function loadSummary() {
  summary.value = await paymentPlanApi.summary()
}
onMounted(loadSummary)

const fields: SearchField[] = [
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(PLAN_STATUS), multiple: true },
  { prop: 'due', label: '到期日', type: 'daterange' },
  { prop: 'overdueOnly', label: '逾期', type: 'select', options: [{ value: true, label: '只看逾期' }] }
]

const columns: TableColumn<PaymentPlanRow>[] = [
  { prop: 'orderNo', label: '订单号', width: 160, type: 'link', onClick: (r) => router.push(`/sales/order/${r.orderId}`) },
  { prop: 'customerName', label: '客户', minWidth: 150 },
  { prop: 'nodeName', label: '回款节点', width: 130, formatter: (r) => `${r.seq}. ${r.nodeName}` },
  { prop: 'baseEvent', label: '起算', width: 90, formatter: (r) => `${labelOf(BASE_EVENT_OPTIONS, r.baseEvent)}${r.days ? `+${r.days}天` : ''}` },
  { prop: 'currency', label: '币别', width: 70 },
  { prop: 'planAmount', label: '计划金额', width: 130, type: 'amount', currencyProp: 'currency' },
  { prop: 'receivedAmount', label: '已收', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'unreceivedAmount', label: '未收', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'dueDate', label: '到期日', width: 110, type: 'date' },
  { prop: 'overdueDays', label: '逾期天数', width: 90, slot: true },
  { prop: 'planStatus', label: '状态', width: 90, type: 'status', statusMap: PLAN_STATUS },
  { prop: 'ownerName', label: '业务员', width: 90 },
  { prop: 'remark', label: '最近跟进', minWidth: 200, formatter: (r) => (r.remark ? `${r.remark}${r.followedAt ? `（${formatDateTime(r.followedAt, true)}）` : ''}` : '') },
  { prop: 'promisedPayDate', label: '承诺付款日', width: 110, type: 'date' }
]
const asRow = (r: unknown) => r as PaymentPlanRow
const exportColumns = () => (tableRef.value?.getVisibleColumns() ?? []).map((c) => String(c.prop))

// ---------- 跟进 ----------
const followVisible = ref(false)
const followRow = ref<PaymentPlanRow>()
const followRemark = ref('')
const followDate = ref<string>()
const following = ref(false)
function openFollow(r: PaymentPlanRow) {
  followRow.value = r
  followRemark.value = ''
  followDate.value = r.promisedPayDate
  followVisible.value = true
}
async function saveFollow() {
  if (!followRemark.value.trim()) return ElMessage.warning('请填写跟进内容')
  following.value = true
  try {
    await paymentPlanApi.followUp(followRow.value!.id, followRemark.value.trim(), followDate.value)
    followVisible.value = false
    ElMessage.success('已记录')
    load()
  } finally {
    following.value = false
  }
}
</script>

<template>
  <ErpPage description="订单审核后按付款条件生成回款计划，出货、提单、开票等事件发生后自动计算到期日；收款由财务模块核销回写">
    <div v-if="summary" class="cards">
      <ErpPanel class="card"><div class="label">本月到期未收</div><div class="value">{{ formatMoney(summary.dueThisMonth, summary.baseCurrency) }}</div></ErpPanel>
      <ErpPanel class="card"><div class="label">逾期未收</div><div class="value text-danger">{{ formatMoney(summary.overdue, summary.baseCurrency) }}</div></ErpPanel>
      <ErpPanel class="card"><div class="label">本月已收</div><div class="value text-success">{{ formatMoney(summary.receivedThisMonth, summary.baseCurrency) }}</div></ErpPanel>
    </div>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable ref="tableRef" :columns="columns" :data="list" :loading="loading" storage-key="sal.payment-plan" :actions-width="90" @refresh="() => { load(); loadSummary() }">
        <template #toolbar-right>
          <ExportButton url="/sales/payment-plans/export" :params="() => toParams({ ...query })" :columns="exportColumns" filename="回款计划" permission="sales:order:export" />
        </template>
        <template #col-overdueDays="{ row }">
          <span :class="{ 'text-danger': asRow(row).overdueDays > 0 }">{{ asRow(row).overdueDays > 0 ? `${asRow(row).overdueDays} 天` : '-' }}</span>
        </template>
        <template #actions="{ row }"><RowActions :actions="[{ label: '记录跟进', handler: () => openFollow(asRow(row)) }]" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="followVisible" title="记录跟进" width="480px" :close-on-click-modal="false" append-to-body>
      <p v-if="followRow" class="text-muted">{{ followRow.orderNo }} {{ followRow.nodeName }}，未收 {{ formatMoney(followRow.unreceivedAmount, followRow.currency) }}</p>
      <el-form label-width="100px">
        <el-form-item label="跟进内容" required><el-input v-model="followRemark" type="textarea" :rows="3" maxlength="256" /></el-form-item>
        <el-form-item label="承诺付款日"><el-date-picker v-model="followDate" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followVisible = false">取消</el-button>
        <el-button type="primary" :loading="following" @click="saveFollow">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--erp-space-4); margin-bottom: var(--erp-space-4); }
.card .label { color: var(--erp-color-text-secondary); }
.card .value { margin-top: var(--erp-space-2); font-size: var(--erp-font-size-metric); font-variant-numeric: tabular-nums; }
</style>
