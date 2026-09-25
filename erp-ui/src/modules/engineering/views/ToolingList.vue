<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { download } from '@/api/http'
import { OWNERSHIP_OPTIONS, toolingApi, TOOLING_STATUS, TOOLING_STATUS_OPTIONS, type ToolingQuery, type ToolingRow, type ToolingSave } from '../api/tooling'

defineOptions({ name: 'EngToolingList' })

/** 工装台账（需求 05-08 3.1，T1 + T2 弹窗）：寿命使用率 ≥ 预警比例标橙，达到寿命标红 */
const router = useRouter()
const importRef = ref<{ open: () => void }>()
type Query = Omit<ToolingQuery, 'pageNo' | 'pageSize'>
const { query, list, total, loading, load, search, reset } = useListPage<Query, ToolingRow>({ api: (q) => toolingApi.page(q as ToolingQuery), refreshOnActivated: true })

const fields: SearchField[] = [
  { prop: 'code', label: '编号', upper: true },
  { prop: 'name', label: '名称' },
  { prop: 'toolingType', label: '类型', type: 'dict', dictType: 'eng_tooling_type' },
  { prop: 'toolingStatus', label: '状态', type: 'select', options: TOOLING_STATUS_OPTIONS },
  { prop: 'materialId', label: '适用物料', type: 'slot' },
  { prop: 'lifeWarning', label: '寿命预警', type: 'slot' }
]
const columns: TableColumn<ToolingRow>[] = [
  { prop: 'code', label: '编号', width: 130, type: 'link', onClick: (r) => router.push(`/engineering/tooling/${r.id}`) },
  { prop: 'name', label: '名称', minWidth: 150 },
  { prop: 'toolingType', label: '类型', width: 80, type: 'dict', dictType: 'eng_tooling_type' },
  { prop: 'spec', label: '规格', minWidth: 120, hidden: true },
  { prop: 'ownership', label: '归属', width: 90, formatter: (r) => (r.ownership === 'CUSTOMER' ? `客户：${r.customerName ?? ''}` : '自有') },
  { prop: 'cavity', label: '模穴', width: 60, align: 'right' },
  { prop: 'usedCount', label: '使用/寿命', width: 180, slot: true },
  { prop: 'toMaintain', label: '距保养', width: 90, align: 'right', formatter: (r) => (r.toMaintain === undefined || r.toMaintain === null ? '-' : String(r.toMaintain)) },
  { prop: 'materials', label: '适用物料', minWidth: 160, formatter: (r) => r.materials.map((m) => m.code).join('、') || '-' },
  { prop: 'location', label: '存放位置', width: 100 },
  { prop: 'holderName', label: '领用人', width: 90 },
  { prop: 'toolingStatus', label: '状态', width: 80, type: 'status', statusMap: TOOLING_STATUS }
]
const lifeClass = (r: ToolingRow) => (r.lifePct && Number(r.lifePct) >= 1 ? 'text-danger' : r.lifeWarn ? 'text-warning' : '')

// ---------- 新建 / 编辑 ----------
const visible = ref(false)
const saving = ref(false)
const editing = ref<ToolingRow>()
const formRef = ref<FormInstance>()
const form = ref<ToolingSave>({ name: '', ownership: 'OWN', materialIds: [] })
const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  toolingType: [{ required: true, message: '请选择工装类型', trigger: 'change' }]
}
function openEdit(r?: ToolingRow) {
  editing.value = r
  form.value = r
    ? { code: r.code, name: r.name, toolingType: r.toolingType, spec: r.spec, ownership: r.ownership, customerId: r.customerId, cavity: r.cavity, designLife: r.designLife,
        maintainCycle: r.maintainCycle, location: r.location, supplierName: r.supplierName, purchaseDate: r.purchaseDate, purchaseAmount: r.purchaseAmount,
        allowOverLife: r.allowOverLife, overLifeReason: r.overLifeReason, materialIds: [...r.materialIds], remark: r.remark, version: r.version }
    : { name: '', ownership: 'OWN', cavity: 1, initialUsedCount: 0, materialIds: [] }
  visible.value = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (form.value.ownership === 'CUSTOMER' && !form.value.customerId) return ElMessage.warning('客户资产必须选择客户')
  if (form.value.allowOverLife && !form.value.overLifeReason?.trim()) return ElMessage.warning('请填写超寿命继续使用的原因')
  saving.value = true
  try {
    const data = { ...form.value, code: form.value.code?.trim() || undefined }
    if (editing.value) await toolingApi.update(editing.value.id, data)
    else await toolingApi.create(data)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function exportList() {
  await download('/engineering/toolings/export', { ...query, pageNo: undefined, pageSize: undefined }, '工装台账.xlsx')
}
const asRow = (r: unknown) => r as ToolingRow
</script>

<template>
  <ErpPage description="模具、治具、夹具、检具、钢网的台账；报工时按模穴折算使用次数，达到预警比例提醒，达到寿命不能继续使用">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" placeholder="全部" class="w200" /></template>
          <template #field-lifeWarning><el-switch v-model="query.lifeWarning" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.tooling" :actions-width="100" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:tooling:create'" type="primary" icon="Plus" @click="openEdit()">新建工装</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Upload" tooltip="导入" permission="eng:tooling:import" @click="importRef?.open()" />
          <ErpIconButton icon="Download" tooltip="导出" permission="eng:tooling:export" @click="exportList" />
        </template>
        <template #col-usedCount="{ row }">
          <span :class="['num', lifeClass(asRow(row))]">{{ asRow(row).usedCount }} / {{ asRow(row).designLife ?? '-' }}</span>
          <span v-if="asRow(row).lifePct" class="text-muted pct">{{ Math.round(Number(asRow(row).lifePct) * 100) }}%</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:tooling:update', visible: asRow(row).toolingStatus !== 'SCRAPPED', handler: () => openEdit(asRow(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:tooling:create'" icon="Plus" @click="openEdit()">新建工装</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? `编辑工装 ${editing.code}` : '新建工装'" width="760px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="编号"><el-input v-model="form.code" maxlength="32" :disabled="!!editing" placeholder="为空时自动生成" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="类型" prop="toolingType"><DictSelect v-model="form.toolingType" type="eng_tooling_type" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="form.spec" maxlength="256" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="归属">
              <el-radio-group v-model="form.ownership"><el-radio v-for="o in OWNERSHIP_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item v-if="form.ownership === 'CUSTOMER'" label="客户" required><CustomerSelect v-model="form.customerId" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="模穴数"><el-input-number v-model="form.cavity" :min="1" :precision="0" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="设计寿命(次)"><el-input-number v-model="form.designLife" :min="1" :precision="0" /></el-form-item></el-col>
          <el-col v-if="!editing" :span="12"><el-form-item label="初始使用次数"><el-input-number v-model="form.initialUsedCount" :min="0" :precision="0" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保养周期(次)"><el-input-number v-model="form.maintainCycle" :min="1" :precision="0" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="存放位置"><el-input v-model="form.location" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商"><el-input v-model="form.supplierName" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="购置日期"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="购置金额"><AmountInput v-model="form.purchaseAmount" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="适用物料"><MaterialSelect v-model="form.materialIds" multiple /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="超寿命继续使用"><el-switch v-model="form.allowOverLife" /></el-form-item></el-col>
          <el-col v-if="form.allowOverLife" :span="12"><el-form-item label="原因" required><el-input v-model="form.overLifeReason" maxlength="256" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog ref="importRef" title="导入工装" base="/engineering/toolings" template-name="工装" allow-partial @done="load" />
  </ErpPage>
</template>

<style scoped>
.pct { margin-left: var(--erp-space-2); }
</style>
