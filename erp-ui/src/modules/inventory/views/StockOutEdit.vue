<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { LineColumn } from '@/components'
import type { MaterialBrief, WarehouseBrief } from '@/api/refs'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty, today } from '@/utils/format'
import { AVAILABLE_TYPES, docApi, DOC_STATUS, labelOf, OUT_TYPE_OPTIONS, parseSerials, stockOutApi, type StockOutDetail, type StockOutLine } from '../api/inventory'

defineOptions({ name: 'InvStockOutEdit' })

/**
 * 出库单编辑 / 确认（需求 08-04 3.2，T4）。核心是分配批次、库位：
 * [自动分配批次] 按 FIFO/FEFO 分配，数量不够一个批次时拆成多行，库存不足的数量单独一行标红；[拆分行] 用于手工指定多个批次。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const saving = ref(false)
const allocating = ref(false)
const detail = ref<StockOutDetail>()
const locationEnabled = ref(false)

interface Line extends StockOutLine {
  serialText?: string
}
interface Form {
  warehouseId?: string
  docDate: string
  receiverDeptId?: string
  receiverId?: string
  reason?: string
  remark?: string
  fileIds: string[]
  lines: Line[]
}

const newLine = (): Line => ({})
const form = ref<Form>({ docDate: today(), fileIds: [], lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)

const manual = computed(() => !detail.value || detail.value.manual)
const fullEdit = computed(() => !detail.value || (detail.value.manual && detail.value.status === 'DRAFT'))
const canConfirm = computed(() => !!detail.value && (detail.value.manual ? detail.value.status === 'APPROVED' : detail.value.status === 'DRAFT'))
const title = computed(() => (!id.value ? '新建其他出库' : canConfirm.value ? `确认出库 ${detail.value?.docNo ?? ''}` : `编辑 ${detail.value?.docNo ?? ''}`))
const scrap = computed(() => form.value.reason === 'SCRAP')

const rules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  reason: [{ required: true, message: '请选择出库原因', trigger: 'change' }]
}

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 140, required: true, editable: () => fullEdit.value },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 150 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 140 },
  { prop: 'uom', label: '单位', type: 'readonly', width: 60 },
  ...(manual.value ? [] : [{ prop: 'requestQty', label: '申请数量', type: 'readonly' as const, width: 90, formatter: (r: Line) => formatQty(r.requestQty) }]),
  { prop: 'availableQty', label: '可用库存', type: 'readonly', width: 90, formatter: (r) => (r.availableQty === undefined ? '' : formatQty(r.availableQty)) },
  { prop: 'qty', label: manual.value ? '数量' : '实发数量', type: 'slot', width: 120 },
  ...(locationEnabled.value ? [{ prop: 'locationId', label: '库位', type: 'slot' as const, width: 140 }] : []),
  { prop: 'batchNo', label: '批次', type: 'slot', width: 200 },
  { prop: 'serialText', label: '序列号', type: 'text', width: 160, editable: (r: Line) => r.tracking === 'SERIAL' || !r.tracking },
  { prop: 'remark', label: '备注', type: 'text', width: 130 }
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
  const d = await stockOutApi.get(id.value)
  detail.value = d
  locationEnabled.value = d.locationEnabled
  form.value = {
    warehouseId: d.warehouseId, docDate: d.docDate, receiverDeptId: d.receiverDeptId, receiverId: d.receiverId, reason: d.reason, remark: d.remark,
    fileIds: [], lines: d.lines.map((l) => ({ ...l, serialText: (l.serialNos ?? []).join(',') }))
  }
  tabs.setTitle(tabKeyOf(route), d.docNo)
  guard.markClean()
}

function toSave(l: Line) {
  return {
    id: l.id, materialId: l.materialId, uom: l.uom, requestQty: l.requestQty, qty: l.qty ?? '0', locationId: l.locationId, batchNo: l.batchNo,
    serialNos: parseSerials(l.serialText), sourceLineId: l.sourceLineId, remark: l.remark
  }
}

// ---------- 批次分配 ----------
async function autoAllocate() {
  if (!id.value) return ElMessage.warning('请先保存单据')
  allocating.value = true
  try {
    const allocated = await stockOutApi.autoAllocate(id.value, form.value.lines.filter((l) => l.materialId).map(toSave))
    const byId = new Map(form.value.lines.map((l) => [l.id, l]))
    form.value.lines = allocated.map((a) => {
      const origin = byId.get(a.id)
      return { ...origin, ...a, id: a.id, uom: a.uom, qty: a.qty, requestQty: a.requestQty ?? origin?.requestQty, batchNo: a.batchNo, locationId: a.locationId,
        locationCode: a.locationCode, shortage: a.shortage, serialText: origin?.serialText }
    })
    const short = allocated.filter((a) => a.shortage)
    if (short.length) ElMessage.warning(`${short.length} 行库存不足（标红），请修改实发数量或删除该行后确认`)
    else ElMessage.success('已按出库规则分配批次')
  } finally {
    allocating.value = false
  }
}

/** 拆分行：复制当前行（同一来源行），数量平分，用于手工指定多个批次 */
function splitRow(index: number) {
  const r = form.value.lines[index]
  const q = Number(r.qty ?? 0)
  const half = Math.floor((q / 2) * 10000) / 10000
  form.value.lines.splice(index + 1, 0, { ...r, qty: String(q - half), batchNo: undefined, locationId: undefined, shortage: undefined, serialText: '' })
  r.qty = String(half)
}

// ---------- 保存 ----------
async function validate() {
  if (fullEdit.value && !(await formRef.value?.validate().catch(() => false))) return false
  return linesRef.value?.validate() ?? true
}

async function save(then?: 'submit' | 'confirm') {
  if (!(await validate())) return
  saving.value = true
  try {
    const lines = form.value.lines.filter((l) => l.materialId).map(toSave)
    const body = { ...form.value, lines, version: detail.value?.version }
    let docId = id.value
    if (!docId) docId = await stockOutApi.create(body)
    else if (detail.value?.status === 'DRAFT') await stockOutApi.update(docId, body)
    guard.markClean()
    const api = docApi('out')
    if (then === 'submit') {
      const st = await api.submit(docId)
      ElMessage.success(st === 'COMPLETED' ? '提交成功，已出库' : '已提交审批')
    } else if (then === 'confirm') {
      await api.confirm(docId, { docDate: form.value.docDate, outLines: detail.value?.status === 'APPROVED' ? lines : undefined })
      ElMessage.success('已确认出库')
    } else ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/inventory/out/${docId}`)
  } finally {
    saving.value = false
  }
}

async function back() {
  if (await guard.confirmLeave()) router.back()
}

/** 拆分出来的行、库存不足行可以删除；原始行至少保留一行 */
const canRemove = (r: Line) => !!r.shortage || form.value.lines.filter((l) => l.id === r.id).length > 1

const rowClass = ({ row }: { row: unknown }) => ((row as Line).shortage ? 'is-shortage' : '')
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
      <el-button v-if="manual && (!detail || detail.status === 'DRAFT') && me.hasPermission('inv:out:submit')" type="primary" :loading="saving" @click="save('submit')">提交</el-button>
      <el-button v-if="canConfirm && me.hasPermission('inv:out:confirm')" type="primary" :loading="saving" @click="save('confirm')">确认出库</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="24">
          <el-col :xl="6" :span="8"><el-form-item label="出库类型">{{ labelOf(OUT_TYPE_OPTIONS, detail?.outType ?? 'OTHER_OUT') }}</el-form-item></el-col>
          <el-col :xl="6" :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <WarehouseSelect v-if="fullEdit" v-model="form.warehouseId" :types="scrap ? [...AVAILABLE_TYPES, 'NG'] : AVAILABLE_TYPES" @select="onWarehouse" />
              <span v-else>{{ detail?.warehouseName }}</span>
            </el-form-item>
          </el-col>
          <el-col :xl="6" :span="8"><el-form-item label="单据日期"><el-date-picker v-model="form.docDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" /></el-form-item></el-col>
          <el-col v-if="manual" :xl="6" :span="8">
            <el-form-item label="出库原因" prop="reason"><DictSelect v-model="form.reason" type="inv_other_out_reason" :disabled="!fullEdit" /></el-form-item>
          </el-col>
          <el-col :xl="6" :span="8"><el-form-item label="领用部门"><OrgTreeSelect v-model="form.receiverDeptId" :disabled="!!detail && detail.status !== 'DRAFT'" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="领用人"><UserSelect v-model="form.receiverId" :disabled="!!detail && detail.status !== 'DRAFT'" /></el-form-item></el-col>
          <el-col v-if="detail?.sourceNo" :xl="6" :span="8"><el-form-item label="来源单号"><span class="mono">{{ detail.sourceNo }}</span></el-form-item></el-col>
          <el-col :xl="12" :span="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="512" :disabled="!!detail && detail.status !== 'DRAFT'" :placeholder="scrap ? '报废出库请填写说明或上传附件' : ''" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra>
        <span class="text-muted">{{ manual ? '冻结、过期批次不能出库（报废出库除外）' : '领料可少于申请数量；销售出库、采购退货实发必须等于申请数量' }}</span>
      </template>
      <div v-if="id && (canConfirm || detail?.status === 'DRAFT')" class="alloc">
        <el-button icon="Operation" :loading="allocating" @click="autoAllocate">自动分配批次</el-button>
      </div>
      <el-table v-if="!fullEdit" :data="form.lines" :row-class-name="rowClass" max-height="560">
        <el-table-column type="index" label="#" width="46" />
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="materialName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column v-if="!manual" label="申请数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.requestQty) }}</span></template></el-table-column>
        <el-table-column label="可用库存" width="100" align="right"><template #default="{ row }"><span class="num">{{ row.availableQty === undefined ? '' : formatQty(row.availableQty) }}</span></template></el-table-column>
        <el-table-column label="实发数量" width="130">
          <template #default="{ row }">
            <QtyInput v-model="asLine(row).qty" :uom="asLine(row).uom" />
            <div v-if="asLine(row).shortage" class="text-danger short">库存不足 {{ formatQty(asLine(row).shortage) }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="locationEnabled" label="库位" width="150">
          <template #default="{ row }"><LocationSelect v-model="asLine(row).locationId" :warehouse-id="form.warehouseId" /></template>
        </el-table-column>
        <el-table-column label="批次" width="220">
          <template #default="{ row }">
            <BatchSelect v-if="asLine(row).tracking === 'BATCH'" v-model="asLine(row).batchNo" :material-id="asLine(row).materialId" :warehouse-id="form.warehouseId"
                         :rule="asLine(row).issueRule === 'FEFO' ? 'FEFO' : 'FIFO'" />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="序列号" width="180">
          <template #default="{ row }">
            <el-input v-if="asLine(row).tracking === 'SERIAL'" v-model="asLine(row).serialText" placeholder="逗号分隔" />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="asLine(row).remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="110" align="center">
          <template #default="{ $index, row }">
            <el-button link type="primary" @click="splitRow($index)">拆分</el-button>
            <el-button link type="danger" :disabled="!canRemove(asLine(row))"
                       @click="form.lines.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <LinesEditor v-else ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial">
        <template #cell-qty="{ row, disabled }">
          <QtyInput v-model="asLine(row).qty" :uom="asLine(row).uom" :disabled="disabled" />
        </template>
        <template #cell-locationId="{ row, disabled }">
          <LocationSelect v-model="asLine(row).locationId" :warehouse-id="form.warehouseId" :disabled="disabled" />
        </template>
        <template #cell-batchNo="{ row, disabled }">
          <BatchSelect v-model="asLine(row).batchNo" :material-id="asLine(row).materialId" :warehouse-id="form.warehouseId" :disabled="disabled" />
        </template>
      </LinesEditor>
    </ErpPanel>

    <ErpPanel v-if="manual" title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="INV_STOCK_OUT" multiple />
      <AttachmentPanel v-else biz-type="INV_STOCK_OUT" :biz-id="id" :editable="fullEdit" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.alloc { margin-bottom: var(--erp-space-3); }
.short { font-size: var(--erp-font-size-secondary); }
:deep(.is-shortage) { background: var(--erp-color-error-bg); }
</style>
