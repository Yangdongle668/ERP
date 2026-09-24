<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useBaseDataStore } from '@/stores/baseData'
import { UOM_CATEGORY_OPTIONS, uomApi, type ConversionRow, type UomRow, type UomSave } from '../api/uom'

defineOptions({ name: 'SystemUomList' })

/** 计量单位与通用换算（01-06，上下两个卡片） */
const baseData = useBaseDataStore()
const query = reactive<{ keyword?: string; category?: string; status?: string }>({})
const list = ref<UomRow[]>([])
const conversions = ref<ConversionRow[]>([])
const loading = ref(false)

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' },
  { prop: 'category', label: '类别', type: 'select', options: UOM_CATEGORY_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]

const columns: TableColumn<UomRow>[] = [
  { prop: 'code', label: '编码', width: 100 },
  { prop: 'name', label: '名称', width: 100 },
  { prop: 'nameEn', label: '英文名称', width: 120 },
  { prop: 'category', label: '类别', width: 90, type: 'enum', options: UOM_CATEGORY_OPTIONS },
  { prop: 'precision', label: '精度', width: 70, align: 'right' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS }
]

async function load() {
  loading.value = true
  try {
    ;[list.value, conversions.value] = await Promise.all([uomApi.list({ ...query }), uomApi.conversions()])
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { keyword: undefined, category: undefined, status: undefined })
  load()
}

function changed() {
  load()
  baseData.loadUoms(true)
}

// ---------- 单位 ----------
const visible = ref(false)
const editing = ref<UomRow>()
const formRef = ref<FormInstance>()
const form = ref<UomSave>({ code: '', name: '', category: 'COUNT', precision: 0, sort: 10 })
const rules: FormRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }, { pattern: /^[A-Za-z0-9]{1,16}$/, message: '编码为 1～16 位字母数字', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }]
}

function openUom(u?: UomRow) {
  editing.value = u
  form.value = u ? { code: u.code, name: u.name, nameEn: u.nameEn, category: u.category, precision: u.precision, sort: u.sort, version: u.version }
    : { code: '', name: '', category: 'COUNT', precision: 0, sort: list.value.reduce((m, x) => Math.max(m, x.sort), 0) + 10 }
  visible.value = true
  formRef.value?.clearValidate()
}

async function saveUom() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const data = { ...form.value, code: form.value.code.toUpperCase() }
  if (editing.value) await uomApi.update(editing.value.id, data)
  else await uomApi.create(data)
  ElMessage.success('保存成功')
  visible.value = false
  changed()
}

async function uomAction(u: UomRow, action: 'enable' | 'disable' | 'remove') {
  if (action === 'disable') await ElMessageBox.confirm(`停用后单位「${u.code}」不能在新的物料、单据中使用。确定停用吗？`, '提示', { type: 'warning' })
  await uomApi[action](u.id)
  ElMessage.success(action === 'remove' ? '删除成功' : '操作成功')
  changed()
}

// ---------- 换算 ----------
const convVisible = ref(false)
const convEditing = ref<ConversionRow>()
const conv = ref({ fromUom: '', toUom: '', rate: '' as string | undefined })
const fromCategory = computed(() => list.value.find((u) => u.code === conv.value.fromUom)?.category)
const convPreview = computed(() => {
  const r = Number(conv.value.rate)
  if (!conv.value.fromUom || !conv.value.toUom || !r) return ''
  return `1 ${conv.value.fromUom} = ${r} ${conv.value.toUom}，1 ${conv.value.toUom} = ${Number((1 / r).toPrecision(10))} ${conv.value.fromUom}`
})

function openConv(c?: ConversionRow) {
  convEditing.value = c
  conv.value = c ? { fromUom: c.fromUom, toUom: c.toUom, rate: c.rate } : { fromUom: '', toUom: '', rate: undefined }
  convVisible.value = true
}

async function saveConv() {
  if (!conv.value.fromUom || !conv.value.toUom || !conv.value.rate) {
    ElMessage.warning('请填写源单位、换算率和目标单位')
    return
  }
  const data = { fromUom: conv.value.fromUom, toUom: conv.value.toUom, rate: conv.value.rate }
  if (convEditing.value) await uomApi.updateConversion(convEditing.value.id, { ...data, version: convEditing.value.version })
  else await uomApi.createConversion(data)
  ElMessage.success('保存成功')
  convVisible.value = false
  changed()
}

async function removeConv(c: ConversionRow) {
  await uomApi.removeConversion(c.id)
  ElMessage.success('删除成功')
  changed()
}

const asUom = (r: unknown) => r as UomRow

onMounted(load)
</script>

<template>
  <ErpPage description="数量单位与精度；通用换算用于所有物料（物料专属换算在物料中维护）">
    <ErpPanel title="计量单位">
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="load" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.uom" :actions-width="170" @refresh="load">
        <template #toolbar><el-button v-perm="'system:uom:create'" type="primary" icon="Plus" @click="openUom()">新建单位</el-button></template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'system:uom:update', handler: () => openUom(asUom(row)) },
            { label: '停用', permission: 'system:uom:update', visible: asUom(row).status === 'ENABLED', handler: () => uomAction(asUom(row), 'disable') },
            { label: '启用', permission: 'system:uom:update', visible: asUom(row).status === 'DISABLED', handler: () => uomAction(asUom(row), 'enable') },
            { label: '删除', permission: 'system:uom:delete', danger: true, visible: !asUom(row).builtin, confirm: `确定删除单位「${asUom(row).code} ${asUom(row).name}」吗？删除后不可恢复。`, handler: () => uomAction(asUom(row), 'remove') }
          ]" />
        </template>
      </ErpTable>
    </ErpPanel>

    <ErpPanel title="通用换算" description="双向生效：1 A = x B 同时得到 1 B = 1/x A">
      <template #extra>
        <el-button v-perm="'system:uom:update'" icon="Plus" @click="openConv()">新建换算</el-button>
      </template>
      <el-table :data="conversions">
        <el-table-column label="换算关系" min-width="260">
          <template #default="{ row }"><span class="num">1 {{ row.fromUom }}</span><span class="eq">=</span><span class="num">{{ row.rate }} {{ row.toUom }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <RowActions :actions="[
              { label: '编辑', permission: 'system:uom:update', handler: () => openConv(row as ConversionRow) },
              { label: '删除', permission: 'system:uom:update', danger: true, confirm: '确定删除该换算吗？', handler: () => removeConv(row as ConversionRow) }
            ]" />
          </template>
        </el-table-column>
        <template #empty><ErpEmpty description="暂无通用换算" compact /></template>
      </el-table>
    </ErpPanel>

  <el-dialog v-model="visible" :title="editing ? '编辑计量单位' : '新建计量单位'" width="640px" :close-on-click-modal="false" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="编码" prop="code"><el-input v-model="form.code" :disabled="!!editing" maxlength="16" @input="form.code = String($event).toUpperCase()" /></el-form-item>
      <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="16" /></el-form-item>
      <el-form-item label="英文名称"><el-input v-model="form.nameEn" maxlength="32" /></el-form-item>
      <el-form-item label="类别" prop="category">
        <el-select v-model="form.category" :disabled="editing?.builtin"><el-option v-for="o in UOM_CATEGORY_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
      </el-form-item>
      <el-form-item label="精度">
        <el-input-number v-model="form.precision" :min="editing ? editing.precision : 0" :max="4" controls-position="right" />
        <div class="form-tip">数量小数位数 0～4；只能调大，调小会截断已有数据</div>
      </el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" controls-position="right" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="saveUom">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="convVisible" :title="convEditing ? '编辑换算' : '新建换算'" width="480px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="源单位" required><UomSelect v-model="conv.fromUom" /></el-form-item>
      <el-form-item label="换算率" required><NumberInput v-model="conv.rate" :precision="6" trim-zeros /></el-form-item>
      <el-form-item label="目标单位" required><UomSelect v-model="conv.toUom" :category="fromCategory" /></el-form-item>
      <el-form-item v-if="convPreview"><span class="form-tip">{{ convPreview }}</span></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="convVisible = false">取消</el-button>
      <el-button type="primary" @click="saveConv">保存</el-button>
    </template>
  </el-dialog>
  </ErpPage>
</template>

<style scoped>
.eq { margin: 0 12px; color: var(--erp-color-text-tertiary); }
</style>
