<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn, SearchField, TableColumn } from '@/components'
import type { MaterialBrief, SupplierBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, today, toDateString } from '@/utils/format'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import { num, submitText } from '../api/common'
import { ORDER_STATUS, ORDER_TYPE_OPTIONS, orderApi, type OrderDetail, type OrderLine, type OrderSave } from '../api/order'
import { priceApi } from '../api/price'
import { requisitionApi, type PendingLine } from '../api/requisition'
import { supplierApi, type Contact } from '../api/supplier'

defineOptions({ name: 'PurOrderEdit' })

/** 采购订单编辑（需求 07-05 3.3，T4）：选供应商带出默认值，明细自动取价，含税/不含税录入 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<PendingLine[]> }>()
const saving = ref(false)
const detail = ref<OrderDetail>()
const canPrice = computed(() => me.hasPermission('pur:price:view'))

interface Line extends OrderLine {
  /** 录入单价（按单头“单价含税”决定含税或不含税） */
  inputPrice?: string
  taxPct?: string
  /** 手工改价 */
  manualPrice?: boolean
}
interface Form {
  orderType: string
  supplierId?: string
  supplierContactId?: string
  currency?: string
  exchangeRate?: string
  paymentTermId?: string
  tradeTerm?: string
  taxIncluded: boolean
  deliveryAddress?: string
  ownerId?: string
  remark?: string
  fileIds: string[]
  lines: Line[]
}

const newLine = (): Line => ({ requiredDate: today() })
const form = ref<Form>({ orderType: 'STANDARD', taxIncluded: true, exchangeRate: '1', fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)
const contacts = ref<Contact[]>([])
const defaultTaxPct = ref<string>()

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  exchangeRate: [{ required: true, message: '请输入汇率', trigger: 'blur' }, {
    validator: (_r, v, cb) => (Number(v) > 0 ? cb() : cb(new Error('汇率必须大于 0'))), trigger: 'blur'
  }],
  paymentTermId: [{ required: true, message: '请选择付款条件', trigger: 'change' }],
  ownerId: [{ required: true, message: '请选择采购员', trigger: 'change' }]
}

const pctOf = (v?: string) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) * 100).toFixed(4))))
const rateOf = (v?: string) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) / 100).toFixed(6))))

/** 行价税合计（前端预览，保存后以后端为准） */
function lineTotal(l: Line) {
  const q = num(l.qty)
  const p = num(l.inputPrice)
  const t = num(l.taxPct) / 100
  return form.value.taxIncluded ? q * p : q * p * (1 + t)
}
const sumTotal = computed(() => form.value.lines.reduce((s, l) => s + (l.materialId ? lineTotal(l) : 0), 0))

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 150 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 150 },
  { prop: 'supplierPartNo', label: '供应商料号', type: 'readonly', width: 110 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'qty', label: '数量', type: 'qty', width: 110, required: true, uomProp: 'uom', summary: true, validate: (v) => (Number(v) > 0 ? undefined : '数量必须大于 0') },
  ...(canPrice.value ? [
    { prop: 'inputPrice', label: form.value.taxIncluded ? '含税单价' : '不含税单价', type: 'slot', width: 130 } as LineColumn<Line>,
    { prop: 'taxPct', label: '税率(%)', type: 'number', width: 90, precision: 2, min: 0, required: true } as LineColumn<Line>,
    { prop: 'listPrice', label: '价格表价', type: 'readonly', width: 100 } as LineColumn<Line>,
    { prop: 'lineTotal', label: '价税合计', type: 'readonly', width: 120, formatter: (r: Line) => (r.materialId ? formatAmount(lineTotal(r)) : '') } as LineColumn<Line>
  ] : []),
  { prop: 'requiredDate', label: '要求到货日期', type: 'date', width: 140, required: true },
  { prop: 'requisitionNo', label: '来源申请', type: 'readonly', width: 140 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

/** 自动取价（R04）：供应商 + 物料 + 币别 + 数量对应的有效价格；手工改过的不覆盖 */
async function fetchPrice(l: Line) {
  if (!canPrice.value || !form.value.supplierId || !form.value.currency || !l.materialId) return
  const p = await priceApi.effective({ supplierId: form.value.supplierId, materialId: l.materialId, currency: form.value.currency, qty: l.qty, uom: l.uom })
    .catch(() => null)
  l.listPrice = p ? (form.value.taxIncluded ? p.priceInclTax : p.price) : undefined
  if (p) {
    l.taxPct = pctOf(p.taxRate)
    if (!l.manualPrice) l.inputPrice = form.value.taxIncluded ? p.priceInclTax : p.price
  }
}

async function onMaterial(row: Line, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.uom = m?.baseUom
  row.taxPct = row.taxPct ?? defaultTaxPct.value
  row.manualPrice = false
  await fetchPrice(row)
}

async function loadContacts(supplierId?: string) {
  contacts.value = []
  if (!supplierId || !me.hasPermission('pur:supplier:query')) return
  const s = await supplierApi.get(supplierId).catch(() => undefined)
  contacts.value = s?.contacts ?? []
  if (!form.value.supplierContactId) form.value.supplierContactId = contacts.value.find((c) => c.isPrimary)?.id
}

async function onSupplier(s?: SupplierBrief | SupplierBrief[]) {
  const x = Array.isArray(s) ? s[0] : s
  if (!x) return
  const hasLines = form.value.lines.some((l) => l.materialId)
  const reprice = !hasLines || (await ElMessageBox.confirm('修改供应商将重新取价，确定吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false))
  form.value.currency = x.currency ?? form.value.currency
  form.value.paymentTermId = x.paymentTermId ?? form.value.paymentTermId
  form.value.ownerId = form.value.ownerId ?? x.buyerId
  form.value.supplierContactId = undefined
  defaultTaxPct.value = pctOf(x.taxRate)
  await loadContacts(x.id)
  if (!reprice) return
  for (const l of form.value.lines) {
    if (!l.materialId) continue
    l.manualPrice = false
    await fetchPrice(l)
  }
}

async function onCurrencyChange() {
  for (const l of form.value.lines) if (l.materialId) await fetchPrice(l)
}

// ---------- 从申请选单 ----------
const pendingColumns: TableColumn<PendingLine>[] = [
  { prop: 'docNo', label: '申请单号', width: 150 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'pendingQty', label: '未转数量', width: 100, type: 'qty' },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'requiredDate', label: '需求日期', width: 110, type: 'date' },
  { prop: 'suggestedSupplierName', label: '建议供应商', width: 130 }
]
const pendingFields: SearchField[] = [{ prop: 'docNo', label: '申请单号', upper: true }]
async function pickRequisitions() {
  const rows = await pickerRef.value?.open({})
  if (!rows?.length) return
  const exist = new Set(form.value.lines.map((l) => l.requisitionLineId).filter(Boolean))
  const added: Line[] = []
  for (const r of rows.filter((x) => !exist.has(x.id))) {
    const l: Line = { ...newLine(), materialId: r.materialId, materialCode: r.materialCode, materialName: r.materialName, materialSpec: r.materialSpec,
      uom: r.baseUom, qty: r.pendingQty, requiredDate: r.requiredDate < today() ? today() : r.requiredDate, requisitionLineId: r.id, requisitionNo: r.docNo,
      taxPct: defaultTaxPct.value }
    await fetchPrice(l)
    added.push(l)
  }
  form.value.lines = [...form.value.lines.filter((l) => l.materialId), ...added, newLine()]
}
const pendingApi = (q: Record<string, any>) => requisitionApi.pending({ ...q, supplierId: undefined } as never)

// ---------- 加载 ----------
onMounted(async () => {
  if (id.value) {
    const d = await orderApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的订单可以修改')
      router.replace(`/purchase/order/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      orderType: d.orderType, supplierId: d.supplierId, supplierContactId: d.supplierContactId, currency: d.currency, exchangeRate: d.exchangeRate,
      paymentTermId: d.paymentTermId, tradeTerm: d.tradeTerm, taxIncluded: d.taxIncluded, deliveryAddress: d.deliveryAddress, ownerId: d.ownerId,
      remark: d.remark, fileIds: [],
      lines: [...d.lines.map((l) => ({ ...l, inputPrice: d.taxIncluded ? l.priceInclTax : l.price, taxPct: pctOf(l.taxRate), manualPrice: true })), newLine()]
    }
    await loadContacts(d.supplierId)
    tabs.setTitle(tabKeyOf(route), `编辑采购订单 ${d.docNo}`)
  } else {
    form.value.ownerId = me.user?.id
    const d = new Date()
    d.setDate(d.getDate() + 7)
    form.value.lines = [{ requiredDate: toDateString(d) }]
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
  router.push(id.value ? `/purchase/order/${id.value}` : '/purchase/order')
}

function payload(): OrderSave {
  const f = form.value
  return {
    orderType: f.orderType, supplierId: f.supplierId!, supplierContactId: f.supplierContactId, currency: f.currency, exchangeRate: f.exchangeRate,
    paymentTermId: f.paymentTermId, tradeTerm: f.tradeTerm, taxIncluded: f.taxIncluded, deliveryAddress: f.deliveryAddress?.trim() || undefined,
    ownerId: f.ownerId, remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    lines: linesRef.value!.validRows().map((l) => ({
      id: l.id, materialId: l.materialId!, uom: l.uom, qty: l.qty!, requiredDate: l.requiredDate!, requisitionLineId: l.requisitionLineId,
      taxRate: rateOf(l.taxPct), price: f.taxIncluded ? undefined : l.inputPrice, priceInclTax: f.taxIncluded ? l.inputPrice : undefined,
      remark: l.remark?.trim() || undefined
    }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  if (canPrice.value) {
    const bad = linesRef.value.validRows().findIndex((l) => !(num(l.inputPrice) > 0))
    if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 行：请输入单价`)
  }
  saving.value = true
  try {
    const data = payload()
    const r = id.value ? await orderApi.update(id.value, data) : await orderApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await orderApi.submit(r.id)).status))
      } catch {
        if (!id.value) router.replace(`/purchase/order/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/order/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑采购订单 ${detail.value.docNo}` : '新建采购订单'))
const supplierStatuses = computed(() => (form.value.orderType === 'SAMPLE' ? ['QUALIFIED', 'POTENTIAL'] : ['QUALIFIED']))
const asLine = (r: unknown) => r as Line
const overrun = (l: Line) => !!l.listPrice && num(l.inputPrice) > num(l.listPrice)
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="ORDER_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('pur:order:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="订单类型">
              <el-radio-group v-model="form.orderType"><el-radio v-for="o in ORDER_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="供应商" prop="supplierId"><SupplierSelect v-model="form.supplierId" :statuses="supplierStatuses" @select="onSupplier" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="联系人">
              <el-select v-model="form.supplierContactId" clearable class="w-full" placeholder="主联系人">
                <el-option v-for="c in contacts" :key="c.id" :value="c.id!" :label="`${c.name}${c.mobile ? ` ${c.mobile}` : ''}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="币别" prop="currency">
              <CurrencySelect v-model="form.currency" :rate-date="today()" @rate="(r) => (form.exchangeRate = r ?? form.exchangeRate)" @update:model-value="onCurrencyChange" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="汇率" prop="exchangeRate"><NumberInput v-model="form.exchangeRate" :precision="6" trim-zeros /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="付款条件" prop="paymentTermId"><PaymentTermSelect v-model="form.paymentTermId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="单价含税"><el-switch v-model="form.taxIncluded" :disabled="form.lines.some((l) => l.materialId)" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="采购员" prop="ownerId"><UserSelect v-model="form.ownerId" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="送货地址"><el-input v-model="form.deliveryAddress" maxlength="256" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" placeholder="打印在订单上" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="明细">
      <template #extra>
        <span v-if="canPrice" class="total">价税合计 <strong class="num">{{ form.currency }} {{ formatAmount(sumTotal) }}</strong></span>
      </template>
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial">
        <template #toolbar>
          <el-button icon="Link" @click="pickRequisitions">从申请选单</el-button>
        </template>
        <template #cell-inputPrice="{ row, disabled }">
          <div class="price-cell">
            <PriceInput v-model="asLine(row).inputPrice" :disabled="disabled" @change="asLine(row).manualPrice = true" />
            <ErpBadge v-if="overrun(asLine(row))" type="warning" :dot="false">超价</ErpBadge>
          </div>
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_ORDER" multiple />
      <AttachmentPanel v-else biz-type="PUR_ORDER" :biz-id="id" editable />
    </ErpPanel>

    <SourceDocPicker ref="pickerRef" title="从采购申请选单" :api="pendingApi" :columns="pendingColumns" :search-fields="pendingFields"
                     :exclude-keys="form.lines.map((l) => l.requisitionLineId).filter((x): x is string => !!x)" />
  </ErpPage>
</template>

<style scoped>
.price-cell { display: flex; align-items: center; gap: var(--erp-space-1); }
.total { color: var(--erp-color-text-secondary); }
</style>
