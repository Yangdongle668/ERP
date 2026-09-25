<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import {
  INVOICE_TYPE_OPTIONS, SUPPLIER_STATUS, SUPPLY_STATUS_OPTIONS, supplierApi,
  type Bank, type Cert, type Contact, type SupplierDetail, type SupplierMaterial, type SupplierSave
} from '../api/supplier'

defineOptions({ name: 'PurSupplierEdit' })

/** 新建/编辑供应商（需求 07-01 3.2，T3）：基本信息、交易信息、联系人、银行、资质、可供物料 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => SupplierMaterial[] }>()
const saving = ref(false)
const detail = ref<SupplierDetail>()

interface CertRow extends Cert {
  files: string[]
}

interface Form extends Omit<SupplierSave, 'purchaseTaxRate' | 'certs'> {
  taxPct?: string
  certs: CertRow[]
}

const newMaterial = (): SupplierMaterial => ({ supplyStatus: 'QUALIFIED', isDefault: false })
const form = ref<Form>({
  name: '', shortName: '', supplierType: 'MANUFACTURER', country: 'CN', currency: 'CNY', taxPct: '13', invoiceType: 'SPECIAL_VAT',
  contacts: [{ isPrimary: true }], banks: [], certs: [], materials: [newMaterial()], fileIds: []
})
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入简称', trigger: 'blur' }],
  supplierType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  country: [{ required: true, message: '请选择国家', trigger: 'change' }],
  buyerId: [{ required: true, message: '请选择采购员', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  paymentTermId: [{ required: true, message: '请选择付款条件', trigger: 'change' }],
  taxPct: [{ required: true, message: '请输入税率', trigger: 'blur' }],
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }]
}

const materialColumns: LineColumn<SupplierMaterial>[] = [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 160 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 160 },
  { prop: 'baseUom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'supplierPartNo', label: '供应商料号', type: 'text', width: 140 },
  { prop: 'supplyStatus', label: '供货状态', type: 'select', width: 100, options: SUPPLY_STATUS_OPTIONS, required: true },
  { prop: 'isDefault', label: '默认供应商', type: 'checkbox', width: 90 },
  { prop: 'leadTimeDays', label: '交期(天)', type: 'number', width: 90, precision: 0, min: 0 },
  { prop: 'moq', label: 'MOQ', type: 'qty', width: 100, uomProp: 'baseUom' },
  { prop: 'mpq', label: 'MPQ', type: 'qty', width: 100, uomProp: 'baseUom' },
  { prop: 'quotaPct', label: '配额(%)', type: 'number', width: 90, precision: 2, min: 0 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
]

function onMaterial(row: SupplierMaterial, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.baseUom = m?.baseUom
}

function setPrimary(list: Contact[], i: number) {
  list.forEach((c, j) => (c.isPrimary = j === i))
}
function setDefaultBank(list: Bank[], i: number) {
  list.forEach((b, j) => (b.isDefault = j === i))
}

onMounted(async () => {
  if (id.value) {
    const d = await supplierApi.get(id.value)
    detail.value = d
    const { contacts, banks, certs, materials, purchaseTaxRate, ...rest } = d
    form.value = {
      ...rest, contacts, banks, materials: [...materials, newMaterial()], fileIds: [],
      taxPct: purchaseTaxRate === undefined || purchaseTaxRate === null ? undefined : String(Number((Number(purchaseTaxRate) * 100).toFixed(4))),
      certs: certs.map((c) => ({ ...c, files: c.fileId ? [c.fileId] : [] }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑供应商 ${d.code}`)
  }
  guard.markClean()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function onKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    save()
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/supplier/${id.value}` : '/purchase/supplier')
}

function payload(): SupplierSave {
  const { taxPct, certs, contacts, banks, ...f } = form.value
  return {
    ...f,
    purchaseTaxRate: taxPct === undefined || taxPct === '' ? undefined : String(Number((Number(taxPct) / 100).toFixed(6))),
    contacts: contacts.filter((c) => c.name?.trim()),
    banks: banks.filter((b) => b.bankName?.trim() || b.accountNo?.trim()),
    certs: certs.filter((c) => c.certType).map(({ files, ...c }) => ({ ...c, fileId: files[0] })),
    materials: linesRef.value!.validRows().map((m) => ({ ...m })),
    version: detail.value?.version
  }
}

async function save() {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  const badCert = form.value.certs.findIndex((c) => c.certType && !c.files.length)
  if (badCert >= 0) return ElMessage.warning(`资质第 ${badCert + 1} 行：请上传证书文件`)
  saving.value = true
  try {
    const data = payload()
    let sid = id.value
    if (sid) await supplierApi.update(sid, data)
    else sid = await supplierApi.create(data)
    guard.markClean()
    ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/supplier/${sid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑供应商 ${detail.value.code}` : '新建供应商'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="SUPPLIER_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="英文名"><el-input v-model="form.nameEn" maxlength="256" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="简称" prop="shortName"><el-input v-model="form.shortName" maxlength="32" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="编码">
              <el-input v-model="form.code" maxlength="32" :disabled="!!detail" placeholder="留空自动生成" @input="form.code = form.code?.toUpperCase()" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="类型" prop="supplierType"><DictSelect v-model="form.supplierType" type="pur_supplier_type" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="等级"><DictSelect v-model="form.level" type="pur_supplier_level" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="国家" prop="country"><CountrySelect v-model="form.country" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="省 / 市">
            <div class="erp-row pair"><el-input v-model="form.province" maxlength="64" placeholder="省" /><el-input v-model="form.city" maxlength="64" placeholder="市" /></div>
          </el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="税号"><el-input v-model="form.taxNo" maxlength="32" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="地址"><el-input v-model="form.address" maxlength="256" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="电话"><el-input v-model="form.phone" maxlength="32" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="邮箱"><el-input v-model="form.email" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="网址"><el-input v-model="form.website" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="采购员" prop="buyerId"><UserSelect v-model="form.buyerId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="所属部门"><OrgTreeSelect v-model="form.deptId" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>

      <ErpPanel title="交易信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="付款条件" prop="paymentTermId"><PaymentTermSelect v-model="form.paymentTermId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="税率(%)" prop="taxPct"><NumberInput v-model="form.taxPct" :precision="2" trim-zeros /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="发票类型" prop="invoiceType">
              <el-select v-model="form.invoiceType" class="w-full"><el-option v-for="o in INVOICE_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="默认交货期"><el-input-number v-model="form.leadTimeDays" :min="0" :max="999" :precision="0" controls-position="right" /><span class="unit">天</span></el-form-item>
          </el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="联系人">
      <template #extra><el-button icon="Plus" @click="form.contacts.push({ isPrimary: !form.contacts.length })">添加联系人</el-button></template>
      <el-table :data="form.contacts" border>
        <el-table-column label="姓名" min-width="110"><template #default="{ row }"><el-input v-model="row.name" maxlength="64" /></template></el-table-column>
        <el-table-column label="职务" width="120"><template #default="{ row }"><el-input v-model="row.title" maxlength="64" /></template></el-table-column>
        <el-table-column label="角色" width="120"><template #default="{ row }"><el-input v-model="row.role" maxlength="32" placeholder="业务/财务/品质" /></template></el-table-column>
        <el-table-column label="电话" width="140"><template #default="{ row }"><el-input v-model="row.phone" maxlength="32" /></template></el-table-column>
        <el-table-column label="手机" width="140"><template #default="{ row }"><el-input v-model="row.mobile" maxlength="32" /></template></el-table-column>
        <el-table-column label="邮箱" min-width="160"><template #default="{ row }"><el-input v-model="row.email" maxlength="128" /></template></el-table-column>
        <el-table-column label="主联系人" width="84" align="center">
          <template #default="{ row, $index }"><el-radio :model-value="!!row.isPrimary" :value="true" @change="setPrimary(form.contacts, $index)" /></template>
        </el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.contacts.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有联系人" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="银行账户">
      <template #extra><el-button icon="Plus" @click="form.banks.push({ isDefault: !form.banks.length, currency: form.currency })">添加账户</el-button></template>
      <el-table :data="form.banks" border>
        <el-table-column label="开户行" min-width="160"><template #default="{ row }"><el-input v-model="row.bankName" maxlength="128" /></template></el-table-column>
        <el-table-column label="户名" min-width="160"><template #default="{ row }"><el-input v-model="row.accountName" maxlength="128" /></template></el-table-column>
        <el-table-column label="账号" min-width="180"><template #default="{ row }"><el-input v-model="row.accountNo" maxlength="64" /></template></el-table-column>
        <el-table-column label="SWIFT" width="130"><template #default="{ row }"><el-input v-model="row.swift" maxlength="32" /></template></el-table-column>
        <el-table-column label="币别" width="120"><template #default="{ row }"><CurrencySelect v-model="row.currency" /></template></el-table-column>
        <el-table-column label="默认" width="70" align="center">
          <template #default="{ row, $index }"><el-radio :model-value="!!row.isDefault" :value="true" @change="setDefaultBank(form.banks, $index)" /></template>
        </el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.banks.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有银行账户" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="资质">
      <template #extra><el-button icon="Plus" @click="form.certs.push({ files: [] })">添加资质</el-button></template>
      <el-table :data="form.certs" border>
        <el-table-column label="类型" width="160"><template #default="{ row }"><DictSelect v-model="row.certType" type="pur_cert_type" /></template></el-table-column>
        <el-table-column label="证书号" width="160"><template #default="{ row }"><el-input v-model="row.certNo" maxlength="64" /></template></el-table-column>
        <el-table-column label="发证日期" width="160"><template #default="{ row }"><el-date-picker v-model="row.issueDate" value-format="YYYY-MM-DD" class="w-full" /></template></el-table-column>
        <el-table-column label="到期日期" width="160"><template #default="{ row }"><el-date-picker v-model="row.expireDate" value-format="YYYY-MM-DD" class="w-full" /></template></el-table-column>
        <el-table-column label="文件" min-width="220"><template #default="{ row }"><AttachmentUpload v-model="row.files" biz-type="PUR_SUPPLIER" category="CERT" /></template></el-table-column>
        <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.certs.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有资质，准入前需上传营业执照等资质" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="可供物料">
      <LinesEditor ref="linesRef" v-model="form.materials" :columns="materialColumns" :new-row="newMaterial" :on-material="onMaterial" />
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_SUPPLIER" multiple />
      <AttachmentPanel v-else biz-type="PUR_SUPPLIER" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.pair { gap: var(--erp-space-2); width: 100%; }
.unit { margin-left: var(--erp-space-2); color: var(--erp-color-text-secondary); }
</style>
