<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty } from '@/utils/format'
import { FORECAST_STATUS, forecastApi, num, type ForecastDetail, type ForecastRowResp } from '../api/sales'

defineOptions({ name: 'SalForecastEdit' })

/** 销售预测编辑（需求 04-05 3.2，T4）：客户 × 物料 × 月份矩阵录入，最多 12 个月 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<ForecastDetail>()
const importRef = ref<{ open: () => void }>()

interface Row { customerId?: string; materialId?: string; materialName?: string; baseUom?: string; remark?: string; qty: Record<string, string | undefined> }
interface Form { title: string; startPeriod?: string; endPeriod?: string; remark?: string; rows: Row[] }

function monthOffset(n: number) {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() + n)
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}`
}
const newRow = (): Row => ({ qty: {} })
const form = ref<Form>({ title: '', startPeriod: monthOffset(1), endPeriod: monthOffset(3), rows: [newRow()] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  startPeriod: [{ required: true, message: '请选择开始月份', trigger: 'change' }],
  endPeriod: [{ required: true, message: '请选择结束月份', trigger: 'change' }]
}

/** 期间内的月份（yyyyMM），最多 12 个 */
const periods = computed(() => {
  const s = form.value.startPeriod
  const e = form.value.endPeriod
  if (!s || !e || e < s) return []
  const out: string[] = []
  let y = Number(s.slice(0, 4))
  let m = Number(s.slice(4))
  while (out.length < 12) {
    const p = `${y}${String(m).padStart(2, '0')}`
    if (p > e) break
    out.push(p)
    m += 1
    if (m > 12) {
      m = 1
      y += 1
    }
  }
  return out
})
const fmtPeriod = (p: string) => `${p.slice(0, 4)}-${p.slice(4)}`
const rowTotal = (r: unknown) => periods.value.reduce((s, p) => s + num((r as Row).qty[p]), 0)
const colTotal = (p: string) => form.value.rows.reduce((s, r) => s + num(r.qty[p]), 0)

function onMaterial(row: unknown, m?: MaterialBrief | MaterialBrief[]) {
  const r = row as Row
  const x = Array.isArray(m) ? m[0] : m
  r.materialName = x?.name
  r.baseUom = x?.baseUom
}
function addRow() {
  form.value.rows.push(newRow())
}
function removeRow(i: number) {
  form.value.rows.splice(i, 1)
  if (!form.value.rows.length) addRow()
}
function toRows(rows: ForecastRowResp[]): Row[] {
  return rows.map((r) => ({ customerId: r.customerId, materialId: r.materialId, materialName: r.materialName, baseUom: r.baseUom, remark: r.remark,
    qty: Object.fromEntries(r.cells.map((c) => [c.period, c.qty])) }))
}
async function fromPrevious() {
  if (!id.value) return
  const rows = await forecastApi.previousRows(id.value)
  if (!rows.length) return ElMessage.info('没有上一期已发布的预测')
  const exist = new Set(form.value.rows.filter((r) => r.materialId).map((r) => `${r.customerId ?? ''}-${r.materialId}`))
  const added = toRows(rows).filter((r) => !exist.has(`${r.customerId ?? ''}-${r.materialId}`))
  form.value.rows = [...form.value.rows.filter((r) => r.materialId), ...added]
  ElMessage.success(`已带入 ${added.length} 行（数量按月份对应，期间外的月份不带入）`)
}

async function reload() {
  if (id.value) {
    const d = await forecastApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的预测可以修改，已发布的请“修订”')
      router.replace(`/sales/forecast/${id.value}`)
      return
    }
    detail.value = d
    form.value = { title: d.title, startPeriod: d.startPeriod, endPeriod: d.endPeriod, remark: d.remark, rows: d.rows.length ? toRows(d.rows) : [newRow()] }
    tabs.setTitle(tabKeyOf(route), `编辑预测 ${d.docNo}`)
  }
  guard.markClean()
}
onMounted(reload)

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/sales/forecast/${id.value}` : '/sales/forecast')
}

async function save() {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!periods.value.length) return ElMessage.warning('结束月份不能早于开始月份')
  const rows = form.value.rows.filter((r) => r.materialId)
  const keys = new Set<string>()
  for (const r of rows) {
    const k = `${r.customerId ?? ''}-${r.materialId}`
    if (keys.has(k)) return ElMessage.warning(`物料 ${r.materialName ?? ''} 重复（同一客户同一物料只能一行）`)
    keys.add(k)
  }
  const f = form.value
  const data = {
    title: f.title.trim(), startPeriod: f.startPeriod, endPeriod: f.endPeriod, remark: f.remark?.trim() || undefined, version: detail.value?.version,
    rows: rows.map((r) => ({ customerId: r.customerId, materialId: r.materialId, remark: r.remark?.trim() || undefined,
      cells: periods.value.filter((p) => num(r.qty[p]) > 0).map((p) => ({ period: p, qty: r.qty[p] })) }))
  }
  saving.value = true
  try {
    let fid = id.value
    if (fid) await forecastApi.update(fid, data)
    else fid = await forecastApi.create(data)
    guard.markClean()
    ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/sales/forecast/${fid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑预测 ${detail.value.docNo}` : '新建预测'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="FORECAST_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="开始月份" prop="startPeriod"><el-date-picker v-model="form.startPeriod" type="month" value-format="YYYYMM" :clearable="false" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="结束月份" prop="endPeriod"><el-date-picker v-model="form.endPeriod" type="month" value-format="YYYYMM" :clearable="false" class="w-full" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="预测明细" description="客户可不填（表示不分客户的总预测）；数量为基本单位">
      <template #extra>
        <el-button icon="Plus" @click="addRow">添加行</el-button>
        <el-button v-if="id" @click="fromPrevious">从上期带入</el-button>
        <el-button v-if="id" v-perm="'sales:forecast:update'" icon="Upload" @click="importRef?.open()">导入</el-button>
      </template>
      <el-table :data="form.rows" border max-height="560" show-summary :summary-method="() => ['合计', '', ...periods.map((p) => formatQty(colTotal(p))), formatQty(form.rows.reduce((s, r) => s + rowTotal(r), 0)), '', '']">
        <el-table-column label="客户" width="200" fixed="left"><template #default="{ row }"><CustomerSelect v-model="row.customerId" placeholder="全部客户" /></template></el-table-column>
        <el-table-column label="物料" width="220" fixed="left">
          <template #default="{ row }"><MaterialSelect v-model="row.materialId" @select="(m) => onMaterial(row, m)" /></template>
        </el-table-column>
        <el-table-column v-for="p in periods" :key="p" :label="fmtPeriod(p)" width="120">
          <template #default="{ row }"><QtyInput v-model="row.qty[p]" :uom="row.baseUom" /></template>
        </el-table-column>
        <el-table-column label="合计" width="110" align="right"><template #default="{ row }">{{ formatQty(rowTotal(row)) }}</template></el-table-column>
        <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="60" fixed="right">
          <template #default="{ $index }"><el-button link type="danger" @click="removeRow($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </ErpPanel>

    <ImportDialog v-if="id" ref="importRef" title="导入预测明细" :base="`/sales/forecasts/${id}`" template-name="销售预测" @done="reload" />
  </ErpPage>
</template>
