<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { num, RECEIPT_TYPES, receiptApi, settingApi, type BankOption, type OrderOption, type ReceiptSave } from '../api/finance'

defineOptions({ name: 'FinReceiptEdit' })

/** 收款单编辑（需求 12-03 3.2，T4）：收款账户决定币别；预收款必须选择该客户未完成的订单；客户付款金额 = 到账 + 手续费 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const banks = ref<BankOption[]>([])
const orders = ref<OrderOption[]>([])
const form = ref<ReceiptSave & { fileIds: string[] }>({
  customerId: typeof route.query.customerId === 'string' ? route.query.customerId : undefined,
  receiptType: 'SALES', settlementMethod: 'TT', receiptDate: new Date().toISOString().slice(0, 10), bankFee: '0', fileIds: []
})
const guard = useLeaveGuard(() => form.value)
const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  receiptType: [{ required: true, message: '请选择收款类型', trigger: 'change' }],
  bankAccountId: [{ required: true, message: '请选择收款账户', trigger: 'change' }],
  settlementMethod: [{ required: true, message: '请选择结算方式', trigger: 'change' }],
  receiptDate: [{ required: true, message: '请选择到账日期', trigger: 'change' }],
  amount: [{ required: true, message: '请填写到账金额', trigger: 'blur' }],
  orderId: [{ validator: (_r, v, cb) => (form.value.receiptType === 'ADVANCE' && !v ? cb(new Error('预收款请选择销售订单')) : cb()), trigger: 'change' }]
}
const currency = computed(() => banks.value.find((b) => b.id === form.value.bankAccountId)?.currency ?? '')
const gross = computed(() => num(form.value.amount) + (form.value.receiptType === 'REFUND' ? 0 : num(form.value.bankFee)))

onMounted(async () => {
  banks.value = await settingApi.bankOptions()
  if (id.value) {
    const d = await receiptApi.get(id.value)
    const h = d.header
    form.value = {
      customerId: h.customerId, receiptType: h.receiptType, bankAccountId: h.bankAccountId, settlementMethod: h.settlementMethod, receiptDate: h.receiptDate,
      exchangeRate: h.exchangeRate, amount: String(Math.abs(Number(h.amount))), bankFee: h.bankFee, bankRefNo: h.bankRefNo, payerName: h.payerName,
      orderId: h.orderId, remark: h.remark, fileIds: []
    }
    tabs.setTitle(tabKeyOf(route), `编辑 ${h.docNo}`)
    guard.markClean()
  } else if (!form.value.bankAccountId) {
    form.value.bankAccountId = banks.value.find((b) => b.isDefault)?.id
  }
})
watch(() => [form.value.customerId, form.value.receiptType], async () => {
  orders.value = form.value.customerId && form.value.receiptType === 'ADVANCE' ? await receiptApi.orderOptions(form.value.customerId) : []
}, { immediate: true })

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/finance/receipt/${id.value}` : '/finance/receipt')
}
async function save(confirm: boolean) {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = { ...form.value, exchangeRate: form.value.exchangeRate || undefined, orderId: form.value.receiptType === 'ADVANCE' ? form.value.orderId : undefined }
    let docId = id.value
    if (docId) await receiptApi.update(docId, body)
    else docId = await receiptApi.create(body)
    guard.markClean()
    if (confirm) {
      await receiptApi.confirm(docId)
      ElMessage.success('已确认')
    } else ElMessage.success('已保存')
    tabs.remove([tabKeyOf(route)])
    router.push(`/finance/receipt/${docId}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="id ? '编辑收款' : '新建收款'" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('fin:receipt:confirm')" type="primary" :loading="saving" @click="save(true)">保存并确认</el-button>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="收款信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="收款类型" prop="receiptType">
              <el-radio-group v-model="form.receiptType">
                <el-radio v-for="t in RECEIPT_TYPES" :key="String(t.value)" :value="t.value">{{ t.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="收款账户" prop="bankAccountId">
              <el-select v-model="form.bankAccountId" class="w-full">
                <el-option v-for="b in banks" :key="b.id" :value="b.id" :label="`${b.name}（${b.currency} ${b.accountNo}）`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="结算方式" prop="settlementMethod"><DictSelect v-model="form.settlementMethod" type="sys_settlement_method" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="到账日期" prop="receiptDate"><el-date-picker v-model="form.receiptDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="汇率"><el-input v-model="form.exchangeRate" :placeholder="`${currency} 为空按到账日汇率`" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item :label="form.receiptType === 'REFUND' ? '退款金额' : '到账金额'" prop="amount">
              <AmountInput v-model="form.amount" :currency="currency" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="手续费"><AmountInput v-model="form.bankFee" :currency="currency" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="客户付款">{{ currency }} {{ formatAmount(gross) }}</el-form-item>
          </el-col>
          <el-col v-if="form.receiptType === 'ADVANCE'" :xl="8" :span="12">
            <el-form-item label="销售订单" prop="orderId">
              <el-select v-model="form.orderId" filterable class="w-full" placeholder="该客户未完成的订单">
                <el-option v-for="o in orders" :key="o.orderId" :value="o.orderId" :label="`${o.orderNo}（${o.currency}）`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="银行流水号"><el-input v-model="form.bankRefNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="付款方名称"><el-input v-model="form.payerName" maxlength="128" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="水单"><AttachmentUpload v-model="form.fileIds" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>
