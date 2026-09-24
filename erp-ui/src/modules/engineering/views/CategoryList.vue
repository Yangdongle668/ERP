<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { categoryApi, findCategory, type CategoryNode, type CategorySave } from '../api/category'
import { MATERIAL_TYPE_OPTIONS, TRACKING_OPTIONS } from '../api/material'

defineOptions({ name: 'EngineeringCategoryList' })

/** 物料类别（需求 05-01，T6 树形表格 + T2 大弹窗双列表单） */
const router = useRouter()
const query = reactive<{ keyword?: string; status?: string }>({})
const list = ref<CategoryNode[]>([])
const loading = ref(false)
const expandAll = ref(true)
const tableKey = ref(0)

const fields: SearchField[] = [
  { prop: 'keyword', label: '名称/编码' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]

const columns: TableColumn<CategoryNode>[] = [
  { prop: 'name', label: '名称', minWidth: 240 },
  { prop: 'code', label: '编码', width: 110 },
  { prop: 'codePrefix', label: '编码前缀', width: 90 },
  { prop: 'defaultMaterialType', label: '默认类型', width: 90, type: 'enum', options: MATERIAL_TYPE_OPTIONS },
  { prop: 'defaultBaseUom', label: '默认单位', width: 80 },
  { prop: 'defaultTracking', label: '库存管理', width: 90, type: 'enum', options: TRACKING_OPTIONS },
  { prop: 'defaultIqcRequired', label: '来料检验', width: 80, type: 'bool' },
  { prop: 'materialCount', label: '物料数', width: 80, align: 'right', slot: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS }
]

async function load() {
  loading.value = true
  try {
    list.value = await categoryApi.tree({ ...query })
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = undefined
  query.status = undefined
  load()
}

function toggleExpand() {
  expandAll.value = !expandAll.value
  tableKey.value++
}

// ---------- 表单 ----------
const visible = ref(false)
const saving = ref(false)
const editing = ref<CategoryNode>()
const formRef = ref<FormInstance>()
const empty = (): CategorySave => ({
  code: '', name: '', codePrefix: '', defaultMaterialType: 'RAW', defaultTracking: 'NONE', defaultIqcRequired: true, sort: 10
})
const form = ref<CategorySave>(empty())
/** 编码前缀是否被手工改过（未改过时跟随编码） */
const prefixTouched = ref(false)
const snapshot = ref('')

const rules: FormRules = {
  code: [{ required: true, message: '请输入类别编码', trigger: 'blur' }, { pattern: /^[A-Z0-9]{1,16}$/, message: '类别编码为 1～16 位大写字母、数字', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  codePrefix: [{ required: true, message: '请输入编码前缀', trigger: 'blur' }, { pattern: /^[A-Z0-9]{1,8}$/, message: '编码前缀为 1～8 位大写字母、数字', trigger: 'blur' }],
  defaultMaterialType: [{ required: true, message: '请选择默认物料类型', trigger: 'change' }],
  defaultTracking: [{ required: true, message: '请选择默认库存管理方式', trigger: 'change' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

/** 上级类别可选项：去掉自己及下级（R02），去掉已有物料的类别（R04） */
const parentOptions = computed(() => {
  const strip = (nodes: CategoryNode[]): CategoryNode[] =>
    nodes.filter((n) => n.id !== editing.value?.id).map((n) => ({ ...n, children: strip(n.children ?? []) }))
  return strip(list.value)
})

async function open(opts: { parent?: CategoryNode; row?: CategoryNode }) {
  editing.value = opts.row
  prefixTouched.value = !!opts.row
  if (opts.row) {
    const r = opts.row
    form.value = {
      parentId: r.parentId, code: r.code, name: r.name, codePrefix: r.codePrefix, defaultMaterialType: r.defaultMaterialType,
      defaultBaseUom: r.defaultBaseUom, defaultTracking: r.defaultTracking, defaultIqcRequired: r.defaultIqcRequired,
      defaultShelfLifeDays: r.defaultShelfLifeDays, sort: r.sort, remark: r.remark, version: r.version
    }
  } else {
    const p = opts.parent
    // 新增下级：默认属性继承上级
    form.value = {
      ...empty(), parentId: p?.id, sort: await categoryApi.nextSort(p?.id).catch(() => 10),
      ...(p ? { defaultMaterialType: p.defaultMaterialType, defaultBaseUom: p.defaultBaseUom, defaultTracking: p.defaultTracking,
        defaultIqcRequired: p.defaultIqcRequired, defaultShelfLifeDays: p.defaultShelfLifeDays } : {})
    }
  }
  snapshot.value = JSON.stringify(form.value)
  visible.value = true
  formRef.value?.clearValidate()
}

function onCode(v: string) {
  form.value.code = v.toUpperCase().replace(/[^A-Z0-9]/g, '')
  if (!prefixTouched.value) form.value.codePrefix = form.value.code.slice(0, 8)
}

function onPrefix(v: string) {
  prefixTouched.value = true
  form.value.codePrefix = v.toUpperCase().replace(/[^A-Z0-9]/g, '')
}

const prefixChanged = computed(() => !!editing.value && form.value.codePrefix !== editing.value.codePrefix)

async function beforeClose(done: () => void) {
  if (JSON.stringify(form.value) !== snapshot.value) {
    const ok = await ElMessageBox.confirm('有未保存的修改，确定关闭吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) return
  }
  done()
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { ...form.value, name: form.value.name.trim() }
    if (editing.value) await categoryApi.update(editing.value.id, data)
    else await categoryApi.create(data)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 状态 ----------
async function changeStatus(row: CategoryNode, action: 'enable' | 'disable') {
  if (action === 'disable') {
    await ElMessageBox.confirm(`停用后新建物料时不能选择类别「${row.name}」，其下已有物料不受影响。确定停用吗？`, '提示', { type: 'warning' })
  }
  await categoryApi[action](row.id)
  ElMessage.success(action === 'enable' ? '已启用' : '已停用')
  load()
}

async function remove(row: CategoryNode) {
  await categoryApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

const asCat = (r: unknown) => r as CategoryNode
const parentName = computed(() => findCategory(list.value, form.value.parentId)?.name)

onMounted(load)
</script>

<template>
  <ErpPage description="物料的多级分类（最多 5 级）：决定物料编码前缀和新建物料的默认属性；物料只能挂在末级类别">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="load" @reset="reset" /></template>
      <ErpTable
        :key="tableKey"
        :columns="columns"
        :data="list"
        :loading="loading"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAll"
        :actions-width="200"
        storage-key="engineering.category"
        empty-text="没有符合条件的类别"
        @refresh="load"
      >
        <template #toolbar>
          <el-button v-perm="'eng:category:create'" type="primary" icon="Plus" @click="open({})">新建一级类别</el-button>
          <el-button @click="toggleExpand">{{ expandAll ? '收起全部' : '展开全部' }}</el-button>
        </template>
        <template #col-materialCount="{ row }">
          <el-link v-if="asCat(row).materialCount" type="primary" underline="never" class="num"
                   @click="router.push({ path: '/engineering/material', query: { categoryId: asCat(row).id } })">{{ asCat(row).materialCount }}</el-link>
          <span v-else class="text-muted num">0</span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '新增下级', permission: 'eng:category:create', visible: asCat(row).status === 'ENABLED' && !asCat(row).hasMaterial && asCat(row).level < 5, handler: () => open({ parent: asCat(row) }) },
            { label: '编辑', permission: 'eng:category:update', handler: () => open({ row: asCat(row) }) },
            { label: '停用', permission: 'eng:category:update', visible: asCat(row).status === 'ENABLED', handler: () => changeStatus(asCat(row), 'disable') },
            { label: '启用', permission: 'eng:category:update', visible: asCat(row).status === 'DISABLED', handler: () => changeStatus(asCat(row), 'enable') },
            { label: '删除', permission: 'eng:category:delete', danger: true, confirm: `确定删除类别「${asCat(row).code} ${asCat(row).name}」吗？删除后不可恢复。`, handler: () => remove(asCat(row)) }
          ]" />
        </template>
      </ErpTable>
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? '编辑物料类别' : parentName ? `新增下级类别 - ${parentName}` : '新建一级类别'" width="960px"
               :close-on-click-modal="false" :before-close="beforeClose" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="上级类别">
              <el-tree-select v-model="form.parentId" :data="parentOptions" node-key="id" :props="{ label: 'name', children: 'children' }"
                              check-strictly clearable filterable placeholder="无（一级类别）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编码" prop="code">
              <el-input :model-value="form.code" maxlength="16" :disabled="!!editing?.hasMaterial" placeholder="大写字母、数字" @update:model-value="onCode" />
              <div v-if="editing?.hasMaterial" class="form-tip">已被物料使用，不能修改编码</div>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="编码前缀" prop="codePrefix">
              <el-input :model-value="form.codePrefix" maxlength="8" @update:model-value="onPrefix" />
              <div class="form-tip">{{ prefixChanged ? '只影响之后新建的物料，已有物料编码不变' : `物料编码示例：${form.codePrefix || 'XX'}00001` }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认物料类型" prop="defaultMaterialType">
              <el-select v-model="form.defaultMaterialType"><el-option v-for="o in MATERIAL_TYPE_OPTIONS" :key="o.value" v-bind="o" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="默认基本单位"><UomSelect v-model="form.defaultBaseUom" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="默认库存管理" prop="defaultTracking">
              <el-radio-group v-model="form.defaultTracking"><el-radio v-for="o in TRACKING_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="默认来料检验"><el-switch v-model="form.defaultIqcRequired" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="默认保质期(天)">
              <el-input-number v-model="form.defaultShelfLifeDays" :min="1" :max="3650" :precision="0" controls-position="right" class="w160" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sort"><el-input-number v-model="form.sort" :min="0" :max="99999" controls-position="right" class="w160" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="beforeClose(() => (visible = false))">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
