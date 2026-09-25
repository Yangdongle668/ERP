<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty, today } from '@/utils/format'
import { DOC_STATUS, labelOf, submitText } from '../api/common'
import { CHANGE_TYPE_OPTIONS, changeApi, orderApi, type ChangeDetail, type ChangeSave, type OrderLine } from '../api/order'

defineOptions({ name: 'PurOrderChangeEdit' })

/**
 * 订单变更单（需求 07-05 R07）：草稿可编辑（修改 / 取消原订单行、新增行），其他状态只读查看。
 * 路由：/purchase/order/change/new?orderId=、/purchase/order/change/:id
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const detail = ref<ChangeDetail>()
const orderId = ref<string>()
const orderNo = ref<string>()
const saving = ref(false)
const linesRef = ref<{ validate: () => boolean; validRows: () => AddLine[] }>()
const canPrice = computed(() => me.hasPermission('pur:price:view'))

interface ExistLine extends OrderLine {
  changeType: '' | 'MODIFY' | 'CANCEL'
  newQty?: string
  newPrice?: string
  newRequiredDate?: string
  changeRemark?: string
}
interface AddLine {
  materialId?: string
  materialCode?: string
  materialName?: string
  uom?: string
  newQty?: string
  newPrice?: string
  taxPct?: string
  newRequiredDate?: string
  remark?: string
}
const reason = ref('')
const exist = ref<ExistLine[]>([])
const added = ref<AddLine[]>([{ newRequiredDate: today() }])
const editable = computed(() => !detail.value || detail.value.status === 'DRAFT')
const guard = useLeaveGuard(() => ({ reason: reason.value, exist: exist.value, added: added.value }))

const addColumns = computed<LineColumn<AddLine>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 180 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'newQty', label: '数量', type: 'qty', width: 110, required: true, uomProp: 'uom' },
  ...(canPrice.value ? [
    { prop: 'newPrice', label: '不含税单价', type: 'price', width: 120 } as LineColumn<AddLine>,
    { prop: 'taxPct', label: '税率(%)', type: 'number', width: 90, precision: 2, min: 0 } as LineColumn<AddLine>
  ] : []),
  { prop: 'newRequiredDate', label: '要求到货日期', type: 'date', width: 140, required: true },
  { prop: 'remark', label: '备注', type: 'text', width: 160 }
])
function onMaterial(row: AddLine, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.uom = m?.baseUom
}

async function loadTemplate(oid: string) {
  const lines = await changeApi.template(oid)
  exist.value = lines.map((l) => ({ ...l, changeType: '', newQty: l.qty, newPrice: l.price, newRequiredDate: l.requiredDate }))
}

onMounted(async () => {
  if (id.value) {
    const d = await changeApi.get(id.value)
    detail.value = d
    orderId.value = d.orderId
    orderNo.value = d.orderNo
    reason.value = d.changeReason
    tabs.setTitle(tabKeyOf(route), d.docNo)
    if (d.status === 'DRAFT') {
      await loadTemplate(d.orderId)
      for (const c of d.lines) {
        if (c.changeType === 'ADD') continue
        const e = exist.value.find((x) => x.id === c.orderLineId)
        if (e) Object.assign(e, { changeType: c.changeType, newQty: c.newQty, newPrice: c.newPrice, newRequiredDate: c.newRequiredDate, changeRemark: c.remark })
      }
      const adds = d.lines.filter((c) => c.changeType === 'ADD').map((c) => ({ materialId: c.materialId, materialCode: c.materialCode, materialName: c.materialName,
        uom: c.uom, newQty: c.newQty, newPrice: c.newPrice, taxPct: c.taxRate ? String(Number(c.taxRate) * 100) : undefined, newRequiredDate: c.newRequiredDate, remark: c.remark }))
      added.value = [...adds, { newRequiredDate: today() }]
    }
  } else if (typeof route.query.orderId === 'string') {
    orderId.value = route.query.orderId
    const o = await orderApi.get(orderId.value)
    orderNo.value = o.docNo
    await loadTemplate(orderId.value)
    tabs.setTitle(tabKeyOf(route), `变更 ${o.docNo}`)
  }
  guard.markClean()
})

function payload(): ChangeSave {
  const lines: ChangeSave['lines'] = []
  for (const e of exist.value) {
    if (!e.changeType) continue
    lines.push({ orderLineId: e.id, changeType: e.changeType, newQty: e.newQty, newPrice: e.newPrice, newRequiredDate: e.newRequiredDate, remark: e.changeRemark?.trim() || undefined })
  }
  for (const a of linesRef.value?.validRows() ?? []) {
    lines.push({ changeType: 'ADD', materialId: a.materialId, uom: a.uom, newQty: a.newQty, newPrice: a.newPrice,
      taxRate: a.taxPct ? String(Number((Number(a.taxPct) / 100).toFixed(6))) : undefined, newRequiredDate: a.newRequiredDate, remark: a.remark?.trim() || undefined })
  }
  return { orderId: orderId.value!, changeReason: reason.value.trim(), lines, version: detail.value?.version }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!reason.value.trim()) return ElMessage.warning('请填写变更原因')
  if (linesRef.value && !linesRef.value.validate()) return
  const data = payload()
  if (!data.lines.length) return ElMessage.warning('请至少修改、取消或新增一行')
  saving.value = true
  try {
    let cid = id.value
    if (cid) await changeApi.update(cid, data)
    else cid = await changeApi.create(data)
    guard.markClean()
    if (submit) {
      try {
        const r = await changeApi.submit(cid)
        ElMessage.success(r.status === 'APPROVED' ? '变更已生效' : submitText(r.status))
      } catch {
        if (!id.value) router.replace(`/purchase/order/change/${cid}`)
        return
      }
      tabs.remove([tabKeyOf(route)])
      router.push(`/purchase/order/${orderId.value}`)
      return
    }
    ElMessage.success('保存成功')
    if (!id.value) router.replace(`/purchase/order/change/${cid}`)
  } finally {
    saving.value = false
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(orderId.value ? `/purchase/order/${orderId.value}` : '/purchase/order')
}

const title = computed(() => (detail.value ? `订单变更 ${detail.value.docNo}` : `变更采购订单 ${orderNo.value ?? ''}`))
const asExist = (r: unknown) => r as ExistLine

async function remove() {
  if (!id.value) return
  await changeApi.remove(id.value)
  ElMessage.success('删除成功')
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(`/purchase/order/${orderId.value}`)
}
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta>
      <StatusTag v-if="detail" :value="detail.status" :map="DOC_STATUS" />
      <span v-if="orderNo" class="text-muted">订单 {{ orderNo }}</span>
    </template>
    <template #actions>
      <el-button @click="back">{{ editable ? '取消' : '返回' }}</el-button>
      <template v-if="editable && me.hasPermission('pur:order:change')">
        <el-popconfirm v-if="id" title="确定删除该变更单吗？删除后不可恢复。" @confirm="remove">
          <template #reference><el-button>删除</el-button></template>
        </el-popconfirm>
        <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="save(true)">提交</el-button>
      </template>
    </template>

    <ErpPanel title="变更原因">
      <el-input v-model="reason" :disabled="!editable" maxlength="512" placeholder="如：供应商交期调整、需求数量变化" />
      <p v-if="detail" class="form-tip">订单版本 V{{ detail.orderVersion }} → V{{ detail.newVersion }}；金额变化（本位币）
        <span class="num">{{ detail.amountChangeBase ? formatAmount(detail.amountChangeBase) : '-' }}</span></p>
    </ErpPanel>

    <!-- 草稿：编辑 -->
    <template v-if="editable">
      <ErpPanel title="原订单行">
        <template #extra><span class="text-muted">新数量不能小于已到货数量；已到货完的行不能改单价；已对账的行不能修改</span></template>
        <el-table :data="exist" border>
          <el-table-column prop="lineNo" label="行" width="50" />
          <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="已到货" width="90" align="right"><template #default="{ row }">{{ formatQty(row.receivedQty) }}</template></el-table-column>
          <el-table-column label="变更" width="110">
            <template #default="{ row }">
              <el-select v-model="asExist(row).changeType" placeholder="不变">
                <el-option value="" label="不变" />
                <el-option v-for="o in CHANGE_TYPE_OPTIONS.filter((x) => x.value !== 'ADD')" :key="o.value" :value="o.value" :label="o.label" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量（原 → 新）" width="200">
            <template #default="{ row }">
              <div class="pair"><span class="num">{{ formatQty(row.qty) }}</span><QtyInput v-if="asExist(row).changeType === 'MODIFY'" v-model="asExist(row).newQty" :uom="row.uom" /></div>
            </template>
          </el-table-column>
          <el-table-column v-if="canPrice" label="不含税单价（原 → 新）" width="210">
            <template #default="{ row }">
              <div class="pair"><span class="num">{{ row.price }}</span><PriceInput v-if="asExist(row).changeType === 'MODIFY'" v-model="asExist(row).newPrice" /></div>
            </template>
          </el-table-column>
          <el-table-column label="要求日期（原 → 新）" width="250">
            <template #default="{ row }">
              <div class="pair"><span>{{ row.requiredDate }}</span>
                <el-date-picker v-if="asExist(row).changeType === 'MODIFY'" v-model="asExist(row).newRequiredDate" value-format="YYYY-MM-DD" class="date" /></div>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }"><el-input v-if="asExist(row).changeType" v-model="asExist(row).changeRemark" maxlength="256" /></template>
          </el-table-column>
        </el-table>
      </ErpPanel>
      <ErpPanel title="新增行">
        <LinesEditor ref="linesRef" v-model="added" :columns="addColumns" :new-row="() => ({ newRequiredDate: today() })" :on-material="onMaterial" />
      </ErpPanel>
    </template>

    <!-- 非草稿：只读 -->
    <ErpPanel v-else-if="detail" title="变更明细">
      <el-table :data="detail.lines">
        <el-table-column prop="lineNo" label="行" width="50" />
        <el-table-column label="类型" width="80"><template #default="{ row }">{{ labelOf(CHANGE_TYPE_OPTIONS, row.changeType) }}</template></el-table-column>
        <el-table-column label="订单行" width="80"><template #default="{ row }">{{ row.orderLineNo ?? '新增' }}</template></el-table-column>
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="数量" width="170"><template #default="{ row }">{{ row.oldQty ? formatQty(row.oldQty) : '-' }} → {{ formatQty(row.newQty) }}</template></el-table-column>
        <el-table-column v-if="detail.priceVisible" label="单价" width="170"><template #default="{ row }">{{ row.oldPrice ?? '-' }} → {{ row.newPrice ?? '-' }}</template></el-table-column>
        <el-table-column label="要求日期" width="220"><template #default="{ row }">{{ row.oldRequiredDate ?? '-' }} → {{ row.newRequiredDate ?? '-' }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" />
      </el-table>
    </ErpPanel>

    <ErpPanel v-if="id" title="审批与日志" flush>
      <el-tabs class="detail-tabs">
        <el-tab-pane label="审批记录"><ApprovalActions biz-type="PUR_ORDER_CHANGE" :biz-id="id" /><ApprovalTimeline biz-type="PUR_ORDER_CHANGE" :biz-id="id" /></el-tab-pane>
        <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="PUR_ORDER_CHANGE" :biz-id="id" :status-map="DOC_STATUS" /></el-tab-pane>
      </el-tabs>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.pair { display: flex; align-items: center; gap: var(--erp-space-2); }
.date { width: 150px; }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
</style>
