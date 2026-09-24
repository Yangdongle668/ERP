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
  <ErpPage description="维护下拉选项等枚举数据；内置字典项由系统声明，不能删除">
    <div class="erp-split">
      <ErpPanel title="字典类型" class="types-panel">
        <template #extra>
          <el-button v-perm="'system:dict:create'" type="primary" icon="Plus" @click="openType()">新建类型</el-button>
        </template>
        <el-input v-model="typeQuery.keyword" placeholder="搜索编码或名称，回车查询" clearable prefix-icon="Search" class="kw"
                  @keyup.enter="typeQuery.pageNo = 1; loadTypes()" @clear="loadTypes()" />
        <el-table v-loading="typeLoading" :data="types" highlight-current-row row-key="id" :current-row-key="current?.id" class="clickable" @row-click="(r: any) => select(r)">
          <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
          <el-table-column label="名称" min-width="130">
            <template #default="{ row }"><span class="name-cell">{{ asType(row).name }}<ErpBadge v-if="asType(row).builtin" :dot="false">内置</ErpBadge></span></template>
          </el-table-column>
          <el-table-column prop="moduleName" label="模块" width="90" />
          <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="asType(row).status" :map="ENABLE_STATUS" /></template></el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <RowActions :actions="[
                { label: '编辑', permission: 'system:dict:update', handler: () => openType(asType(row)) },
                { label: '删除', permission: 'system:dict:delete', danger: true, visible: !asType(row).builtin, confirm: `确定删除字典类型「${asType(row).code} ${asType(row).name}」吗？`, handler: () => removeType(asType(row)) }
              ]" />
            </template>
          </el-table-column>
          <template #empty><ErpEmpty description="没有匹配的字典类型" compact /></template>
        </el-table>
        <el-pagination v-model:current-page="typeQuery.pageNo" :page-size="typeQuery.pageSize" :total="typeTotal" layout="total, prev, pager, next" size="small" background class="pager" @current-change="loadTypes()" />
      </ErpPanel>

      <ErpPanel class="erp-split-main" :title="current ? current.name : '字典项'" :description="current?.code">
        <template #extra>
          <ErpIconButton icon="Refresh" tooltip="刷新字典缓存" permission="system:dict:update" @click="refreshCache" />
          <el-button v-if="current" v-perm="'system:dict:create'" type="primary" icon="Plus" @click="openItem()">新建字典项</el-button>
        </template>
        <el-table v-loading="itemLoading" :data="items">
          <el-table-column prop="value" label="值" min-width="110" show-overflow-tooltip />
          <el-table-column label="标签" min-width="120">
            <template #default="{ row }">
              <ErpBadge v-if="asItem(row).tagType !== 'DEFAULT'" :type="tagTypeOf(asItem(row).tagType as any) || 'info'">{{ asItem(row).label }}</ErpBadge>
              <span v-else>{{ asItem(row).label }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="labelEn" label="英文标签" min-width="120" show-overflow-tooltip />
          <el-table-column prop="sort" label="排序" width="64" align="right" />
          <el-table-column label="默认" width="56"><template #default="{ row }"><el-icon v-if="asItem(row).isDefault" class="text-success"><Check /></el-icon></template></el-table-column>
          <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="asItem(row).status" :map="ENABLE_STATUS" /></template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <RowActions :actions="[
                { label: '编辑', permission: 'system:dict:update', handler: () => openItem(asItem(row)) },
                { label: '停用', permission: 'system:dict:update', visible: !asItem(row).builtin && asItem(row).status === 'ENABLED', handler: () => itemAction(asItem(row), 'disableItem') },
                { label: '启用', permission: 'system:dict:update', visible: !asItem(row).builtin && asItem(row).status === 'DISABLED', handler: () => itemAction(asItem(row), 'enableItem') },
                { label: '删除', permission: 'system:dict:delete', danger: true, visible: !asItem(row).builtin, confirm: `确定删除字典项「${asItem(row).value} ${asItem(row).label}」吗？`, handler: () => itemAction(asItem(row), 'removeItem') }
              ]" />
            </template>
          </el-table-column>
          <template #empty><ErpEmpty :description="current ? '暂无字典项' : '请选择左侧字典类型'" :icon="current ? 'Empty' : 'Back'" /></template>
        </el-table>
      </ErpPanel>
    </div>

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
              <ErpBadge v-if="t !== 'DEFAULT'" :type="tagTypeOf(t) || 'info'">{{ itemForm.label || '示例' }}</ErpBadge>
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
  </ErpPage>
</template>

<style scoped>
.types-panel { width: 38%; min-width: 400px; max-width: 520px; flex-shrink: 0; }
.kw { margin-bottom: 12px; }
.name-cell { display: inline-flex; align-items: center; gap: 8px; }
.clickable :deep(.el-table__row) { cursor: pointer; }
.pager { margin-top: 12px; justify-content: flex-end; }
.tip { margin-left: 8px; }
</style>
