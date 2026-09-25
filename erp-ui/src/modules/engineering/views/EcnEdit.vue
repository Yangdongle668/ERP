<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty, today } from '@/utils/format'
import { bomApi, type BomLine } from '../api/bom'
import {
  ACTION_OPTIONS, DEPT_ROLE_OPTIONS, ecnApi, ECN_STATUS, HANDLING_OPTIONS, IMPACT_TYPE_OPTIONS, MODE_OPTIONS, URGENCY_OPTIONS,
  type EcnDetail, type EcnImpactRow, type EcnLine, type EcnSave, type EcnTaskRow
} from '../api/ecn'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngEcnEdit' })

/** ECN 编辑（需求 05-05 3.2，T4）：单头、变更明细（含批量替换）、影响分析、执行确认、附件 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<EcnDetail>()

/** 明细行：损耗率按百分数编辑 */
interface Line extends EcnLine { newScrapPct?: string }
interface Form {
  title: string
  ecnType?: string
  reasonType?: string
  reason: string
  urgency: string
  effectiveMode: string
  effectiveDate?: string
  customerId?: string
  lines: Line[]
  impacts: EcnImpactRow[]
  tasks: EcnTaskRow[]
  fileIds: string[]
}
const form = ref<Form>({ title: '', reason: '', urgency: 'NORMAL', effectiveMode: 'IMMEDIATE', lines: [], impacts: [], tasks: [], fileIds: [] })
const guard = useLeaveGuard(() => form.value)

const rules = computed<FormRules>(() => ({
  title: [{ required: true, message: '请填写变更标题', trigger: 'blur' }],
  ecnType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  reasonType: [{ required: true, message: '请选择变更原因', trigger: 'change' }],
  reason: [{ required: true, message: '请填写变更原因说明', trigger: 'blur' }],
  effectiveMode: [{ required: true, message: '请选择生效方式', trigger: 'change' }],
  effectiveDate: form.value.effectiveMode === 'DATE'
    ? [{ required: true, message: '请选择生效日期', trigger: 'change' }, { validator: (_r, v, cb) => (v && v < today() ? cb(new Error('生效日期不能早于今天')) : cb()), trigger: 'change' }]
    : []
}))

// ---------- BOM 选择（只列默认且已审核的版本）与子件 ----------
interface BomOption { id: string; docNo: string; parentCode: string; parentName: string }
const bomOptions = ref<BomOption[]>([])
const bomLines = ref<Record<string, BomLine[]>>({})
const bomSearching = ref(false)
async function searchBom(keyword: string) {
  bomSearching.value = true
  try {
    const page = await bomApi.page({ keyword: keyword || undefined, defaultOnly: true, statuses: 'APPROVED', pageNo: 1, pageSize: 30 })
    const found = page.list.map((b) => ({ id: b.id, docNo: b.docNo, parentCode: b.materialCode, parentName: b.materialName }))
    const known = new Map(bomOptions.value.map((o) => [o.id, o]))
    found.forEach((o) => known.set(o.id, o))
    bomOptions.value = [...known.values()]
  } finally {
    bomSearching.value = false
  }
}
async function ensureBomLines(bomId?: string) {
  if (!bomId || bomLines.value[bomId]) return
  bomLines.value[bomId] = (await bomApi.get(bomId)).lines
}
async function onBom(l: Line) {
  l.oldComponentId = undefined
  l.oldQtyPer = undefined
  l.oldScrapRate = undefined
  await ensureBomLines(l.bomId)
}
function onOld(l: Line) {
  const bl = bomLines.value[l.bomId ?? '']?.find((x) => x.componentId === l.oldComponentId)
  l.oldCode = bl?.componentCode
  l.oldName = bl?.componentName
  l.uom = bl?.uom
  l.oldQtyPer = bl?.qtyPer
  l.oldScrapRate = bl?.scrapRate
  if (l.action === 'CHANGE_QTY' || l.action === 'REPLACE') l.newQtyPer = l.newQtyPer ?? bl?.qtyPer
}
const needOld = (l: Line) => !!l.action && l.action !== 'ADD'
const needNew = (l: Line) => l.action === 'ADD' || l.action === 'REPLACE'
const needQty = (l: Line) => !!l.action && l.action !== 'REMOVE'
const pct = (v?: string) => (v === undefined || v === null ? '' : `${Number((Number(v) * 100).toFixed(4))}%`)
function addLine() {
  form.value.lines.push({ action: 'REPLACE' })
}

// ---------- 批量替换 ----------
const batchVisible = ref(false)
const batch = ref<{ oldId?: string; newId?: string; qty?: string }>({})
async function applyBatch() {
  if (!batch.value.oldId || !batch.value.newId) return ElMessage.warning('请选择原子件和新子件')
  const rows = await ecnApi.batchReplacePreview(batch.value.oldId, batch.value.newId, batch.value.qty || undefined)
  if (!rows.length) return ElMessage.warning('没有默认且已审核的 BOM 使用该原子件')
  rows.forEach((r) => {
    if (!bomOptions.value.some((o) => o.id === r.bomId)) bomOptions.value.push({ id: r.bomId!, docNo: r.bomNo!, parentCode: r.parentCode!, parentName: r.parentName! })
  })
  await Promise.all(rows.map((r) => ensureBomLines(r.bomId)))
  form.value.lines = [...form.value.lines.filter((l) => l.bomId), ...rows.map((r) => ({ ...r, id: undefined, newScrapPct: scrapPct(r.newScrapRate) }))]
  ElMessage.success(`已生成 ${rows.length} 行替换明细`)
  batchVisible.value = false
}
const scrapPct = (v?: string) => (v === undefined || v === null ? undefined : String(Number((Number(v) * 100).toFixed(4))))

// ---------- 执行确认 ----------
function addTask() {
  form.value.tasks.push({ deptRole: 'QUALITY', content: '' })
}

// ---------- 加载 ----------
onMounted(async () => {
  if (id.value) {
    const d = await ecnApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的 ECN 可以修改')
      router.replace(`/engineering/ecn/${id.value}`)
      return
    }
    fill(d)
    tabs.setTitle(tabKeyOf(route), `编辑 ${d.docNo}`)
  } else {
    addLine()
  }
  guard.markClean()
})
function fill(d: EcnDetail) {
  detail.value = d
  bomOptions.value = [...new Map(d.lines.map((l) => [l.bomId!, { id: l.bomId!, docNo: l.bomNo!, parentCode: l.parentCode!, parentName: l.parentName! }])).values()]
  d.lines.forEach((l) => ensureBomLines(l.bomId))
  form.value = {
    title: d.title, ecnType: d.ecnType, reasonType: d.reasonType, reason: d.reason, urgency: d.urgency, effectiveMode: d.effectiveMode,
    effectiveDate: d.effectiveDate, customerId: d.customerId, fileIds: [],
    lines: d.lines.map((l) => ({ ...l, newScrapPct: scrapPct(l.newScrapRate) })),
    impacts: d.impacts.map((i) => ({ ...i })),
    tasks: d.tasks.map((t) => ({ ...t }))
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/engineering/ecn/${id.value}` : '/engineering/ecn')
}

function validateLines(): boolean {
  const rows = form.value.lines.filter((l) => l.bomId || l.oldComponentId || l.newComponentId)
  if (!rows.length) return warn('请至少添加一行变更明细')
  for (const [i, l] of rows.entries()) {
    const n = i + 1
    if (!l.bomId) return warn(`第 ${n} 行：请选择 BOM`)
    if (!l.action) return warn(`第 ${n} 行：请选择变更动作`)
    if (needOld(l) && !l.oldComponentId) return warn(`第 ${n} 行：请选择原子件`)
    if (needNew(l) && !l.newComponentId) return warn(`第 ${n} 行：请选择新子件`)
    if (needQty(l) && !(Number(l.newQtyPer) > 0)) return warn(`第 ${n} 行：请填写大于 0 的新用量`)
  }
  for (const [i, t] of form.value.tasks.entries()) {
    if (!t.content?.trim()) return warn(`执行确认第 ${i + 1} 行：请填写执行内容`)
  }
  return true
}
function warn(msg: string) {
  ElMessage.warning(msg)
  return false
}

function payload(): EcnSave {
  const f = form.value
  return {
    title: f.title.trim(), ecnType: f.ecnType, reasonType: f.reasonType, reason: f.reason.trim(), urgency: f.urgency, effectiveMode: f.effectiveMode,
    effectiveDate: f.effectiveMode === 'DATE' ? f.effectiveDate : undefined, customerId: f.customerId, fileIds: f.fileIds, version: detail.value?.version,
    lines: f.lines.filter((l) => l.bomId).map((l) => ({
      bomId: l.bomId!, action: l.action!, oldComponentId: needOld(l) ? l.oldComponentId : undefined, newComponentId: needNew(l) ? l.newComponentId : undefined,
      newQtyPer: needQty(l) ? l.newQtyPer : undefined,
      newScrapRate: needQty(l) && l.newScrapPct !== undefined && l.newScrapPct !== '' ? String(Number((Number(l.newScrapPct) / 100).toFixed(6))) : undefined,
      positionNo: l.positionNo?.trim() || undefined, remark: l.remark?.trim() || undefined
    })),
    impacts: id.value ? f.impacts.map((i) => ({ id: i.id, handling: i.handling, handlingRemark: i.handlingRemark })) : undefined,
    tasks: id.value ? f.tasks.map((t) => ({ deptRole: t.deptRole, assigneeId: t.assigneeId, content: t.content.trim() })) : undefined
  }
}

/** 保存；返回单据 ID */
async function persist(): Promise<string | undefined> {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!validateLines()) return
  const data = payload()
  if (id.value) {
    await ecnApi.update(id.value, data)
    return id.value
  }
  return ecnApi.create(data)
}

async function save(submit: boolean) {
  if (saving.value) return
  saving.value = true
  try {
    const eid = await persist()
    if (!eid) return
    guard.markClean()
    if (submit) {
      try {
        const r = await ecnApi.submit(eid)
        if (r.keyPartWarning) ElNotification({ type: 'warning', title: '认证关键件', message: `${r.keyPartWarning}（已自动增加“认证评估”执行任务）`, duration: 8000 })
        ElMessage.success(r.status === 'PENDING_APPROVAL' ? '已提交审批' : '提交成功，已审核')
      } catch {
        if (!id.value) router.replace(`/engineering/ecn/${eid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/engineering/ecn/${eid}`)
  } finally {
    saving.value = false
  }
}

/** 分析影响：先保存，再分析并刷新影响与任务 */
const analyzing = ref(false)
async function analyze() {
  if (!id.value) return
  analyzing.value = true
  try {
    const eid = await persist()
    if (!eid) return
    await ecnApi.analyze(eid)
    fill(await ecnApi.get(eid))
    guard.markClean()
    ElMessage.success(form.value.impacts.length ? `分析完成：${form.value.impacts.length} 项影响，请选择处理方式` : '分析完成：没有库存、在途或在制影响')
  } finally {
    analyzing.value = false
  }
}

const title = computed(() => (detail.value ? `编辑 ${detail.value.docNo}` : '新建 ECN'))
const asLine = (r: unknown) => r as Line
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="ECN_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('eng:ecn:submit') && id" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="标题" prop="title"><el-input v-model="form.title" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="变更类型" prop="ecnType"><DictSelect v-model="form.ecnType" type="eng_ecn_type" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="变更原因" prop="reasonType"><DictSelect v-model="form.reasonType" type="eng_ecn_reason" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="紧急程度">
              <el-radio-group v-model="form.urgency"><el-radio v-for="o in URGENCY_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="生效方式" prop="effectiveMode">
              <el-select v-model="form.effectiveMode"><el-option v-for="o in MODE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.effectiveMode === 'DATE'" :xl="8" :span="12">
            <el-form-item label="生效日期" prop="effectiveDate"><el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客户"><CustomerSelect v-model="form.customerId" placeholder="客户要求的变更填写" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="原因说明" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="变更明细">
      <template #extra><span class="text-muted">只能选择默认且已审核的 BOM；审批通过后为每个 BOM 生成新版本</span></template>
      <div class="bar">
        <el-button icon="Plus" @click="addLine">添加明细</el-button>
        <el-button icon="Refresh" @click="batchVisible = true">批量替换</el-button>
      </div>
      <el-table :data="form.lines">
        <el-table-column label="行" width="50" align="right"><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column>
        <el-table-column label="BOM" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.bomId" filterable remote :remote-method="searchBom" :loading="bomSearching" placeholder="父件编码搜索"
                       @focus="!bomOptions.length && searchBom('')" @change="onBom(asLine(row))">
              <el-option v-for="o in bomOptions" :key="o.id" :value="o.id" :label="o.docNo">
                <span class="mono">{{ o.docNo }}</span> <span class="text-muted">{{ o.parentName }}</span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="变更动作" width="120">
          <template #default="{ row }">
            <el-select v-model="row.action"><el-option v-for="o in ACTION_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="原子件" min-width="200">
          <template #default="{ row }">
            <el-select v-if="needOld(asLine(row))" v-model="row.oldComponentId" filterable :disabled="!row.bomId" @change="onOld(asLine(row))">
              <el-option v-for="b in bomLines[row.bomId] ?? []" :key="b.componentId" :value="b.componentId!" :label="`${b.componentCode} ${b.componentName}`" />
            </el-select>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="新子件" min-width="200">
          <template #default="{ row }">
            <MaterialSelect v-if="needNew(asLine(row))" v-model="row.newComponentId" />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="原用量" width="90" align="right"><template #default="{ row }"><span class="num">{{ row.oldQtyPer ? formatQty(row.oldQtyPer) : '' }}</span></template></el-table-column>
        <el-table-column label="新用量" width="110">
          <template #default="{ row }"><NumberInput v-if="needQty(asLine(row))" v-model="row.newQtyPer" :precision="6" trim-zeros /></template>
        </el-table-column>
        <el-table-column label="原损耗" width="80" align="right"><template #default="{ row }"><span class="num">{{ pct(row.oldScrapRate) }}</span></template></el-table-column>
        <el-table-column label="新损耗(%)" width="100">
          <template #default="{ row }"><NumberInput v-if="needQty(asLine(row))" v-model="row.newScrapPct" :precision="2" :max="99.99" trim-zeros /></template>
        </el-table-column>
        <el-table-column label="新位号" min-width="120"><template #default="{ row }"><el-input v-model="row.positionNo" maxlength="1024" /></template></el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="56" align="center">
          <template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.lines.splice($index, 1)" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="请添加变更明细或使用批量替换" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="影响分析">
      <template #extra>
        <el-button type="primary" plain icon="DataAnalysis" :disabled="!id" :loading="analyzing" @click="analyze">分析影响</el-button>
      </template>
      <el-alert v-if="!id" type="info" :closable="false" show-icon title="保存草稿后可以分析影响；提交前必须完成分析并为每一项选择处理方式" />
      <template v-else>
        <el-alert v-if="!detail?.analyzed" type="warning" :closable="false" show-icon title="尚未分析影响（或明细已修改），请点击“分析影响”" class="gap" />
        <el-table :data="form.impacts">
          <el-table-column label="影响类型" width="130"><template #default="{ row }">{{ labelOf(IMPACT_TYPE_OPTIONS, row.impactType) }}</template></el-table-column>
          <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
          <el-table-column prop="docNo" label="相关单据" width="150" />
          <el-table-column label="数量" width="110" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }} {{ row.uom }}</span></template></el-table-column>
          <el-table-column label="处理方式" width="150">
            <template #default="{ row }">
              <el-select v-model="row.handling" placeholder="请选择"><el-option v-for="o in HANDLING_OPTIONS[row.impactType] ?? []" :key="o.value" :value="o.value" :label="o.label" /></el-select>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160"><template #default="{ row }"><el-input v-model="row.handlingRemark" maxlength="256" /></template></el-table-column>
          <template #empty><ErpEmpty compact :description="detail?.analyzed ? '没有库存、在途采购、在制或销售影响' : '尚未分析'" /></template>
        </el-table>
      </template>
    </ErpPanel>

    <ErpPanel v-if="id" title="执行确认">
      <template #extra><span class="text-muted">分析影响后自动生成，可调整负责人和内容；生效后推送到负责人待办</span></template>
      <el-table :data="form.tasks">
        <el-table-column label="执行部门" width="130">
          <template #default="{ row }">
            <el-select v-model="row.deptRole"><el-option v-for="o in DEPT_ROLE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="200"><template #default="{ row }"><UserSelect v-model="row.assigneeId" placeholder="未指定时为发起人" /></template></el-table-column>
        <el-table-column label="执行内容" min-width="260"><template #default="{ row }"><el-input v-model="row.content" maxlength="512" /></template></el-table-column>
        <el-table-column label="" width="56" align="center">
          <template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.tasks.splice($index, 1)" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="分析影响后自动生成" /></template>
      </el-table>
      <el-button class="gap-top" icon="Plus" @click="addTask">添加任务</el-button>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="ENG_ECN" multiple />
      <AttachmentPanel v-else biz-type="ENG_ECN" :biz-id="id" editable />
    </ErpPanel>

    <el-dialog v-model="batchVisible" title="批量替换" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="原子件" required><MaterialSelect v-model="batch.oldId" /></el-form-item>
        <el-form-item label="新子件" required><MaterialSelect v-model="batch.newId" /></el-form-item>
        <el-form-item label="新用量"><NumberInput v-model="batch.qty" :precision="6" trim-zeros placeholder="为空时沿用原用量" /></el-form-item>
      </el-form>
      <div class="form-tip">查出所有默认且已审核 BOM 中使用原子件的行，每个 BOM 生成一行“替换子件”明细</div>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" @click="applyBatch">生成明细</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.bar { display: flex; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
.gap { margin-bottom: var(--erp-space-3); }
.gap-top { margin-top: var(--erp-space-3); }
</style>
