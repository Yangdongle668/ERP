<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ENABLE_STATUS, type SearchField, type TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { partApi, type PartRow, type PartSave } from '../api/crm'

defineOptions({ name: 'CrmCustomerPartList' })

/** 客户料号对照（需求 03-02，T1 + T2 中弹窗） */
const router = useRouter()
const importRef = ref<{ open: () => void }>()
type Query = { customerId?: string; customerPartNo?: string; materialId?: string; status?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, PartRow>({ api: (q) => partApi.page(q), refreshOnActivated: true })

const fields: SearchField[] = [
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'customerPartNo', label: '客户料号', placeholder: '前缀匹配' },
  { prop: 'materialId', label: '本厂物料', type: 'slot' },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]
const columns: TableColumn<PartRow>[] = [
  { prop: 'customerShortName', label: '客户', width: 130, type: 'link', onClick: (r) => router.push(`/crm/customer/${r.customerId}`) },
  { prop: 'customerPartNo', label: '客户料号', width: 150 },
  { prop: 'customerPartName', label: '客户品名', minWidth: 160 },
  { prop: 'customerRevision', label: '客户版本', width: 80 },
  { prop: 'materialCode', label: '本厂物料', width: 130, type: 'link', onClick: (r) => router.push(`/engineering/material/${r.materialId}`) },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'materialSpec', label: '规格', minWidth: 150, hidden: true },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS },
  { prop: 'updatedAt', label: '更新时间', width: 150, type: 'datetime' }
]

const visible = ref(false)
const saving = ref(false)
const editing = ref<PartRow>()
const formRef = ref<FormInstance>()
const form = ref<PartSave>({ customerPartNo: '' })
const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  customerPartNo: [{ required: true, message: '请填写客户料号', trigger: 'blur' }],
  materialId: [{ required: true, message: '请选择本厂物料', trigger: 'change' }]
}
function openEdit(r?: PartRow) {
  editing.value = r
  form.value = r
    ? { customerId: r.customerId, customerPartNo: r.customerPartNo, customerPartName: r.customerPartName, customerPartSpec: r.customerPartSpec,
        customerRevision: r.customerRevision, materialId: r.materialId, remark: r.remark, version: r.version }
    : { customerId: query.customerId, customerPartNo: '' }
  visible.value = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const data = { ...form.value, customerPartNo: form.value.customerPartNo.trim() }
    if (editing.value) await partApi.update(editing.value.id, data)
    else await partApi.create(data)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function act(r: PartRow, action: 'enable' | 'disable' | 'remove') {
  await partApi[action](r.id)
  ElMessage.success(action === 'remove' ? '删除成功' : action === 'enable' ? '已启用' : '已停用')
  load()
}
const asRow = (r: unknown) => r as PartRow
</script>

<template>
  <ErpPage description="客户料号 ↔ 本厂物料：销售下单时输入客户料号自动带出物料，对外单据打印客户料号和品名">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" placeholder="全部" class="w200" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" placeholder="全部" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="crm.customer-part" :actions-width="160" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'crm:customer-part:create'" type="primary" icon="Plus" @click="openEdit()">新建对照</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Upload" tooltip="导入" permission="crm:customer-part:import" @click="importRef?.open()" />
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'crm:customer-part:update', handler: () => openEdit(asRow(row)) },
            { label: '停用', permission: 'crm:customer-part:update', visible: asRow(row).status === 'ENABLED', handler: () => act(asRow(row), 'disable') },
            { label: '启用', permission: 'crm:customer-part:update', visible: asRow(row).status === 'DISABLED', handler: () => act(asRow(row), 'enable') },
            { label: '删除', permission: 'crm:customer-part:delete', danger: true, confirm: `确定删除客户料号 ${asRow(row).customerPartNo} 吗？`, handler: () => act(asRow(row), 'remove') }
          ]" />
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? '编辑客户料号' : '新建客户料号'" width="600px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" /></el-form-item>
        <el-form-item label="客户料号" prop="customerPartNo"><el-input v-model="form.customerPartNo" maxlength="64" /></el-form-item>
        <el-form-item label="客户品名"><el-input v-model="form.customerPartName" maxlength="256" /></el-form-item>
        <el-form-item label="客户规格"><el-input v-model="form.customerPartSpec" maxlength="512" /></el-form-item>
        <el-form-item label="客户版本"><el-input v-model="form.customerRevision" maxlength="16" /></el-form-item>
        <el-form-item label="本厂物料" prop="materialId"><MaterialSelect v-model="form.materialId" :types="['SEMI_FINISHED', 'FINISHED']" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <ImportDialog ref="importRef" title="导入客户料号" base="/crm/customer-parts" template-name="客户料号" allow-partial @done="load" />
  </ErpPage>
</template>
