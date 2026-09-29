<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import {
  apApi, num, REQUEST_TYPES, requestApi, round2, type PayableCandidate, type PurchaseOrderOption, type RequestResult, type SupplierBankOption
} from '../api/finance'

defineOptions({ name: 'FinRequestEdit' })

/**
 * 付款申请编辑（需求 12-05 3.1，T4）：按应付付款时选择已确认、可申请金额 > 0 的应付（按到期日），每行申请金额默认可申请金额；
 * 预付款时选择采购订单，金额 ≤ 订单价税合计 − 已预付
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const banks = ref<SupplierBankOption[]>([])
const orders = ref<PurchaseOrderOption[]>([])
type Row = PayableCandidate & { checked: boolean; amount?: string }
const rows = ref<Row[]>([])
const preset = typeof route.query.payableIds === 'string' ? route.query.payableIds.split(',') : []
const form = ref<{ supplierId?: string; requestType: string; currency: string; planPayDate: string; supplierBankId?: string; orderId?: string; amount?: string;
  reason?: string; remark?: string; fileIds: string[] }>({
  supplierId: typeof route.query.supplierId === 'string' ? route.query.supplierId : undefined, requestType: 'PAYABLE',
  currency: typeof route.query.currency === 'string' ? route.query.currency : 'CNY', planPayDate: new Date().toISOString().slice(0, 10), fileIds: []
})
const guard = useLeaveGuard(() => [form.value, rows.value])
const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  planPayDate: [{ required: true, message: '请选择计划付款日', trigger: 'change' }],
  supplierBankId: [{ required: true, message: '请选择收款账户', trigger: 'change' }]
}
const prepay = computed(() => form.value.requestType === 'PREPAYMENT')
const picked = computed(() => rows.value.filter((r) => r.checked && num(r.amount) > 0))
const total = computed(() => (prepay.value ? num(form.value.amount) : round2(picked.value.reduce((s, r) => s + num(r.amount), 0))))
const order = computed(() => orders.value.find((o) => o.orderId === form.value.orderId))

let existing: Record<string, string> = {}
async function loadSupplier() {
  const sid = form.value.supplierId
  banks.value = sid ? await requestApi.supplierBanks(sid) : []
  if (sid && !banks.value.some((b) => b.id === form.value.supplierBankId)) form.value.supplierBankId = banks.value.find((b) => b.isDefault)?.id ?? banks.value[0]?.id
  orders.value = sid ? await requestApi.orderOptions(sid) : []
  await loadCandidates()
}
async function loadCandidates() {
  const sid = form.value.supplierId
  const list = sid && !prepay.value ? await apApi.candidates(sid, form.value.currency) : []
  rows.value = list.map((c) => {
    const had = existing[c.payableId]
    const checked = had !== undefined || preset.includes(c.payableId)
    return { ...c, checked, amount: had ?? (checked ? c.requestableAmount : undefined) }
  })
}
onMounted(async () => {
  if (id.value) {
    const d = await requestApi.get(id.value)
    const h = d.header
    existing = Object.fromEntries(d.lines.map((l) => [l.payableId, l.amount]))
    form.value = { supplierId: h.supplierId, requestType: h.requestType, currency: h.currency, planPayDate: h.planPayDate, supplierBankId: d.supplierBankId,
      orderId: h.orderId, amount: h.requestType === 'PREPAYMENT' ? h.amount : undefined, reason: h.reason, remark: d.remark, fileIds: [] }
    tabs.setTitle(tabKeyOf(route), `编辑 ${h.docNo}`)
  }
  await loadSupplier()
  guard.markClean()
})
watch(() => form.value.supplierId, (_v, old) => { if (old !== undefined || !id.value) loadSupplier() })
watch(() => [form.value.requestType, form.value.currency], loadCandidates)
function toggle(r: Row) { r.amount = r.checked ? r.requestableAmount : undefined }

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/finance/payment/request/${id.value}` : '/finance/payment/request')
}
async function save(submit: boolean) {
  await formRef.value?.validate()
  if (prepay.value && !form.value.orderId) return ElMessage.warning('预付款请选择采购订单')
  if (!prepay.value && !picked.value.length) return ElMessage.warning('请勾选应付并填写申请金额')
  const over = picked.value.find((r) => num(r.amount) > num(r.requestableAmount))
  if (over) return ElMessage.warning(`应付「${over.docNo}」可申请金额为 ${formatAmount(over.requestableAmount)}`)
  saving.value = true
  try {
    const f = form.value
    const body = { supplierId: f.supplierId, requestType: f.requestType, currency: prepay.value ? order.value?.currency ?? f.currency : f.currency,
      planPayDate: f.planPayDate, supplierBankId: f.supplierBankId, orderId: prepay.value ? f.orderId : undefined, amount: prepay.value ? f.amount : undefined,
      reason: f.reason, remark: f.remark, fileIds: f.fileIds, lines: prepay.value ? [] : picked.value.map((r) => ({ payableId: r.payableId, amount: r.amount! })) }
    let r: RequestResult = id.value ? await requestApi.update(id.value, body) : await requestApi.create(body)
    guard.markClean()
    if (submit) r = await requestApi.submit(r.id)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    ElMessage.success(submit ? (r.status === 'APPROVED' ? '已提交，审批通过' : '已提交审批') : '已保存')
    tabs.remove([tabKeyOf(route)])
    router.push(`/finance/payment/request/${r.id}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="id ? '编辑付款申请' : '新建付款申请'" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('fin:payment-request:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="申请信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="供应商" prop="supplierId"><SupplierSelect v-model="form.supplierId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="类型">
              <el-radio-group v-model="form.requestType">
                <el-radio v-for="t in REQUEST_TYPES" :key="String(t.value)" :value="t.value">{{ t.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" :disabled="prepay" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="计划付款日" prop="planPayDate"><el-date-picker v-model="form.planPayDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="16" :span="24">
            <el-form-item label="收款账户" prop="supplierBankId">
              <el-select v-model="form.supplierBankId" class="w-full" placeholder="供应商银行账户">
                <el-option v-for="b in banks" :key="b.id" :value="b.id" :label="`${b.bankName} ${b.accountName ?? ''} ${b.accountNo}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <template v-if="prepay">
            <el-col :xl="8" :span="12">
              <el-form-item label="采购订单" required>
                <el-select v-model="form.orderId" filterable class="w-full">
                  <el-option v-for="o in orders" :key="o.orderId" :value="o.orderId" :label="`${o.orderNo}（可预付 ${o.currency} ${formatAmount(o.available)}）`" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xl="8" :span="12">
              <el-form-item label="预付金额" required><AmountInput v-model="form.amount" :currency="order?.currency" :max="order ? num(order.available) : undefined" /></el-form-item>
            </el-col>
          </template>
          <el-col :span="24"><el-form-item label="原因"><el-input v-model="form.reason" maxlength="500" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="附件"><AttachmentUpload v-model="form.fileIds" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
      <ErpPanel v-if="!prepay" :title="`应付明细（已选 ${picked.length} 张，申请合计 ${form.currency} ${formatAmount(total)}）`">
        <el-table :data="rows" max-height="520">
          <el-table-column width="44"><template #default="{ row }"><el-checkbox v-model="row.checked" @change="toggle(row as Row)" /></template></el-table-column>
          <el-table-column prop="docNo" label="应付单" width="150" />
          <el-table-column prop="statementNo" label="对账单" width="150" />
          <el-table-column label="到期日" width="150">
            <template #default="{ row }"><span :class="{ red: row.overdueDays > 0 }">{{ row.dueDate ?? '-' }}{{ row.overdueDays > 0 ? `（逾期 ${row.overdueDays} 天）` : '' }}</span></template>
          </el-table-column>
          <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
          <el-table-column label="已付 / 已申请" width="160" align="right">
            <template #default="{ row }">{{ formatAmount(row.verifiedAmount) }} / {{ formatAmount(row.requestedAmount) }}</template>
          </el-table-column>
          <el-table-column label="可申请" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.requestableAmount) }}</template></el-table-column>
          <el-table-column label="发票" width="80"><template #default="{ row }"><span :class="{ warn: row.uninvoiced }">{{ row.uninvoiced ? '未收齐' : '已收' }}</span></template></el-table-column>
          <el-table-column label="本次申请" width="150">
            <template #default="{ row }"><AmountInput v-model="row.amount" :currency="row.currency" :disabled="!row.checked" :max="num(row.requestableAmount)" /></template>
          </el-table-column>
        </el-table>
        <ErpEmpty v-if="form.supplierId && !rows.length" description="该供应商没有可申请付款的应付" />
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>

<style scoped>
.red { color: var(--erp-color-error); }
.warn { color: var(--erp-color-warning); }
</style>
