<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import {
  INSP_RESULT, INSP_STATUS, INSPECT_TYPE, IPQC_KIND, inspectionApi, joinList, optionsOf, waitText,
  type InspectionRow, type ProdOrderOption
} from '../api/quality'

/**
 * 检验单列表（需求 10-02 3.1，T1）：IQC / IPQC / FQC / OQC / 退货检验各一个菜单，同一组件按类型过滤。
 * 复检归入 IQC 菜单。
 */
const props = defineProps<{ types: string; perm: string }>()
const router = useRouter()
const me = useUserStore()
const typeList = computed(() => props.types.split(','))
const isIqc = computed(() => typeList.value.includes('IQC'))
const isIpqc = computed(() => typeList.value.includes('IPQC'))
const partnerLabel = computed(() => (isIqc.value ? '供应商' : '客户'))

type Query = {
  types?: string; docNo?: string; materialId?: string; batchNo?: string; supplierId?: string; customerId?: string; upstreamNo?: string
  statuses?: string[]; result?: string; inspectorId?: string; created?: string[]; quick?: string
}
const counts = ref({ pending: 0, overdue: 0, todayJudged: 0 })
const monthAgo = () => {
  const d = new Date(Date.now() - 30 * 86400000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const params = (q: Query) => ({
  ...q, types: props.types, statuses: joinList(q.statuses), dateFrom: q.created?.[0], dateTo: q.created?.[1], created: undefined
})
const { query, list, total, loading, load, search, reset, selection } = useListPage<Query, InspectionRow>({
  api: async (q) => {
    const [page, c] = await Promise.all([inspectionApi.page(params(q) as never), inspectionApi.counts(props.types)])
    counts.value = c
    return page
  },
  defaultQuery: () => ({ statuses: ['PENDING', 'INSPECTING', 'WAIT_MRB'], created: [monthAgo(), ''] }),
  refreshOnActivated: true
})
const fields = computed<SearchField[]>(() => [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'batchNo', label: '批次' },
  { prop: isIqc.value ? 'supplierId' : 'customerId', label: partnerLabel.value, type: 'slot' },
  { prop: 'upstreamNo', label: '上游单号' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(INSP_STATUS), multiple: true },
  { prop: 'result', label: '结果', type: 'select', options: optionsOf(INSP_RESULT) },
  { prop: 'inspectorId', label: '检验员', type: 'user' },
  { prop: 'created', label: '创建日期', type: 'daterange' }
])
const asRow = (r: unknown) => r as InspectionRow
const columns = computed<TableColumn<InspectionRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => open(r.id) },
  ...(typeList.value.length > 1 ? [{ prop: 'inspectType', label: '类型', width: 80, formatter: (r: InspectionRow) => INSPECT_TYPE[r.inspectType] }] : []),
  ...(isIpqc.value ? [{ prop: 'ipqcKind', label: '类别', width: 80, formatter: (r: InspectionRow) => IPQC_KIND[r.ipqcKind ?? ''] ?? '-' }] : []),
  { prop: 'materialCode', label: '物料编码', width: 120 },
  { prop: 'materialName', label: '名称', minWidth: 130 },
  { prop: 'materialSpec', label: '规格', minWidth: 110, hidden: true },
  { prop: 'batchNo', label: '批次', width: 120 },
  { prop: 'partner', label: partnerLabel.value, width: 120, formatter: (r) => (isIqc.value ? r.supplierName : r.customerName) ?? '-' },
  { prop: 'lotQty', label: '批量', width: 90, type: 'qty' },
  { prop: 'sampleQty', label: '样本', width: 70, align: 'right' },
  { prop: 'upstreamNo', label: '上游单号', width: 150 },
  { prop: 'waitMinutes', label: '等待时长', width: 90, slot: true },
  { prop: 'result', label: '结果', width: 70, type: 'status', statusMap: INSP_RESULT },
  { prop: 'qualifiedQty', label: '合格', width: 80, type: 'qty' },
  { prop: 'concessionQty', label: '特采', width: 80, type: 'qty', hidden: true },
  { prop: 'rejectedQty', label: '不合格', width: 80, type: 'qty' },
  { prop: 'inspectorName', label: '检验员', width: 80 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: INSP_STATUS },
  { prop: 'createdAt', label: '创建时间', width: 140, type: 'datetime', hidden: true }
])
function open(id: string) {
  router.push(`/quality/inspection/${id}`)
}
const rowActions = (r: InspectionRow): RowAction[] => [
  { label: '检验', permission: `qc:${props.perm}:inspect`, visible: r.status === 'PENDING' || r.status === 'INSPECTING', handler: () => open(r.id) },
  { label: '查看', visible: !(r.status === 'PENDING' || r.status === 'INSPECTING'), handler: () => open(r.id) }
]
function quick(q: string) {
  query.quick = query.quick === q ? undefined : q
  search()
}
async function batchPass() {
  const ids = selection.value.map((r) => r.id)
  if (!ids.length) return ElMessage.warning('请勾选检验单')
  await ElMessageBox.confirm(`将 ${ids.length} 张检验单判定为合格（仅已录入且建议结果为合格的单据）？`, '批量判定合格')
  const r = await inspectionApi.batchJudgePass(ids)
  if (r.errors.length) ElMessageBox.alert(r.errors.join('\n'), `成功 ${r.success} 张，失败 ${r.errors.length} 张`)
  else ElMessage.success(`已判定 ${r.success} 张`)
  load()
}

// ---------- 手工新建（IPQC 首件 / 巡检 / 末件、复检） ----------
const createVisible = ref(false)
const form = ref<{ inspectType: string; ipqcKind?: string; prodOrderId?: string; operationSeq?: number; materialId?: string; batchNo?: string; lotQty?: string; remark?: string }>({ inspectType: 'IPQC' })
const orders = ref<ProdOrderOption[]>([])
const ops = computed(() => orders.value.find((o) => o.id === form.value.prodOrderId)?.operations ?? [])
async function openCreate() {
  form.value = { inspectType: isIpqc.value ? 'IPQC' : 'RECHECK', ipqcKind: 'FIRST' }
  if (isIpqc.value) orders.value = await inspectionApi.prodOrders()
  createVisible.value = true
}
async function create() {
  if (!form.value.lotQty) return ElMessage.warning('请填写送检数量')
  const id = await inspectionApi.create(form.value)
  createVisible.value = false
  ElMessage.success('已新建检验单')
  open(id)
}
</script>

<template>
  <div>
    <div class="cards">
      <div class="card" :class="{ active: query.quick === 'PENDING' }" @click="quick('PENDING')"><span>待检</span><strong class="num warn">{{ counts.pending }}</strong></div>
      <div class="card" :class="{ active: query.quick === 'OVERDUE' }" @click="quick('OVERDUE')"><span>超时</span><strong class="num danger">{{ counts.overdue }}</strong></div>
      <div class="card" :class="{ active: query.quick === 'TODAY' }" @click="quick('TODAY')"><span>今日已判定</span><strong class="num ok">{{ counts.todayJudged }}</strong></div>
    </div>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection :storage-key="`qc.inspection.${perm}`" :actions-width="90"
                @selection-change="selection = $event" @refresh="load">
        <template #toolbar>
          <el-button v-if="(isIpqc || isIqc) && me.hasPermission('qc:inspection:create')" type="primary" @click="openCreate">{{ isIpqc ? '新建 IPQC' : '新建复检' }}</el-button>
          <el-button v-perm="`qc:${perm}:judge`" @click="batchPass">批量判定合格</el-button>
          <ExportButton url="/quality/inspections/export" :params="() => params(query)" :permission="`qc:${perm}:query`" filename="检验单" />
        </template>
        <template #col-waitMinutes="{ row }">
          <span v-if="['PENDING', 'INSPECTING', 'WAIT_MRB'].includes(asRow(row).status)" :class="{ danger: asRow(row).overdue }">{{ waitText(asRow(row).waitMinutes) }}</span>
          <span v-else>-</span>
        </template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="createVisible" :title="isIpqc ? '新建 IPQC 检验' : '新建复检'" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="96px">
        <template v-if="isIpqc">
          <el-form-item label="检验类别" required>
            <el-radio-group v-model="form.ipqcKind">
              <el-radio-button v-for="k in ['FIRST', 'PATROL', 'LAST']" :key="k" :value="k">{{ IPQC_KIND[k] }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="生产订单" required>
            <el-select v-model="form.prodOrderId" filterable placeholder="已下达 / 生产中的订单">
              <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="`${o.docNo} ${o.materialCode ?? ''} ${o.materialName ?? ''}`" />
            </el-select>
          </el-form-item>
          <el-form-item label="工序">
            <el-select v-model="form.operationSeq" clearable>
              <el-option v-for="op in ops" :key="op.seq" :value="op.seq" :label="`${op.seq} ${op.operation ?? ''}`" />
            </el-select>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="物料" required><MaterialSelect v-model="form.materialId" /></el-form-item>
          <el-form-item label="批次"><el-input v-model="form.batchNo" /></el-form-item>
        </template>
        <el-form-item label="送检数量" required><QtyInput v-model="form.lotQty" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="create">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cards { display: flex; gap: var(--erp-space-4); margin-bottom: var(--erp-section-gap); }
.card {
  flex: 1; display: flex; justify-content: space-between; align-items: center; padding: var(--erp-space-4) var(--erp-space-5); cursor: pointer;
  background: var(--erp-color-surface); border: 1px solid var(--erp-color-border-light); border-radius: var(--erp-radius-card);
}
.card.active { border-color: var(--erp-color-primary); }
.card strong { font-size: var(--erp-font-size-metric); }
.warn { color: var(--erp-color-warning); }
.danger { color: var(--erp-color-error); }
.ok { color: var(--erp-color-success); }
</style>
