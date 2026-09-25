<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief, SupplierBrief } from '@/api/refs'
import { refApi } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { submitText } from '../api/common'
import { ADJUST_STATUS, adjustApi, priceApi, type AdjustDetail, type AdjustLine, type AdjustSave } from '../api/price'

defineOptions({ name: 'PurPriceAdjustEdit' })

/** 调价单编辑（需求 07-02 3.2，T4） */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => AdjustLine[] }>()
const saving = ref(false)
const detail = ref<AdjustDetail>()

interface Form {
  supplierId?: string
  currency?: string
  adjustReason?: string
  remark?: string
  fileIds: string[]
  lines: AdjustLine[]
}

const newLine = (): AdjustLine => ({ minQty: '0', effectiveFrom: today() })
const form = ref<Form>({ fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币别', trigger: 'change' }],
  adjustReason: [{ required: true, message: '请输入调价原因', trigger: 'blur' }]
}

/** 涨跌幅（小数） */
function changePct(l: AdjustLine) {
  const o = Number(l.oldPrice)
  const n = Number(l.newPrice)
  return o > 0 && n > 0 ? (n - o) / o : undefined
}

const columns = computed<LineColumn<AdjustLine>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 150, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 160 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 160 },
  { prop: 'baseUom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'minQty', label: '阶梯起始数量', type: 'qty', width: 120, required: true, uomProp: 'baseUom' },
  { prop: 'oldPrice', label: '原价', type: 'readonly', width: 100 },
  { prop: 'newPrice', label: '新价（不含税）', type: 'price', width: 120, required: true, validate: (v) => (Number(v) > 0 ? undefined : '新价必须大于 0') },
  { prop: 'taxRatePct', label: '税率(%)', type: 'number', width: 90, precision: 2, min: 0 },
  { prop: 'changePct', label: '涨跌幅', type: 'slot', width: 90 },
  { prop: 'effectiveFrom', label: '生效日期', type: 'date', width: 140, required: true },
  { prop: 'effectiveTo', label: '失效日期', type: 'date', width: 140 },
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

async function fillOldPrice(row: AdjustLine) {
  row.oldPrice = undefined
  if (!form.value.supplierId || !row.materialId || !form.value.currency) return
  const p = await priceApi.effective({ supplierId: form.value.supplierId, materialId: row.materialId, currency: form.value.currency, qty: row.minQty }).catch(() => null)
  row.oldPrice = p?.price
}

async function onMaterial(row: AdjustLine, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.baseUom = m?.baseUom
  await fillOldPrice(row)
}

function onSupplier(s?: SupplierBrief | SupplierBrief[]) {
  const x = Array.isArray(s) ? s[0] : s
  if (x?.currency) form.value.currency = x.currency
  form.value.lines.forEach((l) => l.materialId && fillOldPrice(l))
}

onMounted(async () => {
  if (id.value) {
    const d = await adjustApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的调价单可以修改')
      router.replace(`/purchase/price/adjust/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      supplierId: d.supplierId, currency: d.currency, adjustReason: d.adjustReason, remark: d.remark, fileIds: [],
      lines: [...d.lines.map((l) => ({ ...l, taxRatePct: pctOf(l.taxRate) } as AdjustLine)), newLine()]
    }
    tabs.setTitle(tabKeyOf(route), `编辑调价单 ${d.docNo}`)
  } else {
    const q = route.query
    if (typeof q.supplierId === 'string') {
      form.value.supplierId = q.supplierId
      form.value.currency = typeof q.currency === 'string' ? q.currency : undefined
    }
    if (typeof q.materialId === 'string') {
      const [m] = await refApi.materialSearch({ ids: q.materialId, status: '' })
      if (m) {
        const l: AdjustLine = { ...newLine(), materialId: m.id, materialCode: m.code }
        await onMaterial(l, m)
        form.value.lines = [l, newLine()]
      }
    }
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

const pctOf = (v?: string) => (v === undefined || v === null ? undefined : String(Number((Number(v) * 100).toFixed(4))))
const rateOf = (v?: string) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) / 100).toFixed(6))))

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/price/adjust/${id.value}` : '/purchase/price')
}

function payload(): AdjustSave {
  const f = form.value
  return {
    supplierId: f.supplierId!, currency: f.currency, adjustReason: f.adjustReason!.trim(), remark: f.remark?.trim() || undefined, fileIds: f.fileIds,
    version: detail.value?.version,
    lines: linesRef.value!.validRows().map((l) => ({
      materialId: l.materialId!, minQty: l.minQty || '0', newPrice: l.newPrice!, taxRate: rateOf((l as AdjustLine & { taxRatePct?: string }).taxRatePct),
      effectiveFrom: l.effectiveFrom, effectiveTo: l.effectiveTo || undefined, remark: l.remark?.trim() || undefined
    }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!linesRef.value?.validate()) return
  saving.value = true
  try {
    const data = payload()
    let aid = id.value
    if (aid) await adjustApi.update(aid, data)
    else aid = await adjustApi.create(data)
    guard.markClean()
    if (submit) {
      try {
        const r = await adjustApi.submit(aid)
        if (r.warnings?.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
        ElMessage.success(r.status === 'APPROVED' ? '提交成功，价格已生效' : submitText(r.status))
      } catch {
        if (!id.value) router.replace(`/purchase/price/adjust/${aid}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/price/adjust/${aid}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑调价单 ${detail.value.docNo}` : '新建调价单'))
const asLine = (r: unknown) => r as AdjustLine
const pctText = (l: AdjustLine) => {
  const p = changePct(l)
  return p === undefined ? '-' : `${p > 0 ? '+' : ''}${(p * 100).toFixed(2)}%`
}
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="ADJUST_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('pur:price:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <SupplierSelect v-model="form.supplierId" :statuses="['POTENTIAL', 'QUALIFIED']" @select="onSupplier" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="币别" prop="currency"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="调价原因" prop="adjustReason"><el-input v-model="form.adjustReason" maxlength="256" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra><span class="text-muted">新价为不含税单价；税率不填时取供应商默认税率；涨幅超过阈值的行需要审批</span></template>
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial">
        <template #cell-changePct="{ row }">
          <span :class="['num', (changePct(asLine(row)) ?? 0) > 0 ? 'text-danger' : (changePct(asLine(row)) ?? 0) < 0 ? 'text-success' : '']">{{ pctText(asLine(row)) }}</span>
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel title="附件（供应商报价单）">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_PRICE_ADJUST" multiple />
      <AttachmentPanel v-else biz-type="PUR_PRICE_ADJUST" :biz-id="id" editable />
    </ErpPanel>
  </ErpPage>
</template>
