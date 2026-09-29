<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatAmount } from '@/utils/format'
import { reportApi, type AgingDoc, type AgingReport, type AgingRow, type Statement } from '../api/finance'

defineOptions({ name: 'FinReportPage' })

/**
 * 财务报表（需求 12-08，T7）：应收 / 应付账龄（按到期日分段，本位币按截止日汇率折算，可下钻单据）、客户 / 供应商往来对账单。
 * 毛利、损益等报表在成本核算与凭证上线后提供。
 */
const router = useRouter()
const tab = ref<'ar' | 'ap' | 'cs' | 'ss'>('ar')
const today = new Date().toISOString().slice(0, 10)
const monthStart = today.slice(0, 8) + '01'

// ==================== 账龄 ====================
const agingQ = ref<{ asOf: string; partnerId?: string; currency?: string }>({ asOf: today })
const aging = ref<AgingReport>()
const agingLoading = ref(false)
const isAp = computed(() => tab.value === 'ap')
async function loadAging() {
  agingLoading.value = true
  try {
    const q = agingQ.value
    aging.value = isAp.value
      ? await reportApi.apAging({ asOf: q.asOf, supplierId: q.partnerId, currency: q.currency || undefined })
      : await reportApi.arAging({ asOf: q.asOf, customerId: q.partnerId, currency: q.currency || undefined })
  } finally {
    agingLoading.value = false
  }
}
function switchTab(t: string | number) {
  if (t === 'ar' || t === 'ap') {
    agingQ.value.partnerId = undefined
    aging.value = undefined
    loadAging()
  }
}
loadAging()
const BUCKETS: { key: keyof AgingRow; label: string }[] = [
  { key: 'notDue', label: '未到期' }, { key: 'd1to30', label: '逾期 1～30' }, { key: 'd31to60', label: '31～60' }, { key: 'd61to90', label: '61～90' },
  { key: 'd91to180', label: '91～180' }, { key: 'over180', label: '> 180 天' }
]
const BUCKET_LABEL: Record<string, string> = { NOT_DUE: '未到期', D1_30: '1～30', D31_60: '31～60', D61_90: '61～90', D91_180: '91～180', OVER_180: '> 180' }
const docsDialog = ref(false)
const docs = ref<AgingDoc[]>([])
const docsTitle = ref('')
async function drill(r: AgingRow) {
  const q = { asOf: agingQ.value.asOf, currency: r.currency }
  docs.value = isAp.value ? await reportApi.apAgingDocs({ ...q, supplierId: r.partnerId }) : await reportApi.arAgingDocs({ ...q, customerId: r.partnerId })
  docsTitle.value = `${r.partnerName}（${r.currency}）`
  docsDialog.value = true
}
function openDoc(d: AgingDoc) {
  docsDialog.value = false
  router.push(`/finance/${isAp.value ? 'payable' : 'receivable'}/${d.id}`)
}
const agingExport = computed(() => (isAp.value ? '/finance/reports/ap-aging/export' : '/finance/reports/ar-aging/export'))
const agingParams = () => ({ asOf: agingQ.value.asOf, [isAp.value ? 'supplierId' : 'customerId']: agingQ.value.partnerId, currency: agingQ.value.currency || undefined })

// ==================== 对账单 ====================
const stQ = ref<{ partnerId?: string; currency?: string; dates: [string, string] }>({ dates: [monthStart, today] })
const st = ref<Statement>()
const stLoading = ref(false)
const isSupplierSt = computed(() => tab.value === 'ss')
async function loadStatement() {
  if (!stQ.value.partnerId) return ElMessage.warning(isSupplierSt.value ? '请选择供应商' : '请选择客户')
  stLoading.value = true
  try {
    const q = { currency: stQ.value.currency || undefined, dateFrom: stQ.value.dates?.[0], dateTo: stQ.value.dates?.[1] }
    st.value = isSupplierSt.value
      ? await reportApi.supplierStatement({ ...q, supplierId: stQ.value.partnerId })
      : await reportApi.customerStatement({ ...q, customerId: stQ.value.partnerId })
  } finally {
    stLoading.value = false
  }
}
const stExport = computed(() => (isSupplierSt.value ? '/finance/reports/supplier-statement/export' : '/finance/reports/customer-statement/export'))
const stParams = () => ({ [isSupplierSt.value ? 'supplierId' : 'customerId']: stQ.value.partnerId, currency: stQ.value.currency || undefined,
  dateFrom: stQ.value.dates?.[0], dateTo: stQ.value.dates?.[1] })
const DOC_ROUTES: Record<string, string> = { RECEIPT: '/finance/receipt/', RECEIVABLE: '/finance/receivable/', PAYMENT: '/finance/payment/', PAYABLE: '/finance/payable/' }
</script>

<template>
  <ErpPage description="账龄以到期日为准，本位币按截止日汇率折算；对账单期末余额 = 期初 + 本期应收（应付）− 本期收款（付款），与单据余额不一致时标红">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="detail-tabs" @tab-change="switchTab">
        <el-tab-pane label="应收账龄" name="ar" />
        <el-tab-pane label="应付账龄" name="ap" />
        <el-tab-pane label="客户对账单" name="cs" />
        <el-tab-pane label="供应商对账单" name="ss" />
      </el-tabs>
    </ErpPanel>

    <template v-if="tab === 'ar' || tab === 'ap'">
      <ErpPanel>
        <div class="bar">
          <span class="label">截止日</span>
          <el-date-picker v-model="agingQ.asOf" value-format="YYYY-MM-DD" class="date" :clearable="false" />
          <span class="label">{{ isAp ? '供应商' : '客户' }}</span>
          <SupplierSelect v-if="isAp" v-model="agingQ.partnerId" class="partner" />
          <CustomerSelect v-else v-model="agingQ.partnerId" class="partner" />
          <span class="label">币别</span>
          <CurrencySelect v-model="agingQ.currency" class="cur" clearable />
          <el-button type="primary" :loading="agingLoading" @click="loadAging">查询</el-button>
          <span class="grow" />
          <ExportButton :url="agingExport" :params="agingParams" permission="fin:report:export" />
        </div>
      </ErpPanel>
      <ErpPanel v-if="aging" :title="`合计（本位币 ${aging.baseCurrency}）：余额 ${formatAmount(aging.totalBase)}，其中逾期 ${formatAmount(aging.overdueBase)}`" flush>
        <el-table v-loading="agingLoading" :data="aging.rows">
          <el-table-column :label="isAp ? '供应商' : '客户'" min-width="180">
            <template #default="{ row }"><el-link type="primary" underline="never" @click="drill(row as AgingRow)">{{ row.partnerCode }} {{ row.partnerName }}</el-link></template>
          </el-table-column>
          <el-table-column prop="currency" label="币别" width="60" />
          <el-table-column label="余额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.total) }}</template></el-table-column>
          <el-table-column v-for="b in BUCKETS" :key="b.key" :label="b.label" width="110" align="right">
            <template #default="{ row }"><span :class="{ red: b.key !== 'notDue' && Number(row[b.key]) > 0 }">{{ formatAmount(row[b.key]) }}</span></template>
          </el-table-column>
          <el-table-column label="折算汇率" width="90" align="right"><template #default="{ row }">{{ row.rate }}</template></el-table-column>
          <el-table-column label="本位币" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalBase) }}</template></el-table-column>
        </el-table>
      </ErpPanel>
    </template>

    <template v-else>
      <ErpPanel>
        <div class="bar">
          <span class="label">{{ isSupplierSt ? '供应商' : '客户' }}</span>
          <SupplierSelect v-if="isSupplierSt" v-model="stQ.partnerId" class="partner" />
          <CustomerSelect v-else v-model="stQ.partnerId" class="partner" />
          <span class="label">币别</span>
          <CurrencySelect v-model="stQ.currency" class="cur" clearable />
          <span class="label">期间</span>
          <el-date-picker v-model="stQ.dates" type="daterange" value-format="YYYY-MM-DD" class="range" :clearable="false" />
          <el-button type="primary" :loading="stLoading" @click="loadStatement">查询</el-button>
          <span class="grow" />
          <PrintButton v-if="!isSupplierSt && st" biz-type="FIN_CUSTOMER_STATEMENT" :ids="[st.partnerId]" permission="fin:report:query" />
          <ExportButton v-if="st" :url="stExport" :params="stParams" permission="fin:report:export" />
        </div>
      </ErpPanel>
      <ErpPanel v-if="st" flush>
        <el-descriptions :column="4" class="st-head">
          <el-descriptions-item :label="isSupplierSt ? '供应商' : '客户'">{{ st.partnerName }}{{ st.partnerNameEn ? ` / ${st.partnerNameEn}` : '' }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ st.dateFrom }} ~ {{ st.dateTo }}（{{ st.currency }}）</el-descriptions-item>
          <el-descriptions-item label="期初余额">{{ formatAmount(st.opening) }}</el-descriptions-item>
          <el-descriptions-item :label="isSupplierSt ? '本期应付 / 付款' : '本期应收 / 收款'">{{ formatAmount(st.debit) }} / {{ formatAmount(st.credit) }}</el-descriptions-item>
          <el-descriptions-item label="本期核销">{{ formatAmount(st.verified) }}</el-descriptions-item>
          <el-descriptions-item label="期末余额"><b :class="{ red: st.mismatch }">{{ formatAmount(st.closing) }}</b></el-descriptions-item>
          <el-descriptions-item label="单据余额">
            <span :class="{ red: st.mismatch }">{{ formatAmount(st.ledgerBalance) }}{{ st.mismatch ? '（与期末余额不一致，请检查单据）' : '' }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table v-loading="stLoading" :data="st.lines">
          <el-table-column prop="date" label="日期" width="100" />
          <el-table-column label="单号" width="160">
            <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(DOC_ROUTES[row.docType] + row.docId)">{{ row.docNo }}</el-link></template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="200" />
          <el-table-column :label="isSupplierSt ? '应付' : '应收'" width="130" align="right">
            <template #default="{ row }">{{ Number(row.debit) === 0 ? '' : formatAmount(row.debit) }}</template>
          </el-table-column>
          <el-table-column :label="isSupplierSt ? '付款' : '收款'" width="130" align="right">
            <template #default="{ row }">{{ Number(row.credit) === 0 ? '' : formatAmount(row.credit) }}</template>
          </el-table-column>
          <el-table-column label="余额" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.balance) }}</template></el-table-column>
        </el-table>
      </ErpPanel>
    </template>

    <el-dialog v-model="docsDialog" :title="`账龄明细 ${docsTitle}`" width="880px">
      <el-table :data="docs" max-height="480">
        <el-table-column label="单号" width="150">
          <template #default="{ row }"><el-link type="primary" underline="never" @click="openDoc(row as AgingDoc)">{{ row.docNo }}</el-link></template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源" width="140" />
        <el-table-column prop="bizDate" label="业务日期" width="100" />
        <el-table-column prop="dueDate" label="到期日" width="100" />
        <el-table-column label="逾期天数" width="90" align="right"><template #default="{ row }"><span :class="{ red: row.overdueDays > 0 }">{{ row.overdueDays }}</span></template></el-table-column>
        <el-table-column label="账龄段" width="90"><template #default="{ row }">{{ BUCKET_LABEL[row.bucket] }}</template></el-table-column>
        <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
        <el-table-column label="未核销" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.openAmount) }}</template></el-table-column>
      </el-table>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: var(--erp-space-2); flex-wrap: wrap; }
.label { color: var(--erp-color-text-secondary); }
.partner { width: 260px; }
.cur { width: 110px; }
.date { width: 150px; }
.range { width: 260px; }
.grow { flex: 1; }
.red { color: var(--erp-color-error); }
.st-head { padding: var(--erp-space-3); }
</style>
