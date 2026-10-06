<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatQty } from '@/utils/format'
import {
  ISSUE_METHOD_OPTIONS, issueApi, labelOf, num, OP_STATUS, ORDER_TYPE_OPTIONS, PROD_STATUS, prodOrderApi, withConfirm,
  type AdjustLine, type KitCheck, type MaterialResp, type OperationResp, type ProdOrderDetail
} from '../api/production'

defineOptions({ name: 'MfgProdOrderDetail' })

/** 生产订单详情（需求 09-01 3.3～3.6，T5）：状态操作、用料与工序进度、调整用料、齐套检查、超领、完工入库 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<ProdOrderDetail>()
const activeTab = ref('materials')

async function load() {
  d.value = await prodOrderApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.prodStatus)
const running = computed(() => s.value === 'RELEASED' || s.value === 'IN_PROGRESS')
const active = computed(() => running.value || s.value === 'SUSPENDED' || s.value === 'COMPLETED')

async function release() {
  const r = await withConfirm((c) => prodOrderApi.release(id.value, c), '缺料提醒', '仍然下达')
  if (!r) return
  if (r.warnings.length) ElNotification({ type: 'warning', title: '已下达', message: r.warnings.join('；'), duration: 8000 })
  else ElMessage.success('已下达')
  load()
}
async function close(reason?: string) {
  const r = await withConfirm((c) => prodOrderApi.close(id.value, reason, c).then(() => true), '关闭确认', '确认关闭')
  if (!r) return
  ElMessage.success('已关闭')
  load()
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'mfg:prod-order:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除生产订单「${d.value?.docNo}」吗？`,
    handler: async () => {
      await prodOrderApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/production/prod-order')
    } },
  { key: 'void', label: '作废', type: 'danger', permission: 'mfg:prod-order:void', visible: () => s.value === 'DRAFT' || s.value === 'PLANNED',
    reasonRequired: true, reasonTitle: '作废原因', handler: async (reason) => {
      await prodOrderApi.void(id.value, reason!)
      ElMessage.success('已作废')
      load()
    } },
  { key: 'withdraw', label: '撤回', permission: 'mfg:prod-order:submit', visible: () => s.value === 'PENDING', handler: async () => {
    await prodOrderApi.withdraw(id.value)
    ElMessage.success('已撤回')
    load()
  } },
  { key: 'unrelease', label: '撤销下达', permission: 'mfg:prod-order:unrelease', visible: () => s.value === 'RELEASED',
    confirm: '撤销后回到已计划，未开始的工单取消，确定吗？', handler: async () => {
      await prodOrderApi.unrelease(id.value)
      ElMessage.success('已撤销下达')
      load()
    } },
  { key: 'suspend', label: '暂停', permission: 'mfg:prod-order:suspend', visible: () => running.value, reasonRequired: true, reasonTitle: '暂停原因',
    reasonOptions: ['设备故障', '缺料', '品质异常', '客户要求'], handler: async (reason) => {
      await prodOrderApi.suspend(id.value, reason!)
      ElMessage.success('已暂停')
      load()
    } },
  { key: 'resume', label: '恢复', permission: 'mfg:prod-order:suspend', visible: () => s.value === 'SUSPENDED', handler: async () => {
    await prodOrderApi.resume(id.value)
    ElMessage.success('已恢复')
    load()
  } },
  { key: 'close', label: '关闭', permission: 'mfg:prod-order:close', visible: () => active.value && s.value !== 'COMPLETED', reasonRequired: true, reasonTitle: '关闭原因',
    reasonOptions: ['客户取消', '计划变更', '短装结案'], handler: (reason) => close(reason) },
  { key: 'closeDone', label: '关闭', type: 'primary', permission: 'mfg:prod-order:close', visible: () => s.value === 'COMPLETED',
    confirm: '订单已完工，关闭后不能再领料、报工和入库，确定吗？', handler: () => close() },
  { key: 'kit', label: '齐套检查', permission: 'mfg:prod-order:query', visible: () => s.value === 'PLANNED' || running.value, handler: kitCheck },
  { key: 'edit', label: '编辑', permission: 'mfg:prod-order:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/production/prod-order/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'mfg:prod-order:submit', visible: () => s.value === 'DRAFT', handler: async () => {
    const r = await prodOrderApi.submit(id.value)
    ElMessage.success(r.status === 'PENDING' ? '已提交，等待审批' : '提交成功，已计划')
    load()
  } },
  { key: 'release', label: '下达', type: 'primary', permission: 'mfg:prod-order:release', visible: () => s.value === 'PLANNED', handler: release },
  { key: 'issue', label: '领料', permission: 'mfg:issue:create', visible: () => running.value,
    handler: () => router.push({ path: '/production/issue/new', query: { prodOrderId: id.value } }) },
  { key: 'report', label: '报工', permission: 'mfg:report:create', visible: () => running.value,
    handler: () => router.push({ path: '/production/report/new', query: { prodOrderId: id.value } }) },
  { key: 'finish', label: '完工入库', type: 'primary', permission: 'mfg:finish:create',
    visible: () => active.value && !disassembly.value && num(d.value?.finishableQty) > 0, handler: openFinish },
  { key: 'output', label: '拆解入库', type: 'primary', permission: 'mfg:return:create', visible: () => active.value && disassembly.value,
    handler: () => router.push({ path: '/production/return/new', query: { prodOrderId: id.value, returnType: 'OUTPUT' } }) }
])
const disassembly = computed(() => d.value?.orderType === 'DISASSEMBLY')

const steps = [
  { status: 'DRAFT', label: '草稿' }, { status: 'PLANNED', label: '已计划' }, { status: 'RELEASED', label: '已下达' },
  { status: 'IN_PROGRESS', label: '生产中' }, { status: 'COMPLETED', label: '已完工' }, { status: 'CLOSED', label: '已关闭' }
]
const stepStatus = computed(() => (s.value === 'PENDING' ? 'DRAFT' : s.value === 'SUSPENDED' ? 'IN_PROGRESS' : s.value))

// ---------- 用料、工序 ----------
const materialColumns: TableColumn<MaterialResp>[] = [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'code', label: '物料编码', width: 130 },
  { prop: 'name', label: '名称', minWidth: 140, slot: true },
  { prop: 'spec', label: '规格', width: 120, hidden: true },
  { prop: 'qtyPer', label: '单位用量', width: 90, type: 'qty', precision: 6 },
  { prop: 'requiredQty', label: '应领', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'issuedQty', label: '已领', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'overIssuedQty', label: '其中超领', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'returnedQty', label: '已退', width: 80, type: 'qty', uomProp: 'uom' },
  { prop: 'openQty', label: '未领', width: 90, type: 'qty', uomProp: 'uom' },
  { prop: 'netQty', label: '净耗用', width: 90, type: 'qty', uomProp: 'uom', hidden: true },
  { prop: 'availableQty', label: '可用库存', width: 100, slot: true },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'issueMethod', label: '发料', width: 70, formatter: (r) => labelOf(ISSUE_METHOD_OPTIONS, r.issueMethod) },
  { prop: 'operationSeq', label: '工序', width: 60 },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
]
const opColumns: TableColumn<OperationResp>[] = [
  { prop: 'seq', label: '工序号', width: 70 },
  { prop: 'operation', label: '工序', minWidth: 160 },
  { prop: 'workCenterName', label: '工作中心', width: 130 },
  { prop: 'reportPoint', label: '报工点', width: 70, type: 'bool' },
  { prop: 'inspectionPoint', label: '检验点', width: 70, type: 'bool' },
  { prop: 'dispatchedQty', label: '已派工', width: 90, type: 'qty' },
  { prop: 'goodQty', label: '合格', width: 90, type: 'qty' },
  { prop: 'defectQty', label: '待处理不良', width: 100, type: 'qty' },
  { prop: 'repairedQty', label: '返修合格', width: 90, type: 'qty' },
  { prop: 'scrapQty', label: '报废', width: 80, type: 'qty' },
  { prop: 'reportableQty', label: '可报', width: 90, type: 'qty' },
  { prop: 'actualHours', label: '实际工时', width: 90, type: 'qty', precision: 2 },
  { prop: 'stdHours', label: '标准工时', width: 90, type: 'qty', precision: 2 },
  { prop: 'opStatus', label: '状态', width: 80, type: 'status', statusMap: OP_STATUS }
]
const asMat = (r: unknown) => r as MaterialResp
const asOp = (r: unknown) => r as OperationResp

// ---------- 齐套检查 ----------
const kit = ref<KitCheck>()
const kitVisible = ref(false)
async function kitCheck() {
  kit.value = await prodOrderApi.kitCheck(id.value)
  kitVisible.value = true
}

// ---------- 超领 ----------
const overVisible = ref(false)
const over = ref<{ line?: MaterialResp; qty?: string; overReason?: string; overRemark?: string }>({})
const overSaving = ref(false)
function openOver(m: MaterialResp) {
  over.value = { line: m }
  overVisible.value = true
}
async function saveOver() {
  const o = over.value
  if (!(num(o.qty) > 0)) return ElMessage.warning('请填写超领数量')
  if (!o.overReason) return ElMessage.warning('请选择超领原因')
  overSaving.value = true
  try {
    const r = await issueApi.over({ prodOrderId: id.value, materialLineId: o.line!.id, qty: o.qty!, overReason: o.overReason, overRemark: o.overRemark, submit: true })
    ElMessage.success(`已生成超领单 ${r.docNos.join('、')}`)
    overVisible.value = false
    load()
  } finally {
    overSaving.value = false
  }
}

// ---------- 调整用料 ----------
interface AdjRow extends AdjustLine { key: string; code?: string; name?: string; uom?: string; issuedQty?: string; substituteId?: string; substituteQty?: string; subs?: MaterialResp['substitutes'] }
const adjVisible = ref(false)
const adjSaving = ref(false)
const adj = ref<{ reason?: string; qty?: string; rows: AdjRow[] }>({ rows: [] })
let adjSeq = 0
function openAdjust() {
  adj.value = {
    qty: undefined,
    rows: d.value!.materials.map((m) => ({ key: m.id, id: m.id, code: m.code, name: m.name, uom: m.uom, issuedQty: m.issuedQty, requiredQty: m.requiredQty,
      issueMethod: m.issueMethod, operationSeq: m.operationSeq, remark: m.remark, subs: m.substitutes }))
  }
  adjVisible.value = true
}
function addAdjRow() {
  adj.value.rows.push({ key: `n${++adjSeq}`, componentId: undefined, requiredQty: undefined, issueMethod: 'PICK' })
}
async function saveAdjust() {
  const a = adj.value
  if (!a.reason?.trim()) return ElMessage.warning('请填写调整原因')
  const orig = new Map(d.value!.materials.map((m) => [m.id, m]))
  const lines: AdjustLine[] = []
  for (const r of a.rows) {
    if (!r.id) {
      if (!r.componentId || !(num(r.requiredQty) > 0)) return ElMessage.warning('新增行请选择物料并填写应领数量')
      lines.push({ componentId: r.componentId, requiredQty: r.requiredQty, issueMethod: r.issueMethod, operationSeq: r.operationSeq, remark: r.remark })
      continue
    }
    const m = orig.get(r.id)!
    if (r.delete || num(r.requiredQty) !== num(m.requiredQty) || r.issueMethod !== m.issueMethod || (r.remark ?? '') !== (m.remark ?? '')) {
      lines.push({ id: r.id, requiredQty: r.requiredQty, issueMethod: r.issueMethod, delete: r.delete, remark: r.remark })
    }
  }
  const substitutions = a.rows.filter((r) => r.id && r.substituteId && num(r.substituteQty) > 0)
    .map((r) => ({ lineId: r.id!, substituteId: r.substituteId!, qty: r.substituteQty! }))
  if (!lines.length && !substitutions.length && !a.qty) return ElMessage.warning('没有需要调整的内容')
  adjSaving.value = true
  try {
    await prodOrderApi.adjust(id.value, { reason: a.reason.trim(), qty: a.qty || undefined, lines, substitutions, version: d.value!.version })
    ElMessage.success('用料已调整')
    adjVisible.value = false
    load()
  } finally {
    adjSaving.value = false
  }
}

// ---------- 完工入库 ----------
const finVisible = ref(false)
const fin = ref<{ qty?: string; batchNo?: string; serialNos?: string; remark?: string }>({})
const finSaving = ref(false)
function openFinish() {
  fin.value = { qty: d.value?.finishableQty, batchNo: d.value?.batchNo }
  finVisible.value = true
}
async function saveFinish() {
  const f = fin.value
  if (!(num(f.qty) > 0)) return ElMessage.warning('请填写入库数量')
  if (num(f.qty) > num(d.value?.finishableQty)) return ElMessage.warning(`可申请入库数量为 ${formatQty(d.value?.finishableQty)}`)
  finSaving.value = true
  try {
    const serialNos = f.serialNos?.split(/[\s,，]+/).map((x) => x.trim()).filter(Boolean)
    await prodOrderApi.finish(id.value, { qty: f.qty!, batchNo: f.batchNo?.trim() || undefined, serialNos: serialNos?.length ? serialNos : undefined, remark: f.remark?.trim() || undefined })
    ElMessage.success('已生成完工入库单，等待仓库确认')
    finVisible.value = false
    load()
  } finally {
    finSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '生产订单'" :status="d?.prodStatus" :status-map="PROD_STATUS" :actions="actions" @back="router.push('/production/prod-order')">
        <template #extra>
          <ErpBadge v-if="d && d.orderType !== 'NORMAL'" type="primary" :dot="false">{{ labelOf(ORDER_TYPE_OPTIONS, d.orderType) }}</ErpBadge>
          <ErpBadge v-if="d && num(d.pendingDefectQty) > 0" type="warning">待处理不良 {{ formatQty(d.pendingDefectQty) }}</ErpBadge>
        </template>
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="MFG_PROD_ORDER" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && s !== 'DRAFT' && s !== 'VOIDED'" biz-type="MFG_PROD_ORDER" :ids="[id]" permission="mfg:prod-order:print" label="打印流程卡" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="stepStatus" :terminal="{ CLOSED: `已关闭：${d.closeReason ?? ''}`, VOIDED: '已作废' }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="产品">{{ d.materialCode }} {{ d.materialName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ d.materialSpec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ formatQty(d.qty) }} {{ d.baseUom }}</el-descriptions-item>
          <el-descriptions-item label="生产批号">{{ d.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="BOM">{{ d.bomNo ? `${d.bomNo} V${d.bomVersion}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="工艺路线">{{ d.routingNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="车间">{{ d.deptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ d.priority }}</el-descriptions-item>
          <el-descriptions-item label="计划开工 / 完工">{{ d.planStart }} ~ {{ d.planEnd }}</el-descriptions-item>
          <el-descriptions-item label="实际开工 / 完工">
            {{ d.actualStart ? formatDateTime(d.actualStart, true) : '-' }} ~ {{ d.actualEnd ? formatDateTime(d.actualEnd, true) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="下达时间">{{ d.releasedAt ? formatDateTime(d.releasedAt, true) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售订单">
            <el-link v-if="d.salesOrderId" type="primary" underline="never" @click="router.push(`/sales/order/${d.salesOrderId}`)">{{ d.salesOrderNo }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ d.sourceNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="FQC">{{ d.fqcRequired ? '需要' : '免检' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="kpis">
          <div class="kpi"><span>完工</span><strong class="num">{{ formatQty(d.completedQty) }}</strong></div>
          <div class="kpi"><span>报废</span><strong class="num">{{ formatQty(d.scrappedQty) }}</strong></div>
          <div class="kpi"><span>已申请入库</span><strong class="num">{{ formatQty(d.finishedRequestQty) }}</strong></div>
          <div class="kpi"><span>已入库</span><strong class="num">{{ formatQty(d.stockedQty) }}</strong></div>
          <div class="kpi"><span>合格入库</span><strong class="num">{{ formatQty(d.qualifiedStockedQty) }}</strong></div>
          <div class="kpi"><span>FQC 判退</span><strong class="num">{{ formatQty(d.fqcRejectedQty) }}</strong></div>
          <div class="kpi"><span>可申请入库</span><strong class="num">{{ formatQty(d.finishableQty) }}</strong></div>
        </div>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`用料(${d.materials.length})`" name="materials">
            <ErpTable :columns="materialColumns" :data="d.materials" storage-key="mfg.prod-order-materials" :actions-width="running ? 80 : 0">
              <template #toolbar>
                <el-button v-if="running" v-perm="'mfg:prod-order:update'" @click="openAdjust">调整用料</el-button>
              </template>
              <template #col-name="{ row }">
                {{ asMat(row).name }}
                <ErpBadge v-if="asMat(row).substituteOfId" type="primary" :dot="false">替代</ErpBadge>
                <ErpBadge v-else-if="asMat(row).added" type="primary" :dot="false">新增</ErpBadge>
              </template>
              <template #col-availableQty="{ row }">
                <span :class="{ 'text-danger': num(asMat(row).availableQty) < num(asMat(row).openQty) }">{{ formatQty(asMat(row).availableQty) }}</span>
              </template>
              <template v-if="running" #actions="{ row }">
                <el-button v-if="asMat(row).issueMethod === 'PICK'" v-perm="'mfg:issue:over'" link type="primary" @click="openOver(asMat(row))">超领</el-button>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane v-if="disassembly" :label="`拆解产出(${d.outputs.length})`" name="outputs">
            <el-table :data="d.outputs">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="子件" min-width="220"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
              <el-table-column prop="spec" label="规格" width="140" show-overflow-tooltip />
              <el-table-column label="单位产出" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer, 6) }}</template></el-table-column>
              <el-table-column label="预计产出" width="110" align="right"><template #default="{ row }">{{ formatQty(row.expectedQty) }}</template></el-table-column>
              <el-table-column label="已入库" width="110" align="right"><template #default="{ row }">{{ formatQty(row.receivedQty) }}</template></el-table-column>
              <el-table-column prop="uom" label="单位" width="60" />
              <template #empty><ErpEmpty compact description="下达后按 BOM 生成拆解产出" /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`工序(${d.operations.length})`" name="ops">
            <ErpTable :columns="opColumns" :data="d.operations" storage-key="mfg.prod-order-ops" empty-text="没有工艺路线：按末道报工">
              <template #col-operation="{ row }">
                {{ asOp(row).operation }}
                <el-tooltip v-if="asOp(row).ipqcRejectedId" :content="`IPQC ${asOp(row).ipqcRejectedNo} 判定拒收，点击查看`">
                  <ErpBadge type="danger" :dot="false" class="clickable" @click="router.push(`/quality/inspection/${asOp(row).ipqcRejectedId}`)">IPQC 不合格</ErpBadge>
                </el-tooltip>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="MFG_PROD_ORDER" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="MFG_PROD_ORDER" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="MFG_PROD_ORDER" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="kitVisible" :title="kit?.complete ? '齐套检查：已齐套' : '齐套检查：缺料'" width="720px" append-to-body>
      <el-result v-if="kit?.complete" icon="success" title="所有领料物料库存充足" />
      <el-table v-else :data="kit?.shortages ?? []">
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
        <el-table-column label="需求" width="110" align="right"><template #default="{ row }">{{ formatQty(row.requiredQty) }}</template></el-table-column>
        <el-table-column label="可用" width="110" align="right"><template #default="{ row }">{{ formatQty(row.availableQty) }}</template></el-table-column>
        <el-table-column label="缺料" width="110" align="right"><template #default="{ row }"><span class="text-danger">{{ formatQty(row.shortageQty) }}</span></template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
      </el-table>
      <p class="hint">可用库存已扣除先下达的其他生产订单的未领数量。</p>
    </el-dialog>

    <el-dialog v-model="overVisible" :title="`超领 - ${over.line?.code ?? ''} ${over.line?.name ?? ''}`" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="已领 / 应领">{{ formatQty(over.line?.issuedQty) }} / {{ formatQty(over.line?.requiredQty) }} {{ over.line?.uom }}</el-form-item>
        <el-form-item label="超领数量" required><QtyInput v-model="over.qty" :uom="over.line?.uom" /></el-form-item>
        <el-form-item label="超领原因" required><DictSelect v-model="over.overReason" type="mfg_over_issue_reason" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="over.overRemark" type="textarea" :rows="2" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overVisible = false">取消</el-button>
        <el-button type="primary" :loading="overSaving" @click="saveOver">提交超领</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjVisible" title="调整用料" width="1000px" append-to-body>
      <el-form label-width="90px" inline>
        <el-form-item label="调整原因" required><el-input v-model="adj.reason" maxlength="256" class="reason" /></el-form-item>
        <el-form-item label="计划数量"><QtyInput v-model="adj.qty" :uom="d?.baseUom" :placeholder="`当前 ${formatQty(d?.qty)}，修改后应领按比例重算`" /></el-form-item>
      </el-form>
      <el-table :data="adj.rows" row-key="key" max-height="420">
        <el-table-column label="物料" min-width="220">
          <template #default="{ row }">
            <span v-if="row.id" :class="{ 'text-muted': row.delete }">{{ row.code }} {{ row.name }}</span>
            <MaterialSelect v-else v-model="row.componentId" />
          </template>
        </el-table-column>
        <el-table-column label="已领" width="90" align="right"><template #default="{ row }">{{ row.id ? formatQty(row.issuedQty) : '-' }}</template></el-table-column>
        <el-table-column label="应领" width="130"><template #default="{ row }"><QtyInput v-model="row.requiredQty" :uom="row.uom" :disabled="row.delete" /></template></el-table-column>
        <el-table-column label="发料方式" width="110">
          <template #default="{ row }">
            <el-select v-model="row.issueMethod" :disabled="row.delete"><el-option v-for="o in ISSUE_METHOD_OPTIONS" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="改用替代料" width="260">
          <template #default="{ row }">
            <div v-if="row.subs?.length" class="subst">
              <el-select v-model="row.substituteId" clearable placeholder="替代料">
                <el-option v-for="sb in row.subs" :key="sb.substituteId" :value="sb.substituteId" :label="`${sb.code} ${sb.name}（1:${sb.ratio}）`" />
              </el-select>
              <QtyInput v-model="row.substituteQty" :uom="row.uom" placeholder="主料数量" />
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" :disabled="row.delete" /></template></el-table-column>
        <el-table-column label="" width="70">
          <template #default="{ row, $index }">
            <el-button v-if="!row.id" link type="danger" @click="adj.rows.splice($index, 1)">移除</el-button>
            <el-checkbox v-else-if="num(row.issuedQty) === 0" v-model="row.delete">删</el-checkbox>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="add-row" icon="Plus" @click="addAdjRow">新增用料</el-button>
      <template #footer>
        <el-button @click="adjVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjSaving" @click="saveAdjust">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="finVisible" title="完工入库申请" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="可申请入库">{{ formatQty(d?.finishableQty) }} {{ d?.baseUom }}</el-form-item>
        <el-form-item label="入库数量" required><QtyInput v-model="fin.qty" :uom="d?.baseUom" /></el-form-item>
        <el-form-item label="批次号"><el-input v-model="fin.batchNo" maxlength="64" placeholder="默认同生产批号" /></el-form-item>
        <el-form-item v-if="d?.tracking === 'SERIAL'" label="序列号" required>
          <el-input v-model="fin.serialNos" type="textarea" :rows="3" placeholder="每行或用逗号分隔一个序列号，数量与入库数量一致" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="fin.remark" maxlength="1000" /></el-form-item>
      </el-form>
      <p class="hint">{{ d?.fqcRequired ? '需要 FQC：入库到待检仓，检验合格后计入合格入库。' : '免检产品：仓库确认后直接计入合格入库。' }}</p>
      <template #footer>
        <el-button @click="finVisible = false">取消</el-button>
        <el-button type="primary" :loading="finSaving" @click="saveFinish">提交</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.kpis { display: flex; flex-wrap: wrap; gap: var(--erp-space-6); margin-top: var(--erp-space-4); padding-top: var(--erp-space-4); border-top: 1px solid var(--erp-color-border); }
.kpi { display: flex; flex-direction: column; gap: var(--erp-space-1); color: var(--erp-color-text-secondary); }
.kpi strong { font-size: var(--erp-font-size-section-title); color: var(--erp-color-text); }
.hint { margin-top: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.reason { width: 320px; }
.subst { display: flex; gap: var(--erp-space-2); }
.add-row { margin-top: var(--erp-space-3); }
.clickable { cursor: pointer; margin-left: var(--erp-space-1); }
</style>
