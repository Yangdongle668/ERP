<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty, today } from '@/utils/format'
import { joinList, num, optionsOf, pct, workOrderApi, WO_STATUS, type Dispatchable, type DispatchLine, type Load, type WorkCenterSimple, type WorkOrderRow } from '../api/production'

defineOptions({ name: 'MfgWorkOrderList' })

/** 工单派工（需求 09-02，T1 + 派工抽屉）：按工序把生产订单派到日期、班次、工作中心；按工单报工 */
const router = useRouter()
const tab = ref<'orders' | 'dispatch'>('orders')

// ---------- 工单列表 ----------
type Query = { docNo?: string; prodOrderNo?: string; operationSeq?: number; dates?: [string, string]; shift?: string; statuses?: string[] }
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, WorkOrderRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return workOrderApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ statuses: ['DISPATCHED', 'RUNNING'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '工单号', upper: true },
  { prop: 'prodOrderNo', label: '生产订单', upper: true },
  { prop: 'operationSeq', label: '工序号', type: 'number' },
  { prop: 'dates', label: '计划日期', type: 'daterange' },
  { prop: 'shift', label: '班次', type: 'dict', dictType: 'mfg_shift' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(WO_STATUS), multiple: true }
]
const columns: TableColumn<WorkOrderRow>[] = [
  { prop: 'docNo', label: '工单号', width: 150 },
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'materialCode', label: '产品编码', width: 120 },
  { prop: 'materialName', label: '产品名称', minWidth: 140 },
  { prop: 'operationSeq', label: '工序号', width: 70 },
  { prop: 'operation', label: '工序', width: 100 },
  { prop: 'workCenterName', label: '工作中心', width: 120 },
  { prop: 'planDate', label: '计划日期', width: 110, type: 'date' },
  { prop: 'shift', label: '班次', width: 70, type: 'dict', dictType: 'mfg_shift' },
  { prop: 'planQty', label: '派工数量', width: 90, type: 'qty' },
  { prop: 'goodQty', label: '合格', width: 80, type: 'qty' },
  { prop: 'defectQty', label: '不良', width: 70, type: 'qty' },
  { prop: 'scrapQty', label: '报废', width: 70, type: 'qty' },
  { prop: 'completionRate', label: '完成率', width: 80, formatter: (r) => pct(r.completionRate) },
  { prop: 'teamLeaderName', label: '班组长', width: 90 },
  { prop: 'woStatus', label: '状态', width: 80, type: 'status', statusMap: WO_STATUS },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
]
const rowActions = (r: WorkOrderRow): RowAction[] => [
  { label: '报工', permission: 'mfg:report:create', visible: r.woStatus === 'DISPATCHED' || r.woStatus === 'RUNNING',
    handler: () => router.push({ path: '/production/report/new', query: { workOrderId: r.id, prodOrderId: r.prodOrderId, seq: String(r.operationSeq) } }) },
  { label: '完成', permission: 'mfg:work-order:update', visible: r.woStatus === 'RUNNING', confirm: '确定将工单标记为完成吗？', handler: async () => {
    await workOrderApi.complete(r.id)
    ElMessage.success('已完成')
    load()
  } },
  { label: '取消', permission: 'mfg:work-order:update', danger: true, visible: r.woStatus === 'DISPATCHED', confirm: `确定取消工单「${r.docNo}」吗？`, handler: async () => {
    await workOrderApi.cancel(r.id)
    ElMessage.success('已取消')
    load()
  } }
]
const asRow = (r: unknown) => r as WorkOrderRow
const printIds = () => selection.value.map((r) => r.id)

// ---------- 待派工 ----------
const dispDept = ref<string>()
const dispRows = ref<(Dispatchable & { key: string })[]>([])
const dispLoading = ref(false)
async function loadDispatchable() {
  dispLoading.value = true
  try {
    dispRows.value = (await workOrderApi.dispatchable({ deptId: dispDept.value })).map((r) => ({ ...r, key: `${r.prodOrderId}-${r.operationSeq}` }))
  } finally {
    dispLoading.value = false
  }
}
function onTab(t: string | number) {
  if (t === 'dispatch') loadDispatchable()
}
const dispColumns: TableColumn<Dispatchable>[] = [
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'materialCode', label: '产品编码', width: 120 },
  { prop: 'materialName', label: '产品名称', minWidth: 140 },
  { prop: 'priority', label: '优先级', width: 70, align: 'center' },
  { prop: 'planStart', label: '计划开工', width: 110, type: 'date' },
  { prop: 'planEnd', label: '计划完工', width: 110, type: 'date' },
  { prop: 'operationSeq', label: '工序号', width: 70 },
  { prop: 'operation', label: '工序', width: 100 },
  { prop: 'workCenterName', label: '工作中心', width: 120 },
  { prop: 'orderQty', label: '订单数量', width: 90, type: 'qty' },
  { prop: 'dispatchedQty', label: '已派', width: 80, type: 'qty' },
  { prop: 'undispatchedQty', label: '未派', width: 80, type: 'qty' }
]

// ---------- 派工对话框 ----------
const dlgVisible = ref(false)
const target = ref<Dispatchable>()
const lines = ref<(DispatchLine & { load?: Load })[]>([])
const saving = ref(false)
const workCenters = ref<WorkCenterSimple[]>([])
async function openDispatch(r: Dispatchable) {
  if (!workCenters.value.length) workCenters.value = await workOrderApi.workCenters()
  target.value = r
  lines.value = [{ planDate: today(), shift: 'DAY', workCenterId: r.workCenterId, planQty: r.undispatchedQty }]
  dlgVisible.value = true
  refreshLoad(lines.value[0])
}
function addLine() {
  const last = lines.value[lines.value.length - 1]
  const rest = num(target.value?.undispatchedQty) - lines.value.reduce((s, l) => s + num(l.planQty), 0)
  const l = { planDate: last?.planDate ?? today(), shift: last?.shift === 'DAY' ? 'NIGHT' : 'DAY', workCenterId: last?.workCenterId, planQty: rest > 0 ? String(rest) : undefined }
  lines.value.push(l)
  refreshLoad(l)
}
async function refreshLoad(row: unknown) {
  const l = row as DispatchLine & { load?: Load }
  l.load = l.workCenterId && l.planDate ? await workOrderApi.load(l.workCenterId, l.planDate).catch(() => undefined) : undefined
}
const planned = computed(() => lines.value.reduce((s, l) => s + num(l.planQty), 0))
const loadHours = (row: unknown) => {
  const l = row as DispatchLine
  return (num(l.planQty) * num(target.value?.stdRunSeconds)) / 3600
}
async function saveDispatch() {
  if (!lines.value.length) return ElMessage.warning('请至少添加一行派工')
  for (const l of lines.value) {
    if (!l.planDate || !l.shift || !l.workCenterId || !(num(l.planQty) > 0)) return ElMessage.warning('请完整填写日期、班次、工作中心和派工数量')
  }
  if (planned.value > num(target.value?.undispatchedQty)) return ElMessage.warning(`派工数量超过工序可派数量 ${formatQty(target.value?.undispatchedQty)}`)
  saving.value = true
  try {
    const r = await workOrderApi.dispatch({ prodOrderId: target.value!.prodOrderId, operationSeq: target.value!.operationSeq,
      lines: lines.value.map(({ load: _l, ...rest }) => ({ ...rest, remark: rest.remark?.trim() || undefined })) })
    if (r.warnings.length) ElNotification({ type: 'warning', title: `已生成 ${r.ids.length} 张工单`, message: r.warnings.join('；'), duration: 8000 })
    else ElMessage.success(`已生成 ${r.ids.length} 张工单`)
    dlgVisible.value = false
    loadDispatchable()
    load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage description="按工序派工到日期、班次和工作中心；工单条码可直接扫码报工">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="page-tabs" @tab-change="onTab">
        <el-tab-pane label="工单" name="orders">
          <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
          <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="mfg.work-order" :actions-width="150"
                    @selection-change="onSelectionChange" @refresh="load">
            <template #toolbar>
              <el-button v-perm="'mfg:work-order:create'" type="primary" icon="Plus" @click="tab = 'dispatch'; loadDispatchable()">派工</el-button>
              <PrintButton biz-type="MFG_WORK_ORDER" :ids="printIds" permission="mfg:work-order:print" label="打印工单" />
            </template>
            <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
          </ErpTable>
          <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
        </el-tab-pane>
        <el-tab-pane label="待派工" name="dispatch">
          <div class="bar">
            <span>车间</span>
            <OrgTreeSelect v-model="dispDept" only-dept class="dept" @change="loadDispatchable" />
            <el-button icon="Refresh" @click="loadDispatchable">刷新</el-button>
          </div>
          <ErpTable :columns="dispColumns" :data="dispRows" :loading="dispLoading" row-key="key" no-toolbar :actions-width="80"
                    empty-text="没有待派工的工序（已下达、生产中的订单）">
            <template #actions="{ row }">
              <el-button v-perm="'mfg:work-order:create'" link type="primary" @click="openDispatch(row as Dispatchable)">派工</el-button>
            </template>
          </ErpTable>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="dlgVisible" :title="target ? `派工 - ${target.prodOrderNo} 工序 ${target.operationSeq} ${target.operation}` : '派工'" width="900px" append-to-body>
      <p class="hint">
        可派 <strong class="num">{{ formatQty(target?.undispatchedQty) }}</strong>，本次 <strong class="num">{{ formatQty(planned) }}</strong>；
        超出工作中心日产能时提示，不阻止。
      </p>
      <el-table :data="lines">
        <el-table-column label="日期" width="150">
          <template #default="{ row }"><el-date-picker v-model="row.planDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" @change="refreshLoad(row)" /></template>
        </el-table-column>
        <el-table-column label="班次" width="110"><template #default="{ row }"><DictSelect v-model="row.shift" type="mfg_shift" :clearable="false" /></template></el-table-column>
        <el-table-column label="工作中心" width="170">
          <template #default="{ row }">
            <el-select v-model="row.workCenterId" class="w-full" @change="refreshLoad(row)">
              <el-option v-for="w in workCenters" :key="w.id" :value="w.id" :label="`${w.code} ${w.name}`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="派工数量" width="130"><template #default="{ row }"><QtyInput v-model="row.planQty" /></template></el-table-column>
        <el-table-column label="负荷（已派+本次 / 产能 h）" width="190">
          <template #default="{ row }">
            <span v-if="row.load" :class="{ 'text-danger': num(row.load.loadHours) + loadHours(row) > num(row.load.capacityHours) }">
              {{ (num(row.load.loadHours) + loadHours(row)).toFixed(1) }} / {{ num(row.load.capacityHours).toFixed(1) }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="班组长" width="150"><template #default="{ row }"><UserSelect v-model="row.teamLeaderId" /></template></el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="60"><template #default="{ $index }"><el-button link type="danger" @click="lines.splice($index, 1)">删除</el-button></template></el-table-column>
      </el-table>
      <el-button class="add-row" icon="Plus" @click="addLine">添加一行</el-button>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDispatch">生成工单</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.page-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.bar { display: flex; align-items: center; gap: var(--erp-space-3); margin-bottom: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.dept { width: 240px; }
.hint { margin-bottom: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.add-row { margin-top: var(--erp-space-3); }
</style>
