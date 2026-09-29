<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatQty } from '@/utils/format'
import { num, PURCHASE_INVOICE_TYPES, purchaseInvoiceApi, round2, type UninvoicedApLine } from '../api/finance'

defineOptions({ name: 'FinPurchaseInvoiceEdit' })

/**
 * 登记进项发票与三单匹配（需求 12-04 3.3，T4）：选择应付行，填写发票数量与发票不含税单价；
 * 单价差异超过容差（默认 1%）标红并需填写原因；明细合计与发票价税合计的尾差在容差内自动调整最后一行
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const TOLERANCE = 1
type Row = UninvoicedApLine & { checked: boolean; qty2?: string; price2?: string; reason?: string }
const form = ref<{ supplierId?: string; invoiceType: string; invoiceNo: string; invoiceCode?: string; invoiceDate: string; totalAmount?: string; taxAmount?: string; remark?: string; fileIds: string[] }>({
  supplierId: typeof route.query.supplierId === 'string' ? route.query.supplierId : undefined,
  invoiceType: 'VAT_SPECIAL', invoiceNo: '', invoiceDate: new Date().toISOString().slice(0, 10), fileIds: []
})
const rows = ref<Row[]>([])
const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
  invoiceNo: [{ required: true, message: '请填写发票号码', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  totalAmount: [{ required: true, message: '请填写价税合计', trigger: 'blur' }],
  taxAmount: [{ required: true, message: '请填写税额', trigger: 'blur' }]
}
const preset = typeof route.query.payableIds === 'string' ? route.query.payableIds.split(',') : []

async function loadLines() {
  rows.value = []
  if (!form.value.supplierId) return
  const list = await purchaseInvoiceApi.uninvoiced(form.value.supplierId)
  rows.value = list.map((l) => ({
    ...l, checked: preset.includes(l.payableId), qty2: l.uninvoicedQty,
    price2: l.apPrice ?? String(round2(num(l.uninvoicedAmount) / (1 + num(l.taxRate))))
  }))
}
onMounted(loadLines)
watch(() => form.value.supplierId, loadLines)

const hasQty = (r: Row) => !!r.qty && num(r.qty) !== 0
const lineAmount = (r: Row) => round2(hasQty(r) ? num(r.qty2) * num(r.price2) : num(r.price2))
const lineTotal = (r: Row) => round2(lineAmount(r) + round2(lineAmount(r) * num(r.taxRate)))
const diffPct = (r: Row) => {
  const ap = hasQty(r) ? num(r.apPrice) : num(r.uninvoicedAmount) / (1 + num(r.taxRate))
  return ap === 0 ? 0 : round2(((num(r.price2) - ap) / ap) * 100)
}
const over = (r: Row) => Math.abs(diffPct(r)) > TOLERANCE
const picked = computed(() => rows.value.filter((r) => r.checked))
const linesTotal = computed(() => round2(picked.value.reduce((s, r) => s + lineTotal(r), 0)))
const linesTax = computed(() => round2(picked.value.reduce((s, r) => s + lineTotal(r) - lineAmount(r), 0)))
const diff = computed(() => round2(num(form.value.totalAmount) - linesTotal.value))
const currency = computed(() => picked.value[0]?.currency ?? '')
function fillHeader() {
  form.value.totalAmount = String(linesTotal.value)
  form.value.taxAmount = String(linesTax.value)
}

async function save() {
  await formRef.value?.validate()
  if (!picked.value.length) return ElMessage.warning('请勾选要匹配的应付行')
  const missing = picked.value.findIndex((r) => over(r) && !r.reason?.trim())
  if (missing >= 0) return ElMessage.warning(`第 ${missing + 1} 行单价差异超过容差，请填写差异原因`)
  saving.value = true
  try {
    const id = await purchaseInvoiceApi.register({
      supplierId: form.value.supplierId!, invoiceType: form.value.invoiceType, invoiceNo: form.value.invoiceNo.trim(), invoiceCode: form.value.invoiceCode,
      invoiceDate: form.value.invoiceDate, totalAmount: form.value.totalAmount!, taxAmount: form.value.taxAmount!, remark: form.value.remark,
      fileIds: form.value.fileIds,
      lines: picked.value.map((r) => ({ payableLineId: r.payableLineId, qty: hasQty(r) ? r.qty2 : undefined, invoicePrice: r.price2, diffReason: r.reason }))
    })
    ElMessage.success('已登记')
    tabs.remove([tabKeyOf(route)])
    router.push(`/finance/payable/invoice/${id}`)
  } finally {
    saving.value = false
  }
}
function back() {
  tabs.remove([tabKeyOf(route)])
  router.push('/finance/payable/invoice')
}
</script>

<template>
  <ErpPage title="登记进项发票" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="发票信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="供应商" prop="supplierId"><SupplierSelect v-model="form.supplierId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="发票类型" prop="invoiceType">
              <el-select v-model="form.invoiceType" class="w-full"><el-option v-for="o in PURCHASE_INVOICE_TYPES" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="发票号码" prop="invoiceNo"><el-input v-model="form.invoiceNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="发票代码"><el-input v-model="form.invoiceCode" maxlength="32" placeholder="数电票可空" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="开票日期" prop="invoiceDate"><el-date-picker v-model="form.invoiceDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="价税合计" prop="totalAmount">
              <AmountInput v-model="form.totalAmount" :currency="currency" allow-negative />
              <el-button link type="primary" class="fill" @click="fillHeader">按明细填入</el-button>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="税额" prop="taxAmount"><AmountInput v-model="form.taxAmount" :currency="currency" allow-negative /></el-form-item></el-col>
          <el-col :xl="16" :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="500" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="发票附件"><AttachmentUpload v-model="form.fileIds" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
      <ErpPanel :title="`匹配明细（已选 ${picked.length} 行，明细合计 ${formatAmount(linesTotal)}，与发票差异 ${formatAmount(diff)}）`">
        <el-alert v-if="picked.length && Math.abs(diff) > 0" :type="Math.abs(diff) > 1 ? 'error' : 'info'" :closable="false" class="gap"
                  :title="Math.abs(diff) > 1 ? '明细合计与发票价税合计差异超过尾差容差，不能保存' : '差异在尾差容差内，保存时自动调整最后一行'" />
        <el-table :data="rows" max-height="520">
          <el-table-column width="44"><template #default="{ row }"><el-checkbox v-model="row.checked" /></template></el-table-column>
          <el-table-column prop="payableNo" label="应付单" width="140" />
          <el-table-column prop="sourceNo" label="来源单号" width="130" />
          <el-table-column label="物料 / 说明" min-width="160">
            <template #default="{ row }">{{ row.materialCode ? `${row.materialCode} ${row.materialName ?? ''}` : row.description }}</template>
          </el-table-column>
          <el-table-column label="未开票数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.uninvoicedQty) }}</template></el-table-column>
          <el-table-column label="应付单价" width="100" align="right"><template #default="{ row }">{{ row.apPrice ?? '-' }}</template></el-table-column>
          <el-table-column label="发票数量" width="120">
            <template #default="{ row }"><QtyInput v-if="hasQty(row as Row)" v-model="row.qty2" allow-negative :disabled="!row.checked" /></template>
          </el-table-column>
          <el-table-column label="发票单价（不含税）" width="150">
            <template #default="{ row }"><PriceInput v-model="row.price2" :disabled="!row.checked" /></template>
          </el-table-column>
          <el-table-column label="差异%" width="80" align="right">
            <template #default="{ row }"><span :class="{ red: over(row as Row) }">{{ diffPct(row as Row) }}%</span></template>
          </el-table-column>
          <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(lineTotal(row as Row)) }}</template></el-table-column>
          <el-table-column label="差异原因" min-width="160">
            <template #default="{ row }"><el-input v-if="over(row as Row)" v-model="row.reason" :disabled="!row.checked" placeholder="超容差必填" /></template>
          </el-table-column>
        </el-table>
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>

<style scoped>
.red { color: var(--erp-color-error); }
.gap { margin-bottom: var(--erp-space-2); }
.fill { margin-left: var(--erp-space-2); }
</style>
