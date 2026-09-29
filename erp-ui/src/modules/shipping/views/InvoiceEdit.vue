<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { download } from '@/api/http'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { docApi, type InvoiceDetail } from '../api/shipping'

defineOptions({ name: 'ShpInvoiceEdit' })

/**
 * Commercial Invoice 编辑（需求 11-04 3.1，T4）：金额与数量与出货单一致（SHP-DOC-R01），只能修改抬头、收货人、描述、HS 编码、原产地等；
 * 保存时每行必须有 HS 编码（R02）；无价格权限时价格显示 ***（R05）。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<InvoiceDetail>()
const saving = ref(false)
const canEdit = computed(() => me.hasPermission('shp:document:update') && !d.value?.invalid)

async function load() {
  d.value = await docApi.invoice(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.invoiceNo)
}
onMounted(load)
async function save() {
  const x = d.value!
  if (!x.invoiceNo?.trim()) return ElMessage.warning('请填写 Invoice No.')
  const missing = x.lines.find((l) => !l.hsCode?.trim())
  if (missing) return ElMessage.warning(`第 ${missing.lineNo} 行请填写 HS 编码`)
  saving.value = true
  try {
    await docApi.updateInvoice(id.value, {
      invoiceNo: x.invoiceNo.trim(), invoiceDate: x.invoiceDate, billTo: x.billTo, consignee: x.consignee, notifyParty: x.notifyParty, tradeTerm: x.tradeTerm,
      paymentTermText: x.paymentTermText, portOfLoading: x.portOfLoading, portOfDestination: x.portOfDestination, vesselFlight: x.vesselFlight, bankInfo: x.bankInfo,
      remark: x.remark, lines: x.lines.map((l) => ({ id: l.id, customerPoNo: l.customerPoNo, customerPartNo: l.customerPartNo, description: l.description,
        hsCode: l.hsCode?.trim(), origin: l.origin }))
    })
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :title="d ? `Invoice ${d.invoiceNo}` : 'Commercial Invoice'" back="/shipping/invoice" sticky>
    <template #meta><ErpBadge v-if="d?.invalid" type="danger" :dot="false">已失效</ErpBadge></template>
    <template #actions>
      <PrintButton v-if="d" biz-type="SHP_INVOICE" :ids="[id]" permission="shp:document:print" />
      <el-button v-if="d" v-perm="'shp:document:print'" @click="download(`/shipping/invoices/${id}/export`, undefined, `${d.invoiceNo}.xlsx`)">导出 Excel</el-button>
      <el-button v-if="canEdit" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
    <template v-if="d">
      <el-alert v-if="d.invalid" type="error" :closable="false" title="出货单已反确认或作废，本单证已失效（打印带水印）" class="gap-b" />
      <ErpPanel title="单头">
        <el-form label-width="120px" :disabled="!canEdit">
          <el-row :gutter="24">
            <el-col :span="8"><el-form-item label="Invoice No." required><el-input v-model="d.invoiceNo" maxlength="32" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Date" required><el-date-picker v-model="d.invoiceDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item></el-col>
            <el-col :span="8">
              <el-form-item label="出货单">
                <el-link type="primary" underline="never" @click="router.push(`/shipping/shipment/${d.shipmentId}`)">{{ d.shipmentNo }}</el-link>
                <span class="gap-l">{{ d.customerName }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="Bill To"><el-input v-model="d.billTo" type="textarea" :rows="3" maxlength="512" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Consignee"><el-input v-model="d.consignee" type="textarea" :rows="3" maxlength="512" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Notify Party"><el-input v-model="d.notifyParty" type="textarea" :rows="2" maxlength="512" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Bank"><el-input v-model="d.bankInfo" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Trade Term"><DictSelect v-model="d.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Payment Term"><el-input v-model="d.paymentTermText" maxlength="256" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Vessel / Flight"><el-input v-model="d.vesselFlight" maxlength="64" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Port of Loading"><el-input v-model="d.portOfLoading" maxlength="64" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Destination"><el-input v-model="d.portOfDestination" maxlength="64" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="Remark"><el-input v-model="d.remark" maxlength="1000" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </ErpPanel>
      <ErpPanel title="明细" description="数量、单价、金额与出货单一致，不可修改">
        <template #extra>
          <span class="total">Total {{ d.currency }} <strong class="num">{{ d.priceVisible ? formatAmount(d.totalAmount) : '***' }}</strong></span>
        </template>
        <el-table :data="d.lines">
          <el-table-column prop="lineNo" label="No." width="50" />
          <el-table-column label="PO No." width="130"><template #default="{ row }"><el-input v-model="row.customerPoNo" :disabled="!canEdit" maxlength="64" /></template></el-table-column>
          <el-table-column label="Part No." width="140"><template #default="{ row }"><el-input v-model="row.customerPartNo" :disabled="!canEdit" maxlength="64" /></template></el-table-column>
          <el-table-column label="Description" min-width="200">
            <template #default="{ row }"><el-input v-model="row.description" :disabled="!canEdit" maxlength="512" /></template>
          </el-table-column>
          <el-table-column label="HS Code" width="140">
            <template #default="{ row }"><el-input v-model="row.hsCode" :disabled="!canEdit" maxlength="16" :class="{ missing: !row.hsCode }" /></template>
          </el-table-column>
          <el-table-column label="Origin" width="100"><template #default="{ row }"><el-input v-model="row.origin" :disabled="!canEdit" maxlength="32" /></template></el-table-column>
          <el-table-column label="Qty" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }} {{ row.uom }}</template></el-table-column>
          <el-table-column label="Unit Price" width="100" align="right"><template #default="{ row }">{{ d?.priceVisible ? row.unitPrice : '***' }}</template></el-table-column>
          <el-table-column label="Amount" width="120" align="right"><template #default="{ row }">{{ d?.priceVisible ? formatAmount(row.amount) : '***' }}</template></el-table-column>
        </el-table>
        <div v-if="d.priceVisible" class="words">{{ d.amountInWords }}</div>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>
  </ErpPage>
</template>

<style scoped>
.gap-b { margin-bottom: var(--erp-section-gap); }
.gap-l { margin-left: var(--erp-space-2); }
.total { color: var(--erp-color-text-secondary); }
.words { padding-top: var(--erp-space-3); font-weight: var(--erp-font-weight-medium); }
.missing :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px var(--erp-color-warning) inset; }
</style>
