<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief, WarehouseBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { today } from '@/utils/format'
import { AVAILABLE_TYPES, docApi, DOC_STATUS, IN_TYPE_OPTIONS, labelOf, parseSerials, stockInApi, type StockInDetail, type StockInLine } from '../api/inventory'

defineOptions({ name: 'InvStockInEdit' })

/**
 * 入库单编辑 / 确认（需求 08-03 3.2，T4）。
 * 手工“其他入库”可改全部；业务生成的单据只能补充库位、批次、生产日期、序列号、备注后确认入库。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const saving = ref(false)
const detail = ref<StockInDetail>()
const locationEnabled = ref(false)

interface Line extends StockInLine {
  serialText?: string
}
interface Form {
  warehouseId?: string
  docDate: string
  reason?: string
  remark?: string
  fileIds: string[]
  lines: Line[]
}

const newLine = (): Line => ({})
const form = ref<Form>({ docDate: today(), fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)

/** 手工单草稿可改全部 */
const manual = computed(() => !detail.value || detail.value.manual)
const fullEdit = computed(() => !detail.value || (detail.value.manual && detail.value.status === 'DRAFT'))
const canConfirm = computed(() => !!detail.value && (detail.value.manual ? detail.value.status === 'APPROVED' : detail.value.status === 'DRAFT'))
const title = computed(() => (!id.value ? '新建其他入库' : canConfirm.value ? `确认入库 ${detail.value?.docNo ?? ''}` : `编辑 ${detail.value?.docNo ?? ''}`))

const rules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  reason: [{ required: true, message: '请选择入库原因', trigger: 'change' }]
}

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 140, required: true, editable: () => fullEdit.value },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 150 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 150 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'qty', label: '数量', type: 'qty', width: 100, required: true, uomProp: 'uom', editable: () => fullEdit.value, summary: true },
  ...(locationEnabled.value ? [{ prop: 'locationId', label: '库位', type: 'slot' as const, width: 150 }] : []),
  { prop: 'batchNo', label: '批次号', type: 'text', width: 140, editable: (r: Line) => r.tracking === 'BATCH' || !r.tracking },
  { prop: 'supplierBatchNo', label: '供应商批号', type: 'text', width: 120 },
  { prop: 'productionDate', label: '生产日期', type: 'date', width: 130 },
  { prop: 'expireDate', label: '到期日期', type: 'date', width: 130 },
  { prop: 'serialText', label: '序列号', type: 'text', width: 160, editable: (r: Line) => r.tracking === 'SERIAL' || !r.tracking },
  ...(manual.value ? [{ prop: 'unitCost', label: '单价', type: 'price' as const, width: 100, editable: () => fullEdit.value }] : []),
  { prop: 'remark', label: '备注', type: 'text', width: 140 }
])

function onMaterial(row: Line, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.uom = m?.baseUom
  row.baseUom = m?.baseUom
}

function onWarehouse(w?: WarehouseBrief) {
  locationEnabled.value = !!w?.locationEnabled
}

async function load() {
  if (!id.value) return
  const d = await stockInApi.get(id.value)
  detail.value = d
  locationEnabled.value = d.locationEnabled
  form.value = {
    warehouseId: d.warehouseId, docDate: d.docDate, reason: d.reason, remark: d.remark, fileIds: [],
    lines: d.lines.map((l) => ({ ...l, serialText: (l.serialNos ?? []).join(',') }))
  }
  tabs.setTitle(tabKeyOf(route), d.docNo)
  guard.markClean()
}

function payloadLines(rows: Line[]) {
  return rows.map((l) => ({
    id: l.id, materialId: l.materialId, uom: l.uom, qty: l.qty, locationId: l.locationId, batchNo: l.batchNo, supplierBatchNo: l.supplierBatchNo,
    productionDate: l.productionDate, expireDate: l.expireDate, serialNos: parseSerials(l.serialText), unitCost: l.unitCost, remark: l.remark
  }))
}

async function validate() {
  if (fullEdit.value && !(await formRef.value?.validate().catch(() => false))) return false
  return linesRef.value?.validate() ?? true
}

async function save(then?: 'submit' | 'confirm') {
  if (!(await validate())) return
  saving.value = true
  try {
    const body = { ...form.value, lines: payloadLines(linesRef.value!.validRows()), version: detail.value?.version }
    let docId = id.value
    if (!docId) docId = await stockInApi.create(body)
    else if (detail.value?.status === 'DRAFT') await stockInApi.update(docId, body)
    guard.markClean()
    const api = docApi('in')
    if (then === 'submit') {
      const st = await api.submit(docId)
      ElMessage.success(st === 'COMPLETED' ? '提交成功，已入库' : '已提交审批')
    } else if (then === 'confirm') {
      await api.confirm(docId, { docDate: form.value.docDate, lines: detail.value?.status === 'APPROVED' ? payloadLines(linesRef.value!.validRows()) : undefined })
      ElMessage.success('已确认入库')
    } else ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/inventory/in/${docId}`)
  } finally {
    saving.value = false
  }
}

async function back() {
  if (await guard.confirmLeave()) router.back()
}

const asLine = (r: unknown) => r as Line
onMounted(load)
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta>
      <StatusTag v-if="detail" :value="detail.status" :map="DOC_STATUS" />
    </template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button v-if="!detail || detail.status === 'DRAFT'" :loading="saving" @click="save()">保存</el-button>
      <el-button v-if="manual && (!detail || detail.status === 'DRAFT') && me.hasPermission('inv:in:submit')" type="primary" :loading="saving" @click="save('submit')">提交</el-button>
      <el-button v-if="canConfirm && me.hasPermission('inv:in:confirm')" type="primary" :loading="saving" @click="save('confirm')">确认入库</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-alert v-if="detail && !detail.manual" type="info" :closable="false" show-icon class="tip"
                title="业务生成的入库单不能修改物料和数量，只能补充库位、批次、生产日期、序列号和备注" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="24">
          <el-col :xl="6" :span="8"><el-form-item label="入库类型">{{ labelOf(IN_TYPE_OPTIONS, detail?.inType ?? 'OTHER_IN') }}</el-form-item></el-col>
          <el-col :xl="6" :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <WarehouseSelect v-if="fullEdit" v-model="form.warehouseId" :types="[...AVAILABLE_TYPES, 'NG']" @select="onWarehouse" />
              <span v-else>{{ detail?.warehouseName }}</span>
            </el-form-item>
          </el-col>
          <el-col :xl="6" :span="8"><el-form-item label="单据日期"><el-date-picker v-model="form.docDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" /></el-form-item></el-col>
          <el-col v-if="manual" :xl="6" :span="8">
            <el-form-item label="入库原因" prop="reason"><DictSelect v-model="form.reason" type="inv_other_in_reason" :disabled="!fullEdit" /></el-form-item>
          </el-col>
          <el-col v-if="detail?.sourceNo" :xl="6" :span="8"><el-form-item label="来源单号"><span class="mono">{{ detail.sourceNo }}</span></el-form-item></el-col>
          <el-col :xl="12" :span="16"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" :disabled="!!detail && detail.status !== 'DRAFT'" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra><span class="text-muted">数量为业务单位；批次管理物料未填批次号时确认后自动生成</span></template>
      <LinesEditor ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial" :hide-toolbar="!fullEdit">
        <template #cell-locationId="{ row, disabled }">
          <LocationSelect v-model="asLine(row).locationId" :warehouse-id="form.warehouseId" :disabled="disabled" />
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel v-if="manual" title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="INV_STOCK_IN" multiple />
      <AttachmentPanel v-else biz-type="INV_STOCK_IN" :biz-id="id" :editable="fullEdit" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.tip { margin-bottom: var(--erp-space-4); }
</style>
