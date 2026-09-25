<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import {
  docApi, DOC_STATUS, IN_TYPE_OPTIONS, JUDGE_OPTIONS, labelOf, OUT_TYPE_OPTIONS, stockInApi, stockOutApi, transferApi, TRANSFER_TYPE_OPTIONS,
  type DocKind, type StockInDetail, type StockOutDetail, type TransferDetail
} from '../api/inventory'

defineOptions({ name: 'InvDocDetail' })

/** 入库单、出库单、调拨单详情（需求 08-03/04/05，T5）：明细、审批记录、操作日志、附件；三个菜单共用本页面 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const kind = route.path.split('/')[2] as DocKind
const id = String(route.params.id)
const api = docApi(kind)

const META = {
  in: { biz: 'INV_STOCK_IN', perm: 'inv:in', list: '/inventory/in', confirm: '确认入库', types: IN_TYPE_OPTIONS },
  out: { biz: 'INV_STOCK_OUT', perm: 'inv:out', list: '/inventory/out', confirm: '确认出库', types: OUT_TYPE_OPTIONS },
  transfer: { biz: 'INV_TRANSFER', perm: 'inv:transfer', list: '/inventory/transfer', confirm: '确认调拨', types: TRANSFER_TYPE_OPTIONS }
}[kind]

const inDoc = ref<StockInDetail>()
const outDoc = ref<StockOutDetail>()
const tfDoc = ref<TransferDetail>()
const doc = computed(() => inDoc.value ?? outDoc.value ?? tfDoc.value)
const activeTab = ref('lines')

async function load() {
  if (kind === 'in') inDoc.value = await stockInApi.get(id)
  else if (kind === 'out') outDoc.value = await stockOutApi.get(id)
  else tfDoc.value = await transferApi.get(id)
  tabs.setTitle(tabKeyOf(route), doc.value!.docNo)
}

const status = computed(() => doc.value?.status)
/** 手工单（其他入库/出库、普通调拨、复检送检） */
const manual = computed(() => (kind === 'transfer' ? tfDoc.value?.transferType !== 'INSPECTION' : !!(inDoc.value ?? outDoc.value)?.manual))
const typeValue = computed(() => inDoc.value?.inType ?? outDoc.value?.outType ?? tfDoc.value?.transferType)
const canConfirm = computed(() => (kind === 'transfer' || !manual.value ? status.value === 'DRAFT' : status.value === 'APPROVED'))

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: `${META.perm}:delete`, visible: () => manual.value && status.value === 'DRAFT',
    confirm: `确定删除 ${doc.value?.docNo} 吗？删除后不可恢复。`,
    handler: async () => {
      await api.remove(id)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push(META.list)
    } },
  { key: 'void', label: '作废', permission: `${META.perm}:void`, visible: () => manual.value && (status.value === 'DRAFT' || status.value === 'APPROVED'),
    confirm: '作废后不能恢复，确定作废吗？', handler: () => run(api.void(id), '已作废') },
  { key: 'reject', label: '退回', permission: `${META.perm}:confirm`, visible: () => kind !== 'transfer' && !manual.value && status.value === 'DRAFT',
    reasonRequired: true, reasonTitle: '退回原因', confirm: '退回后单据作废，并通知来源单据的经办人。',
    reasonOptions: ['来料与单据不符', '数量有误', '物料有误'], handler: (reason) => run(api.reject(id, reason!), '已退回') },
  { key: 'unconfirm', label: '反确认', permission: `${META.perm}:unconfirm`,
    visible: () => kind !== 'transfer' && status.value === 'COMPLETED' && typeValue.value !== 'COUNT_GAIN' && typeValue.value !== 'COUNT_LOSS' && typeValue.value !== 'OPENING',
    reasonRequired: true, reasonTitle: '反确认原因', confirm: '反确认将冲回库存，来源单据的已入/已出数量同步扣回。',
    handler: (reason) => run(api.unconfirm(id, reason!), '已反确认') },
  { key: 'edit', label: '编辑', permission: `${META.perm}:update`, visible: () => status.value === 'DRAFT' && manual.value,
    handler: () => router.push(`${META.list}/${id}/edit`) },
  { key: 'submit', label: '提交', type: 'primary', permission: `${META.perm}:submit`, visible: () => kind !== 'transfer' && manual.value && status.value === 'DRAFT',
    handler: async () => {
      const st = await api.submit(id)
      ElMessage.success(st === 'COMPLETED' ? '提交成功，已确认' : '已提交审批')
      load()
    } },
  { key: 'confirm', label: META.confirm, type: 'primary', permission: `${META.perm}:confirm`, visible: () => canConfirm.value,
    handler: () => router.push(`${META.list}/${id}/edit`) }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = computed(() => (manual.value && kind !== 'transfer'
  ? [{ status: 'DRAFT', label: '草稿' }, { status: 'PENDING_APPROVAL', label: '待审批' }, { status: 'APPROVED', label: '已审核' }, { status: 'COMPLETED', label: '已完成' }]
  : [{ status: 'DRAFT', label: '草稿' }, { status: 'COMPLETED', label: '已完成' }]))

const title = computed(() => (doc.value ? `${labelOf(META.types, typeValue.value)} ${doc.value.docNo}` : ''))
const totalQty = computed(() => {
  const lines = (inDoc.value?.lines ?? outDoc.value?.lines ?? tfDoc.value?.lines ?? []) as { qty?: string }[]
  return lines.reduce((s, l) => s + Number(l.qty ?? 0), 0)
})

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="title || '单据'" :status="status" :status-map="DOC_STATUS" :actions="actions" @back="router.push(META.list)">
        <template #actions-prefix>
          <ApprovalActions v-if="doc && manual && kind !== 'transfer'" :biz-type="kind === 'in' ? 'INV_OTHER_IN' : 'INV_OTHER_OUT'" :biz-id="id" @changed="load" />
          <PrintButton v-if="doc && doc.status !== 'VOIDED'" :biz-type="META.biz" :ids="[id]" :permission="`${META.perm}:print`" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="doc">
      <ErpPanel>
        <el-alert v-if="inDoc?.rejectReason || outDoc?.rejectReason" type="error" :closable="false" show-icon class="alert"
                  :title="`已退回：${inDoc?.rejectReason ?? outDoc?.rejectReason}`" />
        <DocSteps v-else-if="doc.status !== 'VOIDED'" :steps="steps" :current="doc.status" />
        <el-descriptions :column="3" class="head">
          <template v-if="inDoc">
            <el-descriptions-item label="仓库">{{ inDoc.warehouseName }}</el-descriptions-item>
            <el-descriptions-item label="单据日期">{{ inDoc.docDate }}</el-descriptions-item>
            <el-descriptions-item label="来源单号"><span class="mono">{{ inDoc.sourceNo || '-' }}</span></el-descriptions-item>
            <el-descriptions-item v-if="inDoc.manual" label="入库原因"><DictTag type="inv_other_in_reason" :value="inDoc.reason" /></el-descriptions-item>
          </template>
          <template v-else-if="outDoc">
            <el-descriptions-item label="仓库">{{ outDoc.warehouseName }}</el-descriptions-item>
            <el-descriptions-item label="单据日期">{{ outDoc.docDate }}</el-descriptions-item>
            <el-descriptions-item label="来源单号"><span class="mono">{{ outDoc.sourceNo || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="领用人">{{ outDoc.receiverName || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="outDoc.manual" label="出库原因"><DictTag type="inv_other_out_reason" :value="outDoc.reason" /></el-descriptions-item>
          </template>
          <template v-else-if="tfDoc">
            <el-descriptions-item label="调出仓">{{ tfDoc.fromWarehouseName }}</el-descriptions-item>
            <el-descriptions-item label="调入仓">{{ tfDoc.toWarehouseName }}</el-descriptions-item>
            <el-descriptions-item label="单据日期">{{ tfDoc.docDate }}</el-descriptions-item>
            <el-descriptions-item label="来源检验单"><span class="mono">{{ tfDoc.sourceNo || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="调拨原因">{{ tfDoc.reason || '-' }}</el-descriptions-item>
          </template>
          <el-descriptions-item label="总数量"><span class="num">{{ formatQty(totalQty) }}</span></el-descriptions-item>
          <el-descriptions-item label="确认">{{ doc.confirmedByName ?? '-' }} {{ doc.confirmedAt?.slice(0, 16) ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="创建">{{ doc.createdByName ?? '-' }} {{ doc.createdAt?.slice(0, 16) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ doc.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`明细(${(inDoc ?? outDoc ?? tfDoc)!.lines.length})`" name="lines">
            <el-table v-if="inDoc" :data="inDoc.lines" row-key="id">
              <el-table-column prop="lineNo" label="#" width="50" />
              <el-table-column prop="materialCode" label="物料编码" width="130" />
              <el-table-column prop="materialName" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="materialSpec" label="规格" min-width="140" show-overflow-tooltip />
              <el-table-column prop="uom" label="单位" width="60" />
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
              <el-table-column v-if="inDoc.locationEnabled" prop="locationCode" label="库位" width="100" />
              <el-table-column prop="batchNo" label="批次" width="130" />
              <el-table-column prop="productionDate" label="生产日期" width="100" />
              <el-table-column prop="expireDate" label="到期日期" width="100" />
              <el-table-column label="序列号" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ (row.serialNos ?? []).join(',') }}</template></el-table-column>
              <el-table-column v-if="inDoc.lines.some((l) => l.unitCost)" label="单价" width="90" align="right"><template #default="{ row }"><span class="num">{{ row.unitCost ?? '' }}</span></template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
            <el-table v-else-if="outDoc" :data="outDoc.lines" row-key="id">
              <el-table-column prop="lineNo" label="#" width="50" />
              <el-table-column prop="materialCode" label="物料编码" width="130" />
              <el-table-column prop="materialName" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="materialSpec" label="规格" min-width="140" show-overflow-tooltip />
              <el-table-column prop="uom" label="单位" width="60" />
              <el-table-column label="申请数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.requestQty) }}</span></template></el-table-column>
              <el-table-column label="实发数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
              <el-table-column v-if="outDoc.locationEnabled" prop="locationCode" label="库位" width="100" />
              <el-table-column prop="batchNo" label="批次" width="130" />
              <el-table-column label="序列号" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ (row.serialNos ?? []).join(',') }}</template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
            <el-table v-else-if="tfDoc" :data="tfDoc.lines" row-key="id">
              <el-table-column prop="lineNo" label="#" width="50" />
              <el-table-column prop="materialCode" label="物料编码" width="130" />
              <el-table-column prop="materialName" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="materialSpec" label="规格" min-width="140" show-overflow-tooltip />
              <el-table-column prop="baseUom" label="单位" width="60" />
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
              <el-table-column prop="batchNo" label="批次" width="130" />
              <el-table-column prop="fromLocationCode" label="调出库位" width="100" />
              <el-table-column prop="toLocationCode" label="调入库位" width="100" />
              <el-table-column v-if="tfDoc.transferType === 'INSPECTION'" label="判定" width="80"><template #default="{ row }">{{ labelOf(JUDGE_OPTIONS, row.judgeResult) }}</template></el-table-column>
              <el-table-column label="序列号" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ (row.serialNos ?? []).join(',') }}</template></el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="manual && kind !== 'transfer'" label="审批记录" name="approval" lazy>
            <ApprovalTimeline :biz-type="kind === 'in' ? 'INV_OTHER_IN' : 'INV_OTHER_OUT'" :biz-id="id" />
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable :biz-type="META.biz" :biz-id="id" :status-map="DOC_STATUS" /></el-tab-pane>
          <el-tab-pane v-if="kind !== 'transfer'" label="附件" name="files" lazy>
            <AttachmentPanel :biz-type="META.biz" :biz-id="id" :editable="doc.status === 'DRAFT'" />
          </el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.alert { margin-bottom: var(--erp-space-4); }
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
