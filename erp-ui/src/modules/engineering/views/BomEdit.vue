<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief } from '@/api/refs'
import { refApi } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { bomApi, BOM_STATUS, ISSUE_METHOD_OPTIONS, PARENT_TYPES, type BomDetail, type BomLine, type BomRow, type BomSave, type SubstituteRow } from '../api/bom'
import { labelOf, MATERIAL_TYPE_OPTIONS, type MaterialType } from '../api/material'

defineOptions({ name: 'EngBomEdit' })

/** BOM 编辑（需求 05-03 4.2，T4 单头 + 明细）：保存草稿 / 提交；新建版本时版本说明必填 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const saving = ref(false)
const detail = ref<BomDetail>()
const nextVersion = ref<number>()

/** 明细行：损耗率按百分数编辑 */
interface Line extends Omit<BomLine, 'scrapRate'> {
  scrapPct?: string
}

interface Form {
  materialId?: string
  baseQty: string
  description?: string
  remark?: string
  fileIds: string[]
  lines: Line[]
}

const newLine = (): Line => ({ qtyPer: '1', scrapPct: '0', issueMethod: 'PICK', isKey: false, substitutes: [] })
const form = ref<Form>({ baseQty: '1', fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)
const parent = ref<MaterialBrief>()
const copied = computed(() => !!detail.value?.copiedFromId)

const rules = computed<FormRules>(() => ({
  materialId: [{ required: true, message: '请选择父件', trigger: 'change' }],
  baseQty: [{ required: true, message: '请输入基数', trigger: 'blur' }, {
    validator: (_r, v, cb) => (Number(v) > 0 ? cb() : cb(new Error('基数必须大于 0'))), trigger: 'blur'
  }],
  description: copied.value ? [{ required: true, message: '新建版本时请填写版本说明', trigger: 'blur' }] : []
}))

const typeLabel = (t?: MaterialType) => labelOf(MATERIAL_TYPE_OPTIONS, t)

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'componentCode', label: '子件编码', type: 'material', width: 150, required: true },
  { prop: 'componentName', label: '名称', type: 'readonly', width: 160 },
  { prop: 'componentSpec', label: '规格', type: 'readonly', width: 180 },
  { prop: 'componentType', label: '类型', type: 'readonly', width: 70, formatter: (r) => typeLabel(r.componentType) },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'qtyPer', label: '用量', type: 'qty', width: 100, required: true, uomProp: 'uom' },
  {
    prop: 'scrapPct', label: '损耗率(%)', type: 'number', width: 90, precision: 2, min: 0,
    validate: (v) => (Number(v) > 100 ? '损耗率为 0～100' : undefined)
  },
  { prop: 'positionNo', label: '位号', type: 'text', width: 180 },
  { prop: 'issueMethod', label: '发料方式', type: 'select', width: 90, options: ISSUE_METHOD_OPTIONS, required: true },
  { prop: 'isKey', label: '关键件', type: 'checkbox', width: 64 },
  { prop: 'substitutes', label: '替代料', type: 'slot', width: 90 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

async function onMaterial(row: Line, m: MaterialBrief | undefined) {
  row.componentName = m?.name
  row.componentSpec = m?.spec
  row.componentType = m?.materialType as MaterialType | undefined
  row.uom = m?.baseUom
  if (m && form.value.materialId && m.id === form.value.materialId) {
    ElMessage.warning('子件不能与父件相同')
  }
}

// ---------- 父件 ----------
async function onParent(m?: MaterialBrief | MaterialBrief[]) {
  parent.value = Array.isArray(m) ? m[0] : m
  nextVersion.value = parent.value && !id.value ? (await bomApi.nextVersion(parent.value.id)).version : nextVersion.value
}

// ---------- 替代料 ----------
const subVisible = ref(false)
const subLine = ref<Line>()
const subRows = ref<(Partial<SubstituteRow>)[]>([])
function openSubs(row: Line) {
  if (!row.componentId) return ElMessage.warning('请先选择子件')
  subLine.value = row
  subRows.value = row.substitutes.map((s) => ({ ...s }))
  subVisible.value = true
}
function onSubMaterial(r: Partial<SubstituteRow>, m?: MaterialBrief | MaterialBrief[]) {
  const x = Array.isArray(m) ? m[0] : m
  r.code = x?.code
  r.name = x?.name
  r.spec = x?.spec
  r.uom = x?.baseUom
}
function saveSubs() {
  const mains = new Set(form.value.lines.map((l) => l.componentId).filter(Boolean))
  const seen = new Set<string>()
  for (const [i, s] of subRows.value.entries()) {
    if (!s.substituteId) return ElMessage.warning(`第 ${i + 1} 行：请选择替代料`)
    if (mains.has(s.substituteId) || s.substituteId === form.value.materialId) return ElMessage.warning(`第 ${i + 1} 行：替代料不能是主料本身或本 BOM 的其他主料`)
    if (seen.has(s.substituteId)) return ElMessage.warning(`第 ${i + 1} 行：替代料重复`)
    if (!(Number(s.ratio) > 0)) return ElMessage.warning(`第 ${i + 1} 行：替代比例必须大于 0`)
    seen.add(s.substituteId)
  }
  subLine.value!.substitutes = subRows.value.map((s, i) => ({ ...s, priority: s.priority ?? i + 1, ratio: s.ratio ?? '1' } as SubstituteRow))
  subVisible.value = false
}

// ---------- 从其他 BOM 复制行 ----------
const copyVisible = ref(false)
const copyParent = ref<string>()
const copyVersions = ref<BomRow[]>([])
const copyBomId = ref<string>()
const copyLines = ref<BomLine[]>([])
const copySelected = ref<BomLine[]>([])
async function onCopyParent(v?: string | string[]) {
  copyBomId.value = undefined
  copyLines.value = []
  copyVersions.value = typeof v === 'string' ? (await bomApi.page({ materialId: v, pageNo: 1, pageSize: 50 })).list : []
  if (copyVersions.value.length) {
    copyBomId.value = (copyVersions.value.find((b) => b.isDefault) ?? copyVersions.value[0]).id
    onCopyBom(copyBomId.value)
  }
}
async function onCopyBom(bid?: string) {
  copyLines.value = bid ? (await bomApi.get(bid)).lines : []
}
function onCopySelect(rows: BomLine[]) {
  copySelected.value = rows
}
function applyCopy() {
  if (!copySelected.value.length) return ElMessage.warning('请勾选要带入的行')
  const existing = new Set(form.value.lines.map((l) => l.componentId))
  const rows = copySelected.value.filter((l) => !existing.has(l.componentId)).map((l) => toLine(l))
  form.value.lines = [...form.value.lines.filter((l) => l.componentId), ...rows]
  const skipped = copySelected.value.length - rows.length
  ElMessage.success(`已带入 ${rows.length} 行${skipped ? `，${skipped} 行子件已存在已跳过` : ''}`)
  copyVisible.value = false
}

function toLine(l: BomLine): Line {
  const { scrapRate, id: _id, lineNo: _no, ...rest } = l
  return { ...rest, scrapPct: scrapRate === undefined || scrapRate === null ? '0' : String(Number((Number(scrapRate) * 100).toFixed(4))), substitutes: l.substitutes.map((s) => ({ ...s })) }
}

// ---------- 加载 ----------
onMounted(async () => {
  if (id.value) {
    const d = await bomApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的 BOM 可以修改')
      router.replace(`/engineering/bom/${id.value}`)
      return
    }
    detail.value = d
    form.value = { materialId: d.materialId, baseQty: d.baseQty, description: d.description, remark: d.remark, fileIds: [], lines: [...d.lines.map(toLine), newLine()] }
    parent.value = { id: d.materialId, code: d.materialCode, name: d.materialName, spec: d.materialSpec, baseUom: d.uom, materialType: d.materialType }
    tabs.setTitle(tabKeyOf(route), `编辑 BOM ${d.docNo}`)
  } else if (typeof route.query.materialId === 'string') {
    form.value.materialId = route.query.materialId
    const [m] = await refApi.materialSearch({ ids: route.query.materialId, status: '' })
    await onParent(m)
  }
  guard.markClean()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

/** Ctrl+S 保存草稿，Ctrl+Enter 提交 */
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

// ---------- 保存 ----------
async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/engineering/bom/${id.value}` : '/engineering/bom')
}

function payload(): BomSave {
  const f = form.value
  return {
    materialId: f.materialId!, baseQty: f.baseQty, description: f.description?.trim() || undefined, remark: f.remark?.trim() || undefined,
    fileIds: f.fileIds, rowVersion: detail.value?.rowVersion,
    lines: linesRef.value!.validRows().map((l) => ({
      componentId: l.componentId!, qtyPer: l.qtyPer!, scrapRate: String(Number(((Number(l.scrapPct) || 0) / 100).toFixed(6))),
      positionNo: l.positionNo?.trim() || undefined, issueMethod: l.issueMethod, isKey: !!l.isKey, remark: l.remark?.trim() || undefined,
      substitutes: l.substitutes.map((s, i) => ({ substituteId: s.substituteId, priority: s.priority ?? i + 1, ratio: s.ratio ?? '1', remark: s.remark }))
    }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  saving.value = true
  try {
    const data = payload()
    const r = id.value ? await bomApi.update(id.value, data) : await bomApi.create(data)
    if (r.warnings.length) {
      ElNotification({ type: 'warning', title: '位号个数与用量不一致', message: r.warnings.join('；'), duration: 8000 })
    }
    guard.markClean()
    if (submit) {
      try {
        const st = await bomApi.submit(r.id)
        ElMessage.success(st === 'APPROVED' ? '提交成功，已审核' : '已提交审批')
      } catch {
        // 提交失败（如子件未启用）：已保存为草稿，留在编辑页修改
        if (!id.value) router.replace(`/engineering/bom/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/engineering/bom/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑 BOM ${detail.value.docNo}` : '新建 BOM'))
const versionText = computed(() => (detail.value ? `V${detail.value.version}` : nextVersion.value ? `V${nextVersion.value}` : '选择父件后生成'))
const asLine = (r: unknown) => r as Line
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta>
      <StatusTag v-if="detail" :value="detail.status" :map="BOM_STATUS" />
    </template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('eng:bom:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="父件" prop="materialId">
              <MaterialSelect v-if="!id" v-model="form.materialId" :types="PARENT_TYPES" placeholder="半成品、成品或虚拟件" @select="onParent" />
              <span v-else class="mono">{{ detail?.materialCode }}</span>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="版本"><span class="mono">{{ versionText }}</span>
            <span v-if="detail?.copiedFromNo" class="text-muted copied">复制自 {{ detail.copiedFromNo }}</span></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="基数" prop="baseQty">
              <NumberInput v-model="form.baseQty" :precision="4" trim-zeros />
              <div class="form-tip">用量是生产“基数”个父件所需的子件数量</div>
            </el-form-item>
          </el-col>
          <template v-if="parent">
            <el-col :xl="8" :span="12"><el-form-item label="父件名称">{{ parent.name }}</el-form-item></el-col>
            <el-col :xl="8" :span="12"><el-form-item label="规格">{{ parent.spec || '-' }}</el-form-item></el-col>
            <el-col :xl="8" :span="12"><el-form-item label="单位 / 类型">{{ parent.baseUom }} / {{ typeLabel(parent.materialType as MaterialType) }}</el-form-item></el-col>
          </template>
          <el-col :xl="16" :span="24">
            <el-form-item label="版本说明" prop="description">
              <el-input v-model="form.description" maxlength="256" :placeholder="copied ? '说明本版本改了什么，如“改用 B 供应商连接器”' : '可选'" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra><span class="text-muted">子件用量统一按子件基本单位；位号填写后用量应等于位号个数</span></template>
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial" material-id-prop="componentId">
        <template #toolbar>
          <el-button icon="CopyDocument" @click="copyVisible = true">从其他 BOM 复制行</el-button>
        </template>
        <template #cell-substitutes="{ row, disabled }">
          <el-button link type="primary" :disabled="disabled" @click="openSubs(asLine(row))">替代({{ asLine(row).substitutes.length }})</el-button>
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="ENG_BOM" multiple />
      <AttachmentPanel v-else biz-type="ENG_BOM" :biz-id="id" editable />
    </ErpPanel>

    <!-- 替代料 -->
    <el-dialog v-model="subVisible" :title="`替代料 - ${subLine?.componentCode ?? ''} ${subLine?.componentName ?? ''}`" width="820px" :close-on-click-modal="false" append-to-body>
      <div class="sub-toolbar">
        <el-button icon="Plus" @click="subRows.push({ priority: subRows.length + 1, ratio: '1' })">添加替代料</el-button>
        <span class="form-tip">替代比例：1 个主料 = 比例 × 替代料；优先级 1 最高</span>
      </div>
      <el-table :data="subRows">
        <el-table-column label="替代料" min-width="260">
          <template #default="{ row }"><MaterialSelect v-model="row.substituteId" @select="(m) => onSubMaterial(row, m)" /></template>
        </el-table-column>
        <el-table-column label="单位" width="70"><template #default="{ row }">{{ row.uom }}</template></el-table-column>
        <el-table-column label="优先级" width="110">
          <template #default="{ row }"><el-input-number v-model="row.priority" :min="1" :max="99" :precision="0" controls-position="right" class="w80" /></template>
        </el-table-column>
        <el-table-column label="比例" width="130"><template #default="{ row }"><NumberInput v-model="row.ratio" :precision="6" trim-zeros /></template></el-table-column>
        <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="128" /></template></el-table-column>
        <el-table-column label="" width="56" align="center">
          <template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="subRows.splice($index, 1)" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="没有替代料" /></template>
      </el-table>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSubs">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从其他 BOM 复制行 -->
    <el-dialog v-model="copyVisible" title="从其他 BOM 复制行" width="960px" append-to-body>
      <el-form inline>
        <el-form-item label="父件"><MaterialSelect v-model="copyParent" :types="PARENT_TYPES" class="w260" @update:model-value="onCopyParent" /></el-form-item>
        <el-form-item label="版本">
          <el-select v-model="copyBomId" class="w200" placeholder="选择版本" @change="onCopyBom">
            <el-option v-for="b in copyVersions" :key="b.id" :value="b.id" :label="`V${b.version}${b.isDefault ? '（默认）' : ''} ${BOM_STATUS[b.status]?.label ?? ''}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="copyLines" max-height="400" @selection-change="onCopySelect">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="componentCode" label="子件编码" width="140" />
        <el-table-column prop="componentName" label="名称" min-width="160" />
        <el-table-column prop="componentSpec" label="规格" min-width="160" />
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column prop="qtyPer" label="用量" width="90" align="right" />
        <el-table-column prop="positionNo" label="位号" min-width="120" />
        <template #empty><ErpEmpty compact description="请选择父件和版本" /></template>
      </el-table>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button type="primary" @click="applyCopy">带入选中行</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.copied { margin-left: var(--erp-space-3); font-size: var(--erp-font-size-secondary); }
.sub-toolbar { display: flex; align-items: center; gap: var(--erp-space-3); margin-bottom: var(--erp-space-3); }
.sub-toolbar .form-tip { margin: 0; }
.w80 { width: 80px; }
</style>
