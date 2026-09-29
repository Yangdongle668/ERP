<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DocAction } from '@/components'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatDateTime, formatQty } from '@/utils/format'
import {
  INSP_RESULT, INSP_STATUS, INSPECT_TYPE, IPQC_KIND, LEVELS, SUGGEST_RESULT, basicApi, inspectionApi, num,
  type DefectCodeRow, type DefectRow, type InspectionDetail, type ItemResult
} from '../api/quality'

defineOptions({ name: 'QcInspectionEntry' })

/** 检验录入（需求 10-02 3.2，专用页面）：项目结果、缺陷明细、按 Ac/Re 实时建议结果、判定 / 提交 MRB / 重判 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => String(route.params.id))
const d = ref<InspectionDetail>()
const items = ref<(ItemResult & { values: string[] })[]>([])
const defects = ref<DefectRow[]>([])
const codes = ref<DefectCodeRow[]>([])
const saving = ref(false)
const dirty = ref(false)

const permSeg = computed(() => {
  const t = d.value?.inspectType
  return t === 'RECHECK' ? 'iqc' : (t ?? 'iqc').toLowerCase()
})
const editable = computed(() => d.value?.status === 'PENDING' || d.value?.status === 'INSPECTING')
type ItemRow = ItemResult & { values: string[] }
const isQuant = (i: ItemResult) => i.itemType === 'QUANTITATIVE'
const asItem = (r: unknown) => r as ItemRow
const asDefect = (r: unknown) => r as DefectRow

async function load() {
  d.value = await inspectionApi.get(id.value)
  tabs.setTitle(tabKeyOf(route), d.value.docNo)
  items.value = d.value.items.map((i) => {
    const values = [...(i.measuredValues ?? []).map(String)]
    const n = Math.min(Math.max(i.sampleQty, 1), 50)
    while (isQuant(i) && values.length < n) values.push('')
    return { ...i, values }
  })
  defects.value = d.value.defects.map((x) => ({ ...x }))
  dirty.value = false
}
onMounted(async () => {
  codes.value = await basicApi.enabledDefects()
  await load()
})

const out = (i: ItemResult, v: string) =>
  v !== '' && v !== undefined && ((i.lowerLimit != null && Number(v) < Number(i.lowerLimit)) || (i.upperLimit != null && Number(v) > Number(i.upperLimit)))
function itemNg(i: ItemResult & { values: string[] }) {
  return isQuant(i) ? i.values.filter((v) => out(i, v)).length : num(i.ngCount)
}
/** 从 Excel 粘贴一列数值 */
function paste(i: ItemResult & { values: string[] }, start: number, e: ClipboardEvent) {
  const text = e.clipboardData?.getData('text') ?? ''
  const vals = text.split(/[\r\n\t,;]+/).map((s) => s.trim()).filter((s) => s !== '')
  if (vals.length <= 1) return
  e.preventDefault()
  vals.forEach((v, k) => {
    if (start + k < i.values.length) i.values[start + k] = v
    else if (start + k < i.sampleQty) i.values.push(v)
  })
  dirty.value = true
}

/** 各等级缺陷数：项目不良数合计与缺陷明细合计取较大者 */
const counts = computed(() => {
  const r: Record<string, number> = {}
  for (const lv of ['CR', 'MA', 'MI']) {
    const a = items.value.filter((i) => i.defectLevel === lv).reduce((s, i) => s + itemNg(i), 0)
    const b = defects.value.filter((x) => (x.defectLevel ?? codeLevel(x.defectCode)) === lv).reduce((s, x) => s + num(x.qty), 0)
    r[lv] = Math.max(a, b)
  }
  return r
})
const suggestion = computed(() => {
  const levels = d.value?.sampling?.levels ?? []
  return levels.some((l) => (counts.value[l.level] ?? 0) >= l.re) ? 'FAIL' : 'PASS'
})
const codeLevel = (c?: string) => codes.value.find((x) => x.code === c)?.defaultLevel

function addDefect() {
  defects.value.push({ defectCode: '', qty: 1 })
  dirty.value = true
}
function onCode(x: DefectRow) {
  x.defectLevel = codeLevel(x.defectCode)
  x.defectName = codes.value.find((c) => c.code === x.defectCode)?.name
}

async function save(silent = false) {
  if (!d.value) return
  saving.value = true
  try {
    await inspectionApi.saveResults(id.value, {
      version: d.value.version,
      items: items.value.map((i) => (isQuant(i)
        ? { id: i.id, measuredValues: i.values.filter((v) => v !== ''), remark: i.remark }
        : { id: i.id, ngCount: i.itemResult === undefined && i.ngCount === 0 && !dirty.value ? null : num(i.ngCount), remark: i.remark })),
      defects: defects.value.filter((x) => x.defectCode && num(x.qty) > 0)
    })
    if (!silent) ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 判定 ----------
const judgeVisible = ref(false)
const judge = ref<{ result: string; qualifiedQty?: string; rejectedQty?: string; reason?: string }>({ result: 'QUALIFIED' })
async function openJudge() {
  if (dirty.value) await save(true)
  judge.value = { result: d.value?.mrbSort ? 'SORTED' : suggestion.value === 'PASS' ? 'QUALIFIED' : 'REJECTED' }
  judgeVisible.value = true
}
const sortRest = computed(() => num(d.value?.lotQty) - num(d.value?.presetConcessionQty))
async function doJudge() {
  const j = judge.value
  if (j.result === 'QUALIFIED' && d.value?.suggestedResult === 'FAIL' && !j.reason?.trim()) return ElMessage.warning('建议结果为不合格，请填写让步判定合格的理由')
  if (j.result === 'SORTED' && num(j.qualifiedQty) + num(j.rejectedQty) !== sortRest.value) return ElMessage.warning(`良品 + 不良合计必须等于 ${formatQty(sortRest.value)}`)
  await inspectionApi.judge(id.value, j)
  judgeVisible.value = false
  ElMessage.success('已判定')
  load()
}

const actions = computed<DocAction[]>(() => [
  { key: 'rejudge', label: '重判', permission: 'qc:inspection:rejudge', visible: () => d.value?.status === 'JUDGED' && !d.value.rejudgePending,
    reasonRequired: true, reasonTitle: '重判原因', handler: async (reason) => {
      await inspectionApi.rejudge(id.value, reason!)
      ElMessage.success('已发起重判')
      load()
    } },
  { key: 'mrb', label: '提交 MRB', permission: `qc:${permSeg.value}:judge`, visible: () => editable.value && !d.value?.mrbSort,
    confirm: '将生成 NCR 交 MRB 评审，评审通过后按处置结果自动完成判定。', handler: async () => {
      if (dirty.value) await save(true)
      const ncr = await inspectionApi.toMrb(id.value)
      ElMessage.success('已生成 NCR，请填写处置后提交 MRB')
      router.push(`/quality/ncr/${ncr}`)
    } },
  { key: 'save', label: '保存', permission: `qc:${permSeg.value}:inspect`, visible: () => editable.value, handler: () => save() },
  { key: 'judge', label: '判定', type: 'primary', permission: `qc:${permSeg.value}:judge`, visible: () => editable.value, handler: openJudge }
])
async function leave() {
  if (dirty.value) await ElMessageBox.confirm('录入内容未保存，确定离开吗？', '提示')
  return true
}
const levelText = (lv: string) => {
  const l = d.value?.sampling?.levels.find((x) => x.level === lv)
  return l ? `${lv} ${l.aql} Ac${l.ac}/Re${l.re}` : ''
}
</script>

<template>
  <ErpPage noBreadcrumb>
    <template #header>
      <DocPageHeader :title="d?.docNo ?? '检验单'" :status="d?.status" :status-map="INSP_STATUS" :actions="actions" :before-back="leave"
                     @back="router.back()">
        <template #actions-prefix>
          <PrintButton v-if="d && d.status !== 'CANCELED'" biz-type="QC_INSPECTION" :ids="[id]" :permission="`qc:${permSeg}:query`" label="打印报告" />
        </template>
      </DocPageHeader>
    </template>

    <template v-if="d">
      <ErpPanel>
        <el-descriptions :column="4">
          <el-descriptions-item label="类型">{{ INSPECT_TYPE[d.inspectType] }}<template v-if="d.ipqcKind"> · {{ IPQC_KIND[d.ipqcKind] }}</template></el-descriptions-item>
          <el-descriptions-item label="物料">{{ d.materialCode }} {{ d.materialName }} {{ d.materialSpec ?? '' }}</el-descriptions-item>
          <el-descriptions-item label="批次">{{ d.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="['IQC', 'RECHECK'].includes(d.inspectType) ? '供应商' : '客户'">{{ d.supplierName || d.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="批量">{{ formatQty(d.lotQty) }} {{ d.baseUom }}</el-descriptions-item>
          <el-descriptions-item label="样本量"><strong>{{ d.sampleQty }}</strong>（{{ d.sampling?.text }}）</el-descriptions-item>
          <el-descriptions-item label="检验标准">{{ d.standardCode ? `${d.standardCode} V${d.standardVersion} ${d.standardName ?? ''}` : '未匹配到标准（全检）' }}</el-descriptions-item>
          <el-descriptions-item label="上游单号">{{ d.upstreamNo || '-' }}<template v-if="d.operationSeq"> 工序 {{ d.operationSeq }}</template></el-descriptions-item>
          <el-descriptions-item label="检验员">{{ d.inspectorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始 / 录入">{{ formatDateTime(d.startedAt, true) }} / {{ formatDateTime(d.inspectedAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="NCR">
            <el-link v-if="d.ncrId" type="primary" underline="never" @click="router.push(`/quality/ncr/${d.ncrId}`)">{{ d.ncrNo }}</el-link><span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(d.createdAt, true) }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="d.mrbSort" type="warning" :closable="false" show-icon class="gap"
                  :title="`MRB 处置含挑选：特采 ${formatQty(d.presetConcessionQty)}、不合格 ${formatQty(d.presetRejectedQty)} 已锁定，请录入挑选后的良品和不良数量后按“挑选”判定`" />
        <el-alert v-if="d.rejudgePending" type="info" :closable="false" show-icon class="gap" title="重判审批中" />
      </ErpPanel>

      <ErpPanel title="检验项目">
        <el-table :data="items" border>
          <el-table-column label="#" prop="seq" width="50" />
          <el-table-column label="项目" min-width="120">
            <template #default="{ row }">{{ row.itemName }}<ErpBadge v-if="row.isKey" type="warning" :dot="false" class="key">关键</ErpBadge></template>
          </el-table-column>
          <el-table-column label="规格" min-width="160">
            <template #default="{ row }">
              {{ row.spec ?? '' }}<span v-if="row.lowerLimit != null || row.upperLimit != null" class="muted">
                {{ row.lowerLimit ?? '' }} ~ {{ row.upperLimit ?? '' }} {{ row.unit ?? '' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="等级" prop="defectLevel" width="60" align="center" />
          <el-table-column label="样本" prop="sampleQty" width="60" align="right" />
          <el-table-column label="测量值 / 不良数" min-width="360">
            <template #default="{ row }">
              <div v-if="isQuant(asItem(row))" class="values">
                <el-input v-for="(_, k) in asItem(row).values" :key="k" v-model="asItem(row).values[k]" size="small" class="value num" :class="{ out: out(asItem(row), asItem(row).values[k]) }"
                          :disabled="!editable" @input="dirty = true" @paste="paste(asItem(row), k, $event)" />
              </div>
              <span v-else>不良 <el-input-number v-model="row.ngCount" :min="0" :max="row.sampleQty || undefined" size="small" controls-position="right"
                                                 :disabled="!editable" @change="dirty = true" /></span>
            </template>
          </el-table-column>
          <el-table-column label="不良数" width="70" align="right"><template #default="{ row }">{{ itemNg(asItem(row)) }}</template></el-table-column>
          <el-table-column label="结果" width="70" align="center">
            <template #default="{ row }"><span :class="itemNg(asItem(row)) > 0 ? 'danger' : 'ok'">{{ itemNg(asItem(row)) > 0 ? 'NG' : 'OK' }}</span></template>
          </el-table-column>
        </el-table>
        <p class="muted hint">定量项目按样本逐个录入，超出上下限的值标红并计入不良；可从 Excel 复制一列数值粘贴到第一个输入框。</p>
      </ErpPanel>

      <ErpPanel title="缺陷明细">
        <el-table :data="defects" border>
          <el-table-column label="缺陷代码" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.defectCode" filterable :disabled="!editable" @change="onCode(asDefect(row)); dirty = true">
                <el-option v-for="c in codes" :key="c.code" :value="c.code" :label="`${c.code} ${c.name}`" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="120">
            <template #default="{ row }">
              <el-select v-model="row.defectLevel" :disabled="!editable" @change="dirty = true">
                <el-option v-for="l in LEVELS" :key="String(l.value)" :value="l.value" :label="l.label" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" :disabled="!editable" @change="dirty = true" /></template>
          </el-table-column>
          <el-table-column label="描述" min-width="220">
            <template #default="{ row }"><el-input v-model="row.description" maxlength="512" :disabled="!editable" @input="dirty = true" /></template>
          </el-table-column>
          <el-table-column v-if="editable" label="" width="60" align="center">
            <template #default="{ $index }"><el-button link type="danger" @click="defects.splice($index, 1); dirty = true">删除</el-button></template>
          </el-table-column>
        </el-table>
        <el-button v-if="editable" class="gap" @click="addDefect">+ 添加缺陷</el-button>
      </ErpPanel>

      <ErpPanel title="汇总与判定">
        <div class="summary">
          <span>CR <strong class="num">{{ counts.CR }}</strong> <span class="muted">{{ levelText('CR') }}</span></span>
          <span>MA <strong class="num">{{ counts.MA }}</strong> <span class="muted">{{ levelText('MA') }}</span></span>
          <span>MI <strong class="num">{{ counts.MI }}</strong> <span class="muted">{{ levelText('MI') }}</span></span>
          <StatusTag :value="editable ? suggestion : d.suggestedResult" :map="SUGGEST_RESULT" />
        </div>
        <el-descriptions v-if="d.result" :column="4" class="gap">
          <el-descriptions-item label="判定结果"><StatusTag :value="d.result" :map="INSP_RESULT" /></el-descriptions-item>
          <el-descriptions-item label="合格 / 特采 / 不合格">{{ formatQty(d.qualifiedQty) }} / {{ formatQty(d.concessionQty) }} / {{ formatQty(d.rejectedQty) }}</el-descriptions-item>
          <el-descriptions-item label="判定人">{{ d.judgeName }} {{ formatDateTime(d.judgeAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ d.judgeReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检验调拨">{{ d.transferIds.length ? `${d.transferIds.length} 张` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="重判次数">{{ d.rejudgeCount }}</el-descriptions-item>
        </el-descriptions>
      </ErpPanel>

      <ErpPanel flush>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="附件" lazy><AttachmentPanel biz-type="QC_INSPECTION" :biz-id="id" :editable="editable" /></el-tab-pane>
          <el-tab-pane label="审批记录" lazy><ApprovalTimeline biz-type="QC_REJUDGE" :biz-id="id" /></el-tab-pane>
          <el-tab-pane label="操作日志" lazy><OperationLogTable biz-type="QC_INSPECTION" :biz-id="id" :status-map="INSP_STATUS" /></el-tab-pane>
        </el-tabs>
      </ErpPanel>
    </template>
    <ErpPanel v-else><el-skeleton :rows="8" animated /></ErpPanel>

    <el-dialog v-model="judgeVisible" title="判定" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="判定结果">
          <el-radio-group v-model="judge.result">
            <el-radio-button value="QUALIFIED" :disabled="d?.mrbSort">合格</el-radio-button>
            <el-radio-button value="REJECTED" :disabled="d?.mrbSort">拒收</el-radio-button>
            <el-radio-button value="SORTED">挑选</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="judge.result === 'SORTED'">
          <el-form-item label="挑选后良品"><QtyInput v-model="judge.qualifiedQty" /></el-form-item>
          <el-form-item label="不良"><QtyInput v-model="judge.rejectedQty" /></el-form-item>
          <el-form-item label="">
            <span class="muted">合计须等于 {{ formatQty(sortRest) }}<template v-if="num(d?.presetConcessionQty) > 0">（另有 MRB 特采 {{ formatQty(d?.presetConcessionQty) }}）</template></span>
          </el-form-item>
        </template>
        <el-form-item :label="judge.result === 'QUALIFIED' && d?.suggestedResult === 'FAIL' ? '让步理由' : '说明'" :required="judge.result === 'QUALIFIED' && d?.suggestedResult === 'FAIL'">
          <el-input v-model="judge.reason" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
        <el-alert v-if="judge.result === 'REJECTED'" type="info" :closable="false" title="拒收将按参数自动生成 NCR；需要特采请使用“提交 MRB”" />
      </el-form>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="primary" @click="doJudge">确定判定</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.gap { margin-top: var(--erp-space-3); }
.muted { color: var(--erp-color-text-secondary); }
.hint { margin: var(--erp-space-2) 0 0; font-size: var(--erp-font-size-caption); }
.key { margin-left: var(--erp-space-2); }
.values { display: flex; flex-wrap: wrap; gap: var(--erp-space-1); }
.value { width: 76px; }
.value.out :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px var(--erp-color-error) inset; }
.value.out :deep(input) { color: var(--erp-color-error); }
.danger { color: var(--erp-color-error); }
.ok { color: var(--erp-color-success); }
.summary { display: flex; align-items: center; gap: var(--erp-space-6); }
.summary strong { font-size: var(--erp-font-size-metric); margin: 0 var(--erp-space-1); }
</style>
