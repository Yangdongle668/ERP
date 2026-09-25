<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { CustomerBrief } from '@/api/refs'
import { customerApi, oppApi, STAGE_OPTIONS, type Contact, type OppRow, type OppSave } from '../api/crm'

/** 商机表单（需求 03-05 3.2，T2 大弹窗）；赢率随阶段带出，可修改（按百分数编辑） */
const emit = defineEmits<{ saved: [id: string] }>()
const visible = ref(false)
const saving = ref(false)
const editing = ref<OppRow>()
const fixedCustomer = ref(false)
const formRef = ref<FormInstance>()
const form = ref<OppSave & { ratePct?: string }>({ name: '' })
const contacts = ref<Contact[]>([])

const rules: FormRules = {
  name: [{ required: true, message: '请填写商机名称', trigger: 'blur' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  stage: [{ required: true, message: '请选择阶段', trigger: 'change' }],
  amount: [{ required: true, message: '请填写预计金额', trigger: 'blur' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  expectedDate: [{ required: true, message: '请选择预计成交日期', trigger: 'change' }]
}

const pct = (rate?: string) => (rate === undefined ? undefined : String(Number((Number(rate) * 100).toFixed(2))))

async function loadContacts(customerId?: string) {
  contacts.value = customerId ? (await customerApi.get(customerId).catch(() => undefined))?.contacts.filter((c) => c.status !== 'LEFT') ?? [] : []
}
async function onCustomer(c?: CustomerBrief | CustomerBrief[]) {
  const x = Array.isArray(c) ? c[0] : c
  form.value.contactId = undefined
  if (x?.currency) form.value.currency = x.currency
  if (x?.ownerId && !form.value.ownerId) form.value.ownerId = x.ownerId
  await loadContacts(x?.id)
}
function onStage(v: string) {
  form.value.ratePct = pct(STAGE_OPTIONS.find((s) => s.value === v)?.rate)
}

async function open(row?: OppRow, preset: { customerId?: string; currency?: string } = {}) {
  editing.value = row
  fixedCustomer.value = !!row || !!preset.customerId
  form.value = row
    ? { name: row.name, customerId: row.customerId, contactId: row.contactId, stage: row.stage, amount: row.amount, currency: row.currency,
        ratePct: pct(row.winRate), expectedDate: row.expectedDate, products: row.products, competitor: row.competitor, ownerId: row.ownerId,
        remark: row.remark, version: row.version }
    : { name: '', customerId: preset.customerId, currency: preset.currency, stage: 'CONTACT', ratePct: '10' }
  visible.value = true
  await loadContacts(form.value.customerId)
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const rate = Number(form.value.ratePct)
  if (!(rate >= 0 && rate <= 100)) return ElMessage.warning('赢率为 0～100%')
  saving.value = true
  try {
    const { ratePct, ...rest } = form.value
    const data: OppSave = { ...rest, name: rest.name.trim(), winRate: String(Number((Number(ratePct) / 100).toFixed(4))) }
    let id = editing.value?.id
    if (id) await oppApi.update(id, data)
    else id = await oppApi.create(data)
    ElMessage.success('保存成功')
    visible.value = false
    emit('saved', id)
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="editing ? `编辑商机 ${editing.code}` : '新建商机'" width="760px" :close-on-click-modal="false" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="24"><el-form-item label="商机名称" prop="name"><el-input v-model="form.name" maxlength="128" placeholder="如“ABC 2027 年度储能项目”" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" :disabled="fixedCustomer" @select="onCustomer" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系人">
            <el-select v-model="form.contactId" clearable class="w-full">
              <el-option v-for="c in contacts" :key="c.id" :value="c.id!" :label="c.name" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="阶段" prop="stage">
            <el-select v-model="form.stage" class="w-full" @change="onStage">
              <el-option v-for="s in STAGE_OPTIONS" :key="s.value" :value="s.value" :label="s.label" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="赢率(%)"><NumberInput v-model="form.ratePct" :precision="2" :max="100" trim-zeros /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="预计金额" prop="amount"><AmountInput v-model="form.amount" :currency="form.currency" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="预计成交" prop="expectedDate"><el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        </el-col>
        <el-col :span="12"><el-form-item label="负责人"><UserSelect v-model="form.ownerId" placeholder="默认客户负责人" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="意向产品"><el-input v-model="form.products" maxlength="512" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="竞争对手"><el-input v-model="form.competitor" maxlength="256" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>
