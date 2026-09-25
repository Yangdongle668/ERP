<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatMoney, formatQty } from '@/utils/format'
import { INSPECT_STATUS, labelOf, num, submitText } from '../api/common'
import { OS_STATUS, outsourcingApi, TXN_TYPE_OPTIONS, type OsDetail, type OsMaterial } from '../api/outsourcing'
import { RECEIPT_STATUS } from '../api/receipt'

defineOptions({ name: 'PurOutsourcingDetail' })

/** 委外单详情（需求 07-07 3.3，T5）：发料、收货、余料退回、核销 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<OsDetail>()
const activeTab = ref('materials')

async function load() {
  d.value = await outsourcingApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}

const s = computed(() => d.value?.status)
const active = computed(() => s.value === 'APPROVED' || s.value === 'IN_PROGRESS')
const hasExecution = computed(() => num(d.value?.receivedQty) > 0 || (d.value?.materials ?? []).some((m) => num(m.issuedQty) > 0))

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:outsourcing:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除委外单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await outsourcingApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/outsourcing')
    } },
  { key: 'void', label: '作废', permission: 'pur:outsourcing:delete', visible: () => s.value === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    confirm: '作废后单据不可恢复，也不再参与任何统计。', handler: (reason) => run(outsourcingApi.void(id.value, reason!), '已作废') },
  { key: 'unapprove', label: '反审核', permission: 'pur:outsourcing:submit', visible: () => s.value === 'APPROVED' && !hasExecution.value, reasonRequired: true,
    reasonTitle: '反审核原因', confirm: '反审核后单据回到草稿状态，可以修改。', handler: (reason) => run(outsourcingApi.unapprove(id.value, reason!), '已反审核') },
  { key: 'close', label: '关闭', permission: 'pur:outsourcing:close', visible: () => active.value, reasonRequired: true, reasonTitle: '关闭原因',
    confirm: '关闭后剩余数量不再执行。', handler: (reason) => run(outsourcingApi.close(id.value, reason!), '已关闭') },
  { key: 'return', label: '余料退回', permission: 'pur:outsourcing:issue', visible: () => active.value, handler: () => openQty('RETURN') },
  { key: 'issue', label: '发料', permission: 'pur:outsourcing:issue', visible: () => active.value, handler: () => openQty('ISSUE') },
  { key: 'receive', label: '收货', permission: 'pur:receipt:create', visible: () => active.value,
    handler: () => router.push({ path: '/purchase/receipt/new', query: { outsourcingId: id.value } }) },
  { key: 'settle', label: '核销', type: 'primary', permission: 'pur:outsourcing:receive', visible: () => active.value && num(d.value?.receivedQty) >= num(d.value?.qty),
    handler: openSettle },
  { key: 'edit', label: '编辑', permission: 'pur:outsourcing:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/outsourcing/${id.value}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: 'pur:outsourcing:submit', visible: () => s.value === 'DRAFT',
    handler: async () => {
      ElMessage.success(submitText((await outsourcingApi.submit(id.value)).status))
      load()
    } }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'PENDING_APPROVAL', label: '待审批' },
  { status: 'APPROVED', label: '已审核' },
  { status: 'IN_PROGRESS', label: '执行中' },
  { status: 'COMPLETED', label: '已核销' }
]

// ---------- 发料 / 余料退回 ----------
interface QtyRow extends OsMaterial {
  thisQty?: string
  defective?: boolean
  max: number
}
const qtyMode = ref<'ISSUE' | 'RETURN'>('ISSUE')
const qtyVisible = ref(false)
const qtyRows = ref<QtyRow[]>([])
const running = ref(false)
function openQty(mode: 'ISSUE' | 'RETURN') {
  qtyMode.value = mode
  qtyRows.value = (d.value?.materials ?? []).map((m) => {
    const max = mode === 'ISSUE' ? Math.max(0, num(m.requiredQty) - num(m.issuedQty)) : Math.max(0, num(m.issuedQty) - num(m.returnedQty))
    return { ...m, max, thisQty: mode === 'ISSUE' && max > 0 ? String(max) : undefined, defective: false }
  })
  qtyVisible.value = true
}
async function submitQty() {
  const lines = qtyRows.value.filter((r) => num(r.thisQty) > 0)
  if (!lines.length) return ElMessage.warning('请填写本次数量')
  running.value = true
  try {
    const r = qtyMode.value === 'ISSUE'
      ? await outsourcingApi.issue(id.value, lines.map((l) => ({ outsourcingMaterialId: l.id!, qty: l.thisQty! })))
      : await outsourcingApi.returnMaterial(id.value, lines.map((l) => ({ outsourcingMaterialId: l.id!, qty: l.thisQty!, defective: l.defective })))
    qtyVisible.value = false
    ElMessage.success(`已生成 ${r.stockDocIds.length} 张${qtyMode.value === 'ISSUE' ? '发料出库单' : '退料入库单'}，仓库确认后回写数量`)
    load()
  } finally {
    running.value = false
  }
}

// ---------- 核销 ----------
const settleVisible = ref(false)
const settleRows = ref<(OsMaterial & { reason?: string; consumed: number; loss: number })[]>([])
function openSettle() {
  const q = num(d.value?.qualifiedQty)
  settleRows.value = (d.value?.materials ?? []).map((m) => {
    const consumed = num(m.previewConsumed ?? String(q * num(m.qtyPer)))
    const loss = m.previewLoss !== undefined && m.previewLoss !== null ? num(m.previewLoss) : num(m.issuedQty) - num(m.returnedQty) - consumed
    return { ...m, consumed, loss, reason: m.lossReason }
  })
  settleVisible.value = true
}
async function submitSettle() {
  const miss = settleRows.value.find((r) => r.loss > 0 && !r.reason?.trim())
  if (miss) return ElMessage.warning(`物料「${miss.materialCode}」超耗 ${formatQty(miss.loss)}，请填写原因`)
  running.value = true
  try {
    await outsourcingApi.settle(id.value, settleRows.value.map((r) => ({ outsourcingMaterialId: r.id!, lossReason: r.reason?.trim() || undefined })))
    settleVisible.value = false
    ElMessage.success('核销完成')
    load()
  } finally {
    running.value = false
  }
}

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '委外单'" :status="d?.status" :status-map="OS_STATUS" :actions="actions" @back="router.push('/purchase/outsourcing')">
        <template #actions-prefix>
          <ApprovalActions v-if="d" biz-type="PUR_OUTSOURCING" :biz-id="id" @changed="load" />
          <PrintButton v-if="d && d.status !== 'VOIDED'" biz-type="PUR_OUTSOURCING" :ids="[id]" permission="pur:outsourcing:print" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CLOSED: `已关闭：${d.closeReason ?? ''}`, VOIDED: '已作废' }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="加工商">
            <el-link type="primary" underline="never" @click="router.push(`/purchase/supplier/${d.supplierId}`)">{{ d.supplierName }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="加工物料">{{ d.materialCode }} {{ d.materialName }}</el-descriptions-item>
          <el-descriptions-item label="BOM">{{ d.bomNo ? `${d.bomNo} V${d.bomVersion}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ formatQty(d.qty) }} {{ d.uom }}</el-descriptions-item>
          <el-descriptions-item v-if="d.priceVisible" label="加工费单价">{{ d.processPrice ?? '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.priceVisible" label="价税合计">{{ formatMoney(d.totalAmount, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="要求日期">{{ d.requiredDate }}</el-descriptions-item>
          <el-descriptions-item label="已收货 / 合格">{{ formatQty(d.receivedQty) }} / {{ formatQty(d.qualifiedQty) }}</el-descriptions-item>
          <el-descriptions-item label="已发材料可生产">{{ formatQty(d.kitQty) }}</el-descriptions-item>
          <el-descriptions-item label="采购员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`用料(${d.materials.length})`" name="materials">
            <el-table :data="d.materials">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column label="子件" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column prop="uom" label="单位" width="60" />
              <el-table-column label="单位用量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer) }}</template></el-table-column>
              <el-table-column label="应发" width="90" align="right"><template #default="{ row }">{{ formatQty(row.requiredQty) }}</template></el-table-column>
              <el-table-column label="已发" width="90" align="right"><template #default="{ row }">{{ formatQty(row.issuedQty) }}</template></el-table-column>
              <el-table-column label="退回" width="90" align="right"><template #default="{ row }">{{ formatQty(row.returnedQty) }}</template></el-table-column>
              <el-table-column label="消耗" width="90" align="right"><template #default="{ row }">{{ row.consumedQty ? formatQty(row.consumedQty) : '-' }}</template></el-table-column>
              <el-table-column label="超耗" width="90" align="right">
                <template #default="{ row }"><span :class="{ 'text-danger': num(row.lossQty) > 0 }">{{ row.lossQty ? formatQty(row.lossQty) : '-' }}</span></template>
              </el-table-column>
              <el-table-column prop="lossReason" label="超耗原因" min-width="140" />
              <el-table-column prop="adjustReason" label="应发调整原因" min-width="140" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`收货记录(${d.receipts.length})`" name="receipts">
            <el-table :data="d.receipts">
              <el-table-column label="到货单" width="160">
                <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/purchase/receipt/${row.receiptId}`)">{{ row.receiptNo }}</el-link></template>
              </el-table-column>
              <el-table-column label="到货时间" width="150"><template #default="{ row }">{{ formatDateTime(row.arrivalAt, true) }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.receiptStatus" :map="RECEIPT_STATUS" /></template></el-table-column>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="已入库" width="100" align="right"><template #default="{ row }">{{ formatQty(row.stockedQty) }}</template></el-table-column>
              <el-table-column label="检验" width="100"><template #default="{ row }"><StatusTag :value="row.inspectStatus" :map="INSPECT_STATUS" /></template></el-table-column>
              <el-table-column label="合格" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qualifiedQty) }}</template></el-table-column>
              <el-table-column label="不合格" width="90" align="right"><template #default="{ row }">{{ formatQty(row.rejectedQty) }}</template></el-table-column>
              <template #empty><ErpEmpty compact /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`发料与退料(${d.txns.length})`" name="txns">
            <el-table :data="d.txns">
              <el-table-column label="类型" width="100"><template #default="{ row }">{{ labelOf(TXN_TYPE_OPTIONS, row.txnType) }}</template></el-table-column>
              <el-table-column prop="stockDocNo" label="仓库单据" width="160" />
              <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.reversed ? '已冲回' : '有效' }}</template></el-table-column>
              <el-table-column label="时间" width="150"><template #default="{ row }">{{ formatDateTime(row.createdAt, true) }}</template></el-table-column>
              <template #empty><ErpEmpty compact /></template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="PUR_OUTSOURCING" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_OUTSOURCING" :biz-id="id" :status-map="OS_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_OUTSOURCING" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="qtyVisible" :title="qtyMode === 'ISSUE' ? '发料' : '余料退回'" width="960px" :close-on-click-modal="false" append-to-body>
      <p class="form-tip">{{ qtyMode === 'ISSUE' ? '按物料默认仓生成委外发料出库单，仓库确认出库后回写已发数量' : '良品入物料默认仓，不良入不良品仓；仓库确认入库后回写退回数量' }}</p>
      <el-table :data="qtyRows">
        <el-table-column label="子件" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="应发" width="90" align="right"><template #default="{ row }">{{ formatQty(row.requiredQty) }}</template></el-table-column>
        <el-table-column label="已发" width="90" align="right"><template #default="{ row }">{{ formatQty(row.issuedQty) }}</template></el-table-column>
        <el-table-column label="已退" width="90" align="right"><template #default="{ row }">{{ formatQty(row.returnedQty) }}</template></el-table-column>
        <el-table-column label="本次数量" width="140"><template #default="{ row }"><QtyInput v-model="row.thisQty" :uom="row.uom" /></template></el-table-column>
        <el-table-column v-if="qtyMode === 'RETURN'" label="不良" width="70" align="center"><template #default="{ row }"><el-checkbox v-model="row.defective" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="qtyVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="submitQty">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settleVisible" title="核销" width="960px" :close-on-click-modal="false" append-to-body>
      <p class="form-tip">消耗 = 合格数量 {{ formatQty(d?.qualifiedQty) }} × 单位用量；超耗 = 已发 − 退回 − 消耗，超耗 &gt; 0 的行必须填写原因</p>
      <el-table :data="settleRows">
        <el-table-column label="子件" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="已发" width="90" align="right"><template #default="{ row }">{{ formatQty(row.issuedQty) }}</template></el-table-column>
        <el-table-column label="退回" width="90" align="right"><template #default="{ row }">{{ formatQty(row.returnedQty) }}</template></el-table-column>
        <el-table-column label="消耗" width="90" align="right"><template #default="{ row }">{{ formatQty(row.consumed) }}</template></el-table-column>
        <el-table-column label="超耗" width="90" align="right"><template #default="{ row }"><span :class="{ 'text-danger': row.loss > 0 }">{{ formatQty(row.loss) }}</span></template></el-table-column>
        <el-table-column label="超耗原因" min-width="200"><template #default="{ row }"><el-input v-if="row.loss > 0" v-model="row.reason" maxlength="256" placeholder="必填" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="settleVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="submitSettle">核销</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
