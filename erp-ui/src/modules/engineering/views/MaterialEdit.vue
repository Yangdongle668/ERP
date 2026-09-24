<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormItemProp, type FormRules } from 'element-plus'
import { fetchBlob, upload } from '@/api/http'
import type { FileInfo } from '@/api/system'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { categoryApi, findCategory, type CategorySimple } from '../api/category'
import {
  defaultSource, materialApi, ISSUE_RULE_OPTIONS, MATERIAL_STATUS, MATERIAL_TYPE_OPTIONS, ORDER_POLICY_OPTIONS, SOURCE_TYPE_OPTIONS, TRACKING_OPTIONS,
  type Material, type MaterialSave, type MaterialSettings, type MaterialStatus, type MaterialType, type Suspect
} from '../api/material'

defineOptions({ name: 'EngMaterialEdit' })

/**
 * 新建/编辑物料（需求 05-02 3.2，T3 独立表单页）：7 个页签，校验错误的页签带红点；
 * 选择类别带出默认值（只影响未手工修改的字段）；名称、规格、制造商料号失焦查重。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const status = ref<MaterialStatus>('DRAFT')
const code = ref('')
const settings = ref<MaterialSettings>({ enableApproval: false, manualCodeAllowed: true, duplicateCheck: 'WARN', canViewCost: false })
const categories = ref<CategorySimple[]>([])
const formRef = ref<FormInstance>()
const saving = ref(false)
const activeTab = ref('basic')
const errorTabs = ref<Set<string>>(new Set())

/** 表单模型：百分比字段按百分数编辑，保存时换算为小数 */
interface Form extends Omit<MaterialSave, 'overReceivePct' | 'minRemainingLifePct' | 'purchaseTaxRate' | 'salesTaxRate'> {
  overReceivePct?: string
  minRemainingLifePct?: string
  purchaseTaxRate?: string
  salesTaxRate?: string
  uomRows: { uom?: string; rate?: string; remark?: string; used?: boolean }[]
}

const empty = (): Form => ({
  code: '', name: '', materialType: 'RAW', baseUom: '', sourceType: 'PURCHASE', leadTimeDays: 0, safetyStock: '0', orderPolicy: 'LOT_FOR_LOT',
  moq: '0', mpq: '0', overReceivePct: '0', tracking: 'NONE', issueRule: 'FIFO', iqcRequired: true, fqcRequired: false, oqcRequired: false,
  purchaseTaxRate: '13', salesTaxRate: '13', uomRows: []
})
const form = ref<Form>(empty())
const guard = useLeaveGuard(() => form.value)

const locked = computed(() => status.value !== 'DRAFT')
const readonly = computed(() => status.value === 'PENDING')

// ---------- 字段与页签 ----------
const TAB_FIELDS: Record<string, string[]> = {
  basic: ['categoryId', 'code', 'name', 'nameEn', 'spec', 'materialType', 'baseUom', 'hsCode', 'unitNetWeight', 'unitGrossWeight'],
  plan: ['sourceType', 'leadTimeDays', 'safetyStock', 'maxStock', 'fixedLotQty', 'periodDays', 'moq', 'mpq'],
  purchase: ['purchaseUom', 'overReceivePct'],
  stock: ['tracking', 'issueRule', 'shelfLifeDays', 'minRemainingLifePct'],
  finance: ['standardCost', 'salesUom', 'purchaseTaxRate', 'salesTaxRate']
}

const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? undefined : Number(v))
const auxUoms = computed(() => form.value.uomRows.map((r) => r.uom).filter((u): u is string => !!u))
const unitOptions = computed(() => [form.value.baseUom, ...auxUoms.value].filter(Boolean))

const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择物料类别', trigger: 'change' }],
  code: [{ pattern: /^[A-Z0-9._-]*$/, message: '编码只能包含字母、数字和 . _ -', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  baseUom: [{ required: true, message: '请选择基本单位', trigger: 'change' }],
  hsCode: [{ pattern: /^\d{4,10}$/, message: '海关编码为 4～10 位数字', trigger: 'blur' }],
  unitGrossWeight: [{
    validator: (_r, v, cb) => (num(v) !== undefined && num(form.value.unitNetWeight) !== undefined && num(v)! < num(form.value.unitNetWeight)! ? cb(new Error('单位毛重不能小于单位净重')) : cb()),
    trigger: 'blur'
  }],
  sourceType: [{ required: true, message: '请选择取得方式', trigger: 'change' }, {
    validator: (_r, v, cb) => (form.value.materialType === 'PHANTOM' && v !== 'MAKE' ? cb(new Error('虚拟件的取得方式只能是“自制”')) : cb()), trigger: 'change'
  }],
  leadTimeDays: [{ required: true, message: '请输入提前期', trigger: 'blur' }],
  safetyStock: [{ required: true, message: '请输入安全库存', trigger: 'blur' }],
  maxStock: [{
    validator: (_r, v, cb) => (num(v) !== undefined && num(v)! < (num(form.value.safetyStock) ?? 0) ? cb(new Error('最高库存不能小于安全库存')) : cb()), trigger: 'blur'
  }],
  fixedLotQty: [{
    validator: (_r, v, cb) => (form.value.orderPolicy === 'FIXED_QTY' && !(num(v)! > 0) ? cb(new Error('批量规则为“固定批量”时必须填写固定批量')) : cb()), trigger: 'blur'
  }],
  periodDays: [{
    validator: (_r, v, cb) => (form.value.orderPolicy === 'PERIOD' && !v ? cb(new Error('批量规则为“按周期”时必须填写合并周期天数')) : cb()), trigger: 'blur'
  }],
  moq: [{ required: true, message: '请输入 MOQ', trigger: 'blur' }],
  mpq: [{ required: true, message: '请输入 MPQ', trigger: 'blur' }],
  issueRule: [{
    validator: (_r, v, cb) => (v === 'FEFO' && !form.value.shelfLifeDays ? cb(new Error('出库规则为“先到期先出”时必须填写保质期')) : cb()), trigger: 'change'
  }],
  minRemainingLifePct: [{
    validator: (_r, v, cb) => (num(v) !== undefined && !form.value.shelfLifeDays ? cb(new Error('填写最小剩余保质期前请先填写保质期')) : cb()), trigger: 'blur'
  }],
  purchaseTaxRate: [{ required: true, message: '请输入进项税率', trigger: 'blur' }],
  salesTaxRate: [{ required: true, message: '请输入销项税率', trigger: 'blur' }]
}

// ---------- 类别默认值 ----------
/** 最近一次带出的默认值：当前值仍等于它（或为空）时才会被新的默认值覆盖 */
const applied = ref<Partial<Form>>({})

function applyDefault<K extends keyof Form>(key: K, value: Form[K]) {
  const cur = form.value[key]
  if (cur === undefined || cur === null || cur === '' || cur === applied.value[key]) form.value[key] = value
  applied.value[key] = value
}

function onCategory(cid?: string) {
  const c = findCategory(categories.value, cid)
  if (!c) return
  if (!locked.value) {
    applyDefault('materialType', c.defaultMaterialType)
    if (c.defaultBaseUom) applyDefault('baseUom', c.defaultBaseUom)
  }
  applyDefault('tracking', c.defaultTracking)
  applyDefault('iqcRequired', c.defaultIqcRequired)
  applyDefault('shelfLifeDays', c.defaultShelfLifeDays)
  checkDuplicate()
}

/** 物料类型变化：取得方式、完工/出货检验跟随（未手工修改时） */
watch(() => form.value.materialType, (t, old) => {
  if (!t || !old || t === old || !loaded.value) return
  const f = form.value
  if (f.sourceType === defaultSource(old as MaterialType)) f.sourceType = defaultSource(t)
  const fqc = (x?: MaterialType) => x === 'SEMI_FINISHED' || x === 'FINISHED'
  if (f.fqcRequired === fqc(old as MaterialType)) f.fqcRequired = fqc(t)
  if (f.oqcRequired === (old === 'FINISHED')) f.oqcRequired = t === 'FINISHED'
})

/** 只能选择启用的末级类别 */
const categoryProps = { label: 'name', children: 'children', disabled: (d: Record<string, unknown>) => !d.leaf }

// ---------- 查重 ----------
const suspects = ref<Suspect[]>([])
async function checkDuplicate() {
  const f = form.value
  if (settings.value.duplicateCheck === 'OFF' || (!f.name && !f.mpn)) {
    suspects.value = []
    return
  }
  const r = await materialApi.duplicateCheck({ id: id.value, categoryId: f.categoryId, name: f.name, spec: f.spec, mpn: f.mpn }).catch(() => undefined)
  suspects.value = r?.suspects ?? []
}

// ---------- 单位换算 ----------
function addUom() {
  form.value.uomRows.push({})
}
function uomDesc(r: { uom?: string; rate?: string }) {
  return r.uom && r.rate && form.value.baseUom ? `1 ${r.uom} = ${Number(r.rate)} ${form.value.baseUom}` : ''
}

// ---------- 图片 ----------
const imageUploading = ref(false)
const imageUrl = ref('')
async function loadImage(fileId?: string) {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = fileId ? URL.createObjectURL(await fetchBlob(`/system/files/${fileId}/preview`).catch(() => new Blob())) : ''
}
async function onImage(f: { raw?: File }) {
  const file = f.raw
  if (!file) return
  if (!/^image\//.test(file.type)) return ElMessage.warning('请上传图片文件')
  if (file.size > 2 * 1024 * 1024) return ElMessage.warning('图片不能超过 2MB')
  imageUploading.value = true
  try {
    const info = await upload<FileInfo>('/system/files', file, { category: 'image', ...(id.value ? { bizType: 'ENG_MATERIAL', bizId: id.value } : {}) })
    form.value.imageFileId = info.id
    loadImage(info.id)
  } finally {
    imageUploading.value = false
  }
}
function removeImage() {
  form.value.imageFileId = undefined
  loadImage(undefined)
}

// ---------- 加载 ----------
const loaded = ref(false)
const pct = (v?: string) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) * 100).toFixed(4))))
const dec = (v?: string) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) / 100).toFixed(6))))

function toForm(m: Material, copy: boolean): Form {
  const { id: _i, status: _s, createdAt: _c, updatedAt: _u, uoms, code: c, version, categoryCode: _cc, categoryName: _cn, plannerName: _pn,
    buyerName: _bn, createdBy: _cb, createdByName: _cbn, lowLevelCode: _l, ...rest } = m
  return {
    ...rest,
    code: copy ? '' : c,
    imageFileId: copy ? undefined : m.imageFileId,
    version: copy ? undefined : version,
    overReceivePct: pct(m.overReceivePct), minRemainingLifePct: pct(m.minRemainingLifePct),
    purchaseTaxRate: pct(m.purchaseTaxRate), salesTaxRate: pct(m.salesTaxRate),
    uomRows: (uoms ?? []).map((u) => ({ uom: u.uom, rate: u.rate, remark: u.remark, used: copy ? false : u.used }))
  }
}

onMounted(async () => {
  const [s, c] = await Promise.all([materialApi.settings().catch(() => settings.value), categoryApi.simpleTree().catch(() => [])])
  settings.value = s
  categories.value = c
  if (id.value) {
    const m = await materialApi.get(id.value)
    status.value = m.status
    code.value = m.code
    form.value = toForm(m, false)
    tabs.setTitle(tabKeyOf(route), `编辑物料 ${m.code}`)
    loadImage(m.imageFileId)
  } else if (typeof route.query.from === 'string') {
    const m = await materialApi.get(route.query.from)
    form.value = toForm(m, true)
    tabs.setTitle(tabKeyOf(route), `复制新建 ${m.code}`)
  } else if (typeof route.query.categoryId === 'string') {
    form.value.categoryId = route.query.categoryId
    onCategory(form.value.categoryId)
  }
  guard.markClean()
  loaded.value = true
})

// ---------- 保存 ----------
async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/engineering/material/${id.value}` : '/engineering/material')
}

function markErrorTabs(fields: Record<string, unknown>) {
  const set = new Set<string>()
  for (const f of Object.keys(fields)) {
    const tab = Object.entries(TAB_FIELDS).find(([, list]) => list.includes(f))?.[0]
    if (tab) set.add(tab)
  }
  errorTabs.value = set
  const first = Object.keys(TAB_FIELDS).find((t) => set.has(t))
  if (first) activeTab.value = first
}

function validateUoms(): string | undefined {
  const seen = new Set<string>()
  for (const [i, r] of form.value.uomRows.entries()) {
    if (!r.uom) return `单位换算第 ${i + 1} 行：请选择辅助单位`
    if (r.uom === form.value.baseUom) return `辅助单位 ${r.uom} 不能与基本单位相同`
    if (seen.has(r.uom)) return `辅助单位 ${r.uom} 重复`
    if (!(Number(r.rate) > 0)) return `单位换算第 ${i + 1} 行：换算率必须大于 0`
    seen.add(r.uom)
  }
  for (const [label, u] of [['采购单位', form.value.purchaseUom], ['销售单位', form.value.salesUom]] as const) {
    if (u && !unitOptions.value.includes(u)) return `${label} ${u} 没有与基本单位的换算关系`
  }
  return undefined
}

async function save(andEnable = false) {
  errorTabs.value = new Set()
  const valid = await formRef.value?.validate().then(() => true).catch((fields: Record<string, unknown>) => {
    markErrorTabs(fields)
    return false
  })
  if (!valid) return
  const uomErr = validateUoms()
  if (uomErr) {
    errorTabs.value = new Set(['uom'])
    activeTab.value = 'uom'
    return ElMessage.warning(uomErr)
  }
  const f = form.value
  const { uomRows, ...rest } = f
  const data: MaterialSave = {
    ...rest,
    code: f.code?.trim() || undefined,
    name: f.name.trim(),
    overReceivePct: dec(f.overReceivePct) ?? '0', minRemainingLifePct: dec(f.minRemainingLifePct),
    purchaseTaxRate: dec(f.purchaseTaxRate), salesTaxRate: dec(f.salesTaxRate),
    standardCost: settings.value.canViewCost ? f.standardCost : undefined,
    uoms: uomRows.map((r) => ({ uom: r.uom!, rate: r.rate!, remark: r.remark }))
  }
  saving.value = true
  try {
    let targetId = id.value
    if (targetId) await materialApi.update(targetId, data)
    else targetId = await materialApi.create(data)
    if (andEnable) {
      const st = await materialApi.enable(targetId).catch((e: Error) => {
        ElMessage.warning(`已保存为草稿，启用失败：${e.message}`)
        return undefined
      })
      if (st) ElMessage.success(st === 'PENDING' ? '保存成功，已提交启用审批' : '保存并启用成功')
    } else {
      ElMessage.success('保存成功')
    }
    guard.markClean()
    tabs.remove([tabKeyOf(route)])
    router.push(`/engineering/material/${targetId}`)
  } finally {
    saving.value = false
  }
}

const tabLabel = (key: string, label: string) => (errorTabs.value.has(key) ? `${label} ●` : label)
const canEnable = computed(() => !id.value && me.hasPermission('eng:material:enable'))
const onValidate = (prop: FormItemProp, ok: boolean) => ok && clearTabError(prop)
const clearTabError = (prop: FormItemProp) => {
  const tab = Object.entries(TAB_FIELDS).find(([, list]) => list.includes(String(prop)))?.[0]
  if (tab && errorTabs.value.has(tab)) {
    const s = new Set(errorTabs.value)
    s.delete(tab)
    errorTabs.value = s
  }
}
</script>

<template>
  <ErpPage back sticky :on-back="back">
    <template #meta>
      <StatusTag v-if="id" :value="status" :map="MATERIAL_STATUS" />
    </template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button v-if="canEnable" :loading="saving" @click="save(true)">{{ settings.enableApproval ? '保存并提交启用' : '保存并启用' }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="readonly" @click="save()">保存</el-button>
    </template>

    <el-alert v-if="readonly" type="warning" :closable="false" show-icon title="物料正在审批中，审批结束前不能修改" />
    <el-alert v-if="suspects.length" type="warning" :closable="false" show-icon
              :title="settings.duplicateCheck === 'BLOCK' ? `发现 ${suspects.length} 个疑似重复物料，不能保存` : `发现 ${suspects.length} 个疑似重复物料，请确认不是重复建档`">
      <div class="suspects">
        <el-link v-for="s in suspects" :key="s.id" type="primary" underline="never" @click="router.push(`/engineering/material/${s.id}`)">
          {{ s.code }} {{ s.name }}{{ s.spec ? ' ' + s.spec : '' }}（{{ MATERIAL_STATUS[s.status]?.label }}{{ s.reason === 'MPN' ? '，制造商料号相同' : '' }}）
        </el-link>
      </div>
    </el-alert>

    <ErpPanel>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" :disabled="readonly" class="material-form" @validate="onValidate">
        <el-tabs v-model="activeTab">
          <!-- 1 基本信息 -->
          <el-tab-pane :label="tabLabel('basic', '基本信息')" name="basic">
            <el-row :gutter="24">
              <el-col :xl="8" :span="12">
                <el-form-item label="物料类别" prop="categoryId">
                  <el-tree-select v-model="form.categoryId" :data="categories" node-key="id" :props="categoryProps" filterable :disabled="locked"
                                  placeholder="选择末级类别" @change="onCategory" />
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="编码" prop="code">
                  <el-input v-if="!locked" v-model="form.code" maxlength="64" :disabled="!settings.manualCodeAllowed"
                            :placeholder="settings.manualCodeAllowed ? '留空按类别前缀自动生成' : '保存后自动生成'" @input="form.code = String($event).toUpperCase()" />
                  <span v-else class="mono">{{ code }}</span>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="128" @blur="checkDuplicate" /></el-form-item>
              </el-col>
              <el-col :xl="8" :span="12"><el-form-item label="英文名称" prop="nameEn"><el-input v-model="form.nameEn" maxlength="256" /></el-form-item></el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="物料类型" prop="materialType">
                  <el-select v-model="form.materialType" :disabled="locked"><el-option v-for="o in MATERIAL_TYPE_OPTIONS" :key="o.value" v-bind="o" /></el-select>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="基本单位" prop="baseUom">
                  <UomSelect v-model="form.baseUom" :disabled="locked" />
                  <div v-if="!locked" class="form-tip">启用后不能修改</div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="规格型号" prop="spec"><el-input v-model="form.spec" type="textarea" :rows="2" maxlength="512" show-word-limit @blur="checkDuplicate" /></el-form-item>
              </el-col>
              <el-col :xl="8" :span="12"><el-form-item label="图号"><el-input v-model="form.drawingNo" maxlength="64" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="版本"><el-input v-model="form.revision" maxlength="16" placeholder="如 A、B、A1" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="品牌"><el-input v-model="form.brand" maxlength="64" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="制造商"><el-input v-model="form.manufacturer" maxlength="128" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="制造商料号"><el-input v-model="form.mpn" maxlength="128" @blur="checkDuplicate" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="海关编码" prop="hsCode"><el-input v-model="form.hsCode" maxlength="10" /></el-form-item></el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="单位净重(kg)" prop="unitNetWeight"><NumberInput v-model="form.unitNetWeight" :precision="4" trim-zeros /></el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="单位毛重(kg)" prop="unitGrossWeight"><NumberInput v-model="form.unitGrossWeight" :precision="4" trim-zeros /></el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="图片">
                  <div class="image-field">
                    <img v-if="imageUrl" :src="imageUrl" alt="物料图片" class="thumb" />
                    <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onImage" :disabled="imageUploading || readonly">
                      <el-button :loading="imageUploading" icon="Upload">{{ form.imageFileId ? '更换' : '上传图片' }}</el-button>
                    </el-upload>
                    <el-button v-if="form.imageFileId" link type="danger" @click="removeImage">移除</el-button>
                  </div>
                  <div class="form-tip">不超过 2MB</div>
                </el-form-item>
              </el-col>
              <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" /></el-form-item></el-col>
            </el-row>
          </el-tab-pane>

          <!-- 2 单位换算 -->
          <el-tab-pane :label="tabLabel('uom', `单位换算${form.uomRows.length ? '(' + form.uomRows.length + ')' : ''}`)" name="uom">
            <div class="sub-toolbar">
              <el-button icon="Plus" :disabled="readonly" @click="addUom">添加换算</el-button>
              <span class="form-tip">1 辅助单位 = 换算率 × 基本单位（{{ form.baseUom || '未选择' }}）；已被单据使用的换算不能修改或删除</span>
            </div>
            <el-table :data="form.uomRows" class="uom-table">
              <el-table-column label="辅助单位" width="180">
                <template #default="{ row }"><UomSelect v-model="row.uom" :disabled="row.used" /></template>
              </el-table-column>
              <el-table-column label="换算率" width="180" align="right">
                <template #default="{ row }"><NumberInput v-model="row.rate" :precision="6" trim-zeros :disabled="row.used" /></template>
              </el-table-column>
              <el-table-column label="说明" min-width="160"><template #default="{ row }"><span class="text-muted">{{ uomDesc(row) }}</span></template></el-table-column>
              <el-table-column label="备注" min-width="200"><template #default="{ row }"><el-input v-model="row.remark" maxlength="128" placeholder="如 每箱 100 个" /></template></el-table-column>
              <el-table-column label="" width="60" align="center">
                <template #default="{ row, $index }">
                  <el-button link type="danger" icon="Delete" :disabled="row.used || readonly" @click="form.uomRows.splice($index, 1)" />
                </template>
              </el-table-column>
              <template #empty><ErpEmpty compact description="未配置辅助单位，采购、销售、库存均使用基本单位" /></template>
            </el-table>
          </el-tab-pane>

          <!-- 3 计划属性 -->
          <el-tab-pane :label="tabLabel('plan', '计划属性')" name="plan">
            <el-row :gutter="24">
              <el-col :xl="8" :span="12">
                <el-form-item label="取得方式" prop="sourceType">
                  <el-radio-group v-model="form.sourceType">
                    <el-radio v-for="o in SOURCE_TYPE_OPTIONS" :key="o.value" :value="o.value" :disabled="form.materialType === 'PHANTOM' && o.value !== 'MAKE'">{{ o.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="提前期(天)" prop="leadTimeDays">
                  <el-input-number v-model="form.leadTimeDays" :min="0" :max="365" :precision="0" controls-position="right" class="w160" />
                  <div class="form-tip">采购件为采购提前期，自制件为生产提前期</div>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12"><el-form-item label="计划员"><UserSelect v-model="form.plannerId" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="安全库存" prop="safetyStock"><NumberInput v-model="form.safetyStock" :precision="4" trim-zeros /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="最高库存" prop="maxStock"><NumberInput v-model="form.maxStock" :precision="4" trim-zeros /></el-form-item></el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="批量规则">
                  <el-radio-group v-model="form.orderPolicy"><el-radio v-for="o in ORDER_POLICY_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
                </el-form-item>
              </el-col>
              <el-col v-if="form.orderPolicy === 'FIXED_QTY'" :xl="8" :span="12">
                <el-form-item label="固定批量" prop="fixedLotQty" required><NumberInput v-model="form.fixedLotQty" :precision="4" trim-zeros /></el-form-item>
              </el-col>
              <el-col v-if="form.orderPolicy === 'PERIOD'" :xl="8" :span="12">
                <el-form-item label="合并周期(天)" prop="periodDays" required>
                  <el-input-number v-model="form.periodDays" :min="1" :max="90" :precision="0" controls-position="right" class="w160" />
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="MOQ" prop="moq"><NumberInput v-model="form.moq" :precision="4" trim-zeros />
                  <div class="form-tip">最小订购量：MRP 建议量不足时补足到 MOQ</div></el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="MPQ" prop="mpq"><NumberInput v-model="form.mpq" :precision="4" trim-zeros />
                  <div class="form-tip">最小包装量：建议量向上取整为 MPQ 的整数倍；0 表示不取整</div></el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <!-- 4 采购属性 -->
          <el-tab-pane :label="tabLabel('purchase', '采购属性')" name="purchase">
            <el-row :gutter="24">
              <el-col :xl="8" :span="12"><el-form-item label="采购员"><UserSelect v-model="form.buyerId" /></el-form-item></el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="采购单位" prop="purchaseUom">
                  <el-select v-model="form.purchaseUom" clearable placeholder="默认基本单位"><el-option v-for="u in unitOptions" :key="u" :value="u" :label="u" /></el-select>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="允许超收(%)" prop="overReceivePct"><NumberInput v-model="form.overReceivePct" :precision="2" :max="100" trim-zeros /></el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="默认供应商"><span class="text-muted">由资材模块的供应商价格维护</span></el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <!-- 5 库存属性 -->
          <el-tab-pane :label="tabLabel('stock', '库存属性')" name="stock">
            <el-row :gutter="24">
              <el-col :xl="8" :span="12">
                <el-form-item label="库存管理" prop="tracking">
                  <el-radio-group v-model="form.tracking"><el-radio v-for="o in TRACKING_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
                  <div v-if="locked" class="form-tip">有库存或未完成出入库单据时不能修改</div>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="出库规则" prop="issueRule">
                  <el-radio-group v-model="form.issueRule"><el-radio v-for="o in ISSUE_RULE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="保质期(天)" prop="shelfLifeDays">
                  <el-input-number v-model="form.shelfLifeDays" :min="1" :max="3650" :precision="0" controls-position="right" class="w160" />
                  <div class="form-tip">填写后入库需录入生产日期，系统计算到期日</div>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="最小剩余保质期(%)" prop="minRemainingLifePct">
                  <NumberInput v-model="form.minRemainingLifePct" :precision="2" :max="100" trim-zeros :disabled="!form.shelfLifeDays" />
                  <div class="form-tip">到货时剩余比例低于此值提示拒收</div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <!-- 6 质量属性 -->
          <el-tab-pane label="质量属性" name="quality">
            <el-row :gutter="24">
              <el-col :xl="8" :span="12"><el-form-item label="来料检验"><el-switch v-model="form.iqcRequired" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="完工检验"><el-switch v-model="form.fqcRequired" /></el-form-item></el-col>
              <el-col :xl="8" :span="12"><el-form-item label="出货检验"><el-switch v-model="form.oqcRequired" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="检验标准"><span class="text-muted">由品质模块维护</span></el-form-item></el-col>
            </el-row>
          </el-tab-pane>

          <!-- 7 财务与销售 -->
          <el-tab-pane :label="tabLabel('finance', '财务与销售')" name="finance">
            <el-row :gutter="24">
              <el-col v-if="settings.canViewCost" :xl="8" :span="12">
                <el-form-item label="标准成本" prop="standardCost">
                  <PriceInput v-model="form.standardCost" />
                  <div class="form-tip">本位币 / 基本单位</div>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="销售单位" prop="salesUom">
                  <el-select v-model="form.salesUom" clearable placeholder="默认基本单位"><el-option v-for="u in unitOptions" :key="u" :value="u" :label="u" /></el-select>
                </el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="进项税率(%)" prop="purchaseTaxRate"><NumberInput v-model="form.purchaseTaxRate" :precision="2" :max="100" trim-zeros /></el-form-item>
              </el-col>
              <el-col :xl="8" :span="12">
                <el-form-item label="销项税率(%)" prop="salesTaxRate">
                  <NumberInput v-model="form.salesTaxRate" :precision="2" :max="100" trim-zeros />
                  <div class="form-tip">外销订单按客户设置为 0</div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </el-form>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.suspects { display: flex; flex-direction: column; gap: var(--erp-space-1); margin-top: var(--erp-space-1); }
.material-form :deep(.el-tabs__header) { margin-bottom: var(--erp-space-5); }
.material-form .form-tip { width: 100%; }
.sub-toolbar { display: flex; align-items: center; gap: var(--erp-space-3); margin-bottom: var(--erp-space-3); }
.sub-toolbar .form-tip { margin: 0; }
.image-field { display: flex; align-items: center; gap: var(--erp-space-3); }
.thumb { width: 40px; height: 40px; object-fit: cover; border-radius: var(--erp-radius-xs); border: 1px solid var(--erp-color-border); }
</style>
