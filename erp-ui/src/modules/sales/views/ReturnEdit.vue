<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import type { TableColumn } from '@/components'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatQty } from '@/utils/format'
import { DOC_STATUS, HANDLING_OPTIONS, num, returnApi, submitText, type ReturnDetail, type ReturnLine, type ShippedLine } from '../api/sales'

defineOptions({ name: 'SalReturnEdit' })

/** 销售退货编辑（需求 04-06 3.2，T4）：从客户已出货的订单行选择，退货数量不超过可退数量 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const pickerRef = ref<{ open: (q?: Record<string, unknown>) => Promise<ShippedLine[]> }>()
const saving = ref(false)
const detail = ref<ReturnDetail>()

interface Form {
  customerId?: string; rmaNo?: string; returnReason?: string; handling: string; complaintNo?: string; expectedArrivalDate?: string; remark?: string
  fileIds: string[]; lines: ReturnLine[]
}
const form = ref<Form>({ handling: 'REFUND', fileIds: [], lines: [] })
const guard = useLeaveGuard(() => form.value)

const rules: FormRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  returnReason: [{ required: true, message: '请选择退货原因', trigger: 'change' }]
}

const shippedColumns: TableColumn<ShippedLine>[] = [
  { prop: 'orderNo', label: '订单号', width: 160 },
  { prop: 'lineNo', label: '行', width: 50 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 150 },
  { prop: 'shippedQty', label: '已出货', width: 100, type: 'qty' },
  { prop: 'returnedQty', label: '已退', width: 90, type: 'qty' },
  { prop: 'returnableQty', label: '可退', width: 90, type: 'qty' },
  { prop: 'baseUom', label: '单位', width: 60 },
  { prop: 'priceInclTax', label: '含税单价', width: 100, type: 'price' },
  { prop: 'lastShipDate', label: '最近出货', width: 110, type: 'date' }
]
async function shippedApi(q: Record<string, any>) {
  const rows = await returnApi.shippedLines(q.customerId)
  const from = (q.pageNo - 1) * q.pageSize
  return { list: rows.slice(from, from + q.pageSize), total: rows.length }
}
async function pickLines() {
  if (!form.value.customerId) return ElMessage.warning('请先选择客户')
  const rows = await pickerRef.value?.open({ customerId: form.value.customerId })
  if (!rows?.length) return
  const exist = new Set(form.value.lines.map((l) => l.orderLineId))
  form.value.lines.push(...rows.filter((r) => !exist.has(r.orderLineId)).map((r) => ({
    orderId: r.orderId, orderNo: r.orderNo, orderLineId: r.orderLineId, orderLineNo: r.lineNo, materialId: r.materialId, materialCode: r.materialCode,
    materialName: r.materialName, materialSpec: r.materialSpec, baseUom: r.baseUom, returnableQty: r.returnableQty, priceInclTax: r.priceInclTax, qty: r.returnableQty
  })))
}
/** 上一次确认的客户：更换客户时如有明细需确认，取消则恢复 */
let lastCustomer: string | undefined
async function onCustomer() {
  if (form.value.customerId === lastCustomer) return
  if (form.value.lines.length) {
    const ok = await ElMessageBox.confirm('更换客户将清空退货明细，确定吗？', '提示', { type: 'warning' }).then(() => true).catch(() => false)
    if (!ok) {
      form.value.customerId = lastCustomer
      return
    }
    form.value.lines = []
  }
  lastCustomer = form.value.customerId
}
const lineAmount = (l: ReturnLine) => num(l.qty) * num(l.priceInclTax)
const sumAmount = computed(() => form.value.lines.reduce((s, l) => s + lineAmount(l), 0))

onMounted(async () => {
  if (id.value) {
    const d = await returnApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的退货单可以修改')
      router.replace(`/sales/return/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      customerId: d.customerId, rmaNo: d.rmaNo, returnReason: d.returnReason, handling: d.handling, complaintNo: d.complaintNo,
      expectedArrivalDate: d.expectedArrivalDate, remark: d.remark, fileIds: [], lines: d.lines.map((l) => ({ ...l }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑退货单 ${d.docNo}`)
  } else if (typeof route.query.customerId === 'string') {
    form.value.customerId = route.query.customerId
  }
  lastCustomer = form.value.customerId
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/sales/return/${id.value}` : '/sales/return')
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (!form.value.lines.length) return ElMessage.warning('请选择退货的订单行')
  for (const l of form.value.lines) {
    if (!(num(l.qty) > 0)) return ElMessage.warning(`${l.materialCode}：退货数量必须大于 0`)
    if (l.returnableQty !== undefined && num(l.qty) > num(l.returnableQty)) return ElMessage.warning(`${l.materialCode}：退货数量超过可退数量 ${formatQty(l.returnableQty)}`)
  }
  const f = form.value
  const data = {
    customerId: f.customerId, rmaNo: f.rmaNo?.trim() || undefined, returnReason: f.returnReason, handling: f.handling, complaintNo: f.complaintNo?.trim() || undefined,
    expectedArrivalDate: f.expectedArrivalDate || undefined, remark: f.remark?.trim() || undefined, fileIds: f.fileIds, version: detail.value?.version,
    lines: f.lines.map((l) => ({ orderLineId: l.orderLineId, batchNo: l.batchNo?.trim() || undefined, serialNos: l.serialNos?.trim() || undefined, qty: l.qty,
      remark: l.remark?.trim() || undefined }))
  }
  saving.value = true
  try {
    const r = id.value ? await returnApi.update(id.value, data) : await returnApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      try {
        ElMessage.success(submitText((await returnApi.submit(r.id)).status))
      } catch {
        if (!id.value) router.replace(`/sales/return/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/sales/return/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑退货单 ${detail.value.docNo}` : '新建退货'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="DOC_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('sales:return:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12"><el-form-item label="客户" prop="customerId"><CustomerSelect v-model="form.customerId" @select="onCustomer" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="退货原因" prop="returnReason"><DictSelect v-model="form.returnReason" type="sal_return_reason" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="处理方式">
              <el-radio-group v-model="form.handling"><el-radio v-for="o in HANDLING_OPTIONS" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio></el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="RMA 号"><el-input v-model="form.rmaNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12"><el-form-item label="客诉单号"><el-input v-model="form.complaintNo" maxlength="64" /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="预计到货"><el-date-picker v-model="form.expectedArrivalDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel title="退货明细" description="数量为基本单位；换货时审核后自动在原订单上补回待出货数量">
      <template #extra>
        <span class="total">退货金额 <strong class="num">{{ formatAmount(sumAmount) }}</strong></span>
        <el-button type="primary" icon="Plus" @click="pickLines">选择出货订单行</el-button>
      </template>
      <el-table :data="form.lines">
        <el-table-column label="订单" width="180"><template #default="{ row }">{{ row.orderNo }} 行 {{ row.orderLineNo }}</template></el-table-column>
        <el-table-column label="物料" min-width="200"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column label="可退" width="100" align="right"><template #default="{ row }">{{ formatQty(row.returnableQty) }}</template></el-table-column>
        <el-table-column label="退货数量" width="140"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="row.baseUom" /></template></el-table-column>
        <el-table-column prop="baseUom" label="单位" width="60" />
        <el-table-column label="含税单价" width="100" align="right"><template #default="{ row }">{{ row.priceInclTax }}</template></el-table-column>
        <el-table-column label="批次" width="130"><template #default="{ row }"><el-input v-model="row.batchNo" maxlength="64" /></template></el-table-column>
        <el-table-column label="序列号" width="160"><template #default="{ row }"><el-input v-model="row.serialNos" placeholder="多个用逗号分隔" /></template></el-table-column>
        <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1)">删除</el-button></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="选择客户后，从已出货的订单行中选择" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="附件">
      <AttachmentUpload v-if="!id" v-model="form.fileIds" biz-type="SAL_RETURN" multiple />
      <AttachmentPanel v-else biz-type="SAL_RETURN" :biz-id="id" editable />
    </ErpPanel>

    <SourceDocPicker ref="pickerRef" title="选择已出货的订单行" :api="shippedApi" :columns="shippedColumns" row-key="orderLineId"
                     :exclude-keys="form.lines.map((l) => l.orderLineId)" />
  </ErpPage>
</template>

<style scoped>
.total { margin-right: var(--erp-space-3); color: var(--erp-color-text-secondary); }
</style>
