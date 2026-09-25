<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ENABLE_STATUS, type SearchField, type TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty } from '@/utils/format'
import { workCenterApi, WC_TYPE_OPTIONS, type WorkCenterRow, type WorkCenterSave } from '../api/routing'
import { labelOf } from '../api/material'

defineOptions({ name: 'EngWorkCenterList' })

/** 工作中心（需求 05-04 3.1，T1 + T2 弹窗）：日产能 = 每班小时 × 班次 × 效率；无费率权限时费率显示 *** */
type Query = { keyword?: string; wcType?: string; status?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, WorkCenterRow>({ api: (q) => workCenterApi.page(q) })

const fields: SearchField[] = [
  { prop: 'keyword', label: '编码/名称' },
  { prop: 'wcType', label: '类型', type: 'select', options: WC_TYPE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]
const rate = (r: WorkCenterRow, v?: string) => (r.rateVisible ? (v ? formatQty(v, 2) : '-') : '***')
const columns: TableColumn<WorkCenterRow>[] = [
  { prop: 'code', label: '编码', width: 120 },
  { prop: 'name', label: '名称', minWidth: 150 },
  { prop: 'deptName', label: '所属车间', width: 130 },
  { prop: 'wcType', label: '类型', width: 80, formatter: (r) => labelOf(WC_TYPE_OPTIONS, r.wcType) },
  { prop: 'hoursPerShift', label: '每班小时', width: 90, type: 'qty', precision: 2 },
  { prop: 'shiftCount', label: '班次', width: 60, align: 'right' },
  { prop: 'efficiencyPct', label: '效率', width: 80, align: 'right', formatter: (r) => `${Number((Number(r.efficiencyPct) * 100).toFixed(2))}%` },
  { prop: 'capacityHoursPerDay', label: '日产能(小时)', width: 110, type: 'qty', precision: 2 },
  { prop: 'laborRate', label: '人工费率', width: 90, align: 'right', formatter: (r) => rate(r, r.laborRate) },
  { prop: 'overheadRate', label: '制造费率', width: 90, align: 'right', formatter: (r) => rate(r, r.overheadRate) },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
]

// ---------- 表单 ----------
const visible = ref(false)
const saving = ref(false)
const editing = ref<WorkCenterRow>()
const formRef = ref<FormInstance>()
/** 效率按百分数编辑 */
const effPct = ref<string>()
const form = ref<WorkCenterSave>({ code: '', name: '' })
const rules: FormRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_-]{1,32}$/, message: '编码为 1～32 位字母、数字、_ -', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属车间', trigger: 'change' }],
  wcType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  hoursPerShift: [{ required: true, message: '请输入每班小时', trigger: 'blur' }],
  shiftCount: [{ required: true, message: '请输入班次', trigger: 'blur' }]
}
const capacity = computed(() => {
  const v = Number(form.value.hoursPerShift || 0) * Number(form.value.shiftCount || 0) * (Number(effPct.value || 0) / 100)
  return Number(v.toFixed(2))
})
function openEdit(r?: WorkCenterRow) {
  editing.value = r
  form.value = r
    ? { code: r.code, name: r.name, deptId: r.deptId, wcType: r.wcType, hoursPerShift: r.hoursPerShift, shiftCount: r.shiftCount,
        laborRate: r.laborRate, overheadRate: r.overheadRate, remark: r.remark, version: r.version }
    : { code: '', name: '', hoursPerShift: '8', shiftCount: 1 }
  effPct.value = r ? String(Number((Number(r.efficiencyPct) * 100).toFixed(2))) : '100'
  visible.value = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const eff = Number(effPct.value)
  if (!(eff >= 1 && eff <= 200)) return ElMessage.warning('效率为 1%～200%')
  saving.value = true
  try {
    const data = { ...form.value, efficiencyPct: String(eff / 100) }
    if (editing.value) await workCenterApi.update(editing.value.id, data)
    else await workCenterApi.create(data)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function act(r: WorkCenterRow, action: 'enable' | 'disable' | 'remove') {
  await workCenterApi[action](r.id)
  ElMessage.success(action === 'remove' ? '删除成功' : action === 'enable' ? '已启用' : '已停用')
  load()
}
const asRow = (r: unknown) => r as WorkCenterRow
</script>

<template>
  <ErpPage description="工序在哪里做：产线、设备、人工或委外。日产能 = 每班小时 × 班次 × 效率，用于产能负荷与排程">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" />
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.work-center" :actions-width="170" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:work-center:create'" type="primary" icon="Plus" @click="openEdit()">新建工作中心</el-button>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:work-center:update', handler: () => openEdit(asRow(row)) },
            { label: '停用', permission: 'eng:work-center:update', visible: asRow(row).status === 'ENABLED', handler: () => act(asRow(row), 'disable') },
            { label: '启用', permission: 'eng:work-center:update', visible: asRow(row).status === 'DISABLED', handler: () => act(asRow(row), 'enable') },
            { label: '删除', permission: 'eng:work-center:delete', danger: true, confirm: `确定删除 ${asRow(row).code} 吗？`, handler: () => act(asRow(row), 'remove') }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:work-center:create'" icon="Plus" @click="openEdit()">新建工作中心</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? `编辑工作中心 ${editing.code}` : '新建工作中心'" width="720px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="编码" prop="code"><el-input v-model="form.code" maxlength="32" :disabled="!!editing" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所属车间" prop="deptId"><OrgTreeSelect v-model="form.deptId" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="wcType">
              <el-select v-model="form.wcType"><el-option v-for="o in WC_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="每班小时" prop="hoursPerShift"><NumberInput v-model="form.hoursPerShift" :precision="2" :max="24" trim-zeros /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="班次" prop="shiftCount"><el-input-number v-model="form.shiftCount" :min="1" :max="4" :precision="0" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="效率(%)" required>
              <NumberInput v-model="effPct" :precision="2" :max="200" trim-zeros />
              <div class="form-tip">日产能 {{ capacity }} 小时</div>
            </el-form-item>
          </el-col>
          <el-col :span="12" />
          <el-col :span="12"><el-form-item label="人工费率"><NumberInput v-model="form.laborRate" :precision="2" trim-zeros placeholder="元/小时" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="制造费率"><NumberInput v-model="form.overheadRate" :precision="2" trim-zeros placeholder="元/小时" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="256" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
