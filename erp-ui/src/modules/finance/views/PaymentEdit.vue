<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { num, paymentApi, requestApi, settingApi, type BankOption, type PaymentSave, type RequestDetail } from '../api/finance'

defineOptions({ name: 'FinPaymentEdit' })

/** 付款单编辑（需求 12-05 3.2，T4）：从付款申请带入；付款账户（同币别）、付款日期、金额（≤ 申请未付）、手续费、回单 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const req = ref<RequestDetail>()
const banks = ref<BankOption[]>([])
const form = ref<PaymentSave & { fileIds: string[] }>({
  requestId: typeof route.query.requestId === 'string' ? route.query.requestId : undefined,
  settlementMethod: 'TT', payDate: new Date().toISOString().slice(0, 10), bankFee: '0', fileIds: []
})
const guard = useLeaveGuard(() => form.value)
const rules: FormRules = {
  bankAccountId: [{ required: true, message: '请选择付款账户', trigger: 'change' }],
  settlementMethod: [{ required: true, message: '请选择结算方式', trigger: 'change' }],
  payDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }],
  amount: [{ required: true, message: '请填写付款金额', trigger: 'blur' }]
}
const unpaid = computed(() => num(req.value?.header.unpaidAmount))

onMounted(async () => {
  if (id.value) {
    const p = await paymentApi.get(id.value)
    const h = p.header
    form.value = { requestId: h.requestId, bankAccountId: h.bankAccountId, settlementMethod: h.settlementMethod, payDate: h.payDate, exchangeRate: h.exchangeRate,
      amount: h.amount, bankFee: h.bankFee, bankRefNo: h.bankRefNo, remark: h.remark, fileIds: [] }
    tabs.setTitle(tabKeyOf(route), `编辑 ${h.docNo}`)
  }
  if (!form.value.requestId) return
  req.value = await requestApi.get(form.value.requestId)
  banks.value = await settingApi.bankOptions(req.value.header.currency)
  if (!id.value) {
    form.value.amount = req.value.header.unpaidAmount
    form.value.bankAccountId = banks.value.find((b) => b.isDefault)?.id ?? banks.value[0]?.id
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/finance/payment/${id.value}` : `/finance/payment/request/${form.value.requestId ?? ''}`)
}
async function save(confirm: boolean) {
  await formRef.value?.validate()
  if (num(form.value.amount) > unpaid.value && !id.value) return ElMessage.warning('付款金额超过申请未付金额')
  saving.value = true
  try {
    const body = { ...form.value, exchangeRate: form.value.exchangeRate || undefined }
    let docId = id.value
    if (docId) await paymentApi.update(docId, body)
    else docId = await paymentApi.create(body)
    guard.markClean()
    if (confirm) {
      await paymentApi.confirm(docId)
      ElMessage.success('已确认付款')
    } else ElMessage.success('已保存')
    tabs.remove([tabKeyOf(route)])
    router.push(`/finance/payment/${docId}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="id ? '编辑付款' : '新建付款'" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('fin:payment:confirm')" type="primary" :loading="saving" @click="save(true)">保存并确认</el-button>
    </template>
    <ErpEmpty v-if="!form.requestId" description="请从已审批的付款申请发起付款" />
    <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel v-if="req" title="付款申请">
        <el-descriptions :column="4">
          <el-descriptions-item label="申请单">{{ req.header.docNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ req.header.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="申请金额">{{ req.header.currency }} {{ formatAmount(req.header.amount) }}</el-descriptions-item>
          <el-descriptions-item label="未付">{{ formatAmount(req.header.unpaidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="收款账户" :span="4">{{ req.supplierBankText }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>
      <ErpPanel title="付款信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="付款账户" prop="bankAccountId">
              <el-select v-model="form.bankAccountId" class="w-full">
                <el-option v-for="b in banks" :key="b.id" :value="b.id" :label="`${b.name}（${b.currency} ${b.accountNo}）`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="结算方式" prop="settlementMethod"><DictSelect v-model="form.settlementMethod" type="sys_settlement_method" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="付款日期" prop="payDate"><el-date-picker v-model="form.payDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="金额" prop="amount"><AmountInput v-model="form.amount" :currency="req?.header.currency" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="手续费"><AmountInput v-model="form.bankFee" :currency="req?.header.currency" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="汇率"><el-input v-model="form.exchangeRate" placeholder="为空按付款日汇率" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="银行流水号"><el-input v-model="form.bankRefNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="16" :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="500" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="回单"><AttachmentUpload v-model="form.fileIds" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>
