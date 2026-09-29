<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { CustomerBrief, MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import { orderApi, pctOf, priceListApi, QUOTE_STATUS, quotationApi, rateOf, submitText, type CustomerDefaults, type QuotationDetail, type QuotationLine } from '../api/sales'

defineOptions({ name: 'SalQuotationEdit' })

/** 报价单编辑（需求 04-02 4.2，T4）：选物料默认带出价格表价，可按数量阶梯录入多行 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const saving = ref(false)
const detail = ref<QuotationDetail>()
const contacts = ref<CustomerDefaults['contacts']>([])
const defaultTaxPct = ref<string>()
const canCost = computed(() => me.hasPermission('sales:quotation:cost'))

interface Line extends QuotationLine { taxPct?: string }
interface Form {
  customerId?: string; contactId?: string; currency?: string; exchangeRate?: string; tradeTerm?: string; paymentTermId?: string; taxIncluded: boolean
  validUntil?: string; terms?: string; remark?: string; fileIds: string[]; lines: Line[]
}
const newLine = (): Line => ({ taxPct: defaultTaxPct.value })
const form = ref<Form>({ taxIncluded: true, exchangeRate: '1', fileIds: [], lines: [{}] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  exchangeRate: [{ validator: (_r, v, cb) => (Number(v) > 0 ? cb() : cb(new Error('汇率必须大于 0'))), trigger: 'blur' }]
}

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 150 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 140 },
  { prop: 'customerPartNo', label: '客户料号', type: 'text', width: 120 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'minQty', label: '数量阶梯', type: 'qty', width: 110, uomProp: 'uom' },
  { prop: 'price', label: form.value.taxIncluded ? '含税单价' : '不含税单价', type: 'price', width: 120, required: true,
    validate: (v) => (Number(v) > 0 ? undefined : '单价必须大于 0') },
  { prop: 'taxPct', label: '税率(%)', type: 'number', width: 90, precision: 2, min: 0 },
  ...(canCost.value ? [
    { prop: 'costPrice', label: '单位成本', type: 'price', width: 110 } as LineColumn<Line>,
    { prop: 'marginRate', label: '毛利率', type: 'readonly', width: 90, formatter: (r: Line) => marginText(r) } as LineColumn<Line>
  ] : []),
  { prop: 'moq', label: 'MOQ', type: 'qty', width: 100, uomProp: 'uom' },
  { prop: 'leadTimeDays', label: '交期(天)', type: 'number', width: 90, precision: 0, min: 0 },
  { prop: 'toolingFee', label: '模具费', type: 'amount', width: 110 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

/** 毛利率预览：不含税单价折本位币后与成本比较（保存后以后端为准） */
function marginText(l: Line) {
  const p = Number(l.price)
  const c = Number(l.costPrice)
  if (!(p > 0) || !(c > 0)) return ''
  const net = (form.value.taxIncluded ? p / (1 + Number(l.taxPct ?? 0) / 100) : p) * Number(form.value.exchangeRate || 1)
  return `${(((net - c) / net) * 100).toFixed(2)}%`
}

async function fetchPrice(l: Line) {
  if (!form.value.currency || !l.materialId || !l.uom) return
  const p = await priceListApi.lookup({ customerId: form.value.customerId, materialId: l.materialId, qty: l.minQty, uom: l.uom, currency: form.value.currency })
    .catch(() => null)
  if (p && !l.price) l.price = p.price
}

async function onMaterial(row: Line, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.uom = m?.baseUom
  row.taxPct = row.taxPct ?? defaultTaxPct.value
  row.price = undefined
  await fetchPrice(row)
}

async function loadDefaults(customerId?: string, fill = true) {
  contacts.value = []
  if (!customerId) return
  const d = await orderApi.customerDefaults(customerId).catch(() => undefined)
  if (!d) return
  contacts.value = d.contacts
  defaultTaxPct.value = pctOf(d.salesTaxRate)
  if (!fill) return
  Object.assign(form.value, { contactId: d.contactId, currency: d.currency, exchangeRate: d.exchangeRate ?? form.value.exchangeRate, taxIncluded: d.taxIncluded,
    tradeTerm: d.tradeTerm, paymentTermId: d.paymentTermId })
  form.value.lines.forEach((l) => (l.taxPct = l.taxPct ?? defaultTaxPct.value))
}
function onCustomer(c?: CustomerBrief | CustomerBrief[]) {
  const x = Array.isArray(c) ? c[0] : c
  return loadDefaults(x?.id)
}

onMounted(async () => {
  if (id.value) {
    const d = await quotationApi.get(id.value)
    if (d.quoteStatus !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的报价单可以修改')
      router.replace(`/sales/quotation/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      customerId: d.customerId, contactId: d.contactId, currency: d.currency, exchangeRate: d.exchangeRate, tradeTerm: d.tradeTerm,
      paymentTermId: d.paymentTermId, taxIncluded: d.taxIncluded, validUntil: d.validUntil, terms: d.terms, remark: d.remark, fileIds: [],
      lines: [...d.lines.map((l) => ({ ...l, taxPct: pctOf(l.taxRate) })), {}]
    }
    await loadDefaults(d.customerId, false)
    tabs.setTitle(tabKeyOf(route), `编辑报价单 ${d.docNo}`)
  } else if (typeof route.query.customerId === 'string') {
    form.value.customerId = route.query.customerId
    await loadDefaults(route.query.customerId)
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/sales/quotation/${id.value}` : '/sales/quotation')
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  const f = form.value
  const data = {
    customerId: f.customerId, contactId: f.contactId, currency: f.currency, exchangeRate: f.exchangeRate, tradeTerm: f.tradeTerm, paymentTermId: f.paymentTermId,
    taxIncluded: f.taxIncluded, validUntil: f.validUntil || undefined, terms: f.terms?.trim() || undefined, remark: f.remark?.trim() || undefined,
    fileIds: f.fileIds, version: detail.value?.version,
    lines: linesRef.value.validRows().map((l) => ({
      materialId: l.materialId, customerPartNo: l.customerPartNo?.trim() || undefined, description: l.description, uom: l.uom, minQty: l.minQty || undefined,
      price: l.price, taxRate: rateOf(l.taxPct), moq: l.moq || undefined, leadTimeDays: l.leadTimeDays ?? undefined, toolingFee: l.toolingFee || undefined,
      rfqLineId: l.rfqLineId, costPrice: canCost.value ? l.costPrice || undefined : undefined, remark: l.remark?.trim() || undefined
    }))
  }
  saving.value = true
  try {
    const r = id.value ? await quotationApi.update(id.value, data) : await quotationApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await quotationApi.submit(r.id)).status))
      } catch {
        if (!id.value) router.replace(`/sales/quotation/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/sales/quotation/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑报价单 ${detail.value.docNo}` : '新建报价单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.quoteStatus" :map="QUOTE_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('sales:quotation:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" :statuses="['ACTIVE', 'PROSPECT']" @select="onCustomer" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="联系人">
              <el-select v-model="form.contactId" clearable class="w-full" placeholder="主联系人">
                <el-option v-for="c in contacts" :key="c.id" :value="c.id" :label="`${c.name}${c.email ? ` ${c.email}` : ''}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="有效期至"><el-date-picker v-model="form.validUntil" value-format="YYYY-MM-DD" :disabled-date="(d: Date) => d < new Date(today())" class="w-full" placeholder="默认按系统参数天数" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="币别" prop="currency">
              <CurrencySelect v-model="form.currency" :rate-date="today()" @rate="(r) => (form.exchangeRate = r ?? form.exchangeRate)" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="汇率" prop="exchangeRate"><NumberInput v-model="form.exchangeRate" :precision="6" trim-zeros /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="单价含税"><el-switch v-model="form.taxIncluded" :disabled="form.lines.some((l) => l.materialId)" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="付款条件"><PaymentTermSelect v-model="form.paymentTermId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="报价条款"><el-input v-model="form.terms" type="textarea" :rows="2" maxlength="2000" placeholder="打印在报价单上" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="报价明细" description="同一物料可按数量阶梯录入多行；毛利率低于系统参数下限时需审批">
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial" />
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="SAL_QUOTATION" multiple />
      <AttachmentPanel v-else biz-type="SAL_QUOTATION" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>
