<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { CustomerBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { toDateString } from '@/utils/format'
import { orderApi, RFQ_STATUS, rfqApi, type CustomerDefaults, type RfqDetail, type RfqLine } from '../api/sales'

defineOptions({ name: 'SalRfqEdit' })

/** RFQ 编辑（需求 04-02 3.2，T4）：需求行可以只有描述，工程评估时再关联物料 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => RfqLine[] }>()
const saving = ref(false)
const detail = ref<RfqDetail>()
const contacts = ref<CustomerDefaults['contacts']>([])

interface Form { customerId?: string; contactId?: string; currency?: string; tradeTerm?: string; replyDueDate?: string; remark?: string; fileIds: string[]; lines: RfqLine[] }
const newLine = (): RfqLine => ({})
const inDays = (n: number) => {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return toDateString(d)
}
const form = ref<Form>({ replyDueDate: inDays(3), fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  replyDueDate: [{ required: true, message: '请选择回复截止日期', trigger: 'change' }]
}

const columns: LineColumn<RfqLine>[] = [
  { prop: 'customerPartNo', label: '客户料号', type: 'text', width: 130 },
  { prop: 'description', label: '需求描述', type: 'text', width: 220, required: true },
  { prop: 'materialId', label: '本厂物料（可选）', type: 'slot', width: 200 },
  { prop: 'annualQty', label: '年用量', type: 'qty', width: 110 },
  { prop: 'qtyBreaks', label: '数量阶梯', type: 'text', width: 150, required: true,
    validate: (v) => (String(v ?? '').split(/[,，\s]+/).filter(Boolean).every((x) => Number(x) > 0) ? undefined : '用逗号分隔的正数，如 1000,5000') },
  { prop: 'targetPrice', label: '目标价', type: 'price', width: 110 },
  { prop: 'requiredDate', label: '需求日期', type: 'date', width: 140 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
]

async function loadContacts(customerId?: string) {
  contacts.value = []
  if (!customerId) return
  const d = await orderApi.customerDefaults(customerId).catch(() => undefined)
  contacts.value = d?.contacts ?? []
  if (d && !form.value.contactId) form.value.contactId = d.contactId
  if (d && !form.value.currency) form.value.currency = d.currency
  if (d && !form.value.tradeTerm) form.value.tradeTerm = d.tradeTerm
}
async function onCustomer(c?: CustomerBrief | CustomerBrief[]) {
  const x = Array.isArray(c) ? c[0] : c
  form.value.contactId = undefined
  form.value.currency = x?.currency
  form.value.tradeTerm = x?.tradeTerm
  await loadContacts(x?.id)
}

onMounted(async () => {
  if (id.value) {
    const d = await rfqApi.get(id.value)
    if (d.rfqStatus !== 'DRAFT' && d.rfqStatus !== 'EVALUATING') {
      ElMessage.warning('当前状态的 RFQ 不能修改')
      router.replace(`/sales/rfq/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      customerId: d.customerId, contactId: d.contactId, currency: d.currency, tradeTerm: d.tradeTerm, replyDueDate: d.replyDueDate, remark: d.remark,
      fileIds: [], lines: [...d.lines, newLine()]
    }
    await loadContacts(d.customerId)
    tabs.setTitle(tabKeyOf(route), `编辑 RFQ ${d.docNo}`)
  } else if (typeof route.query.customerId === 'string') {
    form.value.customerId = route.query.customerId
    await loadContacts(route.query.customerId)
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/sales/rfq/${id.value}` : '/sales/rfq')
}

async function save() {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  const f = form.value
  const data = {
    customerId: f.customerId, contactId: f.contactId, currency: f.currency, tradeTerm: f.tradeTerm, replyDueDate: f.replyDueDate,
    remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    lines: linesRef.value.validRows().map((l) => ({
      customerPartNo: l.customerPartNo?.trim() || undefined, description: l.description?.trim(), materialId: l.materialId, annualQty: l.annualQty || undefined,
      qtyBreaks: String(l.qtyBreaks ?? '').split(/[,，\s]+/).filter(Boolean).join(','), targetPrice: l.targetPrice || undefined,
      requiredDate: l.requiredDate || undefined, remark: l.remark?.trim() || undefined
    }))
  }
  saving.value = true
  try {
    const r = id.value ? await rfqApi.update(id.value, data) : await rfqApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/sales/rfq/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑 RFQ ${detail.value.docNo}` : '新建 RFQ'))
const asLine = (r: unknown) => r as RfqLine
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.rfqStatus" :map="RFQ_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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
                <el-option v-for="c in contacts" :key="c.id" :value="c.id" :label="`${c.name}${c.phone ? ` ${c.phone}` : ''}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="回复截止" prop="replyDueDate"><el-date-picker v-model="form.replyDueDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别"><CurrencySelect v-model="form.currency" clearable /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="需求明细" description="数量阶梯用逗号分隔（如 1000,5000,10000），成本核算与报价按每一档计算">
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine">
        <template #cell-materialId="{ row, disabled }"><MaterialSelect v-model="asLine(row).materialId" :disabled="disabled" /></template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel title="附件（图纸、规格书）">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="SAL_RFQ" multiple />
      <AttachmentPanel v-else biz-type="SAL_RFQ" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>
