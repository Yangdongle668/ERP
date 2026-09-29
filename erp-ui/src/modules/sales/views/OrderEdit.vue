<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { CustomerBrief, MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, today, toDateString } from '@/utils/format'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import { submitWithCredit } from '../components/credit'
import { DOC_STATUS, num, orderApi, pctOf, priceListApi, rateOf, submitText, type CustomerDefaults, type OrderDetail, type OrderLine } from '../api/sales'

defineOptions({ name: 'SalOrderEdit' })

/** 销售订单编辑（需求 04-03 3.2，T4）：选客户带出默认值，明细按价格表自动取价 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const saving = ref(false)
const detail = ref<OrderDetail>()
const defaults = ref<CustomerDefaults>()
const defaultTaxPct = ref<string>()

interface Line extends OrderLine {
  /** 录入单价（按单头“单价含税”口径） */
  inputPrice?: string
  taxPct?: string
  manualPrice?: boolean
  priceLabel?: string
}
interface Form {
  docNo?: string; orderType: string; customerId?: string; contactId?: string; customerPoNo?: string; customerPoDate?: string; docDate?: string
  ownerId?: string; currency?: string; exchangeRate?: string; taxIncluded: boolean; paymentTermId?: string; tradeTerm?: string; portOfLoading?: string
  portOfDestination?: string; shipToAddressId?: string; billToAddressId?: string; terms?: string; remark?: string; fileIds: string[]; lines: Line[]
}
const inDays = (n: number) => {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return toDateString(d)
}
const newLine = (): Line => ({ requiredDate: inDays(30), taxPct: defaultTaxPct.value })
const form = ref<Form>({ orderType: 'NORMAL', taxIncluded: true, exchangeRate: '1', docDate: today(), fileIds: [], lines: [{ requiredDate: inDays(30) }] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  exchangeRate: [{ validator: (_r, v, cb) => (Number(v) > 0 ? cb() : cb(new Error('汇率必须大于 0'))), trigger: 'blur' }],
  paymentTermId: [{ required: true, message: '请选择付款条件', trigger: 'change' }],
  ownerId: [{ required: true, message: '请选择业务员', trigger: 'change' }]
}

function lineTotal(l: Line) {
  const v = num(l.qty) * num(l.inputPrice)
  return form.value.taxIncluded ? v : v * (1 + num(l.taxPct) / 100)
}
const sumTotal = computed(() => form.value.lines.reduce((s, l) => s + (l.materialId ? lineTotal(l) : 0), 0))

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 150 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 140 },
  { prop: 'customerPartNo', label: '客户料号', type: 'text', width: 120 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'qty', label: '数量', type: 'qty', width: 110, required: true, uomProp: 'uom', summary: true, validate: (v) => (Number(v) > 0 ? undefined : '数量必须大于 0') },
  { prop: 'inputPrice', label: form.value.taxIncluded ? '含税单价' : '不含税单价', type: 'slot', width: 170 },
  { prop: 'taxPct', label: '税率(%)', type: 'number', width: 90, precision: 2, min: 0 },
  { prop: 'lineTotal', label: '价税合计', type: 'readonly', width: 120, formatter: (r: Line) => (r.materialId ? formatAmount(lineTotal(r)) : '') },
  { prop: 'requiredDate', label: '要求交期', type: 'date', width: 140, required: true },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

/** 自动取价（SAL-SO-R02）：客户专属 → 客户等级 → 通用价格表；手工改过的不覆盖 */
async function fetchPrice(l: Line) {
  if (!form.value.currency || !l.materialId || !l.uom) return
  const p = await priceListApi.lookup({ customerId: form.value.customerId, materialId: l.materialId, qty: l.qty, uom: l.uom, currency: form.value.currency,
    date: form.value.docDate }).catch(() => null)
  l.priceLabel = p?.sourceLabel
  if (p && !l.manualPrice) {
    l.inputPrice = p.price
    l.priceSource = p.sourceNo ? `${p.sourceType}:${p.sourceNo}` : p.sourceType
  }
}
async function repriceAll() {
  for (const l of form.value.lines) {
    if (!l.materialId) continue
    l.manualPrice = false
    await fetchPrice(l)
  }
}

async function onMaterial(row: Line, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.uom = m?.baseUom
  row.taxPct = row.taxPct ?? defaultTaxPct.value
  row.manualPrice = false
  row.inputPrice = undefined
  await fetchPrice(row)
}

async function loadDefaults(customerId?: string, fill = true) {
  defaults.value = undefined
  if (!customerId) return
  const d = await orderApi.customerDefaults(customerId)
  defaults.value = d
  defaultTaxPct.value = pctOf(d.salesTaxRate)
  if (!fill) return
  Object.assign(form.value, {
    contactId: d.contactId, currency: d.currency, exchangeRate: d.exchangeRate ?? form.value.exchangeRate, taxIncluded: d.taxIncluded,
    paymentTermId: d.paymentTermId, tradeTerm: d.tradeTerm, ownerId: d.ownerId ?? form.value.ownerId, shipToAddressId: d.shipToAddressId,
    billToAddressId: d.billToAddressId
  })
  form.value.lines.forEach((l) => (l.taxPct = l.taxPct ?? defaultTaxPct.value))
}
async function onCustomer(c?: CustomerBrief | CustomerBrief[]) {
  const x = Array.isArray(c) ? c[0] : c
  if (!x) return
  const hasLines = form.value.lines.some((l) => l.materialId)
  const reprice = hasLines && (await ElMessageBox.confirm('修改客户将重新取价，确定吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false))
  await loadDefaults(x.id)
  if (reprice) await repriceAll()
}

const shipTos = computed(() => defaults.value?.addresses.filter((a) => a.type !== 'BILL_TO') ?? [])
const billTos = computed(() => defaults.value?.addresses.filter((a) => a.type === 'BILL_TO') ?? [])

onMounted(async () => {
  if (id.value) {
    const d = await orderApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的订单可以修改，已审核订单请走变更')
      router.replace(`/sales/order/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      docNo: d.docNo, orderType: d.orderType, customerId: d.customerId, contactId: d.contactId, customerPoNo: d.customerPoNo, customerPoDate: d.customerPoDate,
      docDate: d.docDate, ownerId: d.ownerId, currency: d.currency, exchangeRate: d.exchangeRate, taxIncluded: d.taxIncluded, paymentTermId: d.paymentTermId,
      tradeTerm: d.tradeTerm, portOfLoading: d.portOfLoading, portOfDestination: d.portOfDestination, shipToAddressId: d.shipToAddressId,
      billToAddressId: d.billToAddressId, terms: d.terms, remark: d.remark, fileIds: [],
      lines: [...d.lines.map((l) => ({ ...l, inputPrice: d.taxIncluded ? l.priceInclTax : l.price, taxPct: pctOf(l.taxRate), manualPrice: true })), newLine()]
    }
    await loadDefaults(d.customerId, false)
    tabs.setTitle(tabKeyOf(route), `编辑销售订单 ${d.docNo}`)
  } else {
    form.value.ownerId = me.user?.id
    if (typeof route.query.customerId === 'string') {
      form.value.customerId = route.query.customerId
      await loadDefaults(route.query.customerId)
    }
  }
  guard.markClean()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function onKey(e: KeyboardEvent) {
  if (!(e.ctrlKey || e.metaKey)) return
  if (e.key === 's') {
    e.preventDefault()
    save(false)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    save(true)
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/sales/order/${id.value}` : '/sales/order')
}

function payload() {
  const f = form.value
  return {
    docNo: id.value ? undefined : f.docNo?.trim() || undefined, orderType: f.orderType, customerId: f.customerId, contactId: f.contactId,
    customerPoNo: f.customerPoNo?.trim() || undefined, customerPoDate: f.customerPoDate || undefined, docDate: f.docDate, ownerId: f.ownerId, currency: f.currency,
    exchangeRate: f.exchangeRate, taxIncluded: f.taxIncluded, paymentTermId: f.paymentTermId, tradeTerm: f.tradeTerm, portOfLoading: f.portOfLoading?.trim() || undefined,
    portOfDestination: f.portOfDestination?.trim() || undefined, shipToAddressId: f.shipToAddressId, billToAddressId: f.billToAddressId,
    terms: f.terms?.trim() || undefined, remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    lines: linesRef.value!.validRows().map((l) => ({
      materialId: l.materialId, customerPartNo: l.customerPartNo?.trim() || undefined, description: l.description, uom: l.uom, qty: l.qty,
      price: l.inputPrice || undefined, taxRate: rateOf(l.taxPct), requiredDate: l.requiredDate, quotationLineId: l.quotationLineId,
      priceSource: l.manualPrice ? 'MANUAL' : l.priceSource, remark: l.remark?.trim() || undefined
    }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  saving.value = true
  try {
    const r = id.value ? await orderApi.update(id.value, payload()) : await orderApi.create(payload())
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      const res = await submitWithCredit((confirm) => orderApi.submit(r.id, confirm))
      if (!res) {
        if (!id.value) router.replace(`/sales/order/${r.id}/edit`)
        return
      }
      ElMessage.success(submitText(res.status))
      if (res.warnings?.length) ElNotification({ type: 'warning', title: '提示', message: res.warnings.join('；'), duration: 8000 })
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/sales/order/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑销售订单 ${detail.value.docNo}` : '新建销售订单'))
const asLine = (r: unknown) => r as Line
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="DOC_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('sales:order:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" :statuses="['ACTIVE']" @select="onCustomer" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="订单类型"><DictSelect v-model="form.orderType" type="sal_order_type" :clearable="false" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="订单号"><el-input v-model="form.docNo" :disabled="!!id" maxlength="64" placeholder="留空自动编号" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客户 PO 号"><el-input v-model="form.customerPoNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="PO 日期"><el-date-picker v-model="form.customerPoDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="下单日期"><el-date-picker v-model="form.docDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="联系人">
              <el-select v-model="form.contactId" clearable class="w-full" placeholder="主联系人">
                <el-option v-for="c in defaults?.contacts ?? []" :key="c.id" :value="c.id" :label="`${c.name}${c.phone ? ` ${c.phone}` : ''}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="业务员" prop="ownerId"><UserSelect v-model="form.ownerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="币别" prop="currency">
              <CurrencySelect v-model="form.currency" :rate-date="form.docDate" @rate="(r) => (form.exchangeRate = r ?? form.exchangeRate)" @update:model-value="repriceAll" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="汇率" prop="exchangeRate"><NumberInput v-model="form.exchangeRate" :precision="6" trim-zeros /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="单价含税"><el-switch v-model="form.taxIncluded" :disabled="form.lines.some((l) => l.materialId)" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="付款条件" prop="paymentTermId"><PaymentTermSelect v-model="form.paymentTermId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col v-if="defaults?.foreign" :xl="8" :span="12"><el-form-item label="装运港"><el-input v-model="form.portOfLoading" maxlength="64" /></el-form-item></el-col>
          <el-col v-if="defaults?.foreign" :xl="8" :span="12"><el-form-item label="目的港"><el-input v-model="form.portOfDestination" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="收货地址">
              <el-select v-model="form.shipToAddressId" clearable class="w-full">
                <el-option v-for="a in shipTos" :key="a.id" :value="a.id" :label="a.text" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开票地址">
              <el-select v-model="form.billToAddressId" clearable class="w-full">
                <el-option v-for="a in billTos" :key="a.id" :value="a.id" :label="a.text" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="合同条款"><el-input v-model="form.terms" type="textarea" :rows="2" maxlength="2000" placeholder="打印在合同 / PI 上" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="明细">
      <template #extra>
        <span class="total">价税合计 <strong class="num">{{ form.currency }} {{ formatAmount(sumTotal) }}</strong></span>
      </template>
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial">
        <template #toolbar>
          <el-button icon="RefreshCw" @click="repriceAll">重新取价</el-button>
        </template>
        <template #cell-inputPrice="{ row, disabled }">
          <div class="price-cell">
            <PriceInput v-model="asLine(row).inputPrice" :disabled="disabled" @change="asLine(row).manualPrice = true" />
            <el-tooltip v-if="asLine(row).priceLabel" :content="asLine(row).priceLabel">
              <ErpBadge :type="asLine(row).manualPrice ? 'warning' : 'info'" :dot="false">{{ asLine(row).manualPrice ? '手工' : '价格表' }}</ErpBadge>
            </el-tooltip>
          </div>
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel title="附件（客户 PO 等）">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="SAL_ORDER" multiple />
      <AttachmentPanel v-else biz-type="SAL_ORDER" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.price-cell { display: flex; align-items: center; gap: var(--erp-space-1); }
.total { color: var(--erp-color-text-secondary); }
</style>
