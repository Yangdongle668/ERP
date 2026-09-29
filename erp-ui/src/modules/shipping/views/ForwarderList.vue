<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { ENABLE, forwarderApi, optionsOf, TRANSPORT_SERVICES, type ForwarderRow } from '../api/shipping'

defineOptions({ name: 'ShpForwarderList' })

/** 货代（需求 11-05 3.2，T1 + T2）：编码唯一；已被单据引用的货代删除时改为停用 */
type Query = { keyword?: string; status?: string; service?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ForwarderRow>({ api: (q) => forwarderApi.page(q as never) })
const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码 / 名称 / 联系人' },
  { prop: 'service', label: '服务', type: 'select', options: TRANSPORT_SERVICES },
  { prop: 'status', label: '状态', type: 'select', options: optionsOf(ENABLE) }
]
const serviceText = (r: ForwarderRow) => r.services.map((s) => TRANSPORT_SERVICES.find((o) => o.value === s)?.label ?? s).join('、')
const columns: TableColumn<ForwarderRow>[] = [
  { prop: 'code', label: '编码', width: 120 },
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'contact', label: '联系人', width: 100 },
  { prop: 'phone', label: '电话', width: 130 },
  { prop: 'email', label: '邮箱', width: 180 },
  { key: 'services', label: '服务', width: 160, formatter: serviceText },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE },
  { prop: 'remark', label: '备注', minWidth: 140 }
]
const asRow = (r: unknown) => r as ForwarderRow

const dialog = ref(false)
const formRef = ref<FormInstance>()
const form = ref<{ id?: string; code?: string; name?: string; contact?: string; phone?: string; email?: string; services: string[]; status: string; remark?: string }>({
  services: [], status: 'ENABLED'
})
const rules: FormRules = {
  code: [{ required: true, message: '请填写编码', trigger: 'blur' }],
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }]
}
function open(r?: ForwarderRow) {
  form.value = r ? { ...r, services: [...r.services] } : { services: [], status: 'ENABLED' }
  dialog.value = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const { id, ...data } = form.value
  if (id) await forwarderApi.update(id, data)
  else await forwarderApi.create(data)
  dialog.value = false
  ElMessage.success('已保存')
  load()
}
const rowActions = (r: ForwarderRow): RowAction[] => [
  { label: '编辑', permission: 'shp:forwarder:manage', handler: () => open(r) },
  { label: '删除', permission: 'shp:forwarder:manage', danger: true, handler: async () => {
    await ElMessageBox.confirm(`删除货代「${r.name}」？已被单据引用时改为停用。`, '删除', { type: 'warning' })
    await forwarderApi.remove(r.id)
    ElMessage.success('已处理')
    load()
  } }
]
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="shp.forwarder" :actions-width="120" @refresh="load">
        <template #toolbar><el-button v-perm="'shp:forwarder:manage'" type="primary" icon="Plus" @click="open()">新建货代</el-button></template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
    <el-dialog v-model="dialog" :title="form.id ? '编辑货代' : '新建货代'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编码" prop="code"><el-input v-model="form.code" maxlength="32" /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" maxlength="64" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" maxlength="32" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="128" /></el-form-item>
        <el-form-item label="服务">
          <el-checkbox-group v-model="form.services">
            <el-checkbox v-for="o in TRANSPORT_SERVICES" :key="String(o.value)" :value="o.value">{{ o.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status"><el-radio value="ENABLED">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
