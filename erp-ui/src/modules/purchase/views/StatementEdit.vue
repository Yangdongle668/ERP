<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SupplierBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty, today, toDateString } from '@/utils/format'
import { labelOf, num, submitText } from '../api/common'
import { LINE_TYPE_OPTIONS, STATEMENT_STATUS, statementApi, type StatementDetail, type StatementLine, type StatementSave } from '../api/statement'

defineOptions({ name: 'PurStatementEdit' })

/** 对账单编辑（需求 07-09 4.2，T4）：加载可对账明细（货款 / 退货），手工录入加扣款 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const loadingLines = ref(false)
const detail = ref<StatementDetail>()

interface SourceRow extends StatementLine {
  checked: boolean
}
interface AdjustRow {
  remark?: string
  totalAmount?: string
  taxPct?: string
  bizDate?: string
}
interface Form {
  supplierId?: string
  currency?: string
  period: [string, string]
  remark?: string
  fileIds: string[]
}
function lastMonth(): [string, string] {
  const now = new Date()
  return [toDateString(new Date(now.getFullYear(), now.getMonth() - 1, 1)), toDateString(new Date(now.getFullYear(), now.getMonth(), 0))]
}
const form = ref<Form>({ period: lastMonth(), fileIds: [] })
const sources = ref<SourceRow[]>([])
const adjusts = ref<AdjustRow[]>([])
const guard = useLeaveGuard(() => ({ f: form.value, s: sources.value, a: adjusts.value }))
const defaultTaxPct = ref('13')

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  period: [{ required: true, message: '请选择对账区间', trigger: 'change' }]
}

const goods = computed(() => sources.value.filter((l) => l.lineType !== 'RETURN'))
const returns = computed(() => sources.value.filter((l) => l.lineType === 'RETURN'))
const sum = (rows: { totalAmount?: string }[]) => rows.reduce((s, l) => s + num(l.totalAmount), 0)
const goodsTotal = computed(() => sum(goods.value.filter((l) => l.checked)))
const returnTotal = computed(() => sum(returns.value.filter((l) => l.checked)))
const adjustTotal = computed(() => sum(adjusts.value))

function onSupplier(s?: SupplierBrief | SupplierBrief[]) {
  const x = Array.isArray(s) ? s[0] : s
  if (x?.currency) form.value.currency = x.currency
  if (x?.taxRate) defaultTaxPct.value = String(Number((Number(x.taxRate) * 100).toFixed(4)))
  if (!id.value) sources.value = []
}

async function loadCandidates() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loadingLines.value = true
  try {
    const list = await statementApi.candidates(form.value.supplierId!, form.value.period[0], form.value.period[1], form.value.currency)
    // 已在本对账单中的行（编辑时）保留，候选中新增的行追加
    const key = (l: StatementLine) => `${l.sourceType}|${l.sourceLineId}`
    const exist = new Set(sources.value.map(key))
    const added = list.filter((l) => !exist.has(key(l))).map((l) => ({ ...l, checked: true }))
    sources.value = [...sources.value, ...added]
    ElMessage.info(added.length ? `新增 ${added.length} 行可对账明细` : '区间内没有新的可对账明细')
  } finally {
    loadingLines.value = false
  }
}

onMounted(async () => {
  if (id.value) {
    const d = await statementApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的对账单可以修改')
      router.replace(`/purchase/statement/${id.value}`)
      return
    }
    detail.value = d
    form.value = { supplierId: d.supplierId, currency: d.currency, period: [d.periodFrom, d.periodTo], remark: d.remark, fileIds: [] }
    sources.value = d.lines.filter((l) => l.lineType !== 'ADJUST').map((l) => ({ ...l, checked: true }))
    adjusts.value = d.lines.filter((l) => l.lineType === 'ADJUST').map((l) => ({ remark: l.remark, totalAmount: l.totalAmount, bizDate: l.bizDate,
      taxPct: l.taxRate ? String(Number((Number(l.taxRate) * 100).toFixed(4))) : undefined }))
    tabs.setTitle(tabKeyOf(route), `编辑对账单 ${d.docNo}`)
  }
  guard.markClean()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function onKey(e: KeyboardEvent) {
  if (!(e.ctrlKey || e.metaKey)) return
  if (e.key === 's') {
    e.preventDefault()
    save(false)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    save(true)
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/statement/${id.value}` : '/purchase/statement')
}

function payload(): StatementSave {
  const f = form.value
  return {
    supplierId: f.supplierId!, currency: f.currency, periodFrom: f.period[0], periodTo: f.period[1], remark: f.remark?.trim() || undefined, fileIds: f.fileIds,
    version: detail.value?.version,
    lines: [
      ...sources.value.filter((l) => l.checked).map((l) => ({ lineType: l.lineType, sourceType: l.sourceType, sourceLineId: l.sourceLineId, qty: l.qty })),
      ...adjusts.value.map((a) => ({ lineType: 'ADJUST', totalAmount: a.totalAmount, taxRate: a.taxPct ? String(Number((Number(a.taxPct) / 100).toFixed(6))) : undefined,
        bizDate: a.bizDate, remark: a.remark?.trim() }))
    ]
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  const badAdj = adjusts.value.findIndex((a) => !a.remark?.trim() || !num(a.totalAmount))
  if (badAdj >= 0) return ElMessage.warning(`调整第 ${badAdj + 1} 行：请填写说明和金额`)
  const data = payload()
  if (!data.lines.length) return ElMessage.warning('请至少添加一行明细')
  saving.value = true
  try {
    let sid = id.value
    if (sid) await statementApi.update(sid, data)
    else sid = await statementApi.create(data)
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await statementApi.submit(sid)).status))
      } catch {
        if (!id.value) router.replace(`/purchase/statement/${sid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/statement/${sid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑对账单 ${detail.value.docNo}` : '新建对账'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="STATEMENT_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('pur:statement:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="供应商" prop="supplierId"><SupplierSelect v-model="form.supplierId" :disabled="!!id" @select="onSupplier" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" :disabled="!!id" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="对账区间" prop="period"><el-date-picker v-model="form.period" type="daterange" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="summary">
        <el-button type="primary" icon="Refresh" :loading="loadingLines" @click="loadCandidates">加载可对账明细</el-button>
        <span>货款 <strong class="num">{{ formatAmount(goodsTotal) }}</strong></span>
        <span>退货 <strong class="num text-danger">{{ formatAmount(returnTotal) }}</strong></span>
        <span>调整 <strong class="num">{{ formatAmount(adjustTotal) }}</strong></span>
        <span>对账总额 <strong class="num">{{ form.currency }} {{ formatAmount(goodsTotal + returnTotal + adjustTotal) }}</strong></span>
      </div>
    </ErpPanel>

    <ErpPanel :title="`货款（${goods.length}）`">
      <template #extra><span class="text-muted">检验已判定、已入库的合格数量；取消勾选的行本次不对账</span></template>
      <el-table :data="goods" max-height="420">
        <el-table-column width="50" align="center"><template #default="{ row }"><el-checkbox v-model="row.checked" /></template></el-table-column>
        <el-table-column label="类型" width="80"><template #default="{ row }">{{ labelOf(LINE_TYPE_OPTIONS, row.lineType) }}</template></el-table-column>
        <el-table-column prop="sourceNo" label="到货单" width="150" />
        <el-table-column prop="orderNo" label="订单号" width="150" />
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="bizDate" label="入库日期" width="110" />
        <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
        <el-table-column prop="priceInclTax" label="含税单价" width="100" align="right" />
        <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template></el-table-column>
        <template #empty><ErpEmpty compact description="点击“加载可对账明细”" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel :title="`退货（${returns.length}）`">
      <el-table :data="returns" max-height="320">
        <el-table-column width="50" align="center"><template #default="{ row }"><el-checkbox v-model="row.checked" /></template></el-table-column>
        <el-table-column prop="sourceNo" label="退货单" width="150" />
        <el-table-column prop="orderNo" label="订单号" width="150" />
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="bizDate" label="出库日期" width="110" />
        <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="text-danger">{{ formatQty(row.qty) }}</span></template></el-table-column>
        <el-table-column prop="priceInclTax" label="含税单价" width="100" align="right" />
        <el-table-column label="价税合计" width="120" align="right"><template #default="{ row }"><span class="text-danger">{{ formatAmount(row.totalAmount) }}</span></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有退款类退货" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="调整（加扣款）">
      <template #extra>
        <el-button icon="Plus" @click="adjusts.push({ taxPct: defaultTaxPct, bizDate: today() })">添加调整</el-button>
      </template>
      <el-table :data="adjusts" border>
        <el-table-column label="说明" min-width="240"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" placeholder="如：来料不良扣款" /></template></el-table-column>
        <el-table-column label="金额（含税，扣款为负）" width="200"><template #default="{ row }"><AmountInput v-model="row.totalAmount" :currency="form.currency" allow-negative /></template></el-table-column>
        <el-table-column label="税率(%)" width="120"><template #default="{ row }"><NumberInput v-model="row.taxPct" :precision="2" trim-zeros /></template></el-table-column>
        <el-table-column label="日期" width="170"><template #default="{ row }"><el-date-picker v-model="row.bizDate" value-format="YYYY-MM-DD" class="w-full" /></template></el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="adjusts.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有加扣款" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.summary { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-5); margin-top: var(--erp-space-3); }
</style>
