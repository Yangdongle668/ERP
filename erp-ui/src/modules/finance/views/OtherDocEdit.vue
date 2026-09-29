<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { apApi, arApi, num, round2, type OtherLine } from '../api/finance'

defineOptions({ name: 'FinOtherDocEdit' })

/** 其他应收 / 其他应付（需求 12-02 3.3、12-04，T4）：单头 + 项目明细（价税合计、税率）；提交按审批流，通过后已确认 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const ap = computed(() => route.path.startsWith('/finance/payable'))
const base = computed(() => (ap.value ? '/finance/payable' : '/finance/receivable'))
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)

interface Form {
  partnerId?: string; apType: string; currency: string; exchangeRate?: string; bizDate: string; dueDate?: string; description: string; remark?: string
  fileIds: string[]; lines: OtherLine[]
}
const form = ref<Form>({ apType: 'OTHER', currency: 'CNY', bizDate: new Date().toISOString().slice(0, 10), description: '', fileIds: [], lines: [{ description: '', taxRate: '0' }] })
const guard = useLeaveGuard(() => form.value)
const rules: FormRules = {
  partnerId: [{ required: true, message: '请选择', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  bizDate: [{ required: true, message: '请选择业务日期', trigger: 'change' }],
  description: [{ required: true, message: '请填写说明', trigger: 'blur' }]
}
const title = computed(() => `${id.value ? '编辑' : '新建'}${ap.value ? '其他应付' : '其他应收'}`)
const total = computed(() => round2(form.value.lines.reduce((s, l) => s + num(l.totalAmount), 0)))
const TAX_RATES = ['0', '0.01', '0.03', '0.06', '0.09', '0.13']

onMounted(async () => {
  if (!id.value) return
  const d = ap.value ? await apApi.get(id.value) : await arApi.get(id.value)
  const h = d.header as { currency: string; exchangeRate: string; bizDate: string; dueDate?: string; description?: string; docNo: string }
  form.value = {
    partnerId: 'supplierId' in d.header ? d.header.supplierId : d.header.customerId,
    apType: 'apType' in d.header ? d.header.apType : 'OTHER',
    currency: h.currency, exchangeRate: h.exchangeRate, bizDate: h.bizDate, dueDate: h.dueDate, description: h.description ?? '', remark: d.remark,
    fileIds: [], lines: d.lines.map((l) => ({ description: l.description ?? '', totalAmount: l.totalAmount, taxRate: String(Number(l.taxRate)) }))
  }
  tabs.setTitle(tabKeyOf(route), `编辑 ${h.docNo}`)
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `${base.value}/${id.value}` : base.value)
}

async function save(submit: boolean) {
  await formRef.value?.validate()
  const lines = form.value.lines.filter((l) => l.description && num(l.totalAmount) !== 0)
  if (!lines.length) return ElMessage.warning('请至少填写一行明细（项目说明、金额）')
  const f = form.value
  const body = { currency: f.currency, exchangeRate: f.exchangeRate || undefined, bizDate: f.bizDate, dueDate: f.dueDate || undefined,
    description: f.description, remark: f.remark, fileIds: f.fileIds, lines }
  saving.value = true
  try {
    let docId = id.value
    if (ap.value) {
      const b = { ...body, supplierId: f.partnerId, apType: f.apType }
      if (docId) await apApi.updateOther(docId, b)
      else docId = await apApi.createOther(b)
    } else {
      const b = { ...body, customerId: f.partnerId }
      if (docId) await arApi.updateOther(docId, b)
      else docId = await arApi.createOther(b)
    }
    guard.markClean()
    if (submit) {
      const r = ap.value ? await apApi.submit(docId!) : await arApi.submit(docId!)
      ElMessage.success(r.status === 'CONFIRMED' ? '已提交并确认' : '已提交审批')
    } else ElMessage.success('已保存')
    tabs.remove([tabKeyOf(route)])
    router.push(`${base.value}/${docId}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission(ap ? 'fin:payable:create-other' : 'fin:receivable:create-other')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item :label="ap ? '供应商' : '客户'" prop="partnerId">
              <SupplierSelect v-if="ap" v-model="form.partnerId" />
              <CustomerSelect v-else v-model="form.partnerId" />
            </el-form-item>
          </el-col>
          <el-col v-if="ap" :xl="8" :span="12">
            <el-form-item label="类型">
              <el-radio-group v-model="form.apType"><el-radio value="OTHER">其他</el-radio><el-radio value="OUTSOURCE">委外加工费</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="汇率"><el-input v-model="form.exchangeRate" placeholder="为空按业务日期汇率" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="业务日期" prop="bizDate"><el-date-picker v-model="form.bizDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="到期日"><el-date-picker v-model="form.dueDate" value-format="YYYY-MM-DD" class="w-full" placeholder="默认业务日期" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="说明" prop="description"><el-input v-model="form.description" maxlength="500" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="附件"><AttachmentUpload v-model="form.fileIds" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
      <ErpPanel :title="`明细（合计 ${form.currency} ${formatAmount(total)}）`">
        <el-table :data="form.lines">
          <el-table-column label="#" width="50" type="index" />
          <el-table-column label="项目说明" min-width="240"><template #default="{ row }"><el-input v-model="row.description" maxlength="200" /></template></el-table-column>
          <el-table-column label="价税合计" width="180">
            <template #default="{ row }"><AmountInput v-model="row.totalAmount" :currency="form.currency" allow-negative /></template>
          </el-table-column>
          <el-table-column label="税率" width="120">
            <template #default="{ row }">
              <el-select v-model="row.taxRate"><el-option v-for="t in TAX_RATES" :key="t" :value="t" :label="`${Number(t) * 100}%`" /></el-select>
            </template>
          </el-table-column>
          <el-table-column width="70">
            <template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>
        <el-button class="add" @click="form.lines.push({ description: '', taxRate: '0' })">添加一行</el-button>
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>

<style scoped>
.add { margin-top: var(--erp-space-2); }
</style>
