<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount } from '@/utils/format'
import PaymentTermSelect from '../components/PaymentTermSelect.vue'
import {
  ADDRESS_TYPE_OPTIONS, CREDIT_CONTROL_OPTIONS, CUSTOMER_STATUS, GENDER_OPTIONS, customerApi,
  type Address, type Bank, type Contact, type CustomerDetail, type CustomerSave, type DuplicateRow
} from '../api/crm'

defineOptions({ name: 'CrmCustomerEdit' })

/** 新建/编辑客户（需求 03-01 3.2，T3）：基本信息、交易信息、联系人、地址、银行、附件；名称/税号/网址失焦查重 */
const HOME_COUNTRY = 'CN'
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<CustomerDetail>()
const canCredit = computed(() => me.hasPermission('crm:customer:credit'))
const canTransfer = computed(() => me.hasPermission('crm:customer:transfer'))

interface Form extends Omit<CustomerSave, 'salesTaxRate'> {
  taxPct?: string
}
const form = ref<Form>({
  name: '', customerType: 'END_USER', level: 'C', country: HOME_COUNTRY, isForeign: false, currency: 'CNY', taxPct: '13', creditControl: 'DEFAULT',
  contacts: [], addresses: [], banks: [], fileIds: []
})
const guard = useLeaveGuard(() => form.value)
const shortTouched = ref(false)

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: '请填写客户名称', trigger: 'blur' }],
  nameEn: form.value.isForeign ? [{ required: true, message: '外销客户必须填写英文名称', trigger: 'blur' }] : [],
  shortName: [{ required: true, message: '请填写简称', trigger: 'blur' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
  level: [{ required: true, message: '请选择等级', trigger: 'change' }],
  country: [{ required: true, message: '请选择国家', trigger: 'change' }],
  ownerId: [{ required: true, message: '请选择负责业务员', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  taxPct: [{ required: true, message: '请填写税率', trigger: 'blur' }]
}))

/** 默认简称：中文取前 10 个字；英文等在 20 个字符内按单词截断（与后端一致） */
function defaultShortName(name: string) {
  if (/\p{Script=Han}/u.test(name)) return name.slice(0, 10)
  if (name.length <= 20) return name
  const space = name.lastIndexOf(' ', 20)
  return (space > 0 ? name.slice(0, space) : name.slice(0, 20)).trim()
}

function onName() {
  if (!shortTouched.value && !id.value) form.value.shortName = defaultShortName(form.value.name.trim())
  checkDuplicate()
}
/** 国家变化：自动设置外销、默认币别、税率 */
function onCountry(v?: string) {
  const foreign = !!v && v !== HOME_COUNTRY
  form.value.isForeign = foreign
  form.value.currency = foreign ? 'USD' : 'CNY'
  form.value.taxPct = foreign ? '0' : '13'
}

// ---------- 查重 ----------
const duplicates = ref<DuplicateRow[]>([])
async function checkDuplicate() {
  const f = form.value
  if (!f.name?.trim() && !f.taxNo?.trim() && !f.website?.trim()) return
  duplicates.value = await customerApi.duplicateCheck({ id: id.value, name: f.name, country: f.country, taxNo: f.taxNo, website: f.website }).catch(() => [])
}
const MATCHED: Record<string, string> = { NAME: '名称相似', TAX_NO: '税号相同', WEBSITE: '网址相同' }

// ---------- 子表 ----------
function setPrimary(i: number) {
  form.value.contacts.forEach((c, j) => (c.isPrimary = j === i))
}
function setDefaultAddress(a: Address) {
  form.value.addresses.forEach((x) => {
    if (x !== a && x.addressType === a.addressType) x.isDefault = false
  })
  a.isDefault = true
}
function addAddress() {
  const type = form.value.addresses.some((a) => a.addressType === 'SHIP_TO') ? 'BILL_TO' : 'SHIP_TO'
  form.value.addresses.push({ addressType: type, companyName: form.value.nameEn || form.value.name, country: form.value.country ?? HOME_COUNTRY,
    addressLine: '', isDefault: !form.value.addresses.some((a) => a.addressType === type) })
}

onMounted(async () => {
  if (id.value) {
    const d = await customerApi.get(id.value)
    if (d.customerStatus === 'BLACKLIST') {
      ElMessage.warning('黑名单客户不能修改')
      router.replace(`/crm/customer/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      code: d.code, name: d.name, nameEn: d.nameEn, shortName: d.shortName, customerType: d.customerType, level: d.level, country: d.country,
      isForeign: d.isForeign, province: d.province, city: d.city, address: d.address, industry: d.industry, source: d.source, website: d.website,
      phone: d.phone, email: d.email, taxNo: d.taxNo, ownerId: d.ownerId, currency: d.currency, paymentTermId: d.paymentTermId, tradeTerm: d.tradeTerm,
      taxPct: String(Number((Number(d.salesTaxRate) * 100).toFixed(4))), creditDays: d.credit?.creditDays, creditControl: d.credit?.creditControl ?? 'DEFAULT',
      remark: d.remark, contacts: d.contacts.map((c) => ({ ...c })), addresses: d.addresses.map((a) => ({ ...a })), banks: d.banks.map((b) => ({ ...b })),
      fileIds: [], version: d.version
    }
    shortTouched.value = true
    tabs.setTitle(tabKeyOf(route), `编辑客户 ${d.code}`)
  } else {
    form.value.ownerId = me.user?.id
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
  router.push(id.value ? `/crm/customer/${id.value}` : '/crm/customer')
}

function validateChildren(): boolean {
  for (const c of form.value.contacts) {
    if (!c.name?.trim()) return warn('联系人姓名不能为空')
    if (!c.email?.trim() && !c.phone?.trim() && !c.mobile?.trim()) return warn(`联系人「${c.name}」至少需要填写邮箱、电话、手机中的一项`)
  }
  for (const [i, a] of form.value.addresses.entries()) {
    if (!a.companyName?.trim() || !a.addressLine?.trim() || !a.country) return warn(`地址第 ${i + 1} 行：请填写抬头公司名、国家和详细地址`)
  }
  for (const [i, b] of form.value.banks.entries()) {
    if (!b.bankName?.trim() || !b.accountName?.trim() || !b.accountNo?.trim()) return warn(`银行第 ${i + 1} 行：请填写开户行、户名和账号`)
  }
  return true
}
function warn(msg: string) {
  ElMessage.warning(msg)
  return false
}

async function save() {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!validateChildren()) return
  saving.value = true
  try {
    const { taxPct, ...f } = form.value
    const data: CustomerSave = { ...f, salesTaxRate: String(Number((Number(taxPct) / 100).toFixed(6))), version: detail.value?.version }
    const r = id.value ? await customerApi.update(id.value, data) : await customerApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/crm/customer/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑客户 ${detail.value.code}` : '新建客户'))
const asContact = (r: unknown) => r as Contact
const asAddress = (r: unknown) => r as Address
const asBank = (r: unknown) => r as Bank
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.customerStatus" :map="CUSTOMER_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-alert v-if="duplicates.length" type="warning" :closable="false" show-icon class="dup">
          <template #title>发现疑似重复客户：
            <span v-for="d in duplicates" :key="d.id" class="dup-item">{{ d.code }} {{ d.name }}（{{ d.country }}，{{ MATCHED[d.matchedBy] }}{{ d.ownerName ? `，负责人 ${d.ownerName}` : '' }}）</span>
          </template>
        </el-alert>
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="客户名称" prop="name"><el-input v-model="form.name" maxlength="128" @blur="onName" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="英文名称" prop="nameEn"><el-input v-model="form.nameEn" maxlength="256" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="简称" prop="shortName"><el-input v-model="form.shortName" maxlength="32" @input="shortTouched = true" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="编码">
              <el-input v-model="form.code" maxlength="32" :disabled="!!detail" placeholder="留空自动生成" @input="form.code = form.code?.toUpperCase()" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客户类型" prop="customerType"><DictSelect v-model="form.customerType" type="crm_customer_type" :clearable="false" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="等级" prop="level"><DictSelect v-model="form.level" type="crm_customer_level" :clearable="false" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="国家" prop="country"><CountrySelect v-model="form.country" @update:model-value="onCountry" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="省 / 市">
            <div class="erp-row pair"><el-input v-model="form.province" maxlength="64" placeholder="省" /><el-input v-model="form.city" maxlength="64" placeholder="市" /></div>
          </el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="税号"><el-input v-model="form.taxNo" maxlength="32" @blur="checkDuplicate" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="公司地址"><el-input v-model="form.address" maxlength="256" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="行业"><DictSelect v-model="form.industry" type="crm_industry" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="来源"><DictSelect v-model="form.source" type="crm_source" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="网址"><el-input v-model="form.website" maxlength="128" @blur="checkDuplicate" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="电话"><el-input v-model="form.phone" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="邮箱"><el-input v-model="form.email" maxlength="128" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="负责业务员" prop="ownerId"><UserSelect v-model="form.ownerId" :disabled="!canTransfer" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>

      <ErpPanel title="交易信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="外销客户"><el-switch v-model="form.isForeign" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="默认币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="付款条件"><PaymentTermSelect v-model="form.paymentTermId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="贸易条款"><DictSelect v-model="form.tradeTerm" type="sys_trade_term" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="销项税率(%)" prop="taxPct"><NumberInput v-model="form.taxPct" :precision="2" :max="99" trim-zeros /></el-form-item></el-col>
          <template v-if="canCredit">
            <el-col :xl="8" :span="12">
              <el-form-item label="信用期"><el-input-number v-model="form.creditDays" :min="0" :max="365" :precision="0" controls-position="right" /><span class="unit">天</span></el-form-item>
            </el-col>
            <el-col :xl="8" :span="12">
              <el-form-item label="信用控制">
                <el-select v-model="form.creditControl" class="w-full"><el-option v-for="o in CREDIT_CONTROL_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select>
              </el-form-item>
            </el-col>
            <el-col :xl="8" :span="12">
              <el-form-item label="信用额度">
                <span class="num">{{ detail?.credit?.creditLimit ? formatAmount(detail.credit.creditLimit) : '未设置' }}</span>
                <el-link v-if="detail" v-perm="'crm:credit:update'" type="primary" underline="never" class="unit" @click="router.push(`/crm/credit?customerId=${detail.id}`)">调整额度</el-link>
              </el-form-item>
            </el-col>
          </template>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="联系人">
      <template #extra><el-button icon="Plus" @click="form.contacts.push({ name: '', gender: 'UNKNOWN', status: 'ACTIVE', isPrimary: !form.contacts.length })">添加联系人</el-button></template>
      <el-table :data="form.contacts" border>
        <el-table-column label="姓名" min-width="110"><template #default="{ row }"><el-input v-model="asContact(row).name" maxlength="64" /></template></el-table-column>
        <el-table-column label="性别" width="90">
          <template #default="{ row }"><el-select v-model="asContact(row).gender"><el-option v-for="o in GENDER_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select></template>
        </el-table-column>
        <el-table-column label="职位" width="110"><template #default="{ row }"><el-input v-model="asContact(row).title" maxlength="64" /></template></el-table-column>
        <el-table-column label="角色" width="110"><template #default="{ row }"><DictSelect v-model="asContact(row).role" type="crm_contact_role" /></template></el-table-column>
        <el-table-column label="邮箱" min-width="160"><template #default="{ row }"><el-input v-model="asContact(row).email" maxlength="128" /></template></el-table-column>
        <el-table-column label="电话" width="130"><template #default="{ row }"><el-input v-model="asContact(row).phone" maxlength="32" /></template></el-table-column>
        <el-table-column label="手机" width="130"><template #default="{ row }"><el-input v-model="asContact(row).mobile" maxlength="32" /></template></el-table-column>
        <el-table-column label="即时通讯" width="130"><template #default="{ row }"><el-input v-model="asContact(row).im" maxlength="64" placeholder="WhatsApp/微信" /></template></el-table-column>
        <el-table-column label="主联系人" width="84" align="center">
          <template #default="{ row, $index }"><el-radio :model-value="!!asContact(row).isPrimary" :value="true" @change="setPrimary($index)" /></template>
        </el-table-column>
        <el-table-column label="在职" width="64" align="center">
          <template #default="{ row }"><el-switch :model-value="asContact(row).status !== 'LEFT'" @update:model-value="asContact(row).status = $event ? 'ACTIVE' : 'LEFT'" /></template>
        </el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.contacts.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有联系人；转正式需要主联系人" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="地址">
      <template #extra><el-button icon="Plus" @click="addAddress">添加地址</el-button></template>
      <el-table :data="form.addresses" border>
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-select v-model="asAddress(row).addressType"><el-option v-for="o in ADDRESS_TYPE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" /></el-select></template>
        </el-table-column>
        <el-table-column label="抬头公司名" min-width="170"><template #default="{ row }"><el-input v-model="asAddress(row).companyName" maxlength="256" /></template></el-table-column>
        <el-table-column label="联系人" width="100"><template #default="{ row }"><el-input v-model="asAddress(row).contactName" maxlength="64" /></template></el-table-column>
        <el-table-column label="电话" width="120"><template #default="{ row }"><el-input v-model="asAddress(row).phone" maxlength="32" /></template></el-table-column>
        <el-table-column label="国家" width="130"><template #default="{ row }"><CountrySelect v-model="asAddress(row).country" /></template></el-table-column>
        <el-table-column label="省/市" width="160">
          <template #default="{ row }"><div class="erp-row pair"><el-input v-model="asAddress(row).province" maxlength="64" /><el-input v-model="asAddress(row).city" maxlength="64" /></div></template>
        </el-table-column>
        <el-table-column label="邮编" width="90"><template #default="{ row }"><el-input v-model="asAddress(row).zip" maxlength="16" /></template></el-table-column>
        <el-table-column label="详细地址" min-width="220"><template #default="{ row }"><el-input v-model="asAddress(row).addressLine" maxlength="512" /></template></el-table-column>
        <el-table-column label="默认" width="64" align="center">
          <template #default="{ row }"><el-radio :model-value="!!asAddress(row).isDefault" :value="true" @change="setDefaultAddress(asAddress(row))" /></template>
        </el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.addresses.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有地址；转正式需要默认收货地址（国内客户还需默认开票地址）" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="银行信息">
      <template #extra><el-button icon="Plus" @click="form.banks.push({ bankName: '', accountName: form.name, accountNo: '', currency: form.currency })">添加账户</el-button></template>
      <el-table :data="form.banks" border>
        <el-table-column label="开户行" min-width="160"><template #default="{ row }"><el-input v-model="asBank(row).bankName" maxlength="128" /></template></el-table-column>
        <el-table-column label="户名" min-width="160"><template #default="{ row }"><el-input v-model="asBank(row).accountName" maxlength="128" /></template></el-table-column>
        <el-table-column label="账号" min-width="180"><template #default="{ row }"><el-input v-model="asBank(row).accountNo" maxlength="64" /></template></el-table-column>
        <el-table-column label="SWIFT" width="130"><template #default="{ row }"><el-input v-model="asBank(row).swift" maxlength="16" /></template></el-table-column>
        <el-table-column label="币别" width="120"><template #default="{ row }"><CurrencySelect v-model="asBank(row).currency" /></template></el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="asBank(row).remark" maxlength="256" /></template></el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.banks.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="没有银行信息" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件">
      <template #extra><span class="text-muted">营业执照、合同、NDA 等</span></template>
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="CRM_CUSTOMER" multiple />
      <AttachmentPanel v-else biz-type="CRM_CUSTOMER" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.pair { gap: var(--erp-space-2); width: 100%; }
.unit { margin-left: var(--erp-space-2); color: var(--erp-color-text-secondary); }
.dup { margin-bottom: var(--erp-space-4); }
.dup-item { margin-right: var(--erp-space-3); }
</style>
