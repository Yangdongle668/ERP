<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { RowAction, StatusMap, TableColumn } from '@/components'
import { appDomainApi, type AppDomainRow, type AppDomainSave } from '../api/crm'

defineOptions({ name: 'CrmAppDomainList' })

/**
 * 应用领域（需求 03-01 R11、3.6）：领域字母即客户编码 LD-字母-流水号 中的字母，每个领域独立计流水号。
 * 已有客户的领域不能改字母、不能删除，只能停用。
 */
const ENABLE: StatusMap = { ENABLED: { label: '启用', type: 'success' }, DISABLED: { label: '停用', type: 'info', plain: true } }
const list = ref<AppDomainRow[]>([])
const loading = ref(false)
const columns: TableColumn<AppDomainRow>[] = [
  { prop: 'code', label: '领域字母', width: 90, align: 'center' },
  { prop: 'name', label: '名称', minWidth: 200 },
  { prop: 'nameEn', label: '英文名称', minWidth: 160 },
  { prop: 'customerCount', label: '客户数', width: 90, align: 'right' },
  { prop: 'nextCode', label: '下一个客户编码', width: 140, formatter: (r) => r.nextCode ?? '-' },
  { prop: 'sort', label: '排序', width: 70, align: 'right' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: ENABLE },
  { prop: 'remark', label: '备注', minWidth: 140 }
]
const asRow = (r: unknown) => r as AppDomainRow

async function load() {
  loading.value = true
  try {
    list.value = await appDomainApi.list()
  } finally {
    loading.value = false
  }
}
onMounted(load)

const dialog = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = ref<AppDomainSave & { id?: string; used?: boolean }>({ code: '', name: '' })
const rules: FormRules = {
  code: [{ required: true, pattern: /^[A-Za-z]$/, message: '请填写一个英文字母 A～Z', trigger: 'blur' }],
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }]
}

/** 新增时默认取下一个未使用的字母 */
function nextLetter() {
  const used = new Set(list.value.map((d) => d.code))
  for (let i = 0; i < 26; i++) {
    const c = String.fromCharCode(65 + i)
    if (!used.has(c)) return c
  }
  return ''
}

function open(r?: AppDomainRow) {
  form.value = r
    ? { id: r.id, code: r.code, name: r.name, nameEn: r.nameEn, sort: r.sort, remark: r.remark, version: r.version, used: r.customerCount > 0 }
    : { code: nextLetter(), name: '' }
  dialog.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const { id, used: _used, ...data } = form.value
  saving.value = true
  try {
    data.code = data.code.toUpperCase()
    if (id) await appDomainApi.update(id, data)
    else await appDomainApi.create(data)
    dialog.value = false
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}

const rowActions = (r: AppDomainRow): RowAction[] => [
  { label: '编辑', permission: 'crm:app-domain:manage', handler: () => open(r) },
  { label: '停用', permission: 'crm:app-domain:manage', visible: r.status === 'ENABLED',
    confirm: `停用应用领域「${r.code} ${r.name}」？停用后不能再选它新建客户，已有客户不受影响。`,
    handler: async () => { await appDomainApi.disable(r.id); ElMessage.success('已停用'); load() } },
  { label: '启用', permission: 'crm:app-domain:manage', visible: r.status === 'DISABLED',
    handler: async () => { await appDomainApi.enable(r.id); ElMessage.success('已启用'); load() } },
  { label: '删除', permission: 'crm:app-domain:manage', danger: true, visible: r.customerCount === 0, handler: async () => {
    await ElMessageBox.confirm(`删除应用领域「${r.code} ${r.name}」？`, '删除', { type: 'warning' })
    await appDomainApi.remove(r.id)
    ElMessage.success('已删除')
    load()
  } }
]
</script>

<template>
  <ErpPage description="客户编码 = LD-领域字母-4 位流水号（如智能穿戴 LD-B-0001），每个领域独立计数；新建客户时选择应用领域即自动生成编码">
    <ErpPanel flush>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="crm.app-domain" :actions-width="150" @refresh="load">
        <template #toolbar><el-button v-perm="'crm:app-domain:manage'" type="primary" icon="Plus" @click="open()">新增应用领域</el-button></template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
    </ErpPanel>
    <el-dialog v-model="dialog" :title="form.id ? '编辑应用领域' : '新增应用领域'" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="领域字母" prop="code">
          <el-input v-model="form.code" maxlength="1" class="letter" :disabled="form.used"
                    @input="form.code = String($event).toUpperCase()" />
          <span class="hint text-muted">{{ form.used ? '已有客户使用，不能修改' : `客户编码 LD-${form.code || '?'}-0001` }}</span>
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" placeholder="如 智能穿戴（戒指、眼镜、手表、耳机等）" /></el-form-item>
        <el-form-item label="英文名称"><el-input v-model="form.nameEn" maxlength="128" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="9999" :precision="0" controls-position="right" placeholder="按字母" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="256" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.letter { width: 80px; }
.hint { margin-left: var(--erp-space-3); }
</style>
