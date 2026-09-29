<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { TableColumn } from '@/components'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import {
  forwarderApi, NOTICE_STATUS, noticeApi, num, type CustomerDefaults, type ForwarderOption, type NoticeDetail, type NoticeLine, type OrderLineOption
} from '../api/shipping'

defineOptions({ name: 'ShpNoticeEdit' })

/** 出货通知编辑（需求 11-01 3.2，T4）：单头 + 从订单行选单；通知数量默认 = min(可通知, 可用库存) */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<OrderLineOption[]> }>()
const saving = ref(false)
const detail = ref<NoticeDetail>()
const defaults = ref<CustomerDefaults>()
const forwarders = ref<ForwarderOption[]>([])

interface Form {
  customerId?: string; shipDate?: string; transportMode?: string; tradeTerm?: string; portOfLoading?: string; portOfDestination?: string
  shipToAddressId?: string; notifyParty?: string; forwarderId?: string; warehouseId?: string; ownerId?: string; remark?: string; fileIds: string[]
  lines: NoticeLine[]
}
const form = ref<Form>({ shipDate: new Date().toISOString().slice(0, 10), fileIds: [], lines: [] })
const guard = useLeaveGuard(() => form.value)
const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  shipDate: [{ required: true, message: '请选择出货日期', trigger: 'change' }],
  transportMode: [{ required: true, message: '请选择运输方式', trigger: 'change' }],
  shipToAddressId: [{ required: true, message: '请选择收货地址', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择出货仓', trigger: 'change' }]
}

const orderColumns: TableColumn<OrderLineOption>[] = [
  { prop: 'orderNo', label: '订单号', width: 150 },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'customerPoNo', label: '客户 PO', width: 120 },
  { prop: 'materialCode', label: '物料编码', width: 120 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'customerPartNo', label: '客户料号', width: 110 },
  { prop: 'qty', label: '订单数量', width: 90, type: 'qty' },
  { prop: 'noticedQty', label: '已通知', width: 90, type: 'qty' },
  { prop: 'noticeableQty', label: '可通知', width: 90, type: 'qty' },
  { prop: 'promisedDate', label: '交期', width: 100, type: 'date' },
  { prop: 'availableQty', label: '可用库存', width: 90, type: 'qty' }
]
async function orderLinesApi(q: Record<string, any>) {
  const rows = await noticeApi.orderLines({ customerId: q.customerId, orderNo: q.orderNo, warehouseId: form.value.warehouseId })
  const from = (q.pageNo - 1) * q.pageSize
  return { list: rows.slice(from, from + q.pageSize), total: rows.length }
}
async function pickLines() {
  if (!form.value.customerId) return ElMessage.warning('请先选择客户')
  const rows = await pickerRef.value?.open({ customerId: form.value.customerId })
  if (!rows?.length) return
  const currency = form.value.lines[0]?.orderNo ? currencyOf.value : rows[0].currency
  if (rows.some((r) => r.currency !== currency)) return ElMessage.warning('不同币别的订单请分开出货')
  currencyOf.value = currency
  form.value.lines.push(...rows.map((r) => {
    const avail = num(r.availableQty)
    const qty = avail > 0 ? Math.min(num(r.noticeableQty), avail) : num(r.noticeableQty)
    return {
      orderId: r.orderId, orderNo: r.orderNo, orderLineId: r.orderLineId, orderLineNo: r.lineNo, customerPoNo: r.customerPoNo, materialId: r.materialId,
      materialCode: r.materialCode, materialName: r.materialName, customerPartNo: r.customerPartNo, description: r.description, uom: r.uom,
      noticeableQty: r.noticeableQty, availableQty: r.availableQty, priceInclTax: r.priceInclTax, qty: String(qty)
    }
  }))
}
const currencyOf = ref<string>()

let lastCustomer: string | undefined
async function onCustomer() {
  if (form.value.customerId === lastCustomer) return
  if (form.value.lines.length) {
    const ok = await ElMessageBox.confirm('更换客户将清空明细，确定吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) {
      form.value.customerId = lastCustomer
      return
    }
    form.value.lines = []
  }
  lastCustomer = form.value.customerId
  await loadDefaults(true)
}
async function loadDefaults(apply: boolean) {
  if (!form.value.customerId) return
  defaults.value = await noticeApi.customerDefaults(form.value.customerId)
  if (!apply) return
  const d = defaults.value
  form.value.shipToAddressId = d.shipToAddressId
  form.value.tradeTerm = d.tradeTerm
  form.value.portOfLoading = d.portOfLoading
  form.value.portOfDestination = d.portOfDestination
  if (!form.value.warehouseId) form.value.warehouseId = d.warehouseId
}

const lineAmount = (l: NoticeLine) => num(l.qty) * num(l.priceInclTax)
const sumQty = computed(() => form.value.lines.reduce((s, l) => s + num(l.qty), 0))
const sumAmount = computed(() => form.value.lines.reduce((s, l) => s + lineAmount(l), 0))

onMounted(async () => {
  forwarders.value = await forwarderApi.options().catch(() => [])
  if (id.value) {
    const d = await noticeApi.get(id.value)
    if (d.noticeStatus !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的出货通知可以修改')
      router.replace(`/shipping/notice/${id.value}`)
      return
    }
    detail.value = d
    currencyOf.value = d.currency
    form.value = {
      customerId: d.customerId, shipDate: d.shipDate, transportMode: d.transportMode, tradeTerm: d.tradeTerm, portOfLoading: d.portOfLoading,
      portOfDestination: d.portOfDestination, shipToAddressId: d.shipToAddressId, notifyParty: d.notifyParty, forwarderId: d.forwarderId,
      warehouseId: d.warehouseId, ownerId: d.ownerId, remark: d.remark, fileIds: [], lines: d.lines.map((l) => ({ ...l }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑出货通知 ${d.docNo}`)
    await loadDefaults(false)
  }
  lastCustomer = form.value.customerId
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/shipping/notice/${id.value}` : '/shipping/notice')
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!form.value.lines.length) return ElMessage.warning('请选择订单行')
  for (const [i, l] of form.value.lines.entries()) {
    if (!(num(l.qty) > 0)) return ElMessage.warning(`第 ${i + 1} 行通知数量必须大于 0`)
  }
  const f = form.value
  const data = {
    customerId: f.customerId, shipDate: f.shipDate, transportMode: f.transportMode, tradeTerm: f.tradeTerm || undefined, portOfLoading: f.portOfLoading?.trim() || undefined,
    portOfDestination: f.portOfDestination?.trim() || undefined, shipToAddressId: f.shipToAddressId, notifyParty: f.notifyParty?.trim() || undefined,
    forwarderId: f.forwarderId || undefined, warehouseId: f.warehouseId, ownerId: f.ownerId || undefined, remark: f.remark?.trim() || undefined, fileIds: f.fileIds,
    lines: f.lines.map((l) => ({ id: l.id, orderLineId: l.orderLineId, qty: l.qty, description: l.description?.trim() || undefined, remark: l.remark?.trim() || undefined,
      shippingPlanLineId: l.shippingPlanLineId }))
  }
  saving.value = true
  try {
    const r = id.value ? await noticeApi.update(id.value, data) : await noticeApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      try {
        const s = await noticeApi.submit(r.id)
        if (s.warnings.length) ElNotification({ type: 'warning', title: '提交提示', message: s.warnings.join('；'), duration: 8000 })
        ElMessage.success('已提交')
      } catch {
        if (!id.value) router.replace(`/shipping/notice/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/shipping/notice/${r.id}`)
  } finally {
    saving.value = false
  }
}
const title = computed(() => (detail.value ? `编辑出货通知 ${detail.value.docNo}` : '新建出货通知'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.noticeStatus" :map="NOTICE_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('shp:notice:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" @select="onCustomer" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="出货日期" prop="shipDate"><el-date-picker v-model="form.shipDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="运输方式" prop="transportMode"><DictSelect v-model="form.transportMode" type="shp_transport_mode" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="起运港"><el-input v-model="form.portOfLoading" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="目的港"><el-input v-model="form.portOfDestination" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="16" :span="24">
            <el-form-item label="收货地址" prop="shipToAddressId">
              <el-select v-model="form.shipToAddressId" class="w-full" placeholder="选择客户后选择收货地址">
                <el-option v-for="a in defaults?.addresses ?? []" :key="a.id" :value="a.id" :label="a.text" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="出货仓" prop="warehouseId"><WarehouseSelect v-model="form.warehouseId" only-available :only-mine="false" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="货代">
              <el-select v-model="form.forwarderId" clearable filterable class="w-full">
                <el-option v-for="f in forwarders" :key="f.id" :value="f.id" :label="`${f.code} ${f.name}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="船务"><UserSelect v-model="form.ownerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="通知方"><el-input v-model="form.notifyParty" maxlength="512" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" placeholder="唛头、包装要求等" /></el-form-item>
          </el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="出货明细" description="通知数量为订单单位；可用库存不足时仅提示（可能在出货日前入库）">
      <template #extra>
        <span class="total">合计 <strong class="num">{{ formatQty(sumQty) }}</strong>，金额 <strong class="num">{{ formatAmount(sumAmount) }}</strong></span>
        <el-button type="primary" icon="Plus" @click="pickLines">选择订单行</el-button>
      </template>
      <el-table :data="form.lines">
        <el-table-column label="订单" width="170"><template #default="{ row }">{{ row.orderNo }} 行 {{ row.orderLineNo }}</template></el-table-column>
        <el-table-column prop="customerPoNo" label="客户 PO" width="110" />
        <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="customerPartNo" label="客户料号" width="110" />
        <el-table-column label="对外描述" min-width="160"><template #default="{ row }"><el-input v-model="row.description" maxlength="512" /></template></el-table-column>
        <el-table-column label="可通知" width="90" align="right"><template #default="{ row }">{{ formatQty(row.noticeableQty) }}</template></el-table-column>
        <el-table-column label="通知数量" width="130"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="row.uom" /></template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column label="可用库存" width="90" align="right">
          <template #default="{ row }"><span :class="{ short: num(row.availableQty) < num(row.qty) }">{{ formatQty(row.availableQty) }}</span></template>
        </el-table-column>
        <el-table-column label="单价" width="100" align="right"><template #default="{ row }">{{ row.priceInclTax ?? '***' }}</template></el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ row.priceInclTax === undefined || row.priceInclTax === null ? '***' : formatAmount(lineAmount(row as NoticeLine)) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删除</el-button></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="选择客户后，从已审核的订单行中选择" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="SHP_NOTICE" multiple />
      <AttachmentPanel v-else biz-type="SHP_NOTICE" :biz-id="id" editable />
    </ErpPanel>

    <SourceDocPicker ref="pickerRef" title="选择订单行（已审核、可通知数量 > 0）" :api="orderLinesApi" :columns="orderColumns" row-key="orderLineId"
                     :search-fields="[{ prop: 'orderNo', label: '订单号', upper: true }]" :exclude-keys="form.lines.map((l) => l.orderLineId)" />
  </ErpPage>
</template>

<style scoped>
.total { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
.short { color: var(--erp-color-warning); }
</style>
