<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { download } from '@/api/http'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { CASH_STATUS, joinList, labelOf, optionsOf, RECEIPT_TYPES, receiptApi, settingApi, type BankImportResult, type BankOption, type ReceiptRow } from '../api/finance'

defineOptions({ name: 'FinReceiptList' })

/** 收款单列表（需求 12-03 3.1，T1）：出纳登记银行收款，确认后由应收会计核销 */
const router = useRouter()
const me = useUserStore()
const iso = (d: Date) => d.toISOString().slice(0, 10)
const monthStart = () => { const d = new Date(); return iso(new Date(d.getFullYear(), d.getMonth(), 1)) }
type Query = { docNo?: string; customerId?: string; receiptTypes?: string[]; bankAccountId?: string; dates?: [string, string]; statuses?: string[]; openOnly?: boolean; orderNo?: string }
const toParams = (q: Query) => {
  const { receiptTypes, statuses, dates, ...rest } = q
  return { ...rest, receiptTypes: joinList(receiptTypes), statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] }
}
const { query, list, total, loading, load, search, reset } = useListPage<Query, ReceiptRow>({
  api: (q) => receiptApi.page(toParams(q) as never),
  defaultQuery: () => ({ dates: [monthStart(), iso(new Date())] }),
  refreshOnActivated: true
})
const banks = ref<BankOption[]>([])
settingApi.bankOptions().then((b) => (banks.value = b)).catch(() => undefined)
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'receiptTypes', label: '类型', type: 'select', options: RECEIPT_TYPES, multiple: true },
  { prop: 'bankAccountId', label: '收款账户', type: 'slot' },
  { prop: 'dates', label: '到账日期', type: 'daterange' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(CASH_STATUS), multiple: true },
  { prop: 'openOnly', label: '未核销', type: 'select', options: [{ value: true, label: '仅未核销完' }] },
  { prop: 'orderNo', label: '订单号', upper: true }
]
const columns: TableColumn<ReceiptRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/finance/receipt/${r.id}`) },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'receiptType', label: '类型', width: 90, formatter: (r) => labelOf(RECEIPT_TYPES, r.receiptType) },
  { prop: 'bankAccountName', label: '账户', width: 120 },
  { prop: 'receiptDate', label: '到账日期', width: 100, type: 'date' },
  { prop: 'currency', label: '币别', width: 60 },
  { prop: 'amount', label: '到账金额', width: 120, type: 'amount', currencyProp: 'currency' },
  { prop: 'bankFee', label: '手续费', width: 90, type: 'amount', currencyProp: 'currency' },
  { prop: 'amountBase', label: '本位币', width: 120, type: 'amount', summary: true },
  { prop: 'allocatedAmount', label: '已核销', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'unallocatedAmount', label: '未核销', width: 110, type: 'amount', currencyProp: 'currency' },
  { prop: 'orderNo', label: '订单号（预收）', width: 140 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: CASH_STATUS },
  { prop: 'bankRefNo', label: '银行流水号', width: 140, hidden: true },
  { prop: 'payerName', label: '付款方', width: 140, hidden: true },
  { prop: 'ownerName', label: '出纳', width: 90, hidden: true }
]

// 导入银行流水
const importDialog = ref(false)
const importBank = ref<string>()
const importFile = ref<File>()
const importing = ref(false)
const importResult = ref<BankImportResult>()
function onFile(f: { raw?: File }) { importFile.value = f.raw }
async function doImport() {
  if (!importBank.value || !importFile.value) return ElMessage.warning('请选择收款账户和文件')
  importing.value = true
  try {
    importResult.value = await receiptApi.importBank(importFile.value, importBank.value)
    if (importResult.value.unmatched.length) {
      ElNotification({ type: 'warning', title: `生成 ${importResult.value.created} 张草稿收款单`, message: `${importResult.value.unmatched.length} 行未匹配客户，请手工登记`, duration: 8000 })
    } else ElMessage.success(`生成 ${importResult.value.created} 张草稿收款单`)
    load()
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <ErpPage description="出纳登记银行到账（收款币别 = 收款账户币别）；确认后才能核销；预收款确认后立即回写订单回款计划">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-bankAccountId>
            <el-select v-model="query.bankAccountId" clearable class="w-full">
              <el-option v-for="b in banks" :key="b.id" :value="b.id" :label="`${b.name}（${b.currency}）`" />
            </el-select>
          </template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="fin.receipt" @refresh="load">
        <template #toolbar>
          <el-button v-if="me.hasPermission('fin:receipt:create')" type="primary" @click="router.push('/finance/receipt/new')">新建收款</el-button>
          <el-button v-if="me.hasPermission('fin:receipt:create')" @click="(importDialog = true), (importResult = undefined)">导入银行流水</el-button>
          <el-button v-if="me.hasPermission('fin:receipt:verify')" @click="router.push('/finance/receipt/verify')">收款核销</el-button>
          <ExportButton url="/finance/receipts/export" :params="() => toParams(query)" permission="fin:receipt:query" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="importDialog" title="导入银行流水" width="720px">
      <el-form label-width="90px">
        <el-form-item label="收款账户" required>
          <el-select v-model="importBank" class="w-full">
            <el-option v-for="b in banks" :key="b.id" :value="b.id" :label="`${b.name}（${b.currency} ${b.accountNo}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload :auto-upload="false" :limit="1" accept=".xlsx" :on-change="onFile">
            <el-button>选择文件</el-button>
            <el-button link type="primary" class="tpl" @click.stop="download('/finance/receipts/import-template', undefined, '银行流水导入模板.xlsx')">下载模板</el-button>
            <template #tip><div class="tip">模板列：日期、金额、币别、付款方名称、流水号；按付款方名称匹配客户生成草稿收款单</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template v-if="importResult?.unmatched.length">
        <div class="tip">以下行未生成收款单，请手工登记：</div>
        <el-table :data="importResult.unmatched" max-height="260">
          <el-table-column prop="rowNo" label="行号" width="60" />
          <el-table-column prop="date" label="日期" width="100" />
          <el-table-column prop="amount" label="金额" width="110" align="right" />
          <el-table-column prop="payerName" label="付款方" min-width="140" />
          <el-table-column prop="bankRefNo" label="流水号" width="130" />
          <el-table-column prop="message" label="原因" width="130" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="importDialog = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.tpl { margin-left: var(--erp-space-3); }
.tip { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); margin: var(--erp-space-1) 0; }
</style>
