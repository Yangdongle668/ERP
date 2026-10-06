<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { useLeaveGuard } from '@/composables/useLeaveGuard'
import { tabKeyOf, useTabsStore } from '@/stores/tabs'
import { useUserStore } from '@/stores/user'
import { formatQty, today, toDateString } from '@/utils/format'
import {
  ISSUE_METHOD_OPTIONS, labelOf, ORDER_TYPE_OPTIONS, PROD_STATUS, prodOrderApi,
  type MaterialSave, type Preview, type ProdOrderDetail, type ProdOrderSave
} from '../api/production'

defineOptions({ name: 'MfgProdOrderEdit' })

/** 生产订单编辑（需求 09-01 3.2，T4）：选择产品后带出 BOM / 工艺路线版本、用料与工序预览；返工订单手工录入投入物料 */
const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()
const me = useUserStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : undefined))
const formRef = ref<FormInstance>()
const saving = ref(false)
const detail = ref<ProdOrderDetail>()
const preview = ref<Preview>()
const previewing = ref(false)

interface Form {
  orderType: string; materialId?: string; qty?: string; bomId?: string; routingId?: string; planStart?: string; planEnd?: string; deptId?: string
  priority: number; batchNo?: string; salesOrderLineId?: string; remark?: string; materials: (MaterialSave & { key: number })[]
}
const form = ref<Form>({ orderType: 'NORMAL', priority: 5, planStart: today(), materials: [] })
const guard = useLeaveGuard(() => form.value)
const baseUom = ref<string>()
let seq = 0

const rules: FormRules = {
  materialId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  qty: [{ required: true, message: '请填写计划数量', trigger: 'blur' }],
  planStart: [{ required: true, message: '请选择计划开工日期', trigger: 'change' }],
  planEnd: [{ required: true, message: '请选择计划完工日期', trigger: 'change' }]
}
const isRework = computed(() => form.value.orderType === 'REWORK')
const isDisassembly = computed(() => form.value.orderType === 'DISASSEMBLY')

async function loadPreview(resetVersions = false) {
  const f = form.value
  if (!f.materialId) {
    preview.value = undefined
    return
  }
  previewing.value = true
  try {
    const p = await prodOrderApi.preview({
      materialId: f.materialId, bomId: resetVersions ? undefined : f.bomId, routingId: resetVersions ? undefined : f.routingId, qty: f.qty, orderType: f.orderType
    })
    preview.value = p
    f.bomId = p.bomId
    f.routingId = p.routingId
    if (resetVersions && p.defaultDeptId && !f.deptId) f.deptId = p.defaultDeptId
    if (f.planStart && !f.planEnd && p.leadTimeDays > 0) {
      const d = new Date(f.planStart)
      d.setDate(d.getDate() + p.leadTimeDays)
      f.planEnd = toDateString(d)
    }
  } finally {
    previewing.value = false
  }
}
function onProduct(m: unknown) {
  baseUom.value = (m as { baseUom?: string } | undefined)?.baseUom
  form.value.bomId = undefined
  form.value.routingId = undefined
  loadPreview(true)
}
function addMaterial() {
  form.value.materials.push({ key: ++seq, componentId: '', qtyPer: '1', issueMethod: 'PICK' })
}

onMounted(async () => {
  if (id.value) {
    const d = await prodOrderApi.get(id.value)
    if (d.prodStatus !== 'DRAFT') {
      ElMessage.warning('只有草稿状态的生产订单可以修改')
      router.replace(`/production/prod-order/${id.value}`)
      return
    }
    detail.value = d
    baseUom.value = d.baseUom
    form.value = {
      orderType: d.orderType, materialId: d.materialId, qty: d.qty, bomId: d.bomId, routingId: d.routingId, planStart: d.planStart, planEnd: d.planEnd,
      deptId: d.deptId, priority: d.priority, batchNo: d.batchNo === d.docNo ? undefined : d.batchNo, salesOrderLineId: d.salesOrderLineId, remark: d.remark,
      materials: d.orderType === 'REWORK' ? d.materials.map((m) => ({ key: ++seq, componentId: m.componentId, qtyPer: m.qtyPer, scrapRate: m.scrapRate,
        issueMethod: m.issueMethod, operationSeq: m.operationSeq, remark: m.remark })) : []
    }
    tabs.setTitle(tabKeyOf(route), `编辑生产订单 ${d.docNo}`)
    await loadPreview()
  } else {
    const q = route.query
    if (typeof q.materialId === 'string') form.value.materialId = q.materialId
    if (typeof q.qty === 'string') form.value.qty = q.qty
    if (typeof q.salesOrderLineId === 'string') form.value.salesOrderLineId = q.salesOrderLineId
    if (typeof q.planEnd === 'string') form.value.planEnd = q.planEnd
    if (form.value.materialId) await loadPreview(true)
  }
  guard.markClean()
})

async function back() {
  if (!(await guard.confirmLeave())) return
  guard.markClean()
  tabs.remove([tabKeyOf(route)])
  router.push(id.value ? `/production/prod-order/${id.value}` : '/production/prod-order')
}

async function save(submit: boolean) {
  if (saving.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  const f = form.value
  if (!(Number(f.qty) > 0)) return ElMessage.warning('计划数量必须大于 0')
  if (f.planEnd && f.planStart && f.planEnd < f.planStart) return ElMessage.warning('计划完工日期不能早于开工日期')
  if (isRework.value) {
    if (!f.materials.length) return ElMessage.warning('返工订单请至少录入一行投入物料')
    if (f.materials.some((m) => !m.componentId || !(Number(m.qtyPer) > 0))) return ElMessage.warning('请完整填写投入物料和单位用量')
  }
  const data: ProdOrderSave = {
    orderType: f.orderType, materialId: f.materialId!, qty: f.qty!, bomId: f.bomId, routingId: f.routingId, planStart: f.planStart!, planEnd: f.planEnd!,
    deptId: f.deptId, priority: f.priority, salesOrderLineId: f.salesOrderLineId, batchNo: f.batchNo?.trim() || undefined, remark: f.remark?.trim() || undefined,
    materials: isRework.value ? f.materials.map(({ key: _k, ...m }) => ({ ...m, remark: m.remark?.trim() || undefined })) : undefined,
    version: detail.value?.version
  }
  saving.value = true
  try {
    const r = id.value ? await prodOrderApi.update(id.value, data) : await prodOrderApi.create(data)
    if (r.warnings.length) ElNotification({ type: 'warning', title: '提示', message: r.warnings.join('；'), duration: 8000 })
    guard.markClean()
    if (submit) {
      try {
        const s = await prodOrderApi.submit(r.id)
        ElMessage.success(s.status === 'PENDING' ? '已提交，等待审批' : '提交成功，已计划')
      } catch {
        if (!id.value) router.replace(`/production/prod-order/${r.id}/edit`)
        return
      }
    } else {
      ElMessage.success('保存成功')
    }
    tabs.remove([tabKeyOf(route)])
    router.push(`/production/prod-order/${r.id}`)
  } finally {
    saving.value = false
  }
}

const title = computed(() => (detail.value ? `编辑生产订单 ${detail.value.docNo}` : '新建生产订单'))
</script>

<template>
  <ErpPage :title="title" back sticky :on-back="back">
    <template #meta><StatusTag v-if="detail" :value="detail.prodStatus" :map="PROD_STATUS" /></template>
    <template #actions>
      <el-button @click="back">取消</el-button>
      <el-button :loading="saving" @click="save(false)">保存草稿</el-button>
      <el-button v-if="me.hasPermission('mfg:prod-order:submit')" type="primary" :loading="saving" @click="save(true)">提交</el-button>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ErpPanel title="基本信息">
        <el-row :gutter="24">
          <el-col :xl="8" :span="12">
            <el-form-item label="订单类型">
              <el-radio-group v-model="form.orderType" @change="loadPreview()">
                <el-radio v-for="o in ORDER_TYPE_OPTIONS.filter((x) => x.value !== 'SAMPLE' || form.orderType === 'SAMPLE')" :key="String(o.value)" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="产品" prop="materialId">
              <MaterialSelect v-model="form.materialId" :types="['FINISHED', 'SEMI_FINISHED']" @select="onProduct" />
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="计划数量" prop="qty"><QtyInput v-model="form.qty" :uom="baseUom" @change="loadPreview()" /></el-form-item>
          </el-col>
          <el-col v-if="!isRework" :xl="8" :span="12">
            <el-form-item label="BOM 版本">
              <el-select v-model="form.bomId" class="w-full" :disabled="!preview" @change="loadPreview()">
                <el-option v-for="b in preview?.boms ?? []" :key="b.id" :value="b.id" :label="b.label + (b.isDefault ? '（默认）' : '')" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="工艺路线">
              <el-select v-model="form.routingId" class="w-full" clearable :disabled="!preview" @change="loadPreview()">
                <el-option v-for="b in preview?.routings ?? []" :key="b.id" :value="b.id" :label="b.label + (b.isDefault ? '（默认）' : '')" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xl="8" :span="12"><el-form-item label="生产车间"><OrgTreeSelect v-model="form.deptId" only-dept /></el-form-item></el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="计划开工" prop="planStart"><el-date-picker v-model="form.planStart" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="计划完工" prop="planEnd"><el-date-picker v-model="form.planEnd" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="1" :max="9" controls-position="right" class="w-full" /></el-form-item>
          </el-col>
          <el-col :xl="8" :span="12">
            <el-form-item label="生产批号"><el-input v-model="form.batchNo" maxlength="64" placeholder="默认同订单号" /></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </ErpPanel>
    </el-form>

    <ErpPanel v-if="isRework" title="投入物料" description="返工订单按录入的物料生成用料，应领 = 计划数量 × 单位用量 × (1 + 损耗率)">
      <template #extra><el-button type="primary" icon="Plus" @click="addMaterial">添加物料</el-button></template>
      <el-table :data="form.materials" row-key="key">
        <el-table-column label="物料" min-width="240"><template #default="{ row }"><MaterialSelect v-model="row.componentId" /></template></el-table-column>
        <el-table-column label="单位用量" width="140"><template #default="{ row }"><QtyInput v-model="row.qtyPer" :precision="6" /></template></el-table-column>
        <el-table-column label="损耗率" width="120"><template #default="{ row }"><NumberInput v-model="row.scrapRate" :precision="4" placeholder="如 0.02" /></template></el-table-column>
        <el-table-column label="发料方式" width="120">
          <template #default="{ row }">
            <el-select v-model="row.issueMethod"><el-option v-for="o in ISSUE_METHOD_OPTIONS" :key="String(o.value)" :value="o.value" :label="o.label" /></el-select>
          </template>
        </el-table-column>
        <el-table-column label="工序号" width="110"><template #default="{ row }"><el-input-number v-model="row.operationSeq" :min="0" controls-position="right" /></template></el-table-column>
        <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" maxlength="256" /></template></el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }"><el-button link type="danger" @click="form.materials.splice($index, 1)">删除</el-button></template>
        </el-table-column>
        <template #empty><ErpEmpty compact description="点击“添加物料”录入返工需要的物料" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel v-else-if="isDisassembly" title="拆解产出预览"
      :description="preview?.bomNo ? `投入产品本身；按 BOM ${preview.bomNo} V${preview.bomVersion} 拆出子件，下达时固化，子件通过“拆解入库”退料单入库` : '选择产品后显示'">
      <el-table v-loading="previewing" :data="preview?.materials ?? []" max-height="420">
        <el-table-column prop="lineNo" label="行" width="50" />
        <el-table-column label="子件" min-width="220"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
        <el-table-column prop="spec" label="规格" width="140" show-overflow-tooltip />
        <el-table-column label="单位产出" width="110" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer, 6) }}</template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <template #empty><ErpEmpty compact description="选择产品后显示 BOM 子件" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel v-else title="用料预览" :description="preview?.bomNo ? `BOM ${preview.bomNo} V${preview.bomVersion}；下达时按当时的 BOM 固化用料` : '选择产品后显示'">
      <el-table v-loading="previewing" :data="preview?.materials ?? []" max-height="420">
        <el-table-column prop="lineNo" label="行" width="50" />
        <el-table-column label="物料" min-width="220"><template #default="{ row }">{{ row.code }} {{ row.name }}</template></el-table-column>
        <el-table-column prop="spec" label="规格" width="140" show-overflow-tooltip />
        <el-table-column label="单位用量" width="100" align="right"><template #default="{ row }">{{ formatQty(row.qtyPer, 6) }}</template></el-table-column>
        <el-table-column label="损耗率" width="80" align="right"><template #default="{ row }">{{ row.scrapRate ? `${(Number(row.scrapRate) * 100).toFixed(2)}%` : '-' }}</template></el-table-column>
        <el-table-column label="应领" width="110" align="right"><template #default="{ row }">{{ formatQty(row.requiredQty) }}</template></el-table-column>
        <el-table-column prop="uom" label="单位" width="60" />
        <el-table-column label="可用库存" width="110" align="right">
          <template #default="{ row }"><span :class="{ 'text-danger': row.shortage }">{{ formatQty(row.availableQty) }}</span></template>
        </el-table-column>
        <el-table-column label="发料方式" width="90"><template #default="{ row }">{{ labelOf(ISSUE_METHOD_OPTIONS, row.issueMethod) }}</template></el-table-column>
        <el-table-column prop="operationSeq" label="工序" width="70" />
        <template #empty><ErpEmpty compact description="选择产品和计划数量后显示 BOM 展开的用料" /></template>
      </el-table>
    </ErpPanel>

    <ErpPanel title="工序预览" :description="preview?.routingNo ? `工艺路线 ${preview.routingNo}` : '没有工艺路线时按末道报工'">
      <el-table :data="preview?.operations ?? []">
        <el-table-column prop="seq" label="工序号" width="80" />
        <el-table-column prop="operation" label="工序" min-width="140" />
        <el-table-column prop="workCenterName" label="工作中心" width="160" />
        <el-table-column label="标准工时(秒/件)" width="130" align="right"><template #default="{ row }">{{ row.stdRunSeconds ?? '-' }}</template></el-table-column>
        <el-table-column label="报工点" width="80" align="center"><template #default="{ row }">{{ row.reportPoint ? '是' : '-' }}</template></el-table-column>
        <el-table-column label="检验点" width="80" align="center"><template #default="{ row }">{{ row.inspectionPoint ? '是' : '-' }}</template></el-table-column>
        <el-table-column label="委外" width="70" align="center"><template #default="{ row }">{{ row.outsourced ? '是' : '-' }}</template></el-table-column>
        <template #empty><ErpEmpty compact description="该产品没有已审核的工艺路线" /></template>
      </el-table>
    </ErpPanel>
  </ErpPage>
</template>
