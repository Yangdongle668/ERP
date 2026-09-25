<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty, today } from '@/utils/format'
import { num } from '../api/common'
import { orderApi, type OpenLine } from '../api/order'
import { outsourcingApi, type OsRow } from '../api/outsourcing'
import { RECEIPT_STATUS, RECEIPT_TYPE_OPTIONS, receiptApi, type ReceiptDetail, type ReceiptLine, type ReceiptSave } from '../api/receipt'

defineOptions({ name: 'PurReceiptEdit' })

/** 到货单编辑（需求 07-06 3.2，T4）：从采购订单行（或委外单）选单带入 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const orderPicker = ref<{ open: (q?: Record<string, unknown>) => Promise<OpenLine[]> }>()
const osPicker = ref<{ open: (q?: Record<string, unknown>) => Promise<OsRow[]> }>()
const saving = ref(false)
const detail = ref<ReceiptDetail>()

interface Form {
  receiptType: string
  supplierId?: string
  deliveryNoteNo?: string
  arrivalAt?: string
  receiverId?: string
  remark?: string
  fileIds: string[]
  lines: ReceiptLine[]
}
const nowText = () => `${today()} ${new Date().toTimeString().slice(0, 8)}`
const form = ref<Form>({ receiptType: 'PURCHASE', fileIds: [], lines: [] })
const guard = useLeaveGuard(() => form.value)
const outsource = computed(() => form.value.receiptType === 'OUTSOURCE')

const rules: FormRules = {
  receiptType: [{ required: true, message: '请选择到货类型', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  arrivalAt: [{ required: true, message: '请选择到货时间', trigger: 'change' }],
  receiverId: [{ required: true, message: '请选择收货人', trigger: 'change' }]
}

// ---------- 选单 ----------
const openColumns: TableColumn<OpenLine>[] = [
  { prop: 'orderNo', label: '订单号', width: 150 },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'uom', label: '单位', width: 60 },
  { prop: 'qty', label: '订购', width: 100, type: 'qty' },
  { prop: 'receivedQty', label: '已到货', width: 100, type: 'qty' },
  { prop: 'openQty', label: '未到货', width: 100, type: 'qty' },
  { prop: 'requiredDate', label: '要求日期', width: 110, type: 'date' },
  { prop: 'confirmedDate', label: '确认交期', width: 110, type: 'date' }
]
const openFields: SearchField[] = [{ prop: 'orderNo', label: '订单号', upper: true }]
const osColumns: TableColumn<OsRow>[] = [
  { prop: 'docNo', label: '委外单号', width: 160 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'qty', label: '委外数量', width: 100, type: 'qty' },
  { prop: 'receivedQty', label: '已收货', width: 100, type: 'qty' },
  { prop: 'requiredDate', label: '要求日期', width: 110, type: 'date' }
]
const openApi = (q: Record<string, any>) => orderApi.openLines(q as Parameters<typeof orderApi.openLines>[0])
const osApi = (q: Record<string, any>) => outsourcingApi.page({ ...q, statuses: 'APPROVED,IN_PROGRESS' } as never)

function fromOpen(l: OpenLine): ReceiptLine {
  return { orderId: l.orderId, orderNo: l.orderNo, orderLineId: l.id, orderLineNo: l.lineNo, materialId: l.materialId, materialCode: l.materialCode,
    materialName: l.materialName, materialSpec: l.materialSpec, uom: l.uom, openQty: l.openQty, qty: l.openQty, inspectRequired: l.inspectRequired,
    shelfLifeDays: l.shelfLifeDays }
}

async function pick() {
  if (!form.value.supplierId) return ElMessage.warning('请先选择供应商')
  if (outsource.value) {
    const rows = (await osPicker.value?.open({ supplierId: form.value.supplierId })) ?? []
    const exist = new Set(form.value.lines.map((l) => l.orderId))
    form.value.lines.push(...rows.filter((r) => !exist.has(r.id)).map((r) => {
      const open = String(Math.max(0, num(r.qty) - num(r.receivedQty)))
      return { orderId: r.id, orderNo: r.docNo, materialId: r.materialId, materialCode: r.materialCode, materialName: r.materialName, uom: r.uom,
        openQty: open, qty: open } as ReceiptLine
    }))
    return
  }
  const rows = (await orderPicker.value?.open({ supplierId: form.value.supplierId, orderType: form.value.receiptType === 'SAMPLE' ? 'SAMPLE' : 'STANDARD' })) ?? []
  const exist = new Set(form.value.lines.map((l) => l.orderLineId))
  form.value.lines.push(...rows.filter((r) => !exist.has(r.id)).map(fromOpen))
}

/** 扫描/输入“采购订单号-行号”快速添加 */
const quick = ref('')
async function quickAdd() {
  const text = quick.value.trim().toUpperCase()
  if (!text || !form.value.supplierId) return
  const i = text.lastIndexOf('-')
  const orderNo = i > 0 ? text.slice(0, i) : text
  const lineNo = i > 0 ? Number(text.slice(i + 1)) : undefined
  const page = await orderApi.openLines({ supplierId: form.value.supplierId, orderNo, pageNo: 1, pageSize: 200 })
  const hits = page.list.filter((l) => l.orderNo === orderNo && (lineNo === undefined || Number.isNaN(lineNo) || l.lineNo === lineNo))
  const hit = hits.find((l) => l.lineNo === lineNo) ?? (hits.length === 1 ? hits[0] : undefined)
  if (!hit) {
    ElMessage.warning(`没有找到未到货的订单行「${text}」`)
    return
  }
  if (!form.value.lines.some((l) => l.orderLineId === hit.id)) form.value.lines.push(fromOpen(hit))
  quick.value = ''
}

// ---------- 加载 ----------
onMounted(async () => {
  if (id.value) {
    const d = await receiptApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的到货单可以修改')
      router.replace(`/purchase/receipt/${id.value}`)
      return
    }
    detail.value = d
    form.value = { receiptType: d.receiptType, supplierId: d.supplierId, deliveryNoteNo: d.deliveryNoteNo, arrivalAt: d.arrivalAt, receiverId: d.receiverId,
      remark: d.remark, fileIds: [], lines: d.lines.map((l) => ({ ...l })) }
    tabs.setTitle(tabKeyOf(route), `编辑到货单 ${d.docNo}`)
  } else {
    form.value.arrivalAt = nowText()
    form.value.receiverId = me.user?.id
    const q = route.query
    if (typeof q.supplierId === 'string') form.value.supplierId = q.supplierId
    if (typeof q.orderNo === 'string' && form.value.supplierId) {
      const page = await orderApi.openLines({ supplierId: form.value.supplierId, orderNo: q.orderNo, pageNo: 1, pageSize: 200 })
      form.value.lines = page.list.filter((l) => l.orderId === q.orderId || l.orderNo === q.orderNo).map(fromOpen)
      if (form.value.lines.some((l) => l.orderNo && page.list.find((x) => x.id === l.orderLineId)?.orderType === 'SAMPLE')) form.value.receiptType = 'SAMPLE'
    }
    if (typeof q.outsourcingId === 'string') {
      const o = await outsourcingApi.get(q.outsourcingId)
      form.value.receiptType = 'OUTSOURCE'
      form.value.supplierId = o.supplierId
      const open = String(Math.max(0, num(o.qty) - num(o.receivedQty)))
      form.value.lines = [{ orderId: o.id, orderNo: o.docNo, materialId: o.materialId, materialCode: o.materialCode, materialName: o.materialName, uom: o.uom,
        openQty: open, qty: open }]
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

function onTypeOrSupplierChange() {
  if (form.value.lines.length) {
    form.value.lines = []
    ElMessage.info('已清空明细，请重新选单')
  }
}

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/purchase/receipt/${id.value}` : '/purchase/receipt')
}

function payload(): ReceiptSave {
  const f = form.value
  return {
    receiptType: f.receiptType, supplierId: f.supplierId!, deliveryNoteNo: f.deliveryNoteNo?.trim() || undefined, arrivalAt: f.arrivalAt,
    receiverId: f.receiverId, remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    lines: f.lines.map((l) => ({ orderLineId: l.orderLineId, orderId: l.orderId, qty: l.qty!, supplierBatchNo: l.supplierBatchNo?.trim() || undefined,
      productionDate: l.productionDate, remark: l.remark?.trim() || undefined }))
  }
}

/** approve = true：保存并审核 */
async function save(approve: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!form.value.lines.length) return ElMessage.warning('请至少添加一行明细')
  const bad = form.value.lines.findIndex((l) => !(num(l.qty) > 0))
  if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 行：到货数量必须大于 0`)
  const noDate = form.value.lines.findIndex((l) => (l.shelfLifeDays ?? 0) > 0 && !l.productionDate)
  if (noDate >= 0) return ElMessage.warning(`第 ${noDate + 1} 行：请填写生产日期`)
  saving.value = true
  try {
    const data = payload()
    const r = id.value ? await receiptApi.update(id.value, data) : await receiptApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (approve) {
      try {
        await receiptApi.approve(r.id)
        ElMessage.success('审核成功，已生成入库单')
      } catch {
        if (!id.value) router.replace(`/purchase/receipt/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/purchase/receipt/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑到货单 ${detail.value.docNo}` : '新建到货'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="RECEIPT_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存</el-button>
      <el-button v-if="me.hasPermission('pur:receipt:approve')" type="primary" :loading="saving" @click="save(true)">审核</el-button>
    </template>

    <ErpPanel title="基本信息">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="到货类型" prop="receiptType">
              <el-radio-group v-model="form.receiptType" :disabled="!!id" @change="onTypeOrSupplierChange">
                <el-radio v-for="o in RECEIPT_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <SupplierSelect v-model="form.supplierId" :statuses="['QUALIFIED', 'SUSPENDED', 'POTENTIAL']" :disabled="!!id" @update:model-value="onTypeOrSupplierChange" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="送货单号"><el-input v-model="form.deliveryNoteNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="到货时间" prop="arrivalAt">
              <el-date-picker v-model="form.arrivalAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="收货人" prop="receiverId"><UserSelect v-model="form.receiverId" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="备注"><el-input v-model="form.remark" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </ErpPanel>

    <ErpPanel title="明细">
      <template #extra>
        <div class="toolbar">
          <el-input v-if="!outsource" v-model="quick" placeholder="扫描/输入 订单号-行号" class="w240" @keyup.enter="quickAdd" />
          <el-button type="primary" icon="Link" @click="pick">{{ outsource ? '选择委外单' : '选择采购订单行' }}</el-button>
        </div>
      </template>
      <el-table :data="form.lines" border max-height="520">
        <el-table-column type="index" label="行" width="50" />
        <el-table-column label="订单号-行号" width="170"><template #default="{ row }">{{ row.orderNo }}{{ row.orderLineNo ? `-${row.orderLineNo}` : '' }}</template></el-table-column>
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column label="未到货" width="100" align="right"><template #default="{ row }">{{ formatQty(row.openQty) }}</template></el-table-column>
        <el-table-column label="到货数量" width="130"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="row.uom" /></template></el-table-column>
        <el-table-column label="供应商批号" width="140"><template #default="{ row }"><el-input v-model="row.supplierBatchNo" maxlength="64" /></template></el-table-column>
        <el-table-column label="生产日期" width="160">
          <template #default="{ row }">
            <el-date-picker v-model="row.productionDate" value-format="YYYY-MM-DD" :placeholder="row.shelfLifeDays ? '必填' : ''" class="w-full" />
          </template>
        </el-table-column>
        <el-table-column label="需检" width="60"><template #default="{ row }">{{ row.inspectRequired === undefined ? '-' : row.inspectRequired ? '是' : '否' }}</template></el-table-column>
        <el-table-column label="入库仓" width="110">
          <template #default="{ row }">{{ row.targetWarehouseName ?? (row.inspectRequired === undefined ? '-' : row.inspectRequired ? '待检仓' : '物料默认仓') }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="120"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column width="56" align="center"><template #default="{ $index }"><el-button link type="danger" icon="Delete" @click="form.lines.splice($index, 1)" /></template></el-table-column>
        <template #empty><ErpEmpty compact :description="outsource ? '点击“选择委外单”带入' : '点击“选择采购订单行”带入，或扫描订单号-行号'" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件（送货单照片）">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="PUR_RECEIPT" multiple />
      <AttachmentPanel v-else biz-type="PUR_RECEIPT" :biz-id="id" editable />
    </ErpPanel>

    <SourceDocPicker ref="orderPicker" title="选择采购订单行" :api="openApi" :columns="openColumns" :search-fields="openFields"
                     :exclude-keys="form.lines.map((l) => l.orderLineId).filter((x): x is string => !!x)" />
    <SourceDocPicker ref="osPicker" title="选择委外单" :api="osApi" :columns="osColumns"
                     :exclude-keys="form.lines.map((l) => l.orderId).filter((x): x is string => !!x)" />
  </ErpPage>
</template>

<style scoped>
.toolbar { display: flex; gap: var(--erp-space-2); }
</style>
