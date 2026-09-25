<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ENABLE_STATUS, type TableColumn } from '@/components'
import { refApi, type CategoryNode } from '@/api/refs'
import { useUserStore } from '@/stores/user'
import {
  warehouseApi, WAREHOUSE_TYPE_OPTIONS, labelOf,
  type CategoryWarehouseRow, type LocationGenerate, type LocationRow, type WarehouseRow, type WarehouseSave
} from '../api/inventory'

defineOptions({ name: 'InvWarehouseList' })

/** 仓库与库位（需求 08-01）：仓库、库位（抽屉，支持批量生成）、仓管员、类别默认仓 */
const me = useUserStore()
const tab = ref('warehouse')

// ==================== 仓库 ====================
const keyword = ref('')
const rows = ref<WarehouseRow[]>([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    rows.value = await warehouseApi.list({ keyword: keyword.value || undefined })
  } finally {
    loading.value = false
  }
}
const columns: TableColumn<WarehouseRow>[] = [
  { prop: 'code', label: '编码', width: 110 },
  { prop: 'name', label: '名称', minWidth: 130, slot: true },
  { prop: 'warehouseType', label: '类型', width: 100, formatter: (r) => labelOf(WAREHOUSE_TYPE_OPTIONS, r.warehouseType) },
  { prop: 'available', label: '可用仓', width: 70, type: 'bool' },
  { prop: 'locationEnabled', label: '库位管理', width: 100, slot: true },
  { prop: 'managerName', label: '仓库主管', width: 100 },
  { prop: 'userNames', key: 'users', label: '仓管员', minWidth: 160, formatter: (r) => r.userNames.join('、') || '-' },
  { prop: 'allowNegative', label: '允许负库存', width: 90, type: 'bool', hidden: true },
  { prop: 'address', label: '地址', minWidth: 140, hidden: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS }
]

const visible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const editing = ref<WarehouseRow>()
const form = ref<WarehouseSave>({ code: '', name: '' })
const rules: FormRules = {
  code: [{ required: true, message: '请输入仓库编码', trigger: 'blur' }, { pattern: /^[A-Za-z0-9-]{1,16}$/, message: '仓库编码为 1～16 位字母、数字、-', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }]
}
function openEdit(r?: WarehouseRow) {
  editing.value = r
  form.value = r
    ? { code: r.code, name: r.name, warehouseType: r.warehouseType, managerId: r.managerId, address: r.address, locationEnabled: r.locationEnabled,
        allowNegative: r.allowNegative, isDefault: r.isDefault, remark: r.remark, version: r.version }
    : { code: '', name: '', locationEnabled: false, allowNegative: false, isDefault: false }
  visible.value = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) await warehouseApi.update(editing.value.id, form.value)
    else await warehouseApi.create(form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function act(r: WarehouseRow, action: 'enable' | 'disable' | 'remove') {
  await warehouseApi[action](r.id)
  ElMessage.success(action === 'remove' ? '删除成功' : action === 'enable' ? '已启用' : '已停用')
  load()
}

// ---------- 仓管员 ----------
const usersVisible = ref(false)
const usersOf = ref<WarehouseRow>()
const userIds = ref<string[]>([])
function openUsers(r: WarehouseRow) {
  usersOf.value = r
  userIds.value = [...r.userIds]
  usersVisible.value = true
}
async function saveUsers() {
  await warehouseApi.setUsers(usersOf.value!.id, userIds.value)
  ElMessage.success('已保存')
  usersVisible.value = false
  load()
}

// ---------- 库位 ----------
const locDrawer = ref(false)
const locOf = ref<WarehouseRow>()
const locations = ref<LocationRow[]>([])
const locForm = ref<Partial<LocationRow>>({})
const locEditing = ref<LocationRow>()
const locVisible = ref(false)
async function openLocations(r: WarehouseRow) {
  locOf.value = r
  locations.value = await warehouseApi.locations(r.id)
  locDrawer.value = true
}
async function reloadLocations() {
  locations.value = await warehouseApi.locations(locOf.value!.id)
}
function editLocation(l?: LocationRow) {
  locEditing.value = l
  locForm.value = l ? { code: l.code, name: l.name, remark: l.remark, version: l.version } : {}
  locVisible.value = true
}
async function saveLocation() {
  if (!locForm.value.code) return ElMessage.warning('请输入库位编码')
  if (locEditing.value) await warehouseApi.updateLocation(locOf.value!.id, locEditing.value.id, locForm.value)
  else await warehouseApi.createLocation(locOf.value!.id, locForm.value)
  ElMessage.success('保存成功')
  locVisible.value = false
  reloadLocations()
}
async function locAct(l: LocationRow, action: 'enableLocation' | 'disableLocation' | 'removeLocation') {
  await warehouseApi[action](locOf.value!.id, l.id)
  ElMessage.success('操作成功')
  reloadLocations()
}
const genVisible = ref(false)
const gen = ref<LocationGenerate>({ zoneFrom: 'A', zoneTo: 'A', rowFrom: 1, rowTo: 2, levelFrom: 1, levelTo: 3, posFrom: 1, posTo: 2, preview: true })
const genPreview = ref<{ codes: string[]; skipped: string[] }>()
async function previewGen() {
  genPreview.value = await warehouseApi.generate(locOf.value!.id, { ...gen.value, preview: true })
}
async function doGen() {
  const r = await warehouseApi.generate(locOf.value!.id, { ...gen.value, preview: false })
  ElMessage.success(`已生成 ${r.codes.length} 个库位${r.skipped.length ? `，跳过已存在 ${r.skipped.length} 个` : ''}`)
  genVisible.value = false
  genPreview.value = undefined
  reloadLocations()
}

// ==================== 类别默认仓 ====================
const catRows = ref<CategoryWarehouseRow[]>([])
const categories = ref<CategoryNode[]>([])
const catVisible = ref(false)
const catEditing = ref<CategoryWarehouseRow>()
const catForm = ref<{ categoryId?: string; warehouseId?: string }>({})
async function loadCats() {
  catRows.value = await warehouseApi.categoryWarehouses()
  if (!categories.value.length) categories.value = await refApi.categoryTree().catch(() => [])
}
function editCat(r?: CategoryWarehouseRow) {
  catEditing.value = r
  catForm.value = r ? { categoryId: r.categoryId, warehouseId: r.warehouseId } : {}
  catVisible.value = true
}
async function saveCat() {
  if (!catForm.value.categoryId || !catForm.value.warehouseId) return ElMessage.warning('请选择物料类别和仓库')
  const body = { categoryId: catForm.value.categoryId, warehouseId: catForm.value.warehouseId }
  if (catEditing.value) await warehouseApi.updateCategoryWarehouse(catEditing.value.id, body)
  else await warehouseApi.createCategoryWarehouse(body)
  ElMessage.success('保存成功')
  catVisible.value = false
  loadCats()
}
async function removeCat(r: CategoryWarehouseRow) {
  await warehouseApi.removeCategoryWarehouse(r.id)
  ElMessage.success('删除成功')
  loadCats()
}
const catProps = { value: 'id', label: 'name', children: 'children' }

const canEdit = computed(() => me.hasPermission('inv:warehouse:update'))
const asW = (r: unknown) => r as WarehouseRow
const asC = (r: unknown) => r as CategoryWarehouseRow
const asL = (r: unknown) => r as LocationRow
onMounted(() => {
  load()
  loadCats()
})
</script>

<template>
  <ErpPage description="待检仓、不良品仓、退货仓是不可用仓，库存不计入可用量；物料入库默认进入其类别的默认仓">
    <ErpPanel flush>
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="仓库" name="warehouse">
          <div class="bar">
            <el-input v-model="keyword" placeholder="编码或名称" clearable class="w200" @change="load" />
            <el-button icon="Search" @click="load">查询</el-button>
          </div>
          <ErpTable :columns="columns" :data="rows" :loading="loading" storage-key="inv.warehouse" :actions-width="220" @refresh="load">
            <template #toolbar>
              <el-button v-perm="'inv:warehouse:create'" type="primary" icon="Plus" @click="openEdit()">新建仓库</el-button>
            </template>
            <template #col-name="{ row }">
              <span>{{ asW(row).name }}</span> <ErpBadge v-if="asW(row).isDefault" type="primary">默认</ErpBadge>
            </template>
            <template #col-locationEnabled="{ row }">
              <el-link v-if="asW(row).locationEnabled" type="primary" underline="never" @click="openLocations(asW(row))">{{ asW(row).locationCount }} 个库位</el-link>
              <span v-else class="text-muted">未启用</span>
            </template>
            <template #actions="{ row }">
              <RowActions :actions="[
                { label: '编辑', permission: 'inv:warehouse:update', handler: () => openEdit(asW(row)) },
                { label: '库位', visible: asW(row).locationEnabled, handler: () => openLocations(asW(row)) },
                { label: '仓管员', permission: 'inv:warehouse:update', handler: () => openUsers(asW(row)) },
                { label: '停用', permission: 'inv:warehouse:update', visible: asW(row).status === 'ENABLED', confirm: `确定停用 ${asW(row).name} 吗？`, handler: () => act(asW(row), 'disable') },
                { label: '启用', permission: 'inv:warehouse:update', visible: asW(row).status === 'DISABLED', handler: () => act(asW(row), 'enable') },
                { label: '删除', permission: 'inv:warehouse:delete', danger: true, confirm: `确定删除 ${asW(row).name} 吗？`, handler: () => act(asW(row), 'remove') }
              ]" />
            </template>
          </ErpTable>
        </el-tab-pane>

        <el-tab-pane label="类别默认仓" name="category">
          <div class="bar">
            <el-button v-perm="'inv:warehouse:update'" type="primary" icon="Plus" @click="editCat()">新增</el-button>
            <span class="text-muted">入库单未指定仓库时，按物料类别（含上级类别）找默认仓；找不到时按物料类型取该类型的默认仓</span>
          </div>
          <el-table :data="catRows">
            <el-table-column prop="categoryPath" label="物料类别" min-width="220" />
            <el-table-column prop="warehouseName" label="默认仓" min-width="140" />
            <el-table-column label="仓库类型" width="110"><template #default="{ row }">{{ labelOf(WAREHOUSE_TYPE_OPTIONS, row.warehouseType) }}</template></el-table-column>
            <el-table-column v-if="canEdit" label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" @click="editCat(asC(row))">编辑</el-button>
                <el-popconfirm title="确定删除吗？" @confirm="removeCat(asC(row))">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? `编辑仓库 ${editing.name}` : '新建仓库'" width="620px" :close-on-click-modal="false" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="编码" prop="code"><el-input v-model="form.code" maxlength="16" :disabled="!!editing" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="仓库类型" prop="warehouseType">
          <el-select v-model="form.warehouseType" class="w-full" :disabled="!!editing">
            <el-option v-for="o in WAREHOUSE_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库主管"><UserSelect v-model="form.managerId" /></el-form-item>
        <el-form-item label="库位管理"><el-switch v-model="form.locationEnabled" /><span class="form-tip">启用后出入库必须指定库位</span></el-form-item>
        <el-form-item label="类型默认仓"><el-switch v-model="form.isDefault" /><span class="form-tip">同类型仓库中的默认仓</span></el-form-item>
        <el-form-item label="允许负库存"><el-switch v-model="form.allowNegative" /><span class="form-tip">还需开启参数“允许负库存”</span></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" maxlength="256" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="usersVisible" :title="`仓管员 - ${usersOf?.name ?? ''}`" width="520px" append-to-body>
      <p class="form-tip">仓管员可以操作该仓库的单据；仓库主管默认有权限</p>
      <UserSelect v-model="userIds" multiple />
      <template #footer>
        <el-button @click="usersVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUsers">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="locDrawer" :title="`库位 - ${locOf?.name ?? ''}`" size="640px" append-to-body>
      <div class="bar">
        <el-button v-perm="'inv:warehouse:update'" type="primary" icon="Plus" @click="editLocation()">新增库位</el-button>
        <el-button v-perm="'inv:warehouse:update'" @click="genVisible = true">批量生成</el-button>
      </div>
      <el-table :data="locations" max-height="640">
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="名称" min-width="100" />
        <el-table-column label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" :map="ENABLE_STATUS" /></template></el-table-column>
        <el-table-column label="有库存" width="70"><template #default="{ row }">{{ row.hasStock ? '是' : '' }}</template></el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="editLocation(asL(row))">编辑</el-button>
            <el-button v-if="row.status === 'ENABLED'" link type="primary" @click="locAct(asL(row), 'disableLocation')">停用</el-button>
            <el-button v-else link type="primary" @click="locAct(asL(row), 'enableLocation')">启用</el-button>
            <el-button link type="danger" @click="locAct(asL(row), 'removeLocation')">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="locVisible" :title="locEditing ? '编辑库位' : '新增库位'" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="编码" required><el-input v-model="locForm.code" maxlength="32" placeholder="如 A-01-02-03" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="locForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="locForm.remark" maxlength="128" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLocation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="genVisible" title="批量生成库位" width="560px" append-to-body>
      <p class="form-tip">编码格式：区-排-层-位，如 A-01-02-03；已存在的编码自动跳过</p>
      <el-form label-width="60px">
        <el-form-item label="区"><el-input v-model="gen.zoneFrom" maxlength="1" class="w80" /> ～ <el-input v-model="gen.zoneTo" maxlength="1" class="w80" /></el-form-item>
        <el-form-item label="排"><el-input-number v-model="gen.rowFrom" :min="1" :max="99" /> ～ <el-input-number v-model="gen.rowTo" :min="1" :max="99" /></el-form-item>
        <el-form-item label="层"><el-input-number v-model="gen.levelFrom" :min="1" :max="99" /> ～ <el-input-number v-model="gen.levelTo" :min="1" :max="99" /></el-form-item>
        <el-form-item label="位"><el-input-number v-model="gen.posFrom" :min="1" :max="99" /> ～ <el-input-number v-model="gen.posTo" :min="1" :max="99" /></el-form-item>
      </el-form>
      <div v-if="genPreview" class="form-tip">将生成 {{ genPreview.codes.length }} 个：{{ genPreview.codes.slice(0, 8).join('、') }}{{ genPreview.codes.length > 8 ? ' …' : '' }}
        <template v-if="genPreview.skipped.length">；跳过已存在 {{ genPreview.skipped.length }} 个</template></div>
      <template #footer>
        <el-button @click="previewGen">预览</el-button>
        <el-button type="primary" @click="doGen">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="catVisible" :title="catEditing ? '编辑类别默认仓' : '新增类别默认仓'" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="物料类别" required>
          <el-tree-select v-model="catForm.categoryId" :data="categories" node-key="id" :props="catProps" check-strictly filterable class="w-full" />
        </el-form-item>
        <el-form-item label="默认仓" required><WarehouseSelect v-model="catForm.warehouseId" only-available :only-mine="false" class="w-full" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCat">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.tabs { padding: 0 var(--erp-space-5) var(--erp-space-5); }
.bar { display: flex; align-items: center; gap: var(--erp-space-3); margin-bottom: var(--erp-space-3); }
.w80 { width: 80px; }
</style>
