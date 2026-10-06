<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { ASSET_STATUS, ASSET_STATUS_OPTIONS, assetApi, type AssetQuery, type AssetRow, type AssetSave } from '../api/asset'

defineOptions({ name: 'AssetList' })

/**
 * 资产台账（需求 15-固定资产，T1 + T2 弹窗）。资产编码按《编码规则管理制度》5.4 自动生成：
 * LD{公司}-{分类}-{名称缩写}-{年月}-{流水}，如 LD1-PD-CPJ-264-001。
 */
type Query = Omit<AssetQuery, 'pageNo' | 'pageSize'>
const { query, list, total, loading, load, search, reset } = useListPage<Query, AssetRow>({ api: (q) => assetApi.page(q as AssetQuery), refreshOnActivated: true })

const fields: SearchField[] = [
  { prop: 'keyword', label: '编码/名称/规格' },
  { prop: 'assetClass', label: '分类', type: 'dict', dictType: 'ast_asset_class' },
  { prop: 'companyNo', label: '所属公司', type: 'dict', dictType: 'sys_factory' },
  { prop: 'assetStatus', label: '状态', type: 'select', options: ASSET_STATUS_OPTIONS },
  { prop: 'deptId', label: '使用部门', type: 'org' }
]
const columns: TableColumn<AssetRow>[] = [
  { prop: 'code', label: '资产编码', width: 170 },
  { prop: 'name', label: '资产名称', minWidth: 140 },
  { prop: 'assetClass', label: '分类', width: 150, type: 'dict', dictType: 'ast_asset_class' },
  { prop: 'spec', label: '规格型号', minWidth: 140 },
  { prop: 'companyNo', label: '所属公司', width: 190, type: 'dict', dictType: 'sys_factory', hidden: true },
  { prop: 'purchaseDate', label: '购置日期', width: 100, type: 'date' },
  { prop: 'originalValue', label: '原值', width: 120, type: 'amount' },
  { prop: 'usefulLifeMonths', label: '年限(月)', width: 80, align: 'right' },
  { prop: 'deptName', label: '使用部门', width: 110 },
  { prop: 'custodianName', label: '保管人', width: 90 },
  { prop: 'location', label: '存放位置', width: 110 },
  { prop: 'customerName', label: '所属客户', width: 120, hidden: true },
  { prop: 'supplierName', label: '供应商', width: 120, hidden: true },
  { prop: 'assetStatus', label: '状态', width: 80, type: 'status', statusMap: ASSET_STATUS }
]

// ---------- 新建 / 编辑 ----------
const visible = ref(false)
const saving = ref(false)
const editing = ref<AssetRow>()
const formRef = ref<FormInstance>()
const empty = (): AssetSave => ({ companyNo: '11', assetClass: '', name: '', nameAbbr: '', purchaseDate: '' })
const form = ref<AssetSave>(empty())
const rules: FormRules = {
  companyNo: [{ required: true, message: '请选择所属公司', trigger: 'change' }],
  assetClass: [{ required: true, message: '请选择资产分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
  nameAbbr: [{ required: true, message: '请输入名称缩写', trigger: 'blur' }, { pattern: /^[A-Za-z]{1,3}$/, message: '1～3 位英文字母，如冲片机 CPJ', trigger: 'blur' }],
  purchaseDate: [{ required: true, message: '请选择购置日期', trigger: 'change' }]
}

function openEdit(r?: AssetRow) {
  editing.value = r
  form.value = r
    ? { companyNo: r.companyNo, assetClass: r.assetClass, name: r.name, nameAbbr: r.nameAbbr, purchaseDate: r.purchaseDate, spec: r.spec, deptId: r.deptId,
        custodianId: r.custodianId, location: r.location, supplierName: r.supplierName, customerName: r.customerName, originalValue: r.originalValue,
        usefulLifeMonths: r.usefulLifeMonths, remark: r.remark, version: r.version }
    : empty()
  visible.value = true
  formRef.value?.clearValidate()
}

/** 编码预览（新建时）：所属公司、分类、缩写、购置日期齐全后显示 */
const preview = ref<string>()
watch(() => [visible.value, form.value.companyNo, form.value.assetClass, form.value.nameAbbr, form.value.purchaseDate], async () => {
  const f = form.value
  if (!visible.value || editing.value || !f.companyNo || !f.assetClass || !/^[A-Za-z]{1,3}$/.test(f.nameAbbr ?? '') || !f.purchaseDate) {
    preview.value = undefined
    return
  }
  const r = await assetApi.preview({ companyNo: f.companyNo, assetClass: f.assetClass, nameAbbr: f.nameAbbr, purchaseDate: f.purchaseDate }).catch(() => undefined)
  preview.value = r?.prefix ? `${r.prefix}###` : undefined
})

/** 《编码规则管理制度》3.5：单价 ≥ 2000 元且使用年限 ≥ 12 个月纳入固定资产（精密仪器、成套工装除外） */
const belowThreshold = computed(() => {
  const v = Number(form.value.originalValue)
  const m = form.value.usefulLifeMonths
  return (form.value.originalValue && v < 2000) || (m !== undefined && m !== null && m < 12)
})

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (form.value.assetClass === 'CU' && !form.value.customerName?.trim()) return ElMessage.warning('客户资产请填写所属客户')
  saving.value = true
  try {
    const data = { ...form.value, nameAbbr: form.value.nameAbbr.toUpperCase(), name: form.value.name.trim() }
    if (editing.value) {
      await assetApi.update(editing.value.id, data)
      ElMessage.success('保存成功')
    } else {
      const id = await assetApi.create(data)
      const created = await assetApi.get(id)
      ElMessage.success(`已新建，资产编码 ${created.code}`)
    }
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 状态 / 报废 / 删除 ----------
async function changeStatus(r: AssetRow, op: 'USE' | 'IDLE' | 'REPAIR' | 'REPAIR_END', label: string) {
  await assetApi.status(r.id, op)
  ElMessage.success(`已${label}`)
  load()
}

const scrapVisible = ref(false)
const scrapping = ref(false)
const scrapRow = ref<AssetRow>()
const scrapForm = ref({ date: '', reason: '' })
function openScrap(r: AssetRow) {
  scrapRow.value = r
  scrapForm.value = { date: new Date().toISOString().slice(0, 10), reason: '' }
  scrapVisible.value = true
}
async function doScrap() {
  if (!scrapForm.value.date) return ElMessage.warning('请选择报废日期')
  if (!scrapForm.value.reason.trim()) return ElMessage.warning('请填写报废原因')
  scrapping.value = true
  try {
    await assetApi.scrap(scrapRow.value!.id, scrapForm.value.date, scrapForm.value.reason.trim())
    ElMessage.success('已报废')
    scrapVisible.value = false
    load()
  } finally {
    scrapping.value = false
  }
}

async function remove(r: AssetRow) {
  await assetApi.remove(r.id)
  ElMessage.success('已删除')
  load()
}

const asRow = (r: unknown) => r as AssetRow
</script>

<template>
  <ErpPage description="固定资产台账：单价 ≥ 2000 元、使用 ≥ 12 个月的设备、仪器、工装、设施；资产编码按《编码规则管理制度》自动生成，报废编码永久封存不复用">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="asset.assets" :actions-width="170" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'ast:asset:create'" type="primary" icon="Plus" @click="openEdit()">新建资产</el-button>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'ast:asset:update', visible: asRow(row).assetStatus !== 'SCRAPPED', handler: () => openEdit(asRow(row)) },
            { label: '闲置', permission: 'ast:asset:update', visible: asRow(row).assetStatus === 'IN_USE', handler: () => changeStatus(asRow(row), 'IDLE', '闲置') },
            { label: '启用', permission: 'ast:asset:update', visible: asRow(row).assetStatus === 'IDLE', handler: () => changeStatus(asRow(row), 'USE', '启用') },
            { label: '送修', permission: 'ast:asset:update', visible: ['IN_USE', 'IDLE'].includes(asRow(row).assetStatus), handler: () => changeStatus(asRow(row), 'REPAIR', '送修') },
            { label: '修复', permission: 'ast:asset:update', visible: asRow(row).assetStatus === 'REPAIRING', handler: () => changeStatus(asRow(row), 'REPAIR_END', '修复') },
            { label: '报废', permission: 'ast:asset:scrap', danger: true, visible: asRow(row).assetStatus !== 'SCRAPPED', handler: () => openScrap(asRow(row)) },
            { label: '删除', permission: 'ast:asset:delete', danger: true, confirm: `确定删除资产「${asRow(row).code} ${asRow(row).name}」吗？编码不会再被使用。`, handler: () => remove(asRow(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'ast:asset:create'" icon="Plus" @click="openEdit()">新建资产</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? `编辑资产 ${editing.code}` : '新建资产'" width="800px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="资产编码">
              <span class="mono code">{{ editing ? editing.code : preview ?? '选择公司、分类，填写名称缩写和购置日期后显示' }}</span>
              <div class="form-tip">LD{公司}-{分类}-{名称缩写}-{年月}-{流水}：公司 1 广东蓝电、0 东莞蓝电；年月为年两位 + 月一位（10/11/12 月为 A/B/C）{{ editing ? '；生成后以下 4 项不能修改' : '' }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属公司" prop="companyNo"><DictSelect v-model="form.companyNo" type="sys_factory" :disabled="!!editing" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产分类" prop="assetClass"><DictSelect v-model="form.assetClass" type="ast_asset_class" :disabled="!!editing" /></el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="资产名称" prop="name"><el-input v-model="form.name" maxlength="128" placeholder="如 冲片机" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="名称缩写" prop="nameAbbr">
              <el-input v-model="form.nameAbbr" maxlength="3" :disabled="!!editing" placeholder="如 CPJ（冲片机）" @input="form.nameAbbr = String($event).toUpperCase()" />
              <div class="form-tip">拼音首字母，不足 3 位自动用 X 补位</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置日期" prop="purchaseDate"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="规格型号"><el-input v-model="form.spec" maxlength="256" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="原值(元)"><AmountInput v-model="form.originalValue" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="使用年限(月)"><el-input-number v-model="form.usefulLifeMonths" :min="1" :max="600" :precision="0" controls-position="right" /></el-form-item>
          </el-col>
          <el-col v-if="belowThreshold" :span="24">
            <el-alert type="warning" :closable="false" show-icon
                      title="单价不足 2000 元或使用年限不足 12 个月，一般不纳入固定资产（精密检测仪器、成套专用工装除外）" class="tip" />
          </el-col>
          <el-col :span="12"><el-form-item label="使用部门"><OrgTreeSelect v-model="form.deptId" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保管人"><UserSelect v-model="form.custodianId" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="存放位置"><el-input v-model="form.location" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商"><el-input v-model="form.supplierName" maxlength="128" /></el-form-item></el-col>
          <el-col v-if="form.assetClass === 'CU'" :span="12">
            <el-form-item label="所属客户" required><el-input v-model="form.customerName" maxlength="128" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scrapVisible" :title="`报废资产 ${scrapRow?.code ?? ''}`" width="480px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="报废日期" required><el-date-picker v-model="scrapForm.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="报废原因" required><el-input v-model="scrapForm.reason" type="textarea" :rows="3" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <p class="text-muted">报废后不能恢复，资产编码永久封存不复用。</p>
      <template #footer>
        <el-button @click="scrapVisible = false">取消</el-button>
        <el-button type="danger" :loading="scrapping" @click="doScrap">确认报废</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.code { font-size: var(--erp-font-size-section-title); }
.tip { margin-bottom: var(--erp-space-4); }
</style>
