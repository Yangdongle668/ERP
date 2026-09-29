<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatQty } from '@/utils/format'
import { num, round2, SALES_INVOICE_TYPES, salesInvoiceApi, type UninvoicedLine } from '../api/finance'

defineOptions({ name: 'FinSalesInvoiceEdit' })

/**
 * 开票登记（需求 12-02 3.4，T4）：选择客户已确认、未完全开票的应收行（可部分开票）；
 * 金额按数量比例自动计算，可调整尾差 ≤ 1 元；保存后回写应收已开票与销售订单已开票数量
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
type Row = UninvoicedLine & { checked: boolean; qty2?: string; total2?: string }
const form = ref<{ customerId?: string; invoiceType: string; invoiceNo: string; invoiceDate: string; remark?: string; fileIds: string[] }>({
  customerId: typeof route.query.customerId === 'string' ? route.query.customerId : undefined,
  invoiceType: 'VAT_SPECIAL', invoiceNo: '', invoiceDate: new Date().toISOString().slice(0, 10), fileIds: []
})
const rows = ref<Row[]>([])
const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
  invoiceNo: [{ required: true, message: '请填写发票号码', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }]
}
const preset = typeof route.query.receivableIds === 'string' ? route.query.receivableIds.split(',') : []

async function loadLines() {
  rows.value = []
  if (!form.value.customerId) return
  const list = await salesInvoiceApi.uninvoiced(form.value.customerId)
  rows.value = list.map((l) => ({ ...l, checked: preset.length ? preset.includes(l.receivableId) : false, qty2: l.uninvoicedQty, total2: l.uninvoicedAmount }))
}
onMounted(loadLines)
watch(() => form.value.customerId, loadLines)

/** 数量变化 → 金额按比例重算（全部开完取剩余金额） */
function onQty(r: Row) {
  if (!r.qty || num(r.qty) === 0) return
  r.total2 = num(r.qty2) === num(r.uninvoicedQty) ? r.uninvoicedAmount : String(round2((num(r.totalAmount) * num(r.qty2)) / num(r.qty)))
}
const picked = computed(() => rows.value.filter((r) => r.checked))
const currency = computed(() => picked.value[0]?.currency ?? rows.value[0]?.currency ?? '')
const total = computed(() => round2(picked.value.reduce((s, r) => s + num(r.total2), 0)))

async function save() {
  await formRef.value?.validate()
  if (!picked.value.length) return ElMessage.warning('请勾选要开票的应收行')
  if (new Set(picked.value.map((r) => r.currency)).size > 1) return ElMessage.warning('请选择同一币别的应收')
  saving.value = true
  try {
    const id = await salesInvoiceApi.register({
      customerId: form.value.customerId!, invoiceType: form.value.invoiceType, invoiceNo: form.value.invoiceNo.trim(), invoiceDate: form.value.invoiceDate,
      remark: form.value.remark, fileIds: form.value.fileIds,
      lines: picked.value.map((r) => ({ receivableLineId: r.receivableLineId, qty: r.qty ? r.qty2! : '1', totalAmount: r.total2 }))
    })
    ElMessage.success('已登记')
    tabs.remove([tabKeyOf(route)])
    router.push({ path: '/finance/receivable/invoice', query: { highlight: id } })
  } finally {
    saving.value = false
  }
}
function back() {
  tabs.remove([tabKeyOf(route)])
  router.push('/finance/receivable/invoice')
}
</script>

<template>
  <ErpPage title="开票登记" back sticky :on-back="back">
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="发票信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="发票类型" prop="invoiceType">
              <el-select v-model="form.invoiceType" class="w-full"><el-option v-for="o in SALES_INVOICE_TYPES" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="发票号码" prop="invoiceNo"><el-input v-model="form.invoiceNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="开票日期" prop="invoiceDate"><el-date-picker v-model="form.invoiceDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="16" :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="500" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="附件"><AttachmentUpload v-model="form.fileIds" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
      <ErpPanel :title="`开票明细（已选 ${picked.length} 行，合计 ${currency} ${formatAmount(total)}）`">
        <el-table :data="rows" max-height="520">
          <el-table-column width="44"><template #default="{ row }"><el-checkbox v-model="row.checked" /></template></el-table-column>
          <el-table-column prop="receivableNo" label="应收单" width="150" />
          <el-table-column prop="sourceNo" label="出货单" width="150" />
          <el-table-column prop="orderNo" label="订单" width="140" />
          <el-table-column label="物料 / 说明" min-width="180">
            <template #default="{ row }">{{ row.materialCode ? `${row.materialCode} ${row.materialName ?? ''}` : row.description }}</template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
          <el-table-column label="未开票数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.uninvoicedQty) }}</template></el-table-column>
          <el-table-column label="未开票金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.uninvoicedAmount) }}</template></el-table-column>
          <el-table-column label="本次数量" width="130">
            <template #default="{ row }"><QtyInput v-if="row.qty" v-model="row.qty2" :disabled="!row.checked" @change="onQty(row as Row)" /></template>
          </el-table-column>
          <el-table-column label="本次金额（含税）" width="150">
            <template #default="{ row }"><AmountInput v-model="row.total2" :currency="row.currency" :disabled="!row.checked" /></template>
          </el-table-column>
        </el-table>
        <ErpEmpty v-if="form.customerId && !rows.length" description="该客户没有待开票的应收" />
      </ErpPanel>
    </el-form>
  </ErpPage>
</template>
