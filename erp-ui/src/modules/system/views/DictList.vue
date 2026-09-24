<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ENABLE_STATUS } from '@/components'
import { useDictStore, tagTypeOf } from '@/stores/dict'
import { dictApi, type DictItemRow, type DictItemSave, type DictTypeRow } from '../api/dict'

defineOptions({ name: 'SystemDictList' })

/** 数据字典（01-04，左右分栏）：左侧类型，右侧字典项 */
const dictStore = useDictStore()
const typeQuery = reactive({ keyword: '', pageNo: 1, pageSize: 20 })
const types = ref<DictTypeRow[]>([])
const typeTotal = ref(0)
const typeLoading = ref(false)
const current = ref<DictTypeRow>()
const items = ref<DictItemRow[]>([])
const itemLoading = ref(false)

const TAGS = ['DEFAULT', 'PRIMARY', 'SUCCESS', 'WARNING', 'DANGER', 'INFO'] as const

async function loadTypes(keepCurrent = true) {
  typeLoading.value = true
  try {
    const page = await dictApi.types({ ...typeQuery })
    types.value = page.list
    typeTotal.value = Number(page.total) || 0
    const keep = keepCurrent && current.value ? types.value.find((t) => t.id === current.value!.id) : undefined
    select(keep ?? types.value[0])
  } finally {
    typeLoading.value = false
  }
}

async function select(t?: DictTypeRow) {
  current.value = t
  items.value = []
  if (!t) return
  itemLoading.value = true
  try {
    items.value = await dictApi.items(t.code)
  } finally {
    itemLoading.value = false
  }
}

async function changed() {
  await select(current.value)
  dictStore.load(true)
}

// ---------- 类型表单 ----------
const typeVisible = ref(false)
const typeEditing = ref<DictTypeRow>()
const typeFormRef = ref<FormInstance>()
const typeForm = ref({ code: '', name: '', status: 'ENABLED', remark: '' })
const typeRules: FormRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }, { pattern: /^[a-z][a-z0-9_]{1,63}$/, message: '编码格式不正确，2～64 位，小写字母开头，小写字母数字下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function openType(t?: DictTypeRow) {
  typeEditing.value = t
  typeForm.value = t ? { code: t.code, name: t.name, status: t.status, remark: t.remark ?? '' } : { code: '', name: '', status: 'ENABLED', remark: '' }
  typeVisible.value = true
  typeFormRef.value?.clearValidate()
}

async function saveType() {
  if (!(await typeFormRef.value?.validate().catch(() => false))) return
  if (typeEditing.value) await dictApi.updateType(typeEditing.value.id, { ...typeForm.value, version: typeEditing.value.version })
  else await dictApi.createType(typeForm.value)
  ElMessage.success('保存成功')
  typeVisible.value = false
  await loadTypes()
  dictStore.load(true)
}

async function removeType(t: DictTypeRow) {
  await dictApi.removeType(t.id)
  ElMessage.success('删除成功')
  current.value = undefined
  loadTypes(false)
}

// ---------- 字典项表单 ----------
const itemVisible = ref(false)
const itemEditing = ref<DictItemRow>()
const itemFormRef = ref<FormInstance>()
const itemForm = ref<DictItemSave>({ typeCode: '', value: '', label: '', tagType: 'DEFAULT', sort: 10, isDefault: false })
const itemRules: FormRules = {
  value: [{ required: true, message: '请输入值', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_]{1,32}$/, message: '值为 1～32 位大写字母数字下划线', trigger: 'blur' }],
  label: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

function openItem(i?: DictItemRow) {
  itemEditing.value = i
  itemForm.value = i
    ? { typeCode: i.typeCode, value: i.value, label: i.label, labelEn: i.labelEn, tagType: i.tagType, sort: i.sort, isDefault: i.isDefault, remark: i.remark, version: i.version }
    : { typeCode: current.value!.code, value: '', label: '', tagType: 'DEFAULT', sort: items.value.reduce((m, x) => Math.max(m, x.sort), 0) + 10, isDefault: false }
  itemVisible.value = true
  itemFormRef.value?.clearValidate()
}

async function saveItem() {
  if (!(await itemFormRef.value?.validate().catch(() => false))) return
  const data = { ...itemForm.value, value: itemForm.value.value.toUpperCase() }
  if (itemEditing.value) await dictApi.updateItem(itemEditing.value.id, data)
  else await dictApi.createItem(data)
  ElMessage.success('保存成功')
  itemVisible.value = false
  changed()
}

async function itemAction(i: DictItemRow, action: 'enableItem' | 'disableItem' | 'removeItem') {
  await dictApi[action](i.id)
  ElMessage.success(action === 'removeItem' ? '删除成功' : '操作成功')
  changed()
}

async function refreshCache() {
  await dictApi.refreshCache()
  await dictStore.load(true)
  ElMessage.success('缓存已刷新')
}

const asType = (r: unknown) => r as DictTypeRow
const asItem = (r: unknown) => r as DictItemRow

onMounted(() => loadTypes(false))
</script>

<template>
  <div class="dict-page">
    <el-card class="left">
      <div class="bar">
        <el-input v-model="typeQuery.keyword" placeholder="编码/名称" clearable class="kw" @keyup.enter="typeQuery.pageNo = 1; loadTypes()" @clear="loadTypes()" />
        <el-button icon="Search" @click="typeQuery.pageNo = 1; loadTypes()">查询</el-button>
        <el-button v-perm="'system:dict:create'" type="primary" icon="Plus" @click="openType()">新建类型</el-button>
      </div>
      <el-table v-loading="typeLoading" :data="types" highlight-current-row border row-key="id" :current-row-key="current?.id" @row-click="(r: any) => select(r)">
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column label="名称" min-width="130">
          <template #default="{ row }">{{ asType(row).name }}<el-tag v-if="asType(row).builtin" size="small" type="info" class="tag">内置</el-tag></template>
        </el-table-column>
        <el-table-column prop="moduleName" label="模块" width="90" />
        <el-table-column label="状态" width="70" align="center"><template #default="{ row }"><StatusTag :value="asType(row).status" :map="ENABLE_STATUS" /></template></el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <RowActions :actions="[
              { label: '编辑', permission: 'system:dict:update', handler: () => openType(asType(row)) },
              { label: '删除', permission: 'system:dict:delete', danger: true, visible: !asType(row).builtin, confirm: `确定删除字典类型「${asType(row).code} ${asType(row).name}」吗？`, handler: () => removeType(asType(row)) }
            ]" />
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="typeQuery.pageNo" :page-size="typeQuery.pageSize" :total="typeTotal" layout="total, prev, pager, next" size="small" class="pager" @current-change="loadTypes()" />
    </el-card>
    <el-card class="right">
      <div class="bar">
        <span class="title">字典项<template v-if="current">：{{ current.name }} <span class="code">{{ current.code }}</span></template></span>
        <div class="spacer" />
        <el-button v-if="current" v-perm="'system:dict:create'" type="primary" icon="Plus" @click="openItem()">新建字典项</el-button>
        <el-tooltip content="刷新缓存" placement="top"><el-button v-perm="'system:dict:update'" icon="Refresh" circle @click="refreshCache" /></el-tooltip>
      </div>
      <el-table v-loading="itemLoading" :data="items" border>
        <el-table-column prop="value" label="值" width="140" />
        <el-table-column label="标签" width="150">
          <template #default="{ row }">
            <el-tag v-if="asItem(row).tagType !== 'DEFAULT'" :type="tagTypeOf(asItem(row).tagType as any) || undefined">{{ asItem(row).label }}</el-tag>
            <span v-else>{{ asItem(row).label }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="labelEn" label="英文标签" min-width="150" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="70" align="right" />
        <el-table-column label="默认" width="60" align="center"><template #default="{ row }">{{ asItem(row).isDefault ? '✓' : '' }}</template></el-table-column>
        <el-table-column label="状态" width="70" align="center"><template #default="{ row }"><StatusTag :value="asItem(row).status" :map="ENABLE_STATUS" /></template></el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <RowActions :actions="[
              { label: '编辑', permission: 'system:dict:update', handler: () => openItem(asItem(row)) },
              { label: '停用', permission: 'system:dict:update', visible: !asItem(row).builtin && asItem(row).status === 'ENABLED', handler: () => itemAction(asItem(row), 'disableItem') },
              { label: '启用', permission: 'system:dict:update', visible: !asItem(row).builtin && asItem(row).status === 'DISABLED', handler: () => itemAction(asItem(row), 'enableItem') },
              { label: '删除', permission: 'system:dict:delete', danger: true, visible: !asItem(row).builtin, confirm: `确定删除字典项「${asItem(row).value} ${asItem(row).label}」吗？`, handler: () => itemAction(asItem(row), 'removeItem') }
            ]" />
          </template>
        </el-table-column>
        <template #empty><el-empty :description="current ? '暂无字典项' : '请选择左侧字典类型'" :image-size="60" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="typeVisible" :title="typeEditing ? '编辑字典类型' : '新建字典类型'" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="编码" prop="code"><el-input v-model="typeForm.code" :disabled="!!typeEditing" maxlength="64" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="typeForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" active-value="ENABLED" inactive-value="DISABLED" :disabled="typeEditing?.builtin" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemVisible" :title="itemEditing ? '编辑字典项' : '新建字典项'" width="640px" :close-on-click-modal="false" append-to-body>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="值" prop="value">
          <el-input v-model="itemForm.value" :disabled="itemEditing?.builtin" maxlength="32" @input="itemForm.value = String($event).toUpperCase()" />
        </el-form-item>
        <el-form-item label="标签" prop="label"><el-input v-model="itemForm.label" maxlength="64" /></el-form-item>
        <el-form-item label="英文标签"><el-input v-model="itemForm.labelEn" maxlength="128" /></el-form-item>
        <el-form-item label="颜色">
          <el-radio-group v-model="itemForm.tagType">
            <el-radio v-for="t in TAGS" :key="t" :value="t">
              <el-tag v-if="t !== 'DEFAULT'" :type="tagTypeOf(t) || undefined" size="small">{{ itemForm.label || '示例' }}</el-tag>
              <span v-else>默认</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sort"><el-input-number v-model="itemForm.sort" :min="0" :max="99999" controls-position="right" /></el-form-item>
        <el-form-item label="默认"><el-switch v-model="itemForm.isDefault" /><span class="form-tip tip">打开后自动取消同类型其他项的默认</span></el-form-item>
        <el-form-item label="备注"><el-input v-model="itemForm.remark" type="textarea" :rows="2" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-page { display: flex; gap: 12px; align-items: flex-start; }
.left { width: 40%; min-width: 420px; }
.right { flex: 1; min-width: 0; margin-top: 0 !important; }
.bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.bar :deep(.el-button + .el-button) { margin-left: 0; }
.kw { flex: 1; }
.title { font-weight: 600; }
.code { color: var(--el-text-color-secondary); font-weight: 400; margin-left: 4px; }
.spacer { flex: 1; }
.tag { margin-left: 4px; }
.pager { margin-top: 8px; justify-content: flex-end; }
.tip { margin-left: 8px; }
</style>
