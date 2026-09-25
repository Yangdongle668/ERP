<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { download } from '@/api/http'
import { certApi, VALIDITY_OPTIONS, VALIDITY_STATUS, type CertQuery, type CertRow, type CertSave } from '../api/cert'

defineOptions({ name: 'EngCertList' })

/** 认证证书（需求 05-09 3.1，T1 + T2 弹窗）：到期前按参数提醒；证书文件必须上传 */
type Query = Omit<CertQuery, 'pageNo' | 'pageSize'>
const { query, list, total, loading, load, search, reset } = useListPage<Query, CertRow>({ api: (q) => certApi.page(q as CertQuery) })

const fields: SearchField[] = [
  { prop: 'certNo', label: '证书编号' },
  { prop: 'certType', label: '类型', type: 'dict', dictType: 'eng_cert_type' },
  { prop: 'materialId', label: '适用物料', type: 'slot' },
  { prop: 'validity', label: '有效性', type: 'select', options: VALIDITY_OPTIONS }
]
const columns: TableColumn<CertRow>[] = [
  { prop: 'certType', label: '类型', width: 80, type: 'dict', dictType: 'eng_cert_type' },
  { prop: 'certNo', label: '证书编号', width: 150 },
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'issuingBody', label: '发证机构', width: 120 },
  { prop: 'materials', label: '适用物料', minWidth: 160, formatter: (r) => r.materials.map((m) => m.code).join('、') || '-' },
  { prop: 'countries', label: '适用国家', width: 110, formatter: (r) => r.countries.join(',') || '-' },
  { prop: 'issueDate', label: '发证日期', width: 110, type: 'date' },
  { prop: 'expireDate', label: '到期日期', width: 110, slot: true },
  { prop: 'validity', label: '有效性', width: 90, type: 'status', statusMap: VALIDITY_STATUS },
  { prop: 'fileCount', label: '文件', width: 60, align: 'right' }
]

// ---------- 新建 / 编辑 ----------
const visible = ref(false)
const saving = ref(false)
const editing = ref<CertRow>()
const formRef = ref<FormInstance>()
const form = ref<CertSave>({ certNo: '', name: '', issuingBody: '', countries: [], materialIds: [], fileIds: [] })
const rules: FormRules = {
  certType: [{ required: true, message: '请选择认证类型', trigger: 'change' }],
  certNo: [{ required: true, message: '请输入证书编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入证书名称', trigger: 'blur' }],
  issuingBody: [{ required: true, message: '请输入发证机构', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请选择发证日期', trigger: 'change' }],
  expireDate: [{ validator: (_r, v, cb) => (v && form.value.issueDate && v < form.value.issueDate ? cb(new Error('到期日期不能早于发证日期')) : cb()), trigger: 'change' }]
}
function openEdit(r?: CertRow) {
  editing.value = r
  form.value = r
    ? { certType: r.certType, certNo: r.certNo, name: r.name, issuingBody: r.issuingBody, holder: r.holder, issueDate: r.issueDate, expireDate: r.expireDate,
        countries: [...r.countries], scope: r.scope, materialIds: r.materials.map((m) => m.id), fileIds: [], remark: r.remark, version: r.version }
    : { certNo: '', name: '', issuingBody: '', countries: [], materialIds: [], fileIds: [] }
  visible.value = true
}
async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!editing.value && !form.value.fileIds.length) return ElMessage.warning('请上传证书文件')
  saving.value = true
  try {
    if (editing.value) await certApi.update(editing.value.id, form.value)
    else await certApi.create(form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function revoke(r: CertRow) {
  const { value } = await ElMessageBox.prompt(`撤销证书 ${r.certNo}，撤销后不再计入有效证书`, '撤销证书', {
    inputPlaceholder: '撤销原因', inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写撤销原因'
  })
  await certApi.revoke(r.id, value)
  ElMessage.success('已撤销')
  load()
}
async function remove(r: CertRow) {
  await certApi.remove(r.id)
  ElMessage.success('删除成功')
  load()
}
async function exportList() {
  await download('/engineering/certifications/export', { ...query, pageNo: undefined, pageSize: undefined }, '认证证书.xlsx')
}
const asRow = (r: unknown) => r as CertRow
</script>

<template>
  <ErpPage description="产品认证证书（CE、UL、CCC 等）及适用物料；到期前提醒认证工程师续证">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" placeholder="全部" class="w200" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="eng.cert" :actions-width="160" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'eng:cert:create'" type="primary" icon="Plus" @click="openEdit()">新建证书</el-button>
        </template>
        <template #toolbar-right>
          <ErpIconButton icon="Download" tooltip="导出" permission="eng:cert:export" @click="exportList" />
        </template>
        <template #col-expireDate="{ row }">
          <span v-if="!asRow(row).expireDate" class="text-muted">长期</span>
          <span v-else :class="{ 'text-danger': asRow(row).validity === 'EXPIRED', 'text-warning': asRow(row).validity === 'EXPIRING' }">
            {{ asRow(row).expireDate }}<template v-if="asRow(row).validity === 'EXPIRING'">（{{ asRow(row).daysLeft }} 天）</template>
          </span>
        </template>
        <template #actions="{ row }">
          <RowActions :actions="[
            { label: '编辑', permission: 'eng:cert:update', visible: asRow(row).certStatus !== 'REVOKED', handler: () => openEdit(asRow(row)) },
            { label: '撤销', permission: 'eng:cert:update', visible: asRow(row).certStatus !== 'REVOKED', handler: () => revoke(asRow(row)) },
            { label: '删除', permission: 'eng:cert:delete', danger: true, confirm: `确定删除证书 ${asRow(row).certNo} 吗？`, handler: () => remove(asRow(row)) }
          ]" />
        </template>
        <template #empty>
          <el-button v-perm="'eng:cert:create'" icon="Plus" @click="openEdit()">新建证书</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="visible" :title="editing ? `编辑证书 ${editing.certNo}` : '新建证书'" width="760px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="认证类型" prop="certType"><DictSelect v-model="form.certType" type="eng_cert_type" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="证书编号" prop="certNo"><el-input v-model="form.certNo" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="证书名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发证机构" prop="issuingBody"><el-input v-model="form.issuingBody" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="持证人"><el-input v-model="form.holder" maxlength="128" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="适用国家"><el-select v-model="form.countries" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="输入国家代码回车，如 DE、US" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发证日期" prop="issueDate"><el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="到期日期" prop="expireDate"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="为空表示长期有效" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="适用物料"><MaterialSelect v-model="form.materialIds" multiple /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="认证范围"><el-input v-model="form.scope" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="证书文件" required>
              <AttachmentUpload v-if="!editing" v-model="form.fileIds" biz-type="ENG_CERT" multiple />
              <AttachmentPanel v-else biz-type="ENG_CERT" :biz-id="editing.id" editable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
