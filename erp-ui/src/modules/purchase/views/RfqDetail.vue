<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatPrice, formatQty } from '@/utils/format'
import { RFQ_STATUS, rfqApi, type QuoteCell, type QuoteMatrix, type QuoteRow, type QuoteSave, type RfqDetail } from '../api/rfq'

defineOptions({ name: 'PurRfqDetail' })

/** 询价单详情（需求 07-04 3.3）：报价矩阵录入、比价、定标 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => String(route.params.id))
const d = ref<RfqDetail>()
const matrix = ref<QuoteMatrix>()
const activeTab = ref('quotes')
const canPrice = computed(() => me.hasPermission('pur:price:view'))

async function load() {
  d.value = await rfqApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  if (canPrice.value && d.value.status !== 'DRAFT') await loadMatrix()
}

async function loadMatrix() {
  matrix.value = await rfqApi.quotes(id.value)
  // 定标勾选：已定标的带出结果；比价中默认勾选最低价
  for (const r of matrix.value.rows) {
    const m: Record<string, { checked: boolean; pct?: string }> = {}
    for (const c of r.cells) m[c.supplierId] = { checked: c.awarded || (matrix.value.status === 'COMPARING' && c.lowest && !r.cells.some((x) => x.awarded)), pct: c.awardQtyPct }
    awards[r.rfqLineId] = m
  }
}

const s = computed(() => d.value?.status)
const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'pur:rfq:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除询价单「${d.value?.docNo}」吗？删除后不可恢复。`,
    handler: async () => {
      await rfqApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/purchase/rfq')
    } },
  { key: 'cancel', label: '取消询价', permission: 'pur:rfq:update', visible: () => s.value === 'DRAFT' || s.value === 'QUOTING' || s.value === 'COMPARING',
    reasonRequired: true, reasonTitle: '取消原因', confirm: '取消后询价单不能再录入报价和定标。', handler: (reason) => run(rfqApi.cancel(id.value, reason!), '已取消') },
  { key: 'edit', label: '编辑', permission: 'pur:rfq:update', visible: () => s.value === 'DRAFT', handler: () => router.push(`/purchase/rfq/${id.value}/edit`) },
  { key: 'send', label: '发出询价', type: 'primary', permission: 'pur:rfq:update', visible: () => s.value === 'DRAFT', handler: () => run(rfqApi.send(id.value), '已发出询价') },
  { key: 'end', label: '结束报价', type: 'primary', permission: 'pur:rfq:quote', visible: () => s.value === 'QUOTING',
    confirm: '结束报价后进入比价，不能再录入报价。确定结束吗？', handler: () => run(rfqApi.endQuote(id.value), '已结束报价') },
  { key: 'award', label: '定标', type: 'primary', permission: 'pur:rfq:award', visible: () => s.value === 'COMPARING' && canPrice.value, handler: award }
])

async function run(p: Promise<unknown>, msg: string) {
  await p
  ElMessage.success(msg)
  load()
}

const steps = [
  { status: 'DRAFT', label: '草稿' },
  { status: 'QUOTING', label: '报价中' },
  { status: 'COMPARING', label: '比价中' },
  { status: 'AWARDED', label: '已定标' }
]

// ---------- 报价录入 ----------
const cellOf = (row: QuoteRow, supplierId: string) => row.cells.find((c) => c.supplierId === supplierId)
const quoteVisible = ref(false)
const quoteForm = ref<QuoteSave>({ rfqLineId: '', supplierId: '' })
const quoteTitle = ref('')
const quoteTaxPct = ref<string>()
const savingQuote = ref(false)
function editQuote(row: QuoteRow, supplierId: string, supplierName: string) {
  if (matrix.value?.status !== 'QUOTING' || !me.hasPermission('pur:rfq:quote')) return
  const c = cellOf(row, supplierId)
  quoteForm.value = { rfqLineId: row.rfqLineId, supplierId, price: c?.price, moq: c?.moq, leadTimeDays: c?.leadTimeDays, validUntil: c?.validUntil, remark: c?.remark }
  quoteTaxPct.value = c?.taxRate ? String(Number((Number(c.taxRate) * 100).toFixed(4))) : undefined
  quoteTitle.value = `${row.materialCode} ${row.materialName} / ${supplierName}`
  quoteVisible.value = true
}
async function saveQuote(clear = false) {
  savingQuote.value = true
  try {
    const q = { ...quoteForm.value, price: clear ? undefined : quoteForm.value.price,
      taxRate: quoteTaxPct.value ? String(Number((Number(quoteTaxPct.value) / 100).toFixed(6))) : undefined }
    const r = await rfqApi.saveQuotes(id.value, [q])
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；') })
    quoteVisible.value = false
    await load()
  } finally {
    savingQuote.value = false
  }
}

// ---------- 定标 ----------
const awards = reactive<Record<string, Record<string, { checked: boolean; pct?: string }>>>({})
const awarding = computed(() => matrix.value?.status === 'COMPARING')
async function award() {
  const rows = matrix.value?.rows ?? []
  const lines = rows.map((r) => {
    const chosen = Object.entries(awards[r.rfqLineId] ?? {}).filter(([, v]) => v.checked)
    return { rfqLineId: r.rfqLineId, awards: chosen.map(([supplierId, v]) => ({ supplierId, pct: chosen.length > 1 ? v.pct : undefined })) }
  })
  const r = await rfqApi.award(id.value, lines)
  ElMessage.success(`已定标，生成 ${r.adjustIds.length} 张草稿调价单，请检查后提交审批`)
  await load()
  activeTab.value = 'adjusts'
}

const pctText = (v?: string) => (v === undefined || v === null ? '' : `${Number(v) > 0 ? '+' : ''}${(Number(v) * 100).toFixed(2)}%`)
const asRow = (r: unknown) => r as QuoteRow
const cell = (r: unknown, sid: string): QuoteCell | undefined => cellOf(r as QuoteRow, sid)

watch(activeTab, (t) => {
  if (t === 'quotes' && !matrix.value && canPrice.value && d.value && d.value.status !== 'DRAFT') loadMatrix()
})

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d ? `${d.docNo} ${d.title}` : '询价单'" :status="d?.status" :status-map="RFQ_STATUS" :actions="actions" @back="router.push('/purchase/rfq')" />
    </template>

    <template v-if="d">
      <ErpPanel>
        <DocSteps :steps="steps" :current="d.status" :terminal="{ CANCELED: `已取消：${d.cancelReason ?? ''}` }" />
        <el-descriptions :column="4" class="head">
          <el-descriptions-item label="币别">{{ d.currency }}</el-descriptions-item>
          <el-descriptions-item label="截止日期">{{ d.quoteDeadline }}</el-descriptions-item>
          <el-descriptions-item label="采购员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="4">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="报价与比价" name="quotes">
            <ErpEmpty v-if="d.status === 'DRAFT'" compact description="发出询价后录入报价" />
            <ErpEmpty v-else-if="!canPrice" compact description="没有查看采购价格的权限" />
            <template v-else-if="matrix">
              <p class="tab-tip">
                <template v-if="matrix.status === 'QUOTING'">点击单元格录入报价（单价为不含税价）；每行最低价以绿色标记，括号内为与当前有效价的差异。</template>
                <template v-else-if="awarding">勾选每个物料的中标供应商；多家中标时填写份额，合计必须为 100%。</template>
              </p>
              <el-table :data="matrix.rows" border class="matrix">
                <el-table-column label="物料" min-width="220" fixed="left">
                  <template #default="{ row }">
                    <div>{{ asRow(row).materialCode }} {{ asRow(row).materialName }}</div>
                    <div class="text-muted">数量 {{ formatQty(asRow(row).qty) }} {{ asRow(row).baseUom }} · 现价 {{ asRow(row).currentPrice ? formatPrice(asRow(row).currentPrice) : '-' }}
                      <span v-if="asRow(row).diffPct" :class="Number(asRow(row).diffPct) < 0 ? 'text-success' : 'text-danger'">（{{ pctText(asRow(row).diffPct) }}）</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column v-for="sp in matrix.suppliers" :key="sp.supplierId" :label="sp.supplierName" min-width="170">
                  <template #default="{ row }">
                    <div :class="['quote-cell', { editable: matrix.status === 'QUOTING' }]" @click="editQuote(asRow(row), sp.supplierId, sp.supplierName)">
                      <template v-if="cell(row, sp.supplierId)?.price">
                        <span :class="['num', { 'text-success': cell(row, sp.supplierId)?.lowest }]">{{ formatPrice(cell(row, sp.supplierId)?.price) }}</span>
                        <span class="text-muted"> / {{ cell(row, sp.supplierId)?.leadTimeDays ?? '-' }} 天</span>
                        <ErpBadge v-if="cell(row, sp.supplierId)?.lowest" type="success" :dot="false">最低</ErpBadge>
                        <ErpBadge v-if="cell(row, sp.supplierId)?.awarded" type="primary" :dot="false">中标 {{ cell(row, sp.supplierId)?.awardQtyPct }}%</ErpBadge>
                      </template>
                      <span v-else class="text-muted">{{ matrix.status === 'QUOTING' ? '录入' : '未报价' }}</span>
                    </div>
                    <div v-if="awarding && cell(row, sp.supplierId)?.price && awards[asRow(row).rfqLineId]?.[sp.supplierId]" class="award">
                      <el-checkbox v-model="awards[asRow(row).rfqLineId][sp.supplierId].checked">中标</el-checkbox>
                      <NumberInput v-if="awards[asRow(row).rfqLineId][sp.supplierId].checked" v-model="awards[asRow(row).rfqLineId][sp.supplierId].pct"
                                   :precision="2" trim-zeros placeholder="份额%" class="pct" />
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div class="totals">
                <span class="text-muted">按供应商汇总（全部向该供应商采购）：</span>
                <span v-for="t in matrix.totals" :key="t.supplierId" class="total">
                  {{ t.supplierName }} <strong class="num">{{ t.totalAmount ? `${matrix.currency} ${formatAmount(t.totalAmount)}` : '-' }}</strong>
                  <span v-if="!t.complete" class="text-warning">（未全部报价）</span>
                </span>
              </div>
            </template>
            <el-skeleton v-else :rows="4" animated />
          </el-tab-pane>

          <el-tab-pane :label="`询价物料(${d.lines.length})`" name="lines">
            <el-table :data="d.lines">
              <el-table-column prop="lineNo" label="行" width="50" />
              <el-table-column prop="materialCode" label="物料编码" width="130" />
              <el-table-column prop="materialName" label="名称" min-width="160" />
              <el-table-column prop="materialSpec" label="规格" min-width="160" />
              <el-table-column prop="baseUom" label="单位" width="60" />
              <el-table-column label="数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qty) }}</template></el-table-column>
              <el-table-column prop="requiredDate" label="需求日期" width="110" />
              <el-table-column prop="remark" label="备注" min-width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`询价供应商(${d.suppliers.length})`" name="suppliers">
            <el-table :data="d.suppliers">
              <el-table-column prop="supplierCode" label="编码" width="130" />
              <el-table-column prop="supplierName" label="供应商" min-width="180" />
              <el-table-column prop="sentAt" label="发出时间" width="170" />
              <el-table-column label="已报价" width="90"><template #default="{ row }">{{ row.quoted ? '是' : '否' }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="d.adjustIds.length" :label="`调价单(${d.adjustIds.length})`" name="adjusts">
            <div class="adjusts">
              <el-button v-for="(a, i) in d.adjustIds" :key="a" link type="primary" @click="router.push(`/purchase/price/adjust/${a}`)">调价单 {{ i + 1 }}</el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="PUR_RFQ" :biz-id="id" :status-map="RFQ_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="PUR_RFQ" :biz-id="id" :editable="d.status === 'DRAFT'" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="quoteVisible" :title="`报价 - ${quoteTitle}`" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="单价（不含税）"><PriceInput v-model="quoteForm.price" /></el-form-item>
        <el-form-item label="税率(%)"><NumberInput v-model="quoteTaxPct" :precision="2" trim-zeros placeholder="不填取供应商默认税率" /></el-form-item>
        <el-form-item label="MOQ"><NumberInput v-model="quoteForm.moq" :precision="4" trim-zeros /></el-form-item>
        <el-form-item label="交期(天)"><el-input-number v-model="quoteForm.leadTimeDays" :min="0" :max="999" :precision="0" controls-position="right" /></el-form-item>
        <el-form-item label="有效期至"><el-date-picker v-model="quoteForm.validUntil" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="quoteForm.remark" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quoteVisible = false">取消</el-button>
        <el-button :loading="savingQuote" @click="saveQuote(true)">清除报价</el-button>
        <el-button type="primary" :loading="savingQuote" :disabled="!(Number(quoteForm.price) > 0)" @click="saveQuote()">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.head { margin-top: var(--erp-space-4); }
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.tab-tip { margin: 0 0 var(--erp-space-3); color: var(--erp-color-text-secondary); }
.quote-cell { display: flex; flex-wrap: wrap; align-items: center; gap: var(--erp-space-1); min-height: 24px; }
.quote-cell.editable { cursor: pointer; }
.award { display: flex; align-items: center; gap: var(--erp-space-2); margin-top: var(--erp-space-1); }
.pct { width: 90px; }
.totals { display: flex; flex-wrap: wrap; gap: var(--erp-space-5); margin-top: var(--erp-space-3); }
.adjusts { display: flex; gap: var(--erp-space-3); }
</style>
