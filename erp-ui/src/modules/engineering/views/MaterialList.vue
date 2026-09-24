<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import {
  materialApi,
  MATERIAL_STATUS,
  MATERIAL_TYPE_OPTIONS,
  type Material,
  type MaterialQuery,
  type MaterialSave
} from '../api/material'

defineOptions({ name: 'EngMaterialList' })

/**
 * 物料列表：前端列表页（T1）的参考实现。新模块照此组织页面：
 * ErpPage（标题、描述） → ErpPanel（filter 插槽放 ErpSearchForm） → ErpTable（工具栏、列定义、行操作） → ErpPagination；
 * 新建/编辑用 T2 弹窗。样式只用公共组件与 token，不在页面里写颜色和字号。
 */
const { query, list, total, loading, load, search, reset } = useListPage<Omit<MaterialQuery, 'pageNo' | 'pageSize'>, Material>({
  api: (q) => materialApi.page(q as MaterialQuery)
})

const fields: SearchField[] = [
  { prop: 'code', label: '编码', placeholder: '编码前缀', upper: true },
  { prop: 'name', label: '名称', placeholder: '名称关键字' },
  { prop: 'materialType', label: '类型', type: 'select', options: MATERIAL_TYPE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: Object.entries(MATERIAL_STATUS).map(([value, s]) => ({ value, label: s.label })) }
]

const columns: TableColumn<Material>[] = [
  { prop: 'code', label: '编码', width: 140, type: 'link', onClick: (r) => openEdit(r) },
  { prop: 'name', label: '名称', minWidth: 180 },
  { prop: 'spec', label: '规格型号', minWidth: 180 },
  { prop: 'materialType', label: '类型', width: 100, type: 'enum', options: MATERIAL_TYPE_OPTIONS },
  { prop: 'baseUom', label: '单位', width: 80 },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: MATERIAL_STATUS },
  { prop: 'updatedAt', label: '更新时间', width: 150, type: 'datetime' }
]

// ---------- 新建 / 编辑（T2 弹窗） ----------
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<string>()
const editingStatus = ref<Material['status']>('DRAFT')
const formRef = ref<FormInstance>()
const emptyForm = (): MaterialSave => ({ name: '', baseUom: 'PCS', materialType: 'RAW' })
const form = ref<MaterialSave>(emptyForm())
const rules: FormRules = {
  name: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  baseUom: [{ required: true, message: '请选择基本单位', trigger: 'change' }]
}

function openCreate() {
  editingId.value = undefined
  editingStatus.value = 'DRAFT'
  form.value = emptyForm()
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function openEdit(row: Material) {
  const m = await materialApi.get(row.id)
  editingId.value = m.id
  editingStatus.value = m.status
  form.value = {
    code: m.code, name: m.name, nameEn: m.nameEn, spec: m.spec, materialType: m.materialType,
    categoryId: m.categoryId, baseUom: m.baseUom, remark: m.remark, version: m.version
  }
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editingId.value) await materialApi.update(editingId.value, form.value)
    else await materialApi.create(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function changeStatus(row: Material, action: 'enable' | 'disable') {
  await materialApi[action](row.id)
  ElMessage.success(action === 'enable' ? '已启用' : '已停用')
  load()
}

async function remove(row: Material) {
  await materialApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

/** 插槽的 row 类型为 any，这里收窄为 Material */
const asMaterial = (row: unknown) => row as Material
</script>

<template>
  <ErpPage description="物料主数据：编码、规格、类型与基本单位；草稿状态可删除，启用后才能在单据中使用">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.material" :actions-width="160" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:material:create'" type="primary" icon="Plus" @click="openCreate">新建物料</el-button>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:material:update', handler: () => openEdit(asMaterial(row)) },
            { label: '启用', permission: 'eng:material:enable', visible: asMaterial(row).status !== 'ENABLED', handler: () => changeStatus(asMaterial(row), 'enable') },
            { label: '停用', permission: 'eng:material:disable', visible: asMaterial(row).status === 'ENABLED', confirm: `停用后物料「${asMaterial(row).code}」不能在新单据中使用。确定停用吗？`, handler: () => changeStatus(asMaterial(row), 'disable') },
            { label: '删除', permission: 'eng:material:delete', danger: true, visible: asMaterial(row).status === 'DRAFT', confirm: `确定删除物料「${asMaterial(row).code} ${asMaterial(row).name}」吗？删除后不可恢复。`, handler: () => remove(asMaterial(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:material:create'" icon="Plus" @click="openCreate">新建物料</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑物料' : '新建物料'" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="editingStatus !== 'DRAFT'" placeholder="留空则按编码规则自动生成" @input="form.code = String($event).toUpperCase()" />
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="英文名称"><el-input v-model="form.nameEn" maxlength="128" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="form.spec" type="textarea" :rows="2" maxlength="256" /></el-form-item>
        <el-form-item label="物料类型" prop="materialType">
          <el-select v-model="form.materialType" :disabled="editingStatus !== 'DRAFT'">
            <el-option v-for="o in MATERIAL_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="基本单位" prop="baseUom">
          <UomSelect v-model="form.baseUom" :disabled="editingStatus !== 'DRAFT'" />
          <div class="form-tip">启用后不能修改</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
