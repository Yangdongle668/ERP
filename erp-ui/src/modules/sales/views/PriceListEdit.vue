<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { PRICE_LIST_STATUS, priceListApi, SCOPE_OPTIONS, submitText, type PriceItem, type PriceListDetail, type PriceListSave } from '../api/sales'

defineOptions({ name: 'SalPriceListEdit' })

/** 销售价格表编辑（需求 04-01 3.2，T4） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => PriceItem[] }>()
const saving = ref(false)
const detail = ref<PriceListDetail>()

interface Form {
  name: string; scope: string; customerId?: string; customerLevel?: string; currency?: string; taxIncluded: boolean; effectiveFrom?: string
  effectiveTo?: string; remark?: string; items: PriceItem[]
}
const newLine = (): PriceItem => ({})
const form = ref<Form>({ name: '', scope: 'CUSTOMER', taxIncluded: true, effectiveFrom: today(), items: [newLine()] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  customerId: [{ validator: (_r, v, cb) => (form.value.scope !== 'CUSTOMER' || v ? cb() : cb(new Error('请选择客户'))), trigger: 'change' }],
  customerLevel: [{ validator: (_r, v, cb) => (form.value.scope !== 'LEVEL' || v ? cb() : cb(new Error('请选择客户等级'))), trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  effectiveFrom: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
  effectiveTo: [{ validator: (_r, v, cb) => (!v || !form.value.effectiveFrom || v >= form.value.effectiveFrom ? cb() : cb(new Error('失效日期不能早于生效日期'))), trigger: 'change' }]
}

const columns = computed<LineColumn<PriceItem>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 160 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 160 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'minQty', label: '起订量（阶梯）', type: 'qty', width: 120, uomProp: 'uom' },
  { prop: 'price', label: form.value.taxIncluded ? '含税单价' : '不含税单价', type: 'price', width: 130, required: true,
    validate: (v) => (Number(v) > 0 ? undefined : '单价必须大于 0') },
  { prop: 'remark', label: '备注', type: 'text', width: 160 }
])

function onMaterial(row: PriceItem, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.uom = m?.baseUom
}

onMounted(async () => {
  if (id.value) {
    const d = await priceListApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的价格表可以修改')
      router.replace(`/sales/price-list/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      name: d.name, scope: d.scope, customerId: d.customerId, customerLevel: d.customerLevel, currency: d.currency, taxIncluded: d.taxIncluded,
      effectiveFrom: d.effectiveFrom, effectiveTo: d.effectiveTo, remark: d.remark, items: [...d.items, newLine()]
    }
    tabs.setTitle(tabKeyOf(route), `编辑价格表 ${d.docNo}`)
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/sales/price-list/${id.value}` : '/sales/price-list')
}

function payload(): PriceListSave {
  const f = form.value
  return {
    name: f.name.trim(), scope: f.scope, customerId: f.scope === 'CUSTOMER' ? f.customerId : undefined, customerLevel: f.scope === 'LEVEL' ? f.customerLevel : undefined,
    currency: f.currency!, taxIncluded: f.taxIncluded, effectiveFrom: f.effectiveFrom!, effectiveTo: f.effectiveTo || undefined,
    remark: f.remark?.trim() || undefined, version: detail.value?.version,
    items: linesRef.value!.validRows().map((l) => ({ materialId: l.materialId!, uom: l.uom!, minQty: l.minQty || undefined, price: l.price!, remark: l.remark?.trim() || undefined }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  if (!linesRef.value.validRows().length) return ElMessage.warning('请至少录入一行物料价格')
  saving.value = true
  try {
    let docId = id.value
    if (docId) await priceListApi.update(docId, payload())
    else docId = await priceListApi.create(payload())
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await priceListApi.submit(docId)).status))
      } catch {
        if (!id.value) router.replace(`/sales/price-list/${docId}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/sales/price-list/${docId}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑价格表 ${detail.value.docNo}` : '新建价格表'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="PRICE_LIST_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('sales:price-list:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="适用范围">
              <el-radio-group v-model="form.scope"><el-radio v-for="o in SCOPE_OPTIONS" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="form.scope === 'CUSTOMER'" :xl="8" :span="12">
            <el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" :statuses="['ACTIVE', 'PROSPECT']" /></el-form-item>
          </el-col>
          <el-col v-if="form.scope === 'LEVEL'" :xl="8" :span="12">
            <el-form-item label="客户等级" prop="customerLevel"><DictSelect v-model="form.customerLevel" type="crm_customer_level" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="价格含税"><el-switch v-model="form.taxIncluded" :disabled="form.items.some((l) => l.materialId)" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="生效日期" prop="effectiveFrom"><el-date-picker v-model="form.effectiveFrom" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="失效日期" prop="effectiveTo"><el-date-picker v-model="form.effectiveTo" value-format="YYYY-MM-DD" class="w-full" placeholder="不填为长期有效" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="价格明细" description="同一物料可按起订量录入多档阶梯价；下单数量达到起订量时取对应档位">
      <LinesEditor ref="linesRef" v-model="form.items" :columns="columns" :new-row="newLine" :on-material="onMaterial" />
    </ErpPanel>
  </ErpPage>
</template>
