<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import { num, round2, verifyApi, type Candidate, type Pick } from '../api/finance'

defineOptions({ name: 'FinVerifyPage' })

/**
 * 核销（需求 12-03 3.3 / 12-05 3.3，专用）：左侧收款、预收、退款、红字应收（付款、预付、红字应付），右侧未核销蓝字应收（应付，按到期日）。
 * 勾选并输入本次金额，贷方（收款 / 预收 / 红字）合计 = 借方（蓝字 / 退款）合计才能确认；汇兑差异按双方汇率估算，以保存结果为准。
 */
const route = useRoute()
const router = useRouter()
const me = useUserStore()
const supplier = computed(() => route.path.startsWith('/finance/payment'))
const partnerId = ref<string | undefined>(typeof route.query[supplier.value ? 'supplierId' : 'customerId'] === 'string'
  ? String(route.query[supplier.value ? 'supplierId' : 'customerId']) : undefined)
const currency = ref<string>(typeof route.query.currency === 'string' ? route.query.currency : '')
type Row = Candidate & { checked: boolean; pick?: string }
const left = ref<Row[]>([])
const right = ref<Row[]>([])
const loading = ref(false)
const saving = ref(false)
const presetLeft = typeof route.query.receiptId === 'string' ? route.query.receiptId : typeof route.query.paymentId === 'string' ? route.query.paymentId : undefined

const KIND_LABEL: Record<string, string> = {
  RECEIPT: '收款', ADVANCE: '预收', REFUND: '退款', RED: '红字', PAYMENT: '付款', PREPAY: '预付', BLUE: ''
}
const DOC_ROUTES: Record<string, string> = { RECEIPT: '/finance/receipt/', RECEIVABLE: '/finance/receivable/', PAYMENT: '/finance/payment/', PAYABLE: '/finance/payable/' }
const isCredit = (r: Candidate) => r.kind !== 'BLUE' && r.kind !== 'REFUND'
const abs = (v?: string) => Math.abs(num(v))

async function load() {
  left.value = []
  right.value = []
  if (!partnerId.value) return
  loading.value = true
  try {
    const c = await verifyApi.candidates(supplier.value ? { supplierId: partnerId.value, currency: currency.value || undefined }
      : { customerId: partnerId.value, currency: currency.value || undefined })
    currency.value = c.currency
    left.value = c.left.map((x) => ({ ...x, checked: x.docId === presetLeft, pick: x.docId === presetLeft ? String(abs(x.available)) : undefined }))
    right.value = c.right.map((x) => ({ ...x, checked: false }))
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(partnerId, () => { currency.value = ''; load() })

function toggle(r: Row) {
  r.pick = r.checked ? String(abs(r.available)) : undefined
}
const all = computed(() => [...left.value, ...right.value].filter((r) => r.checked && num(r.pick) > 0))
const creditSum = computed(() => round2(all.value.filter(isCredit).reduce((s, r) => s + num(r.pick), 0)))
const debitSum = computed(() => round2(all.value.filter((r) => !isCredit(r)).reduce((s, r) => s + num(r.pick), 0)))
const fxEstimate = computed(() => round2(all.value.reduce((s, r) => s + (isCredit(r) ? 1 : -1) * num(r.pick) * num(r.exchangeRate), 0)))
const balanced = computed(() => creditSum.value > 0 && creditSum.value === debitSum.value)
const permission = computed(() => (supplier.value ? 'fin:payment:verify' : 'fin:receipt:verify'))

const picks = (rows: Row[]): Pick[] => rows.filter((r) => r.checked && num(r.pick) > 0).map((r) => ({ docType: r.docType, docId: r.docId, amount: String(num(r.pick)) }))

/** 自动核销：按到期日把勾选的左侧金额依次分配到右侧（预收优先冲对应订单） */
async function auto() {
  const l = picks(left.value)
  if (!l.length) return ElMessage.warning('请先勾选左侧收款 / 付款并输入金额')
  const res = await verifyApi.auto(supplier.value ? 'SUPPLIER' : 'CUSTOMER', { partnerId: partnerId.value!, currency: currency.value, left: l, right: picks(right.value) })
  for (const r of right.value) {
    const m = res.find((p) => p.docId === r.docId && p.docType === r.docType)
    r.checked = !!m
    r.pick = m ? String(m.amount) : undefined
  }
}
async function submit() {
  if (!balanced.value) return ElMessage.warning('核销金额不平衡')
  saving.value = true
  try {
    const req = { partnerId: partnerId.value!, currency: currency.value, left: picks(left.value), right: picks(right.value) }
    const r = supplier.value ? await verifyApi.payment(req) : await verifyApi.receipt(req)
    ElMessage.success(`已核销 ${r.count} 笔，金额 ${formatAmount(r.amount)}，汇兑差异 ${formatAmount(r.fxDiff)}`)
    load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ErpPage :description="supplier ? '预付 / 付款冲应付：左侧预付、未核销付款、红字应付，右侧未付应付' : '左侧收款、预收、退款、红字应收，右侧未核销蓝字应收（按到期日）'">
    <ErpPanel>
      <div class="bar">
        <span class="label">{{ supplier ? '供应商' : '客户' }}</span>
        <SupplierSelect v-if="supplier" v-model="partnerId" class="partner" />
        <CustomerSelect v-else v-model="partnerId" class="partner" />
        <span class="label">币别</span>
        <CurrencySelect v-model="currency" class="cur" @change="load" />
        <span class="grow" />
        <el-button :disabled="!partnerId" @click="auto">自动核销</el-button>
        <el-button v-if="me.hasPermission(permission)" type="primary" :disabled="!balanced" :loading="saving" @click="submit">确认核销</el-button>
      </div>
    </ErpPanel>
    <div class="cols">
      <ErpPanel :title="supplier ? '付款 / 预付 / 红字应付' : '收款 / 预收 / 退款 / 红字应收'" flush>
        <el-table v-loading="loading" :data="left" max-height="560">
          <el-table-column width="44"><template #default="{ row }"><el-checkbox v-model="row.checked" @change="toggle(row as Row)" /></template></el-table-column>
          <el-table-column label="单据" min-width="170">
            <template #default="{ row }">
              <el-tag v-if="KIND_LABEL[row.kind]" size="small" :type="row.kind === 'RED' || row.kind === 'REFUND' ? 'danger' : 'info'">{{ KIND_LABEL[row.kind] }}</el-tag>
              <el-link type="primary" underline="never" class="doc" @click="router.push(DOC_ROUTES[row.docType] + row.docId)">{{ row.docNo }}</el-link>
              <div class="sub">{{ row.docDate }}<template v-if="row.orderNo"> · 订单 {{ row.orderNo }}</template></div>
            </template>
          </el-table-column>
          <el-table-column label="可核销" width="120" align="right">
            <template #default="{ row }"><span :class="{ red: num(row.available) < 0 }">{{ formatAmount(row.available) }}</span></template>
          </el-table-column>
          <el-table-column label="本次" width="140"><template #default="{ row }"><AmountInput v-model="row.pick" :disabled="!row.checked" :max="abs(row.available)" /></template></el-table-column>
        </el-table>
      </ErpPanel>
      <ErpPanel :title="supplier ? '应付（未付）' : '应收（未核销）'" flush>
        <el-table v-loading="loading" :data="right" max-height="560">
          <el-table-column width="44"><template #default="{ row }"><el-checkbox v-model="row.checked" @change="toggle(row as Row)" /></template></el-table-column>
          <el-table-column label="单据" min-width="170">
            <template #default="{ row }">
              <el-link type="primary" underline="never" @click="router.push(DOC_ROUTES[row.docType] + row.docId)">{{ row.docNo }}</el-link>
              <div class="sub">{{ row.sourceNo ?? '' }} {{ row.docDate }}</div>
            </template>
          </el-table-column>
          <el-table-column label="到期日" width="100"><template #default="{ row }">{{ row.dueDate ?? '-' }}</template></el-table-column>
          <el-table-column label="未核销" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.available) }}</template></el-table-column>
          <el-table-column label="本次" width="140"><template #default="{ row }"><AmountInput v-model="row.pick" :disabled="!row.checked" :max="abs(row.available)" /></template></el-table-column>
        </el-table>
      </ErpPanel>
    </div>
    <ErpPanel>
      <div class="sum">
        本次核销：{{ supplier ? '付款' : '收款' }} <b class="num">{{ formatAmount(creditSum) }}</b>
        <span :class="balanced ? 'ok' : 'red'">{{ balanced ? '=' : '≠' }}</span>
        {{ supplier ? '应付' : '应收' }} <b class="num">{{ formatAmount(debitSum) }}</b>
        <span class="gap">预计汇兑差异（本位币）：<b class="num">{{ formatAmount(fxEstimate) }}</b></span>
      </div>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: var(--erp-space-2); }
.label { color: var(--erp-color-text-secondary); }
.partner { width: 280px; }
.cur { width: 120px; }
.grow { flex: 1; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: var(--erp-section-gap); }
.doc { margin-left: var(--erp-space-1); }
.sub { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.red { color: var(--erp-color-error); }
.ok { color: var(--erp-color-success); }
.sum { font-size: var(--erp-font-size-body); }
.sum b { margin: 0 var(--erp-space-1); }
.gap { margin-left: var(--erp-space-6); color: var(--erp-color-text-secondary); }
</style>
