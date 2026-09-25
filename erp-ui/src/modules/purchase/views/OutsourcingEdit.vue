<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { MaterialBrief, SupplierBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty, today, toDateString } from '@/utils/format'
import { num, submitText } from '../api/common'
import { OS_STATUS, outsourcingApi, type OsDetail, type OsMaterial, type OsSave } from '../api/outsourcing'
import { priceApi } from '../api/price'

defineOptions({ name: 'PurOutsourcingEdit' })

/** 委外单编辑（需求 07-07 3.2，T4）：选加工物料带出默认 BOM 用料，数量变化重算应发 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<OsDetail>()
const canPrice = computed(() => me.hasPermission('pur:price:view'))

interface Mat extends OsMaterial {
  /** BOM 计算的应发数量（调整判断用） */
  calcQty?: string
}
interface Form {
  supplierId?: string
  materialId?: string
  bomId?: string
  bomText?: string
  qty?: string
  processPrice?: string
  taxPct?: string
  currency?: string
  exchangeRate?: string
  requiredDate?: string
  remark?: string
  fileIds: string[]
  materials: Mat[]
}
const form = ref<Form>({ currency: 'CNY', exchangeRate: '1', fileIds: [], materials: [] })
const guard = useLeaveGuard(() => form.value)
const materialUom = ref<string>()

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择加工商', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择加工物料', trigger: 'change' }],
  qty: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  requiredDate: [{ required: true, message: '请选择要求日期', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }]
}

async function preview() {
  if (!form.value.materialId) return
  const p = await outsourcingApi.preview(form.value.materialId, form.value.qty || '1', form.value.bomId)
  form.value.bomId = p.bomId
  form.value.bomText = `${p.bomNo} V${p.bomVersion}`
  const old = new Map(form.value.materials.map((m) => [m.materialId, m]))
  form.value.materials = p.materials.map((m) => {
    const o = old.get(m.materialId)
    const adjusted = o && o.calcQty !== undefined && o.requiredQty !== o.calcQty
    return { ...m, calcQty: m.requiredQty, requiredQty: adjusted ? o!.requiredQty : m.requiredQty, adjustReason: adjusted ? o!.adjustReason : undefined }
  })
}

async function fetchPrice() {
  if (!canPrice.value || !form.value.supplierId || !form.value.materialId || !form.value.currency) return
  const p = await priceApi.effective({ supplierId: form.value.supplierId, materialId: form.value.materialId, currency: form.value.currency, qty: form.value.qty })
    .catch(() => null)
  if (p) {
    form.value.processPrice = p.price
    form.value.taxPct = String(Number((Number(p.taxRate) * 100).toFixed(4)))
  }
}

async function onMaterial(m?: MaterialBrief | MaterialBrief[]) {
  const x = Array.isArray(m) ? m[0] : m
  materialUom.value = x?.baseUom
  form.value.bomId = undefined
  form.value.materials = []
  if (x) {
    await preview()
    await fetchPrice()
  }
}

function onSupplier(s?: SupplierBrief | SupplierBrief[]) {
  const x = Array.isArray(s) ? s[0] : s
  if (x?.currency) form.value.currency = x.currency
  if (x?.taxRate) form.value.taxPct = String(Number((Number(x.taxRate) * 100).toFixed(4)))
  fetchPrice()
}

onMounted(async () => {
  if (id.value) {
    const d = await outsourcingApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的委外单可以修改')
      router.replace(`/purchase/outsourcing/${id.value}`)
      return
    }
    detail.value = d
    materialUom.value = d.uom
    form.value = {
      supplierId: d.supplierId, materialId: d.materialId, bomId: d.bomId, bomText: d.bomNo ? `${d.bomNo} V${d.bomVersion}` : undefined, qty: d.qty,
      processPrice: d.processPrice, taxPct: String(Number((Number(d.taxRate) * 100).toFixed(4))), currency: d.currency, exchangeRate: d.exchangeRate,
      requiredDate: d.requiredDate, remark: d.remark, fileIds: [],
      materials: d.materials.map((m) => ({ ...m, calcQty: m.adjustReason ? undefined : m.requiredQty }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑委外单 ${d.docNo}`)
  } else {
    const d = new Date()
    d.setDate(d.getDate() + 14)
    form.value.requiredDate = toDateString(d)
  }
  guard.markClean()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function onKey(e: KeyboardEvent) {
  if (!(e.ctrlKey || e.metaKey)) return
  if (e.key === 's') {
    e.preventDefault()
    save(false)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    save(true)
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/outsourcing/${id.value}` : '/purchase/outsourcing')
}

const isAdjusted = (m: Mat) => m.calcQty === undefined ? !!m.adjustReason : num(m.requiredQty) !== num(m.calcQty)

function payload(): OsSave {
  const f = form.value
  return {
    supplierId: f.supplierId!, materialId: f.materialId!, bomId: f.bomId, qty: f.qty!, processPrice: f.processPrice,
    taxRate: f.taxPct ? String(Number((Number(f.taxPct) / 100).toFixed(6))) : undefined, currency: f.currency, exchangeRate: f.exchangeRate,
    requiredDate: f.requiredDate!, remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    materials: f.materials.filter(isAdjusted).map((m) => ({ materialId: m.materialId!, requiredQty: m.requiredQty, adjustReason: m.adjustReason?.trim() }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!(num(form.value.qty) > 0)) return ElMessage.warning('数量必须大于 0')
  const noReason = form.value.materials.find((m) => isAdjusted(m) && !m.adjustReason?.trim())
  if (noReason) return ElMessage.warning(`物料「${noReason.materialCode}」调整了应发数量，请填写原因`)
  saving.value = true
  try {
    const data = payload()
    let oid = id.value
    if (oid) await outsourcingApi.update(oid, data)
    else oid = await outsourcingApi.create(data)
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await outsourcingApi.submit(oid)).status))
      } catch {
        if (!id.value) router.replace(`/purchase/outsourcing/${oid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/outsourcing/${oid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑委外单 ${detail.value.docNo}` : '新建委外单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="OS_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('pur:outsourcing:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="加工商" prop="supplierId"><SupplierSelect v-model="form.supplierId" :statuses="['QUALIFIED']" @select="onSupplier" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="加工物料" prop="materialId">
              <MaterialSelect v-model="form.materialId" :types="['SEMI_FINISHED', 'FINISHED']" :disabled="!!id" @select="onMaterial" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="BOM"><span class="mono">{{ form.bomText || '选择物料后带出默认 BOM' }}</span></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="数量" prop="qty"><QtyInput v-model="form.qty" :uom="materialUom" @change="preview" /></el-form-item>
          </el-col>
          <el-col v-if="canPrice" :xl="8" :span="12"><el-form-item label="加工费单价"><PriceInput v-model="form.processPrice" placeholder="不含税" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="税率(%)"><NumberInput v-model="form.taxPct" :precision="2" trim-zeros /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="币别" prop="currency">
              <CurrencySelect v-model="form.currency" :rate-date="today()" @rate="(r) => (form.exchangeRate = r ?? form.exchangeRate)" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="汇率"><NumberInput v-model="form.exchangeRate" :precision="6" trim-zeros /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="要求日期" prop="requiredDate"><el-date-picker v-model="form.requiredDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="用料明细">
      <template #extra><span class="text-muted">应发数量 = 数量 × 单位用量；调整应发数量需填写原因</span></template>
      <el-table :data="form.materials" border>
        <el-table-column type="index" label="行" width="50" />
        <el-table-column label="子件" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column label="单位用量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer) }}</template></el-table-column>
        <el-table-column label="应发数量" width="140"><template #default="{ row }"><QtyInput v-model="row.requiredQty" :uom="row.uom" /></template></el-table-column>
        <el-table-column label="可用库存" width="100" align="right"><template #default="{ row }">{{ formatQty(row.availableQty) }}</template></el-table-column>
        <el-table-column label="调整原因" min-width="180">
          <template #default="{ row }"><el-input v-if="isAdjusted(row)" v-model="row.adjustReason" maxlength="256" placeholder="必填" /></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="选择加工物料后按默认 BOM 生成用料" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_OUTSOURCING" multiple />
      <AttachmentPanel v-else biz-type="PUR_OUTSOURCING" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>
