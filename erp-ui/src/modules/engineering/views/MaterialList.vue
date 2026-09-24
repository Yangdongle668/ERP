<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  materialApi,
  MATERIAL_STATUS,
  MATERIAL_TYPE_OPTIONS,
  type Material,
  type MaterialQuery,
  type MaterialSave
} from '../api/material'

/** 物料列表：前端 CRUD 页面的参考实现（查询、分页、新建/编辑弹窗、状态操作、按钮权限）。 */
const query = reactive<MaterialQuery>({ pageNo: 1, pageSize: 20 })
const list = ref<Material[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await materialApi.page(query)
    list.value = page.list
    total.value = Number(page.total) || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNo = 1
  load()
}

function reset() {
  Object.assign(query, { code: undefined, name: undefined, materialType: undefined, status: undefined, pageNo: 1 })
  load()
}

// ---------- 新建 / 编辑 ----------
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
  baseUom: [{ required: true, message: '请输入基本单位', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = undefined
  editingStatus.value = 'DRAFT'
  form.value = emptyForm()
  dialogVisible.value = true
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
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editingId.value) {
      await materialApi.update(editingId.value, form.value)
    } else {
      await materialApi.create(form.value)
    }
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
  await ElMessageBox.confirm(`确定删除物料「${row.code} ${row.name}」吗？`, '提示', { type: 'warning' })
  await materialApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

/** el-table 插槽的 row 类型为 DefaultRow，这里收窄为 Material */
const asMaterial = (row: unknown) => row as Material

const typeLabel = (t: string) => MATERIAL_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? t

onMounted(load)
</script>

<template>
  <el-card>
    <el-form :model="query" inline @submit.prevent="search">
      <el-form-item label="编码"><el-input v-model="query.code" placeholder="编码前缀" clearable /></el-form-item>
      <el-form-item label="名称"><el-input v-model="query.name" placeholder="名称" clearable /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.materialType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="o in MATERIAL_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(s, k) in MATERIAL_STATUS" :key="k" :value="k" :label="s.label" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="page-toolbar">
      <el-button v-perm="'eng:material:create'" type="primary" icon="Plus" @click="openCreate">新建物料</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="code" label="编码" width="130" />
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="spec" label="规格" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.materialType) }}</template>
      </el-table-column>
      <el-table-column prop="baseUom" label="单位" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="MATERIAL_STATUS[asMaterial(row).status].type">
            {{ MATERIAL_STATUS[asMaterial(row).status].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="165" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'eng:material:update'" link type="primary" @click="openEdit(asMaterial(row))">编辑</el-button>
          <el-button v-if="row.status !== 'ENABLED'" v-perm="'eng:material:enable'" link type="success" @click="changeStatus(asMaterial(row), 'enable')">启用</el-button>
          <el-button v-if="row.status === 'ENABLED'" v-perm="'eng:material:disable'" link type="warning" @click="changeStatus(asMaterial(row), 'disable')">停用</el-button>
          <el-button v-if="row.status === 'DRAFT'" v-perm="'eng:material:delete'" link type="danger" @click="remove(asMaterial(row))">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-pagination">
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑物料' : '新建物料'" width="560px" :close-on-click-modal="false">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="编码">
        <el-input v-model="form.code" :disabled="editingStatus !== 'DRAFT'" placeholder="留空则自动生成" />
      </el-form-item>
      <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="英文名称"><el-input v-model="form.nameEn" /></el-form-item>
      <el-form-item label="规格型号"><el-input v-model="form.spec" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="物料类型" prop="materialType">
        <el-select v-model="form.materialType" :disabled="editingStatus !== 'DRAFT'">
          <el-option v-for="o in MATERIAL_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="基本单位" prop="baseUom">
        <el-input v-model="form.baseUom" :disabled="editingStatus !== 'DRAFT'" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>
