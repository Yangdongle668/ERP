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
import { AVAILABLE_TYPES, docApi, DOC_STATUS, JUDGE_OPTIONS, labelOf, parseSerials, transferApi, TRANSFER_TYPE_OPTIONS, type TransferDetail, type TransferLine } from '../api/inventory'

defineOptions({ name: 'InvTransferEdit' })

/**
 * 调拨单编辑 / 确认（需求 08-05 3.2，T4）。普通调拨、复检送检手工新建；
 * 检验调拨由系统生成，只能补充调入库位后确认。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const linesRef = ref<{ validate: () => boolean; validRows: () => Line[] }>()
const saving = ref(false)
const detail = ref<TransferDetail>()
const fromLoc = ref(false)
const toLoc = ref(false)

interface Line extends TransferLine {
  serialText?: string
}
interface Form {
  transferType: string
  fromWarehouseId?: string
  toWarehouseId?: string
  docDate: string
  reason?: string
  remark?: string
  lines: Line[]
}

const newLine = (): Line => ({})
const form = ref<Form>({ transferType: route.query.type === 'RECHECK' ? 'RECHECK' : 'NORMAL', docDate: today(), lines: [newLine()] })
const guard = useLeaveGuard(() => form.value)
const inspection = computed(() => form.value.transferType === 'INSPECTION')
const recheck = computed(() => form.value.transferType === 'RECHECK')
const title = computed(() => (!id.value ? (recheck.value ? '复检送检' : '新建调拨') : `确认调拨 ${detail.value?.docNo ?? ''}`))

/** 1.1 节：普通调拨 可用仓 → 可用仓/不良品仓；复检送检 可用仓/不良品仓 → 待检仓 */
const fromTypes = computed(() => (recheck.value ? [...AVAILABLE_TYPES, 'NG'] : AVAILABLE_TYPES))
const toTypes = computed(() => (recheck.value ? ['QC'] : [...AVAILABLE_TYPES, 'NG']))

const rules: FormRules = {
  fromWarehouseId: [{ required: true, message: '请选择调出仓', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓', trigger: 'change' }],
  reason: [{ required: true, message: '请填写调拨原因', trigger: 'blur' }]
}

const columns = computed<LineColumn<Line>[]>(() => [
  { prop: 'materialCode', label: '物料编码', type: 'material', width: 140, required: true },
  { prop: 'materialName', label: '名称', type: 'readonly', width: 150 },
  { prop: 'materialSpec', label: '规格', type: 'readonly', width: 140 },
  { prop: 'baseUom', label: '单位', type: 'readonly', width: 60 },
  { prop: 'batchNo', label: '批次', type: 'slot', width: 200 },
  ...(fromLoc.value ? [{ prop: 'fromLocationId', label: '调出库位', type: 'slot' as const, width: 140 }] : []),
  ...(toLoc.value ? [{ prop: 'toLocationId', label: '调入库位', type: 'slot' as const, width: 140 }] : []),
  { prop: 'qty', label: '数量', type: 'qty', width: 110, required: true, uomProp: 'baseUom', summary: true },
  { prop: 'serialText', label: '序列号', type: 'text', width: 160 },
  { prop: 'remark', label: '备注', type: 'text', width: 130 }
])

function onMaterial(row: Line, m: MaterialBrief | undefined) {
  row.materialName = m?.name
  row.materialSpec = m?.spec
  row.baseUom = m?.baseUom
}

async function load() {
  if (!id.value) return
  const d = await transferApi.get(id.value)
  detail.value = d
  fromLoc.value = d.fromLocationEnabled
  toLoc.value = d.toLocationEnabled
  form.value = {
    transferType: d.transferType, fromWarehouseId: d.fromWarehouseId, toWarehouseId: d.toWarehouseId, docDate: d.docDate, reason: d.reason,
    remark: d.remark, lines: d.lines.map((l) => ({ ...l, serialText: (l.serialNos ?? []).join(',') }))
  }
  tabs.setTitle(tabKeyOf(route), d.docNo)
  guard.markClean()
}

function toSave(l: Line) {
  return { id: l.id, materialId: l.materialId, qty: l.qty, batchNo: l.batchNo, fromLocationId: l.fromLocationId, toLocationId: l.toLocationId,
    serialNos: parseSerials(l.serialText), remark: l.remark }
}

async function save(confirm: boolean) {
  if (!inspection.value) {
    if (!(await formRef.value?.validate().catch(() => false))) return
    if (!linesRef.value?.validate()) return
  }
  saving.value = true
  try {
    const lines = (inspection.value ? form.value.lines : linesRef.value!.validRows()).map(toSave)
    const body = { ...form.value, lines, version: detail.value?.version }
    let docId = id.value
    if (!docId) docId = await transferApi.create(body)
    else await transferApi.update(docId, body)
    guard.markClean()
    if (confirm) {
      await docApi('transfer').confirm(docId, { docDate: form.value.docDate })
      ElMessage.success('已确认调拨')
    } else ElMessage.success('保存成功')
    tabs.remove([tabKeyOf(route)])
    router.push(`/inventory/transfer/${docId}`)
  } finally {
    saving.value = false
  }
}

async function back() {
  if (await guard.confirmLeave()) router.back()
}

const onFrom = (w?: WarehouseBrief) => { fromLoc.value = !!w?.locationEnabled }
const onTo = (w?: WarehouseBrief) => { toLoc.value = !!w?.locationEnabled }
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
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('inv:transfer:confirm')" type="primary" :loading="saving" @click="save(true)">确认调拨</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-alert v-if="inspection" type="info" :closable="false" show-icon class="tip" title="检验调拨单由品质判定后生成，不能修改物料和数量，只能补充库位后确认" />
      <el-alert v-else-if="recheck" type="info" :closable="false" show-icon class="tip" title="复检送检：库存超期或质量存疑时送回待检仓，确认后通知品质创建复检单" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="24">
          <el-col :xl="6" :span="8"><el-form-item label="调拨类型">{{ labelOf(TRANSFER_TYPE_OPTIONS, form.transferType) }}</el-form-item></el-col>
          <el-col :xl="6" :span="8">
            <el-form-item label="调出仓" prop="fromWarehouseId">
              <WarehouseSelect v-if="!inspection" v-model="form.fromWarehouseId" :types="fromTypes" @select="onFrom" />
              <span v-else>{{ detail?.fromWarehouseName }}</span>
            </el-form-item>
          </el-col>
          <el-col :xl="6" :span="8">
            <el-form-item label="调入仓" prop="toWarehouseId">
              <WarehouseSelect v-if="!inspection" v-model="form.toWarehouseId" :types="toTypes" @select="onTo" />
              <span v-else>{{ detail?.toWarehouseName }}</span>
            </el-form-item>
          </el-col>
          <el-col :xl="6" :span="8"><el-form-item label="单据日期"><el-date-picker v-model="form.docDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" /></el-form-item></el-col>
          <el-col :xl="12" :span="16"><el-form-item label="调拨原因" prop="reason"><el-input v-model="form.reason" maxlength="256" :disabled="inspection" /></el-form-item></el-col>
          <el-col :xl="12" :span="16"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="512" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra><span class="text-muted">调拨统一使用基本单位；同一仓库调拨时调出、调入库位必须不同</span></template>
      <el-table v-if="inspection" :data="form.lines">
        <el-table-column type="index" label="#" width="46" />
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="materialName" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="baseUom" label="单位" width="60" />
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column label="判定" width="80"><template #default="{ row }">{{ labelOf(JUDGE_OPTIONS, row.judgeResult) }}</template></el-table-column>
        <el-table-column label="数量" width="100" align="right"><template #default="{ row }"><span class="num">{{ formatQty(row.qty) }}</span></template></el-table-column>
        <el-table-column v-if="fromLoc" label="调出库位" width="150">
          <template #default="{ row }"><LocationSelect v-model="asLine(row).fromLocationId" :warehouse-id="form.fromWarehouseId" /></template>
        </el-table-column>
        <el-table-column v-if="toLoc" label="调入库位" width="150">
          <template #default="{ row }"><LocationSelect v-model="asLine(row).toLocationId" :warehouse-id="form.toWarehouseId" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="asLine(row).remark" maxlength="256" /></template></el-table-column>
      </el-table>
      <LinesEditor v-else ref="linesRef" v-model="form.lines" :columns="columns" :new-row="newLine" :on-material="onMaterial">
        <template #cell-batchNo="{ row, disabled }">
          <BatchSelect v-model="asLine(row).batchNo" :material-id="asLine(row).materialId" :warehouse-id="form.fromWarehouseId" :disabled="disabled" />
        </template>
        <template #cell-fromLocationId="{ row, disabled }">
          <LocationSelect v-model="asLine(row).fromLocationId" :warehouse-id="form.fromWarehouseId" :disabled="disabled" />
        </template>
        <template #cell-toLocationId="{ row, disabled }">
          <LocationSelect v-model="asLine(row).toLocationId" :warehouse-id="form.toWarehouseId" :disabled="disabled" />
        </template>
      </LinesEditor>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.tip { margin-bottom: var(--erp-space-4); }
</style>
