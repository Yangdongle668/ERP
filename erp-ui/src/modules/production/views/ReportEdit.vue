<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { formatQty, today } from '@/utils/format'
import OrderSelect from '../components/OrderSelect.vue'
import { num, PROD_STATUS, REPORT_STATUS, reportApi, type DefectSave, type OperatorSave, type ReportContext, type ReportSave, type ReportDetail } from '../api/production'

defineOptions({ name: 'MfgReportEdit' })

/**
 * 快速报工（需求 09-04 3.2，T4）：扫描流程卡 / 工单条码或选择生产订单 + 工序，带出可报数量；
 * 录入合格、不良（按不良代码明细）、报废、工时与人员。参数“报工自动审核”开启时保存即审核。
 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const saving = ref(false)
const detail = ref<ReportDetail>()
const ctx = ref<ReportContext>()
const barcode = ref('')
const barcodeRef = ref<{ focus: () => void }>()

interface Form {
  prodOrderId?: string; operationSeq?: number; workOrderId?: string; reportDate: string; shift?: string; goodQty?: string; defectQty?: string; scrapQty?: string
  scrapReason?: string; workHours?: string; machineHours?: string; toolingId?: string; remark?: string; operators: OperatorSave[]; defects: DefectSave[]
}
const empty = (): Form => ({ reportDate: today(), shift: 'DAY', operators: [], defects: [] })
const form = ref<Form>(empty())
const guard = useLeaveGuard(() => form.value)

function applyContext(c: ReportContext) {
  ctx.value = c
  form.value.prodOrderId = c.prodOrderId
  form.value.operationSeq = c.operationSeq
  form.value.workOrderId = c.workOrderId
}
async function scan() {
  const code = barcode.value.trim()
  if (!code) return
  applyContext(await reportApi.context({ barcode: code }))
  barcode.value = ''
}
async function loadContext() {
  const f = form.value
  if (!f.prodOrderId) {
    ctx.value = undefined
    return
  }
  applyContext(await reportApi.context({ prodOrderId: f.prodOrderId, seq: f.operationSeq, workOrderId: f.workOrderId }))
}
function onOrder() {
  form.value.operationSeq = undefined
  form.value.workOrderId = undefined
  loadContext()
}

const defectSum = computed(() => form.value.defects.reduce((s, x) => s + num(x.qty), 0))
const operatorHours = computed(() => form.value.operators.reduce((s, x) => s + num(x.hours), 0))
const total = computed(() => num(form.value.goodQty) + num(form.value.defectQty) + num(form.value.scrapQty))

onMounted(async () => {
  if (id.value) {
    const d = await reportApi.get(id.value)
    if (d.status !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的报工单可以修改')
      router.replace(`/production/report/${id.value}`)
      return
    }
    detail.value = d
    form.value = {
      prodOrderId: d.prodOrderId, operationSeq: d.operationSeq, workOrderId: d.workOrderId, reportDate: d.reportDate, shift: d.shift, goodQty: d.goodQty,
      defectQty: d.defectQty, scrapQty: d.scrapQty, scrapReason: d.scrapReason, workHours: d.workHours, machineHours: d.machineHours, toolingId: d.toolingId,
      remark: d.remark, operators: d.operators.map((o) => ({ userId: o.userId, operatorName: o.operatorName, hours: o.hours })),
      defects: d.defects.map((x) => ({ defectCode: x.defectCode, qty: x.qty, position: x.position, description: x.description }))
    }
    tabs.setTitle(tabKeyOf(route), `编辑报工单 ${d.docNo}`)
    await loadContext()
  } else {
    const q = route.query
    if (typeof q.prodOrderId === 'string') form.value.prodOrderId = q.prodOrderId
    if (typeof q.seq === 'string') form.value.operationSeq = Number(q.seq)
    if (typeof q.workOrderId === 'string') form.value.workOrderId = q.workOrderId
    if (form.value.prodOrderId) await loadContext()
    else barcodeRef.value?.focus()
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/production/report/${id.value}` : '/production/report')
}

async function save(again: boolean) {
  if (saving.value) return
  const f = form.value
  const c = ctx.value
  if (!f.prodOrderId || f.operationSeq === undefined) return ElMessage.warning('请扫描条码或选择生产订单和工序')
  if (c?.requireWorkOrder && !f.workOrderId) return ElMessage.warning('报工必须选择工单')
  if (total.value <= 0) return ElMessage.warning('合格、不良、报废数量不能都为 0')
  if (c?.reportableQty !== undefined && total.value > num(c.reportableQty) + (detail.value ? num(detail.value.goodQty) + num(detail.value.defectQty) + num(detail.value.scrapQty) : 0)) {
    return ElMessage.warning(`本工序可报数量为 ${formatQty(c.reportableQty)}`)
  }
  if (num(f.scrapQty) > 0 && !f.scrapReason) return ElMessage.warning('报废数量大于 0 时必须选择报废原因')
  if (!(num(f.workHours) > 0)) return ElMessage.warning('工时必须大于 0')
  if (f.defects.length && defectSum.value !== num(f.defectQty)) return ElMessage.warning(`不良明细合计 ${defectSum.value} 与不良数 ${num(f.defectQty)} 不一致`)
  if (f.defects.some((x) => !x.defectCode || !(num(x.qty) > 0))) return ElMessage.warning('请完整填写不良代码和数量')
  const data: ReportSave = {
    ...f, prodOrderId: f.prodOrderId, operationSeq: f.operationSeq, workCenterId: c?.workCenterId, remark: f.remark?.trim() || undefined, version: detail.value?.version,
    operators: f.operators.filter((o) => o.userId || o.operatorName?.trim()), defects: f.defects
  }
  saving.value = true
  try {
    const r = id.value ? await reportApi.update(id.value, data) : await reportApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    ElMessage.success(r.status === 'APPROVED' ? `报工单 ${r.docNo} 已审核` : `报工单 ${r.docNo} 已保存，待审核`)
    guard.markClean()
    if (again && !id.value) {
      const keep = { prodOrderId: f.prodOrderId, operationSeq: f.operationSeq, workOrderId: f.workOrderId, shift: f.shift, reportDate: f.reportDate }
      form.value = { ...empty(), ...keep }
      await loadContext()
      guard.markClean()
      return
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/production/report/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑报工单 ${detail.value.docNo}` : '快速报工'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.status" :map="REPORT_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button v-if="!id" :loading="saving" @click="save(true)">保存并继续</el-button>
      <el-button type="primary" :loading="saving" @click="save(false)">{{ ctx?.autoApprove ? '保存并审核' : '保存' }}</el-button>
    </template>

    <ErpPanel title="生产订单与工序">
      <el-form label-width="100px">
        <el-row :gutter="24">
          <el-col v-if="!id" :xl="8" :span="12">
            <el-form-item label="扫码">
              <el-input ref="barcodeRef" v-model="barcode" placeholder="扫描流程卡 / 工单条码后回车" clearable @keyup.enter="scan" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="生产订单" required><OrderSelect v-model="form.prodOrderId" :disabled="!!id" @update:model-value="onOrder" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="工序" required>
              <el-select v-model="form.operationSeq" class="w-full" :disabled="!ctx" @change="form.workOrderId = undefined; loadContext()">
                <el-option v-for="o in ctx?.operations ?? []" :key="o.seq" :value="o.seq" :disabled="!o.reportPoint"
                           :label="`${o.seq} ${o.operation}${o.reportPoint ? `（可报 ${formatQty(o.reportableQty)}）` : '（非报工点）'}`" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-descriptions v-if="ctx" :column="4" class="ctx">
        <el-descriptions-item label="产品">{{ ctx.materialCode }} {{ ctx.materialName }}</el-descriptions-item>
        <el-descriptions-item label="订单数量">{{ formatQty(ctx.orderQty) }} {{ ctx.baseUom }}</el-descriptions-item>
        <el-descriptions-item label="订单状态"><StatusTag :value="ctx.prodStatus" :map="PROD_STATUS" /></el-descriptions-item>
        <el-descriptions-item label="批号">{{ ctx.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工作中心">{{ ctx.workCenterName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="投入上限">{{ formatQty(ctx.inputLimit) }}</el-descriptions-item>
        <el-descriptions-item label="已报">{{ formatQty(ctx.reportedQty) }}</el-descriptions-item>
        <el-descriptions-item label="可报"><strong class="num">{{ formatQty(ctx.reportableQty) }}</strong></el-descriptions-item>
        <el-descriptions-item v-if="ctx.workOrderNo" label="工单">{{ ctx.workOrderNo }}（派工 {{ formatQty(ctx.woPlanQty) }}，已报 {{ formatQty(ctx.woReportedQty) }}）</el-descriptions-item>
        <el-descriptions-item v-if="ctx.inspectionPoint" label="检验点">审核后通知品质 IPQC</el-descriptions-item>
      </el-descriptions>
    </ErpPanel>

    <ErpPanel title="报工数据">
      <el-form label-width="100px">
        <el-row :gutter="24">
          <el-col :xl="6" :span="8"><el-form-item label="报工日期" required><el-date-picker v-model="form.reportDate" value-format="YYYY-MM-DD" :clearable="false" class="w-full" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="班次"><DictSelect v-model="form.shift" type="mfg_shift" /></el-form-item></el-col>
          <el-col :xl="6" :span="8">
            <el-form-item label="模具">
              <el-select v-model="form.toolingId" clearable class="w-full" :disabled="!ctx?.toolings.length">
                <el-option v-for="t in ctx?.toolings ?? []" :key="t.id" :value="t.id" :label="`${t.code} ${t.name}`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="6" :span="8"><el-form-item label="合格数量"><QtyInput v-model="form.goodQty" :uom="ctx?.baseUom" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="不良数量"><QtyInput v-model="form.defectQty" :uom="ctx?.baseUom" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="报废数量"><QtyInput v-model="form.scrapQty" :uom="ctx?.baseUom" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="报废原因"><DictSelect v-model="form.scrapReason" type="mfg_scrap_reason" :disabled="!(num(form.scrapQty) > 0)" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="工时(h)" required><NumberInput v-model="form.workHours" :precision="2" /></el-form-item></el-col>
          <el-col :xl="6" :span="8"><el-form-item label="机器工时(h)"><NumberInput v-model="form.machineHours" :precision="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <p class="hint">本次投入 <strong class="num">{{ formatQty(total) }}</strong> = 合格 + 不良 + 报废；不良进入待处理，可在“不良记录”中返修或报废。</p>
    </ErpPanel>

    <ErpPanel title="不良明细" :description="`合计 ${formatQty(defectSum)}，需与不良数量一致`">
      <template #extra><el-button icon="Plus" @click="form.defects.push({ qty: undefined })">添加不良</el-button></template>
      <el-table :data="form.defects">
        <el-table-column label="不良代码" width="180"><template #default="{ row }"><DictSelect v-model="row.defectCode" type="mfg_defect_code" /></template></el-table-column>
        <el-table-column label="数量" width="130"><template #default="{ row }"><QtyInput v-model="row.qty" :uom="ctx?.baseUom" /></template></el-table-column>
        <el-table-column label="位置" width="160"><template #default="{ row }"><el-input v-model="row.position" maxlength="128" placeholder="如 U3、R12" /></template></el-table-column>
        <el-table-column label="描述" min-width="200"><template #default="{ row }"><el-input v-model="row.description" maxlength="512" /></template></el-table-column>
        <el-table-column label="" width="60"><template #default="{ $index }"><el-button link type="danger" @click="form.defects.splice($index, 1)">删除</el-button></template></el-table-column>
        <template #empty><ErpEmpty compact description="有不良时按不良代码录入明细，用于良率柏拉图" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="作业人员" :description="form.operators.length ? `人员工时合计 ${operatorHours.toFixed(2)} h，需与报工工时一致` : '不录入时按当前用户'">
      <template #extra><el-button icon="Plus" @click="form.operators.push({})">添加人员</el-button></template>
      <el-table :data="form.operators">
        <el-table-column label="人员" width="220"><template #default="{ row }"><UserSelect v-model="row.userId" /></template></el-table-column>
        <el-table-column label="临时工姓名" width="180"><template #default="{ row }"><el-input v-model="row.operatorName" maxlength="64" placeholder="非系统用户时填写" /></template></el-table-column>
        <el-table-column label="工时(h)" width="140"><template #default="{ row }"><NumberInput v-model="row.hours" :precision="2" /></template></el-table-column>
        <el-table-column label="" width="60"><template #default="{ $index }"><el-button link type="danger" @click="form.operators.splice($index, 1)">删除</el-button></template></el-table-column>
        <template #empty><ErpEmpty compact description="多人作业时按人员分摊工时" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.ctx { margin-top: var(--erp-space-2); }
.hint { color: var(--erp-color-text-secondary); }
</style>
