<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { DocAction, LineColumn, TableColumn } from '@/components'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatMoney, formatQty, today } from '@/utils/format'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import { submitWithCredit } from '../components/credit'
import { CHANGE_TYPE_OPTIONS, changeApi, DOC_STATUS, labelOf, submitText, type ChangeDetail, type ChangeHeader, type ChangeLine } from '../api/sales'

defineOptions({ name: 'SalChangeDetail' })

/**
 * 销售订单变更（需求 04-04，T4/T5）：草稿时可编辑，逐行修改数量、单价、交期或取消，也可新增行；
 * 提交后按规则审批，审批通过订单升版。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<ChangeDetail>()
const saving = ref(false)
const activeTab = ref('lines')

interface EditRow {
  orderLineId: string; lineNo?: number; materialCode?: string; materialName?: string; uom?: string; oldQty?: string; oldPrice?: string
  oldRequiredDate?: string; noticedQty?: string; shippedQty?: string; newQty?: string; newPrice?: string; newRequiredDate?: string; cancel: boolean; remark?: string
}
interface Form { changeReason?: string; reasonRemark?: string; header: ChangeHeader; rows: EditRow[]; adds: ChangeLine[] }
const form = ref<Form>({ header: {}, rows: [], adds: [] })
const guard = useLeaveGuard(() => form.value)
const editing = computed(() => d.value?.status === 'DRAFT' && me.hasPermission('sales:order:change'))

async function load() {
  const x = await changeApi.get(id.value)
  d.value = x
  tabs.setTitle(tabKeyOf(route), x.docNo)
  const byLine = new Map(x.lines.filter((l) => l.orderLineId).map((l) => [l.orderLineId!, l]))
  form.value = {
    changeReason: x.changeReason || undefined, reasonRemark: x.reasonRemark || undefined, header: { ...x.header },
    rows: x.orderLines.filter((l) => l.lineStatus !== 'CLOSED').map((l) => {
      const c = byLine.get(l.id!)
      const oldPrice = x.taxIncluded ? l.priceInclTax : l.price
      return {
        orderLineId: l.id!, lineNo: l.lineNo, materialCode: l.materialCode, materialName: l.materialName, uom: l.uom, oldQty: l.qty, oldPrice,
        oldRequiredDate: l.requiredDate, noticedQty: l.noticedQty, shippedQty: l.shippedQty, cancel: c?.changeType === 'CANCEL',
        newQty: c?.changeType === 'MODIFY' ? c.newQty : l.qty, newPrice: c?.changeType === 'MODIFY' ? c.newPrice : oldPrice,
        newRequiredDate: c?.changeType === 'MODIFY' ? c.newRequiredDate : l.requiredDate, remark: c?.remark
      }
    }),
    adds: [...x.lines.filter((l) => l.changeType === 'ADD'), newAdd()]
  }
  guard.markClean()
}

const newAdd = (): ChangeLine => ({ changeType: 'ADD', newRequiredDate: today() })
const addColumns = computed<LineColumn<ChangeLine>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 160 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'newQty', label: '数量', type: 'qty', width: 110, required: true, uomProp: 'uom', validate: (v) => (Number(v) > 0 ? undefined : '数量必须大于 0') },
  { prop: 'newPrice', label: d.value?.taxIncluded ? '含税单价' : '不含税单价', type: 'price', width: 120 },
  { prop: 'newRequiredDate', label: '要求交期', type: 'date', width: 140, required: true },
  { prop: 'newCustomerPartNo', label: '客户料号', type: 'text', width: 120 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])
function onMaterial(row: ChangeLine, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.uom = m?.baseUom
}
const addsRef = ref<{ validRows: () => ChangeLine[] }>()

const changed = (x: unknown) => ((r: EditRow) => r.cancel || Number(r.newQty) !== Number(r.oldQty) || Number(r.newPrice) !== Number(r.oldPrice) || r.newRequiredDate !== r.oldRequiredDate)(x as EditRow)

function payload() {
  const f = form.value
  const lines = [
    ...f.rows.filter(changed).map((r) => r.cancel
      ? { changeType: 'CANCEL', orderLineId: r.orderLineId, remark: r.remark?.trim() || undefined }
      : { changeType: 'MODIFY', orderLineId: r.orderLineId, newQty: r.newQty, newPrice: r.newPrice, newRequiredDate: r.newRequiredDate, remark: r.remark?.trim() || undefined }),
    ...(addsRef.value?.validRows() ?? []).map((a) => ({ changeType: 'ADD', materialId: a.materialId, uom: a.uom, newQty: a.newQty, newPrice: a.newPrice || undefined,
      newRequiredDate: a.newRequiredDate, newCustomerPartNo: a.newCustomerPartNo?.trim() || undefined, remark: a.remark?.trim() || undefined }))
  ]
  return { changeReason: f.changeReason, reasonRemark: f.reasonRemark?.trim(), header: f.header, lines, version: d.value?.version }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!form.value.changeReason) return ElMessage.warning('请选择变更原因')
  if (!form.value.reasonRemark?.trim()) return ElMessage.warning('请填写原因说明')
  saving.value = true
  try {
    await changeApi.update(id.value, payload())
    guard.markClean()
    if (submit) {
      const r = await submitWithCredit((confirm) => changeApi.submit(id.value, confirm))
      if (!r) return load()
      ElMessage.success(submitText(r.status))
      if (r.warnings?.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    } else {
      ElMessage.success('保存成功')
    }
    await load()
  } finally {
    saving.value = false
  }
}

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:order:change', visible: () => d.value?.status === 'DRAFT', confirm: '确定删除该变更单吗？',
    handler: async () => {
      await changeApi.remove(id.value)
      guard.markClean()
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push(`/sales/order/${d.value!.orderId}`)
    } },
  { key: 'void', label: '作废', permission: 'sales:order:change', visible: () => d.value?.status === 'DRAFT', reasonRequired: true, reasonTitle: '作废原因',
    handler: async (reason) => {
      await changeApi.void(id.value, reason!)
      ElMessage.success('已作废')
      load()
    } },
  { key: 'save', label: '保存', visible: () => editing.value, handler: () => save(false) },
  { key: 'submit', label: '提交', type: 'primary', visible: () => editing.value, handler: () => save(true) }
])

const lineColumns: TableColumn<ChangeLine>[] = [
  { prop: 'lineNo', label: '序', width: 50 },
  { prop: 'changeType', label: '类型', width: 70, formatter: (r) => labelOf(CHANGE_TYPE_OPTIONS, r.changeType) },
  { prop: 'orderLineNo', label: '订单行', width: 70 },
  { prop: 'materialCode', label: '物料', minWidth: 180, formatter: (r) => `${r.materialCode ?? ''} ${r.materialName ?? ''}` },
  { prop: 'qty', label: '数量', width: 170, slot: true },
  { prop: 'price', label: '单价', width: 170, slot: true },
  { prop: 'date', label: '要求交期', width: 200, slot: true },
  { prop: 'remark', label: '备注', minWidth: 120 }
]
const asLine = (r: unknown) => r as ChangeLine
const diff = (a?: string, b?: string) => a !== undefined && b !== undefined && Number(a) !== Number(b)

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '订单变更'" :status="d?.status" :status-map="DOC_STATUS" :actions="actions" @back="router.push('/sales/order-change')">
        <template #extra><ErpBadge v-if="d" type="primary" :dot="false">V{{ d.orderVersionFrom }} → V{{ d.orderVersionFrom + 1 }}</ErpBadge></template>
        <template #actions-prefix><ApprovalActions v-if="d" biz-type="SAL_ORDER_CHANGE" :biz-id="id" @changed="load" /></template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="订单">
            <el-link type="primary" underline="never" @click="router.push(`/sales/order/${d.orderId}`)">{{ d.orderNo }}</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="变更前金额">{{ formatMoney(d.amountBefore, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="变更后金额">{{ formatMoney(d.amountAfter, d.currency) }}</el-descriptions-item>
          <el-descriptions-item label="金额变化(本位币)">{{ formatMoney(d.amountChangeBase) }}</el-descriptions-item>
          <el-descriptions-item label="经办人">{{ d.ownerName || d.createdByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(d.createdAt, true) }}</el-descriptions-item>
          <el-descriptions-item v-if="!editing" label="变更原因"><DictTag type="sal_change_reason" :value="d.changeReason" /> {{ d.reasonRemark }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <template v-if="editing">
        <ErpPanel title="变更原因">
          <el-form label-width="100px">
            <el-row :gutter="24">
              <el-col :span="8"><el-form-item label="变更原因" required><DictSelect v-model="form.changeReason" type="sal_change_reason" /></el-form-item></el-col>
              <el-col :span="16"><el-form-item label="原因说明" required><el-input v-model="form.reasonRemark" maxlength="512" /></el-form-item></el-col>
            </el-row>
          </el-form>
        </ErpPanel>
        <ErpPanel title="单头变更">
          <el-form label-width="100px">
            <el-row :gutter="24">
              <el-col :xl="8" :span="12"><el-form-item label="客户 PO 号"><el-input v-model="form.header.customerPoNo" maxlength="64" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="付款条件"><PaymentTermSelect v-model="form.header.paymentTermId" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.header.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="装运港"><el-input v-model="form.header.portOfLoading" maxlength="64" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="目的港"><el-input v-model="form.header.portOfDestination" maxlength="64" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="合同条款"><el-input v-model="form.header.terms" type="textarea" :rows="2" maxlength="2000" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.header.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
            </el-row>
          </el-form>
        </ErpPanel>
        <ErpPanel title="明细变更" description="直接修改新数量、新单价、新交期；已通知出货的行不能取消，数量不能低于已通知数量">
          <el-table :data="form.rows" max-height="520">
            <el-table-column prop="lineNo" label="行" width="50" />
            <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
            <el-table-column label="已通知/已出货" width="120" align="right"><template #default="{ row }">{{ formatQty(row.noticedQty) }} / {{ formatQty(row.shippedQty) }}</template></el-table-column>
            <el-table-column label="原数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.oldQty) }}</template></el-table-column>
            <el-table-column label="新数量" width="140"><template #default="{ row }"><QtyInput v-model="row.newQty" :uom="row.uom" :disabled="row.cancel" /></template></el-table-column>
            <el-table-column label="原单价" width="100" align="right"><template #default="{ row }">{{ row.oldPrice }}</template></el-table-column>
            <el-table-column label="新单价" width="130"><template #default="{ row }"><PriceInput v-model="row.newPrice" :disabled="row.cancel" /></template></el-table-column>
            <el-table-column label="原交期" width="110"><template #default="{ row }">{{ row.oldRequiredDate }}</template></el-table-column>
            <el-table-column label="新交期" width="160">
              <template #default="{ row }"><el-date-picker v-model="row.newRequiredDate" value-format="YYYY-MM-DD" :clearable="false" :disabled="row.cancel" class="w-full" /></template>
            </el-table-column>
            <el-table-column label="取消" width="60" align="center">
              <template #default="{ row }"><el-checkbox v-model="row.cancel" :disabled="Number(row.noticedQty) > 0" /></template>
            </el-table-column>
            <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
            <el-table-column label="" width="70">
              <template #default="{ row }"><ErpBadge v-if="changed(row)" :type="row.cancel ? 'danger' : 'warning'" :dot="false">{{ row.cancel ? '取消' : '修改' }}</ErpBadge></template>
            </el-table-column>
          </el-table>
        </ErpPanel>
        <ErpPanel title="新增行">
          <LinesEditor ref="addsRef" v-model="form.adds" :columns="addColumns" :new-row="newAdd" :on-material="onMaterial" />
        </ErpPanel>
      </template>

      <ErpPanel v-else flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`变更明细(${d.lines.length})`" name="lines">
            <div v-if="d.headerChanges.length" class="header-changes">
              <div v-for="h in d.headerChanges" :key="h.field">{{ h.label }}：<span class="text-muted">{{ h.oldValue || '空' }}</span> → <strong>{{ h.newValue || '空' }}</strong></div>
            </div>
            <ErpTable :columns="lineColumns" :data="d.lines" no-toolbar>
              <template #col-qty="{ row }">
                <span v-if="asLine(row).changeType === 'ADD'">{{ formatQty(asLine(row).newQty) }}</span>
                <span v-else :class="{ 'text-warning': diff(asLine(row).oldQty, asLine(row).newQty) }">{{ formatQty(asLine(row).oldQty) }} → {{ formatQty(asLine(row).newQty) }}</span>
              </template>
              <template #col-price="{ row }">
                <span v-if="asLine(row).changeType === 'ADD'">{{ asLine(row).newPrice }}</span>
                <span v-else-if="asLine(row).changeType === 'MODIFY'" :class="{ 'text-warning': diff(asLine(row).oldPrice, asLine(row).newPrice) }">{{ asLine(row).oldPrice }} → {{ asLine(row).newPrice }}</span>
                <span v-else>-</span>
              </template>
              <template #col-date="{ row }">
                <span v-if="asLine(row).changeType === 'ADD'">{{ asLine(row).newRequiredDate }}</span>
                <span v-else-if="asLine(row).changeType === 'MODIFY'" :class="{ 'text-warning': asLine(row).oldRequiredDate !== asLine(row).newRequiredDate }">
                  {{ asLine(row).oldRequiredDate }} → {{ asLine(row).newRequiredDate }}
                </span>
                <span v-else>-</span>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approval" lazy><ApprovalTimeline biz-type="SAL_ORDER_CHANGE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SAL_ORDER_CHANGE" :biz-id="id" :status-map="DOC_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.header-changes { margin-bottom: var(--erp-space-3); line-height: 1.8; }
</style>
