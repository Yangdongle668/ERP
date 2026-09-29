<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { ALERT_CAUSE_OPTIONS, ALERT_LEVEL, ALERT_STATUS, alertApi, joinList, labelOf, optionsOf, type AlertRow } from '../api/pmc'

defineOptions({ name: 'PmcAlertList' })

/** 交期预警（需求 06-07，T1）：预计可出货日期晚于承诺交期的订单行，分级预警；延期消除时自动关闭 */
const router = useRouter()
type Query = { levels?: string[]; cause?: string; customerId?: string; ownerId?: string; materialId?: string; statuses?: string[] }
const toParams = (q: Query) => ({ ...q, levels: joinList(q.levels), statuses: joinList(q.statuses) })
const summary = ref({ critical: 0, warning: 0, info: 0 })
const { query, list, total, loading, load, search, reset } = useListPage<Query, AlertRow>({
  api: async (q) => {
    const p = toParams(q)
    const [page, s] = await Promise.all([alertApi.page(p as never), alertApi.summary({ ...p, levels: undefined })])
    summary.value = s
    return page
  },
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'levels', label: '级别', type: 'select', options: optionsOf(ALERT_LEVEL), multiple: true },
  { prop: 'cause', label: '原因', type: 'select', options: ALERT_CAUSE_OPTIONS },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'statuses', label: '处理状态', type: 'select', options: optionsOf(ALERT_STATUS, ['CLOSED']), multiple: true }
]
const columns: TableColumn<AlertRow>[] = [
  { prop: 'alertLevel', label: '级别', width: 70, type: 'status', statusMap: ALERT_LEVEL },
  { prop: 'orderNo', label: '订单', width: 170, slot: true },
  { prop: 'customerName', label: '客户', width: 120 },
  { prop: 'materialCode', label: '物料编码', width: 120 },
  { prop: 'materialName', label: '名称', minWidth: 130 },
  { prop: 'openQty', label: '未出货', width: 90, type: 'qty' },
  { prop: 'promisedDate', label: '承诺交期', width: 105, type: 'date' },
  { prop: 'estimatedDate', label: '预计日期', width: 105, type: 'date' },
  { prop: 'delayDays', label: '延期(天)', width: 80, align: 'right' },
  { prop: 'cause', label: '原因', width: 110, formatter: (r) => labelOf(ALERT_CAUSE_OPTIONS, r.cause) },
  { prop: 'causeDetail', label: '原因说明', minWidth: 220 },
  { prop: 'ownerName', label: '业务员', width: 80 },
  { prop: 'handleStatus', label: '处理', width: 80, type: 'status', statusMap: ALERT_STATUS },
  { prop: 'handleRemark', label: '处理说明', minWidth: 160, hidden: true },
  { prop: 'calculatedAt', label: '计算时间', width: 140, type: 'datetime', hidden: true }
]
const asRow = (r: unknown) => r as AlertRow
async function prompt(title: string) {
  const { value } = await ElMessageBox.prompt(title, '交期预警', { inputType: 'textarea', inputValidator: (v) => (v && v.trim() ? true : `请填写${title}`) })
  return value.trim()
}
const rowActions = (r: AlertRow): RowAction[] => [
  { label: '处理', permission: 'pmc:alert:handle', visible: r.handleStatus !== 'IGNORED', handler: async () => {
    await alertApi.handle(r.id, await prompt('处理说明（如“已与客户沟通延期至 10-15”）'))
    ElMessage.success('已处理')
    load()
  } },
  { label: '调整交期', permission: 'pmc:delivery:reply', handler: () => router.push('/pmc/delivery-reply') },
  { label: '忽略', permission: 'pmc:alert:handle', visible: r.handleStatus === 'OPEN', handler: async () => {
    await alertApi.ignore(r.id, await prompt('忽略原因'))
    ElMessage.success('已忽略')
    load()
  } }
]
const recalculating = ref(false)
async function recalc() {
  recalculating.value = true
  try {
    const r = await alertApi.recalculate()
    ElMessage.success(`已计算 ${r.lineCount} 个订单行：预警 ${r.alertCount}，新增 / 升级 ${r.raised}，消除 ${r.resolved}`)
    load()
  } finally {
    recalculating.value = false
  }
}
function byLevel(level: string) {
  query.levels = [level]
  search()
}
</script>

<template>
  <ErpPage description="每天 06:00 和每次 MRP 后计算：库存 → 生产订单（排产完工 / 计划完工，缺料顺延）→ 累计提前期估算预计可出货日期">
    <div class="cards">
      <div class="card critical" @click="byLevel('CRITICAL')"><span>严重</span><strong class="num">{{ summary.critical }}</strong></div>
      <div class="card warning" @click="byLevel('WARNING')"><span>警告</span><strong class="num">{{ summary.warning }}</strong></div>
      <div class="card info" @click="byLevel('INFO')"><span>提示</span><strong class="num">{{ summary.info }}</strong></div>
    </div>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pmc.alert" :actions-width="170" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pmc:alert:handle'" :loading="recalculating" @click="recalc">重新计算</el-button>
        </template>
        <template #col-orderNo="{ row }">
          <el-link type="primary" underline="never" @click="router.push(`/sales/order/${asRow(row).orderId}`)">{{ asRow(row).orderNo }}</el-link> 行 {{ asRow(row).lineNo }}
        </template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.cards { display: flex; gap: var(--erp-space-4); margin-bottom: var(--erp-section-gap); }
.card {
  flex: 1; display: flex; justify-content: space-between; align-items: center; padding: var(--erp-space-4) var(--erp-space-5); cursor: pointer;
  background: var(--erp-color-surface); border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-card);
}
.card strong { font-size: var(--erp-font-size-metric); }
.card.critical strong { color: var(--erp-color-error); }
.card.warning strong { color: var(--erp-color-warning); }
.card.info strong { color: var(--erp-color-primary); }
</style>
