<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { num, submitText } from '../api/common'
import { HANDLING_OPTIONS, receiptApi, RETURN_STATUS, returnApi, type ReturnableLine, type ReturnDetail, type ReturnLine, type ReturnSave } from '../api/receipt'

defineOptions({ name: 'PurReturnEdit' })

/** 退货单编辑（需求 07-08 3.2，T4）：从到货记录选单带入 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<ReturnableLine[]> }>()
const saving = ref(false)
const detail = ref<ReturnDetail>()
const canPrice = computed(() => me.hasPermission('pur:price:view'))

interface Form {
  supplierId?: string
  returnReason?: string
  handling: string
  warehouseId?: string
  ncrNo?: string
  remark?: string
  fileIds: string[]
  lines: ReturnLine[]
}
const form = ref<Form>({ handling: 'REPLACE', fileIds: [], lines: [] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  returnReason: [{ required: true, message: '请选择退货原因', trigger: 'change' }],
  handling: [{ required: true, message: '请选择处理方式', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择出库仓', trigger: 'change' }]
}

const pickColumns: TableColumn<ReturnableLine>[] = [
  { prop: 'receiptNo', label: '到货单', width: 150 },
  { prop: 'orderNo', label: '订单号', width: 150 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'batchNo', label: '批次', width: 130 },
  { prop: 'baseQty', label: '到货', width: 90, type: 'qty' },
  { prop: 'rejectedQty', label: '不合格', width: 90, type: 'qty' },
  { prop: 'returnedQty', label: '已退货', width: 90, type: 'qty' },
  { prop: 'returnableQty', label: '可退', width: 90, type: 'qty' }
]
const returnableApi = (q: Record<string, any>) => receiptApi.returnable(q as Parameters<typeof receiptApi.returnable>[0])
const pickFields: SearchField[] = [{ prop: 'receiptNo', label: '到货单号', upper: true }]

async function pick() {
  if (!form.value.supplierId) return ElMessage.warning('请先选择供应商')
  const rows = (await pickerRef.value?.open({ supplierId: form.value.supplierId })) ?? []
  const exist = new Set(form.value.lines.map((l) => l.receiptLineId))
  form.value.lines.push(...rows.filter((r) => !exist.has(r.id)).map((r) => ({
    receiptLineId: r.id, receiptId: r.receiptId, receiptNo: r.receiptNo, orderNo: r.orderNo, materialId: r.materialId, materialCode: r.materialCode,
    materialName: r.materialName, baseUom: r.baseUom, batchNo: r.batchNo, returnableQty: r.returnableQty, priceInclTax: r.priceInclTax,
    qty: num(r.rejectedQty) > 0 ? String(Math.min(num(r.rejectedQty), num(r.returnableQty))) : r.returnableQty
  } as ReturnLine)))
}

const total = computed(() => form.value.lines.reduce((s, l) => s + num(l.qty) * num(l.priceInclTax), 0))

onMounted(async () => {
  if (id.value) {
    const d = await returnApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的退货单可以修改')
      router.replace(`/purchase/return/${id.value}`)
      return
    }
    detail.value = d
    form.value = { supplierId: d.supplierId, returnReason: d.returnReason, handling: d.handling, warehouseId: d.warehouseId, ncrNo: d.ncrNo, remark: d.remark,
      fileIds: [], lines: d.lines.map((l) => ({ ...l })) }
    tabs.setTitle(tabKeyOf(route), `编辑退货单 ${d.docNo}`)
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

function onSupplierChange() {
  if (form.value.lines.length) {
    form.value.lines = []
    ElMessage.info('已清空明细，请重新选择到货记录')
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/return/${id.value}` : '/purchase/return')
}

function payload(): ReturnSave {
  const f = form.value
  return {
    supplierId: f.supplierId!, returnReason: f.returnReason!, handling: f.handling, warehouseId: f.warehouseId!, ncrNo: f.ncrNo?.trim() || undefined,
    remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    lines: f.lines.map((l) => ({ receiptLineId: l.receiptLineId!, batchNo: l.batchNo?.trim() || undefined, qty: l.qty!, remark: l.remark?.trim() || undefined }))
  }
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!form.value.lines.length) return ElMessage.warning('请至少添加一行明细')
  const bad = form.value.lines.findIndex((l) => !(num(l.qty) > 0))
  if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 行：退货数量必须大于 0`)
  saving.value = true
  try {
    const data = payload()
    const r = id.value ? await returnApi.update(id.value, data) : await returnApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '库存提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await returnApi.submit(r.id)).status))
      } catch {
        if (!id.value) router.replace(`/purchase/return/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/return/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑退货单 ${detail.value.docNo}` : '新建退货'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="RETURN_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('pur:return:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="供应商" prop="supplierId"><SupplierSelect v-model="form.supplierId" :disabled="!!id" @update:model-value="onSupplierChange" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="退货原因" prop="returnReason"><DictSelect v-model="form.returnReason" type="pur_return_reason" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="处理方式" prop="handling">
              <el-radio-group v-model="form.handling"><el-radio v-for="o in HANDLING_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="出库仓" prop="warehouseId"><WarehouseSelect v-model="form.warehouseId" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="关联 NCR"><el-input v-model="form.ncrNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="1000" /></el-form-item></el-col>
        </el-row>
        <p class="form-tip">换货：出库后订单行恢复未到货数量，供应商补货后再次到货；退款：对账时生成负数行冲减应付</p>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra>
        <span v-if="canPrice" class="total">金额 <strong class="num">{{ formatAmount(total) }}</strong></span>
        <el-button type="primary" icon="Link" @click="pick">选择到货记录</el-button>
      </template>
      <el-table :data="form.lines" border>
        <el-table-column type="index" label="行" width="50" />
        <el-table-column prop="receiptNo" label="到货单" width="150" />
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="批次" width="150"><template #default="{ row }"><el-input v-model="row.batchNo" maxlength="64" /></template></el-table-column>
        <el-table-column label="可退数量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.returnableQty) }}</template></el-table-column>
        <el-table-column label="退货数量" width="130"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="row.baseUom" /></template></el-table-column>
        <el-table-column v-if="canPrice" label="含税单价" width="110" align="right"><template #default="{ row }">{{ row.priceInclTax ?? '-' }}</template></el-table-column>
        <el-table-column v-if="canPrice" label="金额" width="120" align="right"><template #default="{ row }">{{ formatAmount(num(row.qty) * num(row.priceInclTax)) }}</template></el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.lines.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact description="点击“选择到货记录”带入" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_RETURN" multiple />
      <AttachmentPanel v-else biz-type="PUR_RETURN" :biz-id="id" editable />
    </ErpPanel>

    <SourceDocPicker ref="pickerRef" title="选择到货记录" :api="returnableApi" :columns="pickColumns" :search-fields="pickFields"
                     :exclude-keys="form.lines.map((l) => l.receiptLineId).filter((x): x is string => !!x)" />
  </ErpPage>
</template>

<style scoped>
.total { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
</style>
