<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { DocAction, TableColumn } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatAmount, formatDateTime, formatPrice, formatQty } from '@/utils/format'
import { FEASIBILITY_OPTIONS, labelOf, pctOf, RFQ_STATUS, rfqApi, rateOf, type CostSheet, type RfqDetail, type RfqLine } from '../api/sales'

defineOptions({ name: 'SalRfqDetail' })

/** RFQ 详情（需求 04-02 3.3～3.5，T5）：分派工程师、可行性评估、成本核算、生成报价 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<RfqDetail>()
const activeTab = ref('lines')

async function load() {
  d.value = await rfqApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
}
const s = computed(() => d.value?.rfqStatus)
const open = computed(() => s.value === 'DRAFT' || s.value === 'EVALUATING' || s.value === 'COSTED')

const actions = computed<DocAction[]>(() => [
  { key: 'delete', label: '删除', permission: 'sales:rfq:delete', visible: () => s.value === 'DRAFT', confirm: `确定删除 RFQ「${d.value?.docNo}」吗？`,
    handler: async () => {
      await rfqApi.remove(id.value)
      ElMessage.success('删除成功')
      tabs.remove([tabKeyOf(route)])
      router.push('/sales/rfq')
    } },
  { key: 'close', label: '关闭', permission: 'sales:rfq:close', visible: () => s.value !== 'CLOSED', reasonRequired: true, reasonTitle: '关闭原因',
    reasonOptions: ['客户取消询价', '无法生产', '价格无竞争力'], handler: async (reason) => {
      await rfqApi.close(id.value, reason!)
      ElMessage.success('已关闭')
      load()
    } },
  { key: 'assign', label: '分派', permission: 'sales:rfq:assign', visible: () => open.value, handler: openAssign },
  { key: 'feasibility', label: '可行性评估', visible: () => open.value && !!d.value?.canEvaluate, handler: openFeasibility },
  { key: 'edit', label: '编辑', permission: 'sales:rfq:update', visible: () => s.value === 'DRAFT' || s.value === 'EVALUATING',
    handler: () => router.push(`/sales/rfq/${id.value}/edit`) },
  { key: 'quote', label: '生成报价单', type: 'primary', permission: 'sales:quotation:create', visible: () => open.value || s.value === 'QUOTED',
    confirm: '按可行的需求行生成草稿报价单（有成本核算的带出建议价），确定吗？', handler: async () => {
      const qid = await rfqApi.toQuotation(id.value)
      ElMessage.success('已生成报价单')
      router.push(`/sales/quotation/${qid}/edit`)
    } }
])

const lineColumns = computed<TableColumn<RfqLine>[]>(() => [
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'customerPartNo', label: '客户料号', width: 120 },
  { prop: 'description', label: '需求描述', minWidth: 200 },
  { prop: 'materialCode', label: '本厂物料', width: 180, formatter: (r) => (r.materialCode ? `${r.materialCode} ${r.materialName ?? ''}` : '-') },
  { prop: 'annualQty', label: '年用量', width: 100, type: 'qty' },
  { prop: 'qtyBreaks', label: '数量阶梯', width: 140 },
  { prop: 'targetPrice', label: '目标价', width: 100, type: 'price' },
  { prop: 'requiredDate', label: '需求日期', width: 110, type: 'date' },
  { prop: 'feasibility', label: '可行性', width: 150, slot: true },
  ...(d.value?.costVisible ? [{ prop: 'costSheets', label: '成本核算', minWidth: 220, slot: true } as TableColumn<RfqLine>] : []),
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
])
const asLine = (r: unknown) => r as RfqLine

// ---------- 分派 ----------
const assignVisible = ref(false)
const assignForm = ref<{ engineerId?: string; costEngineerId?: string }>({})
function openAssign() {
  assignForm.value = { engineerId: d.value?.engineerId, costEngineerId: d.value?.costEngineerId }
  assignVisible.value = true
}
async function saveAssign() {
  await rfqApi.assign(id.value, assignForm.value)
  assignVisible.value = false
  ElMessage.success('已分派，已通知相关人员')
  load()
}

// ---------- 可行性评估 ----------
const feasVisible = ref(false)
const feasRows = ref<{ lineId: string; lineNo?: number; description?: string; feasibility: string; remark?: string; materialId?: string }[]>([])
function openFeasibility() {
  feasRows.value = (d.value?.lines ?? []).map((l) => ({ lineId: l.id!, lineNo: l.lineNo, description: l.description, feasibility: l.feasibility ?? 'PENDING',
    remark: l.feasibilityRemark, materialId: l.materialId }))
  feasVisible.value = true
}
async function saveFeasibility() {
  const bad = feasRows.value.find((r) => r.feasibility === 'NG' && !r.remark?.trim())
  if (bad) return ElMessage.warning(`第 ${bad.lineNo} 行：不可行时请填写说明`)
  await rfqApi.feasibility(id.value, feasRows.value.filter((r) => r.feasibility !== 'PENDING')
    .map((r) => ({ lineId: r.lineId, feasibility: r.feasibility, remark: r.remark?.trim() || undefined, materialId: r.materialId })))
  feasVisible.value = false
  ElMessage.success('评估结果已保存')
  load()
}

// ---------- 成本核算 ----------
const costVisible = ref(false)
const costLine = ref<RfqLine>()
const costForm = ref<{ qty?: string; toolingTotal?: string; toolingQty?: string; packingFreightCost?: string; adminPct?: string; profitPct?: string }>({})
const materialPrices = ref<Record<string, string>>({})
const sheet = ref<CostSheet>()
const calculating = ref(false)
const qtyOptions = computed(() => String(costLine.value?.qtyBreaks ?? '').split(/[,，\s]+/).filter(Boolean))

async function openCost(line: RfqLine) {
  if (!line.materialId) return ElMessage.warning('请先在可行性评估中关联本厂物料')
  costLine.value = line
  sheet.value = undefined
  materialPrices.value = {}
  costForm.value = { qty: qtyOptions.value[0], adminPct: '5', profitPct: '15' }
  costVisible.value = true
  await loadSheet()
}
async function loadSheet() {
  if (!costLine.value?.id || !costForm.value.qty) return
  const sheets = await rfqApi.costSheets(id.value, costLine.value.id)
  const hit = sheets.find((x) => Number(x.qty) === Number(costForm.value.qty))
  if (hit) applySheet(hit)
  else await calc()
}
function applySheet(x: CostSheet) {
  sheet.value = x
  costForm.value = { ...costForm.value, toolingTotal: x.toolingTotal, toolingQty: x.toolingQty, packingFreightCost: x.packingFreightCost,
    adminPct: pctOf(x.adminRate), profitPct: pctOf(x.profitRate) }
  materialPrices.value = Object.fromEntries(x.materials.filter((m) => m.source === 'MANUAL').map((m) => [m.componentId, m.unitPrice]))
}
function costReq() {
  const f = costForm.value
  return { qty: f.qty!, materialPrices: materialPrices.value, toolingTotal: f.toolingTotal || undefined, toolingQty: f.toolingQty || undefined,
    packingFreightCost: f.packingFreightCost || undefined, adminRate: rateOf(f.adminPct), profitRate: rateOf(f.profitPct) }
}
async function calc() {
  if (!costLine.value?.id || !costForm.value.qty) return
  calculating.value = true
  try {
    sheet.value = await rfqApi.calc(id.value, costLine.value.id, costReq())
  } finally {
    calculating.value = false
  }
}
async function saveCost() {
  if (!costLine.value?.id) return
  calculating.value = true
  try {
    sheet.value = await rfqApi.saveCost(id.value, costLine.value.id, costReq())
    ElMessage.success('成本核算已保存')
    load()
  } finally {
    calculating.value = false
  }
}
function setMaterialPrice(componentId: string, v?: string) {
  if (v) materialPrices.value[componentId] = v
  else delete materialPrices.value[componentId]
}
const SOURCE: Record<string, string> = { STANDARD: '标准成本', PURCHASE: '最新采购价', MANUAL: '手工', NONE: '无价格' }

onMounted(load)
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? 'RFQ'" :status="d?.rfqStatus" :status-map="RFQ_STATUS" :actions="actions" @back="router.push('/sales/rfq')" />
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="客户">{{ d.customerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ d.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="回复截止">{{ d.replyDueDate }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ d.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工程师">{{ d.engineerName || '未分派' }}</el-descriptions-item>
          <el-descriptions-item label="成本核算">{{ d.costEngineerName || '未分派' }}</el-descriptions-item>
          <el-descriptions-item label="币别">{{ d.currency || '-' }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款"><DictTag v-if="d.tradeTerm" type="sys_trade_term" :value="d.tradeTerm" /><span v-else>-</span></el-descriptions-item>
          <el-descriptions-item label="单据日期">{{ d.docDate }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(d.createdAt, true) }}</el-descriptions-item>
          <el-descriptions-item v-if="d.closeReason" label="关闭原因">{{ d.closeReason }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ d.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane :label="`需求明细(${d.lines.length})`" name="lines">
            <ErpTable :columns="lineColumns" :data="d.lines" storage-key="sal.rfq-lines">
              <template #col-feasibility="{ row }">
                <span :class="{ 'text-success': asLine(row).feasibility === 'OK', 'text-danger': asLine(row).feasibility === 'NG', 'text-muted': asLine(row).feasibility === 'PENDING' }">
                  {{ labelOf(FEASIBILITY_OPTIONS, asLine(row).feasibility) }}
                </span>
                <div v-if="asLine(row).feasibilityRemark" class="sub">{{ asLine(row).feasibilityRemark }}</div>
              </template>
              <template #col-costSheets="{ row }">
                <div v-for="c in asLine(row).costSheets ?? []" :key="c.id" class="sub">
                  {{ formatQty(c.qty) }}：成本 {{ formatPrice(c.totalCost) }}，建议价 {{ formatPrice(c.suggestedPriceCur ?? c.suggestedPrice) }}
                </div>
                <el-button v-if="d.canCost && open && asLine(row).feasibility !== 'NG'" link type="primary" @click="openCost(asLine(row))">核算</el-button>
              </template>
            </ErpTable>
          </el-tab-pane>
          <el-tab-pane :label="`关联单据(${d.related.length})`" name="related"><RelatedDocs :docs="d.related" /></el-tab-pane>
          <el-tab-pane label="操作日志" name="log" lazy><OperationLogTable biz-type="SAL_RFQ" :biz-id="id" :status-map="RFQ_STATUS" /></el-tab-pane>
          <el-tab-pane label="附件" name="files" lazy><AttachmentPanel biz-type="SAL_RFQ" :biz-id="id" editable /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="6" animated /></ErpPanel>

    <el-dialog v-model="assignVisible" title="分派" width="480px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="评估工程师"><UserSelect v-model="assignForm.engineerId" clearable /></el-form-item>
        <el-form-item label="成本核算"><UserSelect v-model="assignForm.costEngineerId" clearable /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssign">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="feasVisible" title="可行性评估" width="960px" :close-on-click-modal="false" append-to-body>
      <el-table :data="feasRows" max-height="480">
        <el-table-column prop="lineNo" label="行" width="50" />
        <el-table-column prop="description" label="需求描述" min-width="180" />
        <el-table-column label="可行性" width="130">
          <template #default="{ row }">
            <el-select v-model="row.feasibility"><el-option v-for="o in FEASIBILITY_OPTIONS" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="本厂物料" width="220"><template #default="{ row }"><MaterialSelect v-model="row.materialId" /></template></el-table-column>
        <el-table-column label="说明" min-width="200"><template #default="{ row }"><el-input v-model="row.remark" maxlength="512" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="feasVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFeasibility">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="costVisible" :title="`成本核算 - ${costLine?.materialCode ?? ''} ${costLine?.materialName ?? ''}`" width="1100px" :close-on-click-modal="false" append-to-body>
      <el-form label-width="110px" class="cost-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="核算数量">
              <el-select v-model="costForm.qty" @change="loadSheet"><el-option v-for="q in qtyOptions" :key="q" :value="q" :label="formatQty(q)" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="模具费"><AmountInput v-model="costForm.toolingTotal" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="模具分摊数量"><QtyInput v-model="costForm.toolingQty" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="包装运费(单件)"><PriceInput v-model="costForm.packingFreightCost" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="管理费率(%)"><NumberInput v-model="costForm.adminPct" :precision="2" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="利润率(%)"><NumberInput v-model="costForm.profitPct" :precision="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template v-if="sheet">
        <el-descriptions :column="4" border class="summary">
          <el-descriptions-item label="材料">{{ formatPrice(sheet.materialCost) }}</el-descriptions-item>
          <el-descriptions-item label="人工">{{ formatPrice(sheet.laborCost) }}</el-descriptions-item>
          <el-descriptions-item label="制造费用">{{ formatPrice(sheet.overheadCost) }}</el-descriptions-item>
          <el-descriptions-item label="换线">{{ formatPrice(sheet.setupCost) }}</el-descriptions-item>
          <el-descriptions-item label="模具分摊">{{ formatPrice(sheet.toolingCost) }}</el-descriptions-item>
          <el-descriptions-item label="包装运费">{{ formatPrice(sheet.packingFreightCost) }}</el-descriptions-item>
          <el-descriptions-item label="单位成本">{{ formatPrice(sheet.totalCost) }} {{ sheet.baseCurrency }}</el-descriptions-item>
          <el-descriptions-item label="建议售价">
            <strong>{{ formatPrice(sheet.suggestedPrice) }} {{ sheet.baseCurrency }}</strong>
            <span v-if="sheet.suggestedPriceCur && sheet.currency !== sheet.baseCurrency">（{{ formatPrice(sheet.suggestedPriceCur) }} {{ sheet.currency }}）</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="sheet.targetPrice" label="客户目标价">
            {{ formatPrice(sheet.targetPrice) }}
            <span v-if="sheet.targetDiffPct" :class="Number(sheet.targetDiffPct) > 0 ? 'text-danger' : 'text-success'">（差 {{ (Number(sheet.targetDiffPct) * 100).toFixed(2) }}%）</span>
          </el-descriptions-item>
          <el-descriptions-item label="BOM">{{ sheet.bomNo || '无 BOM' }}</el-descriptions-item>
        </el-descriptions>
        <el-tabs>
          <el-tab-pane :label="`材料(${sheet.materials.length})`">
            <el-table :data="sheet.materials" max-height="300">
              <el-table-column prop="code" label="编码" width="130" />
              <el-table-column prop="name" label="名称" min-width="140" />
              <el-table-column prop="spec" label="规格" min-width="120" />
              <el-table-column label="单位用量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer) }}</template></el-table-column>
              <el-table-column label="单价" width="140">
                <template #default="{ row }">
                  <PriceInput :model-value="materialPrices[row.componentId] ?? row.unitPrice" @change="(v) => setMaterialPrice(row.componentId, v)" />
                </template>
              </el-table-column>
              <el-table-column label="来源" width="100"><template #default="{ row }"><span :class="{ 'text-danger': row.source === 'NONE' }">{{ SOURCE[row.source] ?? row.source }}</span></template></el-table-column>
              <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.amount, 6) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`工序(${sheet.operations.length})`">
            <el-table :data="sheet.operations" max-height="300">
              <el-table-column prop="materialCode" label="物料" width="120" />
              <el-table-column prop="seq" label="工序号" width="70" />
              <el-table-column prop="operation" label="工序" min-width="120" />
              <el-table-column prop="workCenterName" label="工作中心" width="120" />
              <el-table-column label="单件工时" width="90" align="right"><template #default="{ row }">{{ formatQty(row.runHours) }}</template></el-table-column>
              <el-table-column label="人工" width="100" align="right"><template #default="{ row }">{{ formatPrice(row.labor) }}</template></el-table-column>
              <el-table-column label="制造费用" width="100" align="right"><template #default="{ row }">{{ formatPrice(row.overhead) }}</template></el-table-column>
              <el-table-column label="换线分摊" width="100" align="right"><template #default="{ row }">{{ formatPrice(row.setup) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer>
        <el-button @click="costVisible = false">关闭</el-button>
        <el-button :loading="calculating" @click="calc">重新计算</el-button>
        <el-button type="primary" :loading="calculating" @click="saveCost">保存核算</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.detail-tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.sub { color: var(--erp-color-text-secondary); font-size: var(--erp-font-size-caption); }
.summary { margin-bottom: var(--erp-space-3); }
.cost-form { margin-bottom: var(--erp-space-2); }
</style>
