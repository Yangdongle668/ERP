<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import {
  COST_EXCEPTION_TYPES, COST_RUN_STATUS, costApi, currentPeriod, num, type CostCheck, type CostException, type CostExpense, type CostRun, type MaterialCost,
  type OrderCost, type ProductCost
} from '../api/finance'

defineOptions({ name: 'FinCostPage' })

/**
 * 成本计算（需求 12-07，专用）：选择库存已结账、财务未结账的期间 → 按车间录入人工、制费 → 开始计算（月加权平均、按工时分配、逐层卷算、回填出库成本）
 * → 查看异常清单 → 锁定（月结前必须锁定，锁定后发布 CostCalculatedEvent）。页签：产品成本表（可下钻订单成本与材料明细）、物料单价表。
 */
const me = useUserStore()
const period = ref(currentPeriod())
const tab = ref<'calc' | 'product' | 'material'>('calc')
const check = ref<CostCheck>()
const expenses = ref<CostExpense[]>([])
const runs = ref<CostRun[]>([])
const exceptions = ref<CostException[]>([])
const running = ref(false)
const saving = ref(false)

async function loadCalc() {
  const p = period.value
  const [c, e, r, x] = await Promise.all([costApi.check(p), costApi.expenses(p), costApi.runs(p), costApi.exceptions(p)])
  check.value = c
  expenses.value = e
  runs.value = r
  exceptions.value = x
}
async function refresh() {
  if (!/^\d{6}$/.test(period.value)) return ElMessage.warning('期间格式为 yyyyMM')
  if (tab.value === 'calc') await loadCalc()
  else if (tab.value === 'product') await loadProducts()
  else await loadMaterials()
}
onMounted(refresh)

const checkItems = computed(() => {
  const c = check.value
  if (!c) return []
  return [
    { state: c.inventoryClosed ? 'ok' : 'red', label: c.inventoryClosed ? '库存已月结' : '库存期间尚未月结' },
    { state: c.financeClosed ? 'red' : 'ok', label: c.financeClosed ? '财务已结账' : '财务未结账' },
    { state: '', label: `本期有报工的生产订单 ${c.orderCount} 张` },
    { state: c.expenseEntered ? 'ok' : 'warn', label: c.expenseEntered ? `费用已录入（人工 ${formatAmount(c.laborTotal)}，制费 ${formatAmount(c.overheadTotal)}）` : '本期费用未录入' },
    { state: c.locked ? 'ok' : '', label: c.locked ? '已锁定' : '未锁定' }
  ]
})
const expenseTotal = computed(() => expenses.value.reduce((s, e) => s + num(e.laborAmount) + num(e.overheadAmount), 0))
async function saveExpenses() {
  saving.value = true
  try {
    await costApi.saveExpenses(period.value, expenses.value.filter((e) => num(e.laborAmount) || num(e.overheadAmount) || e.remark))
    ElMessage.success('费用已保存')
    await loadCalc()
  } finally {
    saving.value = false
  }
}
async function calculate() {
  running.value = true
  try {
    const r = await costApi.calculate(period.value)
    if (r.status === 'SUCCESS') ElMessage.success(`计算完成：物料 ${r.materialCount}、订单 ${r.orderCount}、异常 ${r.exceptionCount}`)
    else ElMessage.error(r.errorMessage ?? '计算失败')
    await loadCalc()
  } finally {
    running.value = false
  }
}
async function lock(locked: boolean) {
  if (locked) {
    await ElMessageBox.confirm(`锁定 ${period.value} 的成本？锁定后不能再计算。`, '锁定成本', { type: 'warning' })
    await costApi.lock(period.value)
  } else {
    await costApi.unlock(period.value)
  }
  ElMessage.success(locked ? '已锁定' : '已解锁')
  await loadCalc()
}

// 产品成本表 / 物料单价表
const products = ref<ProductCost[]>([])
const materials = ref<MaterialCost[]>([])
const keyword = ref('')
const loading = ref(false)
async function loadProducts() {
  loading.value = true
  try { products.value = await costApi.products(period.value) } finally { loading.value = false }
}
async function loadMaterials() {
  loading.value = true
  try { materials.value = await costApi.materials(period.value, keyword.value || undefined) } finally { loading.value = false }
}
const orders = ref<OrderCost[]>([])
const orderDialog = ref(false)
const orderTitle = ref('')
async function drill(p: ProductCost) {
  orders.value = await costApi.orders(period.value, p.materialId)
  orderTitle.value = `${p.materialCode} ${p.materialName}`
  orderDialog.value = true
}
const swing = (m: MaterialCost) => num(m.prevUnitCost) > 0 && Math.abs(num(m.unitCost) / num(m.prevUnitCost) - 1) > 0.3
</script>

<template>
  <ErpPage description="月末库存月结后按月加权平均计算材料成本，按工时分配人工与制费，完工成本回填生产入库并继续上一层；锁定后才能财务结账">
    <ErpPanel>
      <div class="bar">
        <span class="label">期间</span>
        <el-input v-model="period" class="period" placeholder="yyyyMM" @change="refresh" />
        <el-radio-group v-model="tab" @change="refresh">
          <el-radio-button value="calc">成本计算</el-radio-button>
          <el-radio-button value="product">产品成本表</el-radio-button>
          <el-radio-button value="material">物料单价表</el-radio-button>
        </el-radio-group>
        <span class="grow" />
        <el-button icon="Refresh" @click="refresh">刷新</el-button>
      </div>
    </ErpPanel>

    <template v-if="tab === 'calc' && check">
      <ErpPanel title="状态检查">
        <div class="checks">
          <span v-for="c in checkItems" :key="c.label" :class="c.state">
            <el-icon><CircleCheck v-if="c.state === 'ok'" /><CircleClose v-else-if="c.state === 'red'" /><Warning v-else-if="c.state === 'warn'" /><InfoFilled v-else /></el-icon>
            {{ c.label }}
          </span>
        </div>
        <div v-for="m in check.messages" :key="m" class="msg">{{ m }}</div>
        <div class="actions">
          <el-button v-if="me.hasPermission('fin:cost:calculate')" type="primary" icon="Play" :loading="running" :disabled="!check.canCalculate" @click="calculate">开始计算</el-button>
          <el-button v-if="me.hasPermission('fin:cost:lock') && !check.locked" :disabled="check.lastRun?.status !== 'SUCCESS'" icon="Lock" @click="lock(true)">锁定</el-button>
          <el-button v-if="me.hasPermission('fin:cost:lock') && check.locked" :disabled="check.financeClosed" @click="lock(false)">解锁</el-button>
        </div>
      </ErpPanel>
      <ErpPanel :title="`费用录入（合计 ${formatAmount(expenseTotal)}）`" flush>
        <template #extra>
          <el-button v-if="me.hasPermission('fin:cost:calculate')" :disabled="check.locked" :loading="saving" @click="saveExpenses">保存费用</el-button>
        </template>
        <el-table :data="expenses" max-height="360">
          <el-table-column prop="deptName" label="车间" min-width="160" />
          <el-table-column label="本期工时" width="110" align="right"><template #default="{ row }">{{ formatQty(row.workHours) }}</template></el-table-column>
          <el-table-column label="人工总额" width="160"><template #default="{ row }"><AmountInput v-model="row.laborAmount" :disabled="check.locked" /></template></el-table-column>
          <el-table-column label="制造费用总额" width="160"><template #default="{ row }"><AmountInput v-model="row.overheadAmount" :disabled="check.locked" /></template></el-table-column>
          <el-table-column label="备注" min-width="160"><template #default="{ row }"><el-input v-model="row.remark" :disabled="check.locked" maxlength="200" /></template></el-table-column>
        </el-table>
      </ErpPanel>
      <ErpPanel v-if="runs.length" title="计算记录" flush>
        <el-table :data="runs" max-height="240">
          <el-table-column prop="startedAt" label="开始时间" width="170" />
          <el-table-column prop="finishedAt" label="结束时间" width="170" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" :map="COST_RUN_STATUS" /></template></el-table-column>
          <el-table-column prop="materialCount" label="物料数" width="80" align="right" />
          <el-table-column prop="orderCount" label="订单数" width="80" align="right" />
          <el-table-column label="总成本" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.totalCost) }}</template></el-table-column>
          <el-table-column prop="exceptionCount" label="异常" width="70" align="right" />
          <el-table-column prop="operatorName" label="操作人" width="90" />
          <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
        </el-table>
      </ErpPanel>
      <ErpPanel :title="`异常清单（${exceptions.length}）`" flush>
        <el-table :data="exceptions" max-height="360">
          <el-table-column label="类型" width="130"><template #default="{ row }">{{ COST_EXCEPTION_TYPES[row.type] ?? row.type }}</template></el-table-column>
          <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column prop="prodOrderNo" label="生产订单" width="150" />
          <el-table-column prop="deptName" label="车间" width="120" />
          <el-table-column prop="message" label="说明" min-width="260" />
        </el-table>
        <ErpEmpty v-if="!exceptions.length" description="没有异常" />
      </ErpPanel>
    </template>

    <ErpPanel v-else-if="tab === 'product'" flush>
      <template #extra><ExportButton url="/finance/cost/products/export" :params="() => ({ period })" permission="fin:cost:query" /></template>
      <el-table v-loading="loading" :data="products">
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }"><el-link type="primary" underline="never" @click="drill(row as ProductCost)">{{ row.materialCode }} {{ row.materialName }}</el-link></template>
        </el-table-column>
        <el-table-column label="完工数量" width="110" align="right"><template #default="{ row }">{{ formatQty(row.finishedQty) }}</template></el-table-column>
        <el-table-column label="单位成本" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.unitCost, 4) }}</template></el-table-column>
        <el-table-column label="材料" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.materialCost) }}</template></el-table-column>
        <el-table-column label="人工" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.laborCost) }}</template></el-table-column>
        <el-table-column label="制费" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.overheadCost) }}</template></el-table-column>
        <el-table-column label="完工成本" width="130" align="right"><template #default="{ row }">{{ formatAmount(row.totalCost) }}</template></el-table-column>
        <el-table-column label="标准成本" width="100" align="right"><template #default="{ row }">{{ row.standardCost ? formatAmount(row.standardCost, 4) : '-' }}</template></el-table-column>
        <el-table-column label="差异率" width="90" align="right"><template #default="{ row }">{{ row.diffRate ? `${row.diffRate}%` : '-' }}</template></el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="80" align="right" />
      </el-table>
    </ErpPanel>

    <ErpPanel v-else flush>
      <template #extra>
        <el-input v-model="keyword" placeholder="物料编码 / 名称" clearable class="kw" @change="loadMaterials" />
        <ExportButton url="/finance/cost/materials/export" :params="() => ({ period, keyword: keyword || undefined })" permission="fin:cost:query" />
      </template>
      <el-table v-loading="loading" :data="materials">
        <el-table-column label="物料" min-width="200" fixed><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="期初数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.openingQty) }}</template></el-table-column>
        <el-table-column label="期初金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.openingAmount) }}</template></el-table-column>
        <el-table-column label="入库数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.inQty) }}</template></el-table-column>
        <el-table-column label="入库金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.inAmount) }}</template></el-table-column>
        <el-table-column label="加权单价" width="110" align="right">
          <template #default="{ row }"><span :class="{ warn: swing(row as MaterialCost) }">{{ formatAmount(row.unitCost, 4) }}</span></template>
        </el-table-column>
        <el-table-column label="上期单价" width="100" align="right"><template #default="{ row }">{{ row.prevUnitCost ? formatAmount(row.prevUnitCost, 4) : '-' }}</template></el-table-column>
        <el-table-column label="出库数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.outQty) }}</template></el-table-column>
        <el-table-column label="出库金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.outAmount) }}</template></el-table-column>
        <el-table-column label="期末数量" width="100" align="right">
          <template #default="{ row }"><span :class="{ red: num(row.closingQty) < 0 }">{{ formatQty(row.closingQty) }}</span></template>
        </el-table-column>
        <el-table-column label="期末金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.closingAmount) }}</template></el-table-column>
      </el-table>
    </ErpPanel>

    <el-dialog v-model="orderDialog" :title="`订单成本 ${orderTitle}`" width="960px">
      <div v-for="o in orders" :key="o.prodOrderId" class="order">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="生产订单">{{ o.prodOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="车间">{{ o.deptName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="工时">{{ formatQty(o.workHours) }}</el-descriptions-item>
          <el-descriptions-item label="期初在制">{{ formatAmount(o.openingWip) }}</el-descriptions-item>
          <el-descriptions-item label="本期材料">{{ formatAmount(o.materialCost) }}</el-descriptions-item>
          <el-descriptions-item label="人工 / 制费">{{ formatAmount(o.laborCost) }} / {{ formatAmount(o.overheadCost) }}</el-descriptions-item>
          <el-descriptions-item label="完工">{{ formatQty(o.finishedQty) }} → {{ formatAmount(o.finishedCost) }}</el-descriptions-item>
          <el-descriptions-item label="单位成本 / 期末在制">{{ formatAmount(o.unitCost, 4) }} / {{ formatAmount(o.endingWip) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="o.materials" size="small" class="mats">
          <el-table-column label="材料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column label="领用" width="100" align="right"><template #default="{ row }">{{ formatQty(row.issueQty) }}</template></el-table-column>
          <el-table-column label="退料" width="100" align="right"><template #default="{ row }">{{ formatQty(row.returnQty) }}</template></el-table-column>
          <el-table-column label="单价" width="110" align="right"><template #default="{ row }">{{ formatAmount(row.unitCost, 4) }}</template></el-table-column>
          <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(row.amount) }}</template></el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: var(--erp-space-3); flex-wrap: wrap; }
.label { color: var(--erp-color-text-secondary); }
.period { width: 120px; }
.kw { width: 200px; margin-right: var(--erp-space-2); }
.grow { flex: 1; }
.checks { display: flex; gap: var(--erp-space-6); flex-wrap: wrap; }
.checks span { display: inline-flex; align-items: center; gap: var(--erp-space-1); }
.msg { margin-top: var(--erp-space-2); color: var(--erp-color-text-secondary); }
.actions { margin-top: var(--erp-space-3); display: flex; gap: var(--erp-space-2); }
.ok { color: var(--erp-color-success); }
.warn { color: var(--erp-color-warning); }
.red { color: var(--erp-color-error); }
.order + .order { margin-top: var(--erp-space-4); }
.mats { margin-top: var(--erp-space-2); }
</style>
