<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { ENABLE_STATUS } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { BASE_EVENT_OPTIONS, USAGE_OPTIONS, paymentTermApi, type TermRow, type TermSave } from '../api/paymentTerm'

defineOptions({ name: 'SystemPaymentTermList' })

/** 付款条件（01-14）：列表 + 大弹窗表单（节点表格，比例合计 100%） */
const { query, list, loading, load, search, reset } = useListPage<{ keyword?: string; usage?: string; status?: string }, TermRow>({
  api: (q) => paymentTermApi.list({ keyword: q.keyword, usage: q.usage, status: q.status })
})

const fields: SearchField[] = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' },
  { prop: 'usage', label: '适用范围', type: 'select', options: USAGE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: [{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }] }
]

const columns: TableColumn<TermRow>[] = [
  { prop: 'code', label: '编码', width: 130, type: 'link', onClick: (r) => open(r) },
  { prop: 'name', label: '名称', width: 160 },
  { prop: 'nameEn', label: '英文名称', minWidth: 180 },
  { prop: 'settlementMethod', label: '结算方式', width: 110, type: 'dict', dictType: 'sys_settlement_method' },
  { prop: 'usage', label: '适用范围', width: 110, type: 'enum', options: USAGE_OPTIONS },
  { prop: 'nodeSummary', label: '节点', minWidth: 220 },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE_STATUS }
]

/** 表单中比例按百分数编辑，保存时转回小数 */
interface NodeForm {
  name: string
  percent?: string
  baseEvent: string
  days: number
}

const visible = ref(false)
const editing = ref<TermRow>()
const formRef = ref<FormInstance>()
const form = reactive({
  code: '', name: '', nameEn: '', settlementMethod: '', usage: 'BOTH', remark: '',
  nodes: [] as NodeForm[]
})
const rules: FormRules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_-]{1,32}$/, message: '编码为 1～32 位字母、数字、- _', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  settlementMethod: [{ required: true, message: '请选择结算方式', trigger: 'change' }],
  usage: [{ required: true, message: '请选择适用范围', trigger: 'change' }]
}

/** 以“万分之一”为单位累加，避免浮点误差 */
const percentSum = computed(() => form.nodes.reduce((s, n) => s + Math.round((Number(n.percent) || 0) * 100), 0) / 100)

function toPercent(decimal: string) {
  return String(Number((Number(decimal) * 100).toFixed(2)))
}

function toDecimal(percent?: string) {
  return String(Number((Number(percent) / 100).toFixed(4)))
}

function open(t?: TermRow) {
  editing.value = t
  Object.assign(form, t
    ? {
        code: t.code, name: t.name, nameEn: t.nameEn ?? '', settlementMethod: t.settlementMethod, usage: t.usage, remark: t.remark ?? '',
        nodes: t.nodes.map((n) => ({ name: n.name, percent: toPercent(n.percent), baseEvent: n.baseEvent, days: n.days }))
      }
    : { code: '', name: '', nameEn: '', settlementMethod: '', usage: 'BOTH', remark: '', nodes: [{ name: '全款', percent: '100', baseEvent: 'SHIPMENT', days: 0 }] })
  visible.value = true
  formRef.value?.clearValidate()
}

function addNode() {
  const rest = Math.max(0, 100 - percentSum.value)
  form.nodes.push({ name: form.nodes.length ? '尾款' : '全款', percent: rest ? String(rest) : undefined, baseEvent: 'SHIPMENT', days: 0 })
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!form.nodes.length) {
    ElMessage.warning('请至少添加一个付款节点')
    return
  }
  const bad = form.nodes.findIndex((n) => !n.name.trim() || !(Number(n.percent) > 0) || !n.baseEvent || n.days < 0)
  if (bad >= 0) {
    ElMessage.warning(`第 ${bad + 1} 个节点：请填写名称、大于 0 的比例、起算事件和天数`)
    return
  }
  if (percentSum.value !== 100) {
    ElMessage.warning('付款节点比例合计必须等于 100%')
    return
  }
  const data: TermSave = {
    code: form.code, name: form.name, nameEn: form.nameEn || undefined, settlementMethod: form.settlementMethod, usage: form.usage,
    remark: form.remark || undefined,
    nodes: form.nodes.map((n) => ({ name: n.name.trim(), percent: toDecimal(n.percent), baseEvent: n.baseEvent, days: n.days })),
    version: editing.value?.version
  }
  if (editing.value) await paymentTermApi.update(editing.value.id, data)
  else await paymentTermApi.create(data)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

async function action(t: TermRow, act: 'enable' | 'disable' | 'remove') {
  if (act === 'disable') await ElMessageBox.confirm(`停用后付款条件「${t.name}」不能在新的客户、供应商和单据中选择。确定停用吗？`, '提示', { type: 'warning' })
  await paymentTermApi[act](t.id)
  ElMessage.success(act === 'remove' ? '删除成功' : '操作成功')
  load()
}

const asTerm = (r: unknown) => r as TermRow
</script>

<template>
  <ErpPage description="付款节点决定回款 / 付款计划与到期日；修改节点只影响之后新建的单据">
    <ErpPanel>
      <template #filter><ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset" /></template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="system.payment-term" :actions-width="170" @refresh="load">
        <template #toolbar><el-button v-perm="'system:payment-term:create'" type="primary" icon="Plus" @click="open()">新建付款条件</el-button></template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'system:payment-term:update', handler: () => open(asTerm(row)) },
            { label: '停用', permission: 'system:payment-term:update', visible: asTerm(row).status === 'ENABLED', handler: () => action(asTerm(row), 'disable') },
            { label: '启用', permission: 'system:payment-term:update', visible: asTerm(row).status === 'DISABLED', handler: () => action(asTerm(row), 'enable') },
            { label: '删除', permission: 'system:payment-term:delete', danger: true, confirm: `确定删除付款条件「${asTerm(row).name}」吗？删除后不可恢复。`, handler: () => action(asTerm(row), 'remove') }
          ]" />
        </template>
      </ErpTable>
    </ErpPanel>

  <el-dialog v-model="visible" :title="editing ? '编辑付款条件' : '新建付款条件'" width="860px" :close-on-click-modal="false" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="编码" prop="code"><el-input v-model="form.code" :disabled="!!editing" maxlength="32" @input="form.code = String($event).toUpperCase()" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="英文名称"><el-input v-model="form.nameEn" maxlength="128" placeholder="用于外贸单据打印" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="结算方式" prop="settlementMethod"><DictSelect v-model="form.settlementMethod" type="sys_settlement_method" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="适用范围" prop="usage">
            <el-select v-model="form.usage"><el-option v-for="o in USAGE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item></el-col>
      </el-row>
    </el-form>
    <div class="nodes-head">
      <span class="group-title">付款节点</span>
      <el-button icon="Plus" @click="addNode">添加节点</el-button>
    </div>
    <el-table :data="form.nodes">
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column label="名称" min-width="140"><template #default="{ row }"><el-input v-model="row.name" maxlength="32" placeholder="如 定金、尾款" /></template></el-table-column>
      <el-table-column label="比例(%)" width="140"><template #default="{ row }"><NumberInput v-model="row.percent" :precision="2" :min="0" :max="100" trim-zeros /></template></el-table-column>
      <el-table-column label="起算事件" width="150">
        <template #default="{ row }">
          <el-select v-model="row.baseEvent"><el-option v-for="o in BASE_EVENT_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
        </template>
      </el-table-column>
      <el-table-column label="天数" width="130"><template #default="{ row }"><el-input-number v-model="row.days" :min="0" :max="999" controls-position="right" class="w100" /></template></el-table-column>
      <el-table-column label="" width="60" align="center">
        <template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.nodes.splice($index, 1)" /></template>
      </el-table-column>
    </el-table>
    <div class="sum" :class="{ bad: percentSum !== 100 }">比例合计：{{ percentSum }}%<span v-if="percentSum !== 100">（必须等于 100%）</span></div>
    <div class="form-tip">起算事件为“月结”时到期日 = 事件当月月末 + 天数。修改节点只影响之后新建的单据。</div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
  </ErpPage>
</template>

<style scoped>
.nodes-head { display: flex; justify-content: space-between; align-items: center; margin: 4px 0 8px; }
.nodes-head .group-title { margin: 0; }
.sum { margin-top: 8px; text-align: right; font-weight: var(--erp-font-weight-medium); }
.sum.bad { color: var(--el-color-danger); }
.w100 { width: 100px; }
</style>
