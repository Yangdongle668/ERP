<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useBaseDataStore } from '@/stores/baseData'
import { today } from '@/utils/format'
import {
  RATE_TYPE_OPTIONS, currencyApi, rateQueryParams,
  type CurrencyRow, type CurrencySave, type RateQuery, type RateRow, type RateSave
} from '../api/currency'

defineOptions({ name: 'SystemCurrencyPage' })

/** 币别与汇率（01-07）：上方币别卡片，下方汇率列表 */
const baseData = useBaseDataStore()
const currencies = ref<CurrencyRow[]>([])
const curLoading = ref(false)
const baseCode = computed(() => currencies.value.find((c) => c.base)?.code)
const foreign = computed(() => currencies.value.filter((c) => !c.base && c.status === 'ENABLED'))

const curColumns: TableColumn<CurrencyRow>[] = [
  { prop: 'code', label: '币别代码', width: 140, slot: true },
  { prop: 'name', label: '名称', width: 110 },
  { prop: 'nameEn', label: '英文名称', minWidth: 160 },
  { prop: 'symbol', label: '符号', width: 70 },
  { prop: 'amountPrecision', label: '金额精度', width: 90, align: 'right' },
  { prop: 'sort', label: '排序', width: 70, align: 'right' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS }
]

async function loadCurrencies() {
  curLoading.value = true
  try {
    currencies.value = await currencyApi.list()
  } finally {
    curLoading.value = false
  }
}

function currenciesChanged() {
  loadCurrencies()
  baseData.loadCurrencies(true)
}

// ---------- 币别编辑 ----------
const curVisible = ref(false)
const curEditing = ref<CurrencyRow>()
const curFormRef = ref<FormInstance>()
const curForm = ref<CurrencySave>({ code: '', name: '', amountPrecision: 2, sort: 10 })
const curRules: FormRules = {
  code: [{ required: true, message: '请输入币别代码', trigger: 'blur' }, { pattern: /^[A-Z]{3}$/, message: '币别代码为 3 位大写字母（ISO 4217）', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function openCurrency(c?: CurrencyRow) {
  curEditing.value = c
  curForm.value = c
    ? { code: c.code, name: c.name, nameEn: c.nameEn, symbol: c.symbol, amountPrecision: c.amountPrecision, sort: c.sort, version: c.version }
    : { code: '', name: '', amountPrecision: 2, sort: currencies.value.reduce((m, x) => Math.max(m, x.sort), 0) + 10 }
  curVisible.value = true
  curFormRef.value?.clearValidate()
}

async function saveCurrency() {
  if (!(await curFormRef.value?.validate().catch(() => false))) return
  if (curEditing.value) await currencyApi.update(curEditing.value.id, curForm.value)
  else await currencyApi.create(curForm.value)
  ElMessage.success('保存成功')
  curVisible.value = false
  currenciesChanged()
}

async function currencyAction(c: CurrencyRow, action: 'enable' | 'disable' | 'setBase') {
  if (action === 'disable') await ElMessageBox.confirm(`停用后币别「${c.code}」不能在新单据中使用。确定停用吗？`, '提示', { type: 'warning' })
  if (action === 'setBase') {
    await ElMessageBox.confirm(`确定将本位币改为「${c.code} ${c.name}」吗？已有业务数据后不允许修改本位币。`, '设为本位币', { type: 'warning' })
  }
  await currencyApi[action](c.id)
  ElMessage.success('操作成功')
  currenciesChanged()
}

const asCur = (r: unknown) => r as CurrencyRow

// ---------- 汇率列表 ----------
const importRef = ref<{ open: () => void }>()
const { query, list, total, loading, load, search, reset } = useListPage<Omit<RateQuery, 'pageNo' | 'pageSize'>, RateRow>({
  api: (q) => currencyApi.rates(q as RateQuery),
  defaultQuery: { rateType: 'DAILY' }
})

const rateFields: SearchField[] = [
  { prop: 'currency', label: '币别', type: 'currency' },
  { prop: 'rateType', label: '汇率类型', type: 'select', options: RATE_TYPE_OPTIONS },
  { prop: 'dateRange', label: '生效日期', type: 'daterange' }
]

const rateColumns: TableColumn<RateRow>[] = [
  { prop: 'currency', label: '币别', width: 80 },
  { prop: 'rateType', label: '汇率类型', width: 100, type: 'enum', options: RATE_TYPE_OPTIONS },
  { prop: 'effectiveDate', label: '生效日期', width: 110, type: 'date', sortable: true },
  { prop: 'rate', label: '汇率', width: 130, type: 'rate', slot: true },
  { prop: 'source', label: '来源', width: 80, type: 'enum', options: [{ value: 'MANUAL', label: '手工' }, { value: 'IMPORT', label: '导入' }] },
  { prop: 'remark', label: '备注', minWidth: 160 },
  { prop: 'updatedByName', label: '维护人', width: 100 },
  { prop: 'updatedAt', label: '维护时间', width: 150, type: 'datetime' }
]

function onSort(s: { prop?: string; order?: string }) {
  query.sortOrder = s.order
  search()
}

// ---------- 汇率编辑 ----------
const rateVisible = ref(false)
const rateEditing = ref<RateRow>()
const rateForm = ref<RateSave>({ currency: '', rateType: 'DAILY', effectiveDate: today(), rate: '' })
const lastRate = ref<{ rate: string; effectiveDate: string }>()

const rateChange = computed(() => {
  const prev = Number(lastRate.value?.rate)
  const cur = Number(rateForm.value.rate)
  if (!prev || !cur || rateEditing.value) return 0
  return Math.abs(cur - prev) / prev
})

async function refreshLastRate() {
  lastRate.value = undefined
  const f = rateForm.value
  if (!f.currency || !f.effectiveDate || rateEditing.value) return
  lastRate.value = await currencyApi.lookup(f.currency, f.effectiveDate, f.rateType).catch(() => undefined)
  if (lastRate.value && !f.rate) f.rate = lastRate.value.rate
}

watch(() => [rateForm.value.currency, rateForm.value.rateType, rateForm.value.effectiveDate], () => {
  if (rateVisible.value) refreshLastRate()
})

function openRate(r?: RateRow) {
  rateEditing.value = r
  rateForm.value = r
    ? { currency: r.currency, rateType: r.rateType, effectiveDate: r.effectiveDate, rate: r.rate, remark: r.remark, version: r.version }
    : { currency: (query.currency as string) ?? '', rateType: (query.rateType as 'DAILY' | 'MONTH_END') ?? 'DAILY', effectiveDate: today(), rate: '' }
  rateVisible.value = true
  refreshLastRate()
}

async function saveRate() {
  const f = rateForm.value
  if (!f.currency || !f.effectiveDate || !(Number(f.rate) > 0)) {
    ElMessage.warning('请填写币别、生效日期和大于 0 的汇率')
    return
  }
  if (rateEditing.value) await currencyApi.updateRate(rateEditing.value.id, f)
  else await currencyApi.createRate(f)
  ElMessage.success('保存成功')
  rateVisible.value = false
  load()
}

async function removeRate(r: RateRow) {
  await currencyApi.removeRate(r.id)
  ElMessage.success('删除成功')
  load()
}

const asRate = (r: unknown) => r as RateRow

// ---------- 批量录入 ----------
const batchVisible = ref(false)
const batch = reactive({ rateType: 'DAILY', effectiveDate: today(), lines: [] as { currency: string; name: string; rate?: string; last?: string }[] })

async function openBatch() {
  batch.effectiveDate = today()
  batch.lines = foreign.value.map((c) => ({ currency: c.code, name: c.name }))
  batchVisible.value = true
  await Promise.all(batch.lines.map(async (l) => {
    const r = await currencyApi.lookup(l.currency, batch.effectiveDate, batch.rateType).catch(() => undefined)
    l.last = r?.rate
  }))
}

async function saveBatch() {
  const lines = batch.lines.filter((l) => Number(l.rate) > 0).map((l) => ({ currency: l.currency, rate: String(l.rate) }))
  if (!lines.length) {
    ElMessage.warning('请至少填写一个币别的汇率')
    return
  }
  const n = await currencyApi.batch({ rateType: batch.rateType, effectiveDate: batch.effectiveDate, lines })
  ElMessage.success(`已保存 ${n} 条汇率`)
  batchVisible.value = false
  load()
}

const changeOf = (l: { rate?: string; last?: string }) => {
  const prev = Number(l.last)
  const cur = Number(l.rate)
  return prev && cur ? Math.abs(cur - prev) / prev : 0
}

onMounted(loadCurrencies)
</script>

<template>
  <ErpPage description="维护币别、本位币与汇率；单据按单据日期取当天或之前最近的日汇率">
    <ErpPanel title="币别">
      <ErpTable :columns="curColumns" :data="currencies" :loading="curLoading" storage-key="system.currency" :actions-width="200" @refresh="loadCurrencies">
        <template #toolbar><el-button v-perm="'system:currency:create'" type="primary" icon="Plus" @click="openCurrency()">新建币别</el-button></template>
        <template #col-code="{ row }">
          <span class="code-cell"><span class="num">{{ asCur(row).code }}</span><ErpBadge v-if="asCur(row).base" type="primary" :dot="false">本位币</ErpBadge></span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'system:currency:update', handler: () => openCurrency(asCur(row)) },
            { label: '设为本位币', permission: 'system:currency:set-base', visible: !asCur(row).base && asCur(row).status === 'ENABLED', handler: () => currencyAction(asCur(row), 'setBase') },
            { label: '停用', permission: 'system:currency:update', visible: asCur(row).status === 'ENABLED' && !asCur(row).base, handler: () => currencyAction(asCur(row), 'disable') },
            { label: '启用', permission: 'system:currency:update', visible: asCur(row).status === 'DISABLED', handler: () => currencyAction(asCur(row), 'enable') }
          ]" />
        </template>
      </ErpTable>
    </ErpPanel>

    <ErpPanel title="汇率" :description="baseCode ? `1 外币 = x ${baseCode}` : undefined">
      <template #filter><ErpSearchForm v-model="query" :fields="rateFields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="rateColumns" :data="list" :loading="loading" storage-key="system.rate" :actions-width="120" empty-text="暂无汇率" @sort-change="onSort" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'system:rate:create'" type="primary" icon="Plus" @click="openRate()">新增汇率</el-button>
          <el-button v-perm="'system:rate:create'" @click="openBatch">批量录入</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Upload" tooltip="导入" permission="system:rate:import" @click="importRef?.open()" />
          <ExportButton url="/system/exchange-rates/export" :params="() => rateQueryParams({ ...(query as RateQuery) })" filename="汇率" permission="system:rate:export" />
        </template>
        <template #col-rate="{ row }"><span class="num">{{ asRate(row).rate }}</span></template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'system:rate:update', handler: () => openRate(asRate(row)) },
            { label: '删除', permission: 'system:rate:delete', danger: true, confirm: `确定删除 ${asRate(row).currency} ${asRate(row).effectiveDate} 的汇率吗？`, handler: () => removeRate(asRate(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'system:rate:create'" @click="openBatch">批量录入今日汇率</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

  <ImportDialog ref="importRef" title="导入汇率" base="/system/exchange-rates" template-name="汇率" @done="load" />

  <el-dialog v-model="curVisible" :title="curEditing ? '编辑币别' : '新建币别'" width="560px" :close-on-click-modal="false" append-to-body>
    <el-form ref="curFormRef" :model="curForm" :rules="curRules" label-width="90px">
      <el-form-item label="币别代码" prop="code">
        <el-input v-model="curForm.code" :disabled="!!curEditing" maxlength="3" @input="curForm.code = String($event).toUpperCase()" />
      </el-form-item>
      <el-form-item label="名称" prop="name"><el-input v-model="curForm.name" maxlength="16" /></el-form-item>
      <el-form-item label="英文名称"><el-input v-model="curForm.nameEn" maxlength="32" /></el-form-item>
      <el-form-item label="符号"><el-input v-model="curForm.symbol" maxlength="4" class="w120" /></el-form-item>
      <el-form-item label="金额精度">
        <el-input-number v-model="curForm.amountPrecision" :min="0" :max="4" controls-position="right" />
        <div class="form-tip">已被业务单据使用后不能修改</div>
      </el-form-item>
      <el-form-item label="排序"><el-input-number v-model="curForm.sort" :min="0" controls-position="right" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="curVisible = false">取消</el-button>
      <el-button type="primary" @click="saveCurrency">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="rateVisible" :title="rateEditing ? '编辑汇率' : '新增汇率'" width="520px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="币别" required><CurrencySelect v-model="rateForm.currency" exclude-base :disabled="!!rateEditing" /></el-form-item>
      <el-form-item label="汇率类型" required>
        <el-radio-group v-model="rateForm.rateType" :disabled="!!rateEditing">
          <el-radio v-for="o in RATE_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="生效日期" required>
        <el-date-picker v-model="rateForm.effectiveDate" type="date" value-format="YYYY-MM-DD" :disabled="!!rateEditing" :clearable="false" />
      </el-form-item>
      <el-form-item label="汇率" required>
        <NumberInput v-model="rateForm.rate" :precision="6" :min="0" />
        <div v-if="lastRate" class="form-tip">上次汇率 {{ lastRate.rate }}（{{ lastRate.effectiveDate }}）</div>
        <el-alert v-if="rateChange > 0.1" type="warning" :closable="false" show-icon class="mt4"
                  :title="`与上次汇率相差 ${(rateChange * 100).toFixed(2)}%，请确认`" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="rateForm.remark" maxlength="200" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="rateVisible = false">取消</el-button>
      <el-button type="primary" @click="saveRate">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="batchVisible" title="批量录入汇率" width="620px" :close-on-click-modal="false" append-to-body>
    <el-form inline>
      <el-form-item label="汇率类型">
        <el-select v-model="batch.rateType" class="w120"><el-option v-for="o in RATE_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
      </el-form-item>
      <el-form-item label="生效日期"><el-date-picker v-model="batch.effectiveDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
    </el-form>
    <el-table :data="batch.lines" max-height="420">
      <el-table-column prop="currency" label="币别" width="80" />
      <el-table-column prop="name" label="名称" width="110" />
      <el-table-column label="上次汇率" width="120" align="right"><template #default="{ row }">{{ row.last ?? '-' }}</template></el-table-column>
      <el-table-column label="汇率" min-width="200">
        <template #default="{ row }">
          <NumberInput v-model="row.rate" :precision="6" :min="0" />
          <div v-if="changeOf(row) > 0.1" class="warn">与上次相差 {{ (changeOf(row) * 100).toFixed(2) }}%，请确认</div>
        </template>
      </el-table-column>
    </el-table>
    <div class="form-tip">未填写汇率的币别不保存；同一币别、类型、日期已有汇率时不能重复录入，请在列表中编辑。</div>
    <template #footer>
      <el-button @click="batchVisible = false">取消</el-button>
      <el-button type="primary" @click="saveBatch">保存</el-button>
    </template>
  </el-dialog>
  </ErpPage>
</template>

<style scoped>
.code-cell { display: inline-flex; align-items: center; gap: 8px; }
.mt4 { margin-top: 4px; }
.warn { color: var(--el-color-warning); font-size: var(--erp-font-size-caption); }
</style>
