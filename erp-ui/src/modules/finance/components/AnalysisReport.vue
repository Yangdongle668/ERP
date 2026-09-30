<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatAmount, formatQty } from '@/utils/format'
import {
  analysisApi, currentPeriod, settingApi, type AccountBalance, type AccountOption, type BankOption, type CashDailyRow, type Ledger, type MarginReport,
  type ProfitLoss
} from '../api/finance'

/**
 * 财务分析报表（需求 12-08 P1）：收付款日报、订单 / 产品 / 客户毛利、月度损益简表、科目余额表、明细账。
 * 成本未计算的期间，毛利类报表成本显示“未计算”并在顶部提示；科目余额、明细账默认只取已过账凭证。
 */
const props = defineProps<{ kind: 'cash' | 'order' | 'product' | 'customer' | 'pl' | 'balance' | 'ledger'; account?: string }>()
const emit = defineEmits<{ ledger: [accountCode: string] }>()
const router = useRouter()
const today = new Date().toISOString().slice(0, 10)
const monthStart = today.slice(0, 8) + '01'
const loading = ref(false)
const dates = ref<[string, string]>([monthStart, today])
const periods = ref<[string, string]>([currentPeriod(), currentPeriod()])
const period = ref(currentPeriod())
const bankId = ref<string>()
const customerId = ref<string>()
const materialId = ref<string>()
const group = ref('LINE')
const includeUnposted = ref(false)
const accountCode = ref<string | undefined>(props.account)
const banks = ref<BankOption[]>([])
const accounts = ref<AccountOption[]>([])

const cash = ref<CashDailyRow[]>([])
const margin = ref<MarginReport>()
const pl = ref<ProfitLoss>()
const balance = ref<AccountBalance>()
const ledger = ref<Ledger>()

const isMargin = computed(() => ['order', 'product', 'customer'].includes(props.kind))
const params = () => {
  switch (props.kind) {
    case 'cash': return { dateFrom: dates.value[0], dateTo: dates.value[1], bankAccountId: bankId.value }
    case 'order': return { dateFrom: dates.value[0], dateTo: dates.value[1], group: group.value, customerId: customerId.value }
    case 'product': return { dateFrom: dates.value[0], dateTo: dates.value[1], materialId: materialId.value }
    case 'customer': return { dateFrom: dates.value[0], dateTo: dates.value[1], customerId: customerId.value }
    case 'pl': return { period: period.value }
    case 'balance': return { periodFrom: periods.value[0], periodTo: periods.value[1], includeUnposted: includeUnposted.value }
    default: return { accountCode: accountCode.value, periodFrom: periods.value[0], periodTo: periods.value[1], includeUnposted: includeUnposted.value }
  }
}
const exportUrl = computed(() => ({
  cash: '/finance/reports/cash-daily/export', order: '/finance/reports/order-margin/export', product: '/finance/reports/product-margin/export',
  customer: '/finance/reports/customer-margin/export', pl: '/finance/reports/profit-loss/export', balance: '/finance/reports/account-balance/export',
  ledger: '/finance/reports/ledger/export'
})[props.kind])

async function load() {
  loading.value = true
  try {
    const k = props.kind
    if (k === 'cash') cash.value = (await analysisApi.cashDaily(params() as never)).rows
    else if (k === 'order' || k === 'product' || k === 'customer') margin.value = await analysisApi.margin(k, params())
    else if (k === 'pl') pl.value = await analysisApi.profitLoss(period.value)
    else if (k === 'balance') balance.value = await analysisApi.accountBalance(params() as never)
    else {
      if (!accountCode.value) return ElMessage.warning('请选择科目')
      ledger.value = await analysisApi.ledger(params() as never)
    }
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  if (props.kind === 'cash') banks.value = await settingApi.bankOptions()
  if (props.kind === 'ledger') accounts.value = await settingApi.accountOptions()
  if (props.kind !== 'ledger' || accountCode.value) load()
})
watch(() => [props.kind, props.account], () => {
  if (props.account) accountCode.value = props.account
  if (props.kind !== 'ledger' || accountCode.value) load()
})
const cost = (v?: string) => (v === undefined || v === null ? '未计算' : formatAmount(v))
function openLedger(code: string) {
  emit('ledger', code)
}
</script>

<template>
  <ErpPanel>
    <div class="bar">
      <template v-if="kind === 'cash' || isMargin">
        <span class="label">日期</span>
        <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" class="range" :clearable="false" />
      </template>
      <template v-if="kind === 'cash'">
        <span class="label">账户</span>
        <el-select v-model="bankId" clearable class="bank" placeholder="全部账户">
          <el-option v-for="b in banks" :key="b.id" :value="b.id" :label="`${b.code} ${b.name}（${b.currency}）`" />
        </el-select>
      </template>
      <template v-if="kind === 'order' || kind === 'customer'">
        <span class="label">客户</span>
        <CustomerSelect v-model="customerId" class="partner" />
      </template>
      <template v-if="kind === 'order'">
        <span class="label">汇总</span>
        <el-radio-group v-model="group">
          <el-radio-button value="LINE">订单行</el-radio-button>
          <el-radio-button value="ORDER">订单</el-radio-button>
          <el-radio-button value="CUSTOMER">客户</el-radio-button>
          <el-radio-button value="SALESMAN">业务员</el-radio-button>
        </el-radio-group>
      </template>
      <template v-if="kind === 'product'">
        <span class="label">产品</span>
        <MaterialSelect v-model="materialId" class="partner" />
      </template>
      <template v-if="kind === 'pl'">
        <span class="label">期间</span>
        <el-input v-model="period" class="period" placeholder="yyyyMM" />
      </template>
      <template v-if="kind === 'balance' || kind === 'ledger'">
        <template v-if="kind === 'ledger'">
          <span class="label">科目</span>
          <el-select v-model="accountCode" filterable class="account" placeholder="编码 / 名称">
            <el-option v-for="a in accounts" :key="a.code" :value="a.code" :label="`${a.code} ${a.fullName}`" />
          </el-select>
        </template>
        <span class="label">期间</span>
        <el-input v-model="periods[0]" class="period" placeholder="yyyyMM" />
        <span class="label">~</span>
        <el-input v-model="periods[1]" class="period" placeholder="yyyyMM" />
        <el-checkbox v-model="includeUnposted">含未过账凭证</el-checkbox>
      </template>
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <span class="grow" />
      <ExportButton :url="exportUrl" :params="params" permission="fin:report:export" />
    </div>
  </ErpPanel>

  <ErpPanel v-if="kind === 'cash'" flush>
    <el-table v-loading="loading" :data="cash">
      <el-table-column prop="date" label="日期" width="110" />
      <el-table-column label="账户" min-width="180"><template #default="{ row }">{{ row.bankCode }} {{ row.bankName }}</template></el-table-column>
      <el-table-column prop="currency" label="币别" width="60" />
      <el-table-column label="期初余额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.opening) }}</template></el-table-column>
      <el-table-column label="收款" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.income) }}（{{ row.receiptCount }}）</template></el-table-column>
      <el-table-column label="付款" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.expense) }}（{{ row.paymentCount }}）</template></el-table-column>
      <el-table-column label="期末余额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.closing) }}</template></el-table-column>
    </el-table>
  </ErpPanel>

  <template v-else-if="isMargin && margin">
    <el-alert v-if="margin.uncalculatedPeriods.length" type="warning" :closable="false" show-icon class="alert"
              :title="`期间 ${margin.uncalculatedPeriods.join('、')} 成本尚未计算，成本与毛利显示“未计算”`" />
    <ErpPanel :title="`收入 ${formatAmount(margin.totalRevenue)}，成本 ${cost(margin.totalCost)}，毛利 ${cost(margin.totalMargin)}${margin.marginRate ? `（${margin.marginRate}%）` : ''}`" flush>
      <el-table v-loading="loading" :data="margin.rows">
        <el-table-column v-if="kind !== 'order' || group !== 'LINE'" prop="rank" label="排名" width="60" align="right" />
        <el-table-column v-if="kind === 'order' && (group === 'LINE' || group === 'ORDER')" prop="orderNo" label="订单号" width="150" />
        <el-table-column v-if="kind === 'customer' || (kind === 'order' && group !== 'SALESMAN')" prop="customerName" label="客户" min-width="160" />
        <el-table-column v-if="kind === 'order' && (group === 'LINE' || group === 'SALESMAN')" prop="salesmanName" label="业务员" width="100" />
        <el-table-column v-if="kind === 'product' || (kind === 'order' && group === 'LINE')" label="产品" min-width="180">
          <template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template>
        </el-table-column>
        <el-table-column v-if="kind === 'product' || (kind === 'order' && group === 'LINE')" label="数量" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.qty) }}</template>
        </el-table-column>
        <el-table-column label="收入" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.revenue) }}</template></el-table-column>
        <el-table-column label="成本" width="130" align="right">
          <template #default="{ row }"><span :class="{ muted: row.cost == null }">{{ cost(row.cost) }}</span></template>
        </el-table-column>
        <el-table-column label="毛利" width="130" align="right">
          <template #default="{ row }"><span :class="{ red: Number(row.margin) < 0 }">{{ row.margin == null ? '-' : formatAmount(row.margin) }}</span></template>
        </el-table-column>
        <el-table-column label="毛利率" width="90" align="right"><template #default="{ row }">{{ row.marginRate == null ? '-' : `${row.marginRate}%` }}</template></el-table-column>
      </el-table>
    </ErpPanel>
  </template>

  <template v-else-if="kind === 'pl' && pl">
    <el-alert v-if="pl.uncalculatedPeriods.length" type="warning" :closable="false" show-icon class="alert"
              :title="`期间 ${pl.uncalculatedPeriods.join('、')} 成本尚未计算，营业成本、毛利、利润显示“未计算”`" />
    <ErpPanel :title="`${pl.period} 损益简表（本位币）`" flush>
      <el-table v-loading="loading" :data="pl.items">
        <el-table-column label="项目" min-width="160"><template #default="{ row }"><span :class="{ strong: row.bold }">{{ row.label }}</span></template></el-table-column>
        <el-table-column label="本月" width="160" align="right"><template #default="{ row }"><span :class="{ strong: row.bold }">{{ cost(row.month) }}</span></template></el-table-column>
        <el-table-column label="本年累计" width="160" align="right"><template #default="{ row }"><span :class="{ strong: row.bold }">{{ cost(row.ytd) }}</span></template></el-table-column>
        <el-table-column label="上年同期" width="160" align="right"><template #default="{ row }"><span :class="{ strong: row.bold }">{{ cost(row.lastYear) }}</span></template></el-table-column>
      </el-table>
    </ErpPanel>
  </template>

  <ErpPanel v-else-if="kind === 'balance' && balance" :title="`借方发生 ${formatAmount(balance.totalDebit)}，贷方发生 ${formatAmount(balance.totalCredit)}`" flush>
    <el-table v-loading="loading" :data="balance.rows">
      <el-table-column label="科目" min-width="220">
        <template #default="{ row }">
          <span :style="{ paddingLeft: `calc(var(--erp-space-4) * ${row.level - 1})` }">
            <el-link v-if="row.leaf" type="primary" underline="never" @click="openLedger(row.accountCode)">{{ row.accountCode }} {{ row.accountName }}</el-link>
            <span v-else class="strong">{{ row.accountCode }} {{ row.accountName }}</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="方向" width="60"><template #default="{ row }">{{ row.direction === 'CREDIT' ? '贷' : '借' }}</template></el-table-column>
      <el-table-column label="期初余额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.opening) }}</template></el-table-column>
      <el-table-column label="借方发生" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.debit) }}</template></el-table-column>
      <el-table-column label="贷方发生" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.credit) }}</template></el-table-column>
      <el-table-column label="期末余额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.closing) }}</template></el-table-column>
    </el-table>
    <ErpEmpty v-if="!balance.rows.length" description="所选期间没有已过账凭证" />
  </ErpPanel>

  <ErpPanel v-else-if="kind === 'ledger' && ledger"
            :title="`${ledger.accountCode} ${ledger.accountName ?? ''}：期初 ${formatAmount(ledger.opening)}，借方 ${formatAmount(ledger.debit)}，贷方 ${formatAmount(ledger.credit)}，期末 ${formatAmount(ledger.closing)}`" flush>
    <el-table v-loading="loading" :data="ledger.lines">
      <el-table-column prop="date" label="日期" width="100" />
      <el-table-column label="凭证号" width="160">
        <template #default="{ row }"><el-link type="primary" underline="never" @click="router.push(`/finance/voucher/${row.voucherId}`)">{{ row.voucherNo }}</el-link></template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="200" />
      <el-table-column prop="accountCode" label="科目" width="100" />
      <el-table-column label="借方" width="130" align="right"><template #default="{ row }">{{ Number(row.debit) ? formatAmount(row.debit) : '' }}</template></el-table-column>
      <el-table-column label="贷方" width="130" align="right"><template #default="{ row }">{{ Number(row.credit) ? formatAmount(row.credit) : '' }}</template></el-table-column>
      <el-table-column label="方向" width="60"><template #default="{ row }">{{ row.direction === 'CREDIT' ? '贷' : '借' }}</template></el-table-column>
      <el-table-column label="余额" width="140" align="right"><template #default="{ row }">{{ formatAmount(row.balance) }}</template></el-table-column>
    </el-table>
  </ErpPanel>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: var(--erp-space-2); flex-wrap: wrap; }
.label { color: var(--erp-color-text-secondary); }
.range { width: 260px; }
.bank, .partner { width: 240px; }
.account { width: 260px; }
.period { width: 100px; }
.grow { flex: 1; }
.alert { margin-bottom: var(--erp-section-gap); }
.red { color: var(--erp-color-error); }
.muted { color: var(--erp-color-text-secondary); }
.strong { font-weight: var(--erp-font-weight-medium); }
</style>
