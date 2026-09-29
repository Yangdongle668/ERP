<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { today, toDateString } from '@/utils/format'
import { DEMAND_STATUS, DEMAND_TYPE, demandApi, joinList, optionsOf, type DemandRow, type DemandSave } from '../api/pmc'

defineOptions({ name: 'PmcDemandList' })

/** 需求池（需求 06-01 4.1，T1）：销售订单、预测由系统自动维护，计划员可补充手工需求 */
const router = useRouter()
const in90 = () => {
  const d = new Date()
  d.setDate(d.getDate() + 90)
  return toDateString(d)
}

type Query = { materialId?: string; customerId?: string; demandTypes?: string[]; dates?: [string, string]; openOnly?: boolean; plannerId?: string }
const { query, list, total, loading, load, search, reset } = useListPage<Query, DemandRow>({
  api: (q) => {
    const { demandTypes, dates, ...rest } = q
    return demandApi.page({ ...rest, demandTypes: joinList(demandTypes), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ openOnly: true, dates: [today(), in90()] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'demandTypes', label: '需求类型', type: 'select', options: optionsOf(DEMAND_TYPE), multiple: true },
  { prop: 'dates', label: '需求日期', type: 'daterange' },
  { prop: 'openOnly', label: '只看未满足', type: 'select', options: [{ value: true, label: '是' }, { value: false, label: '否' }], clearable: false },
  { prop: 'plannerId', label: '计划员', type: 'user' }
]
const columns: TableColumn<DemandRow>[] = [
  { prop: 'demandType', label: '需求类型', width: 90, type: 'status', statusMap: DEMAND_TYPE },
  { prop: 'sourceNo', label: '来源单号', width: 170, slot: true },
  { prop: 'customerName', label: '客户', width: 130 },
  { prop: 'materialCode', label: '物料编码', width: 130 },
  { prop: 'materialName', label: '名称', minWidth: 140 },
  { prop: 'materialSpec', label: '规格', width: 120, hidden: true },
  { prop: 'qty', label: '需求数量', width: 100, type: 'qty', uomProp: 'baseUom' },
  { prop: 'fulfilledQty', label: '已满足', width: 90, type: 'qty', uomProp: 'baseUom' },
  { prop: 'openQty', label: '未满足', width: 90, type: 'qty', uomProp: 'baseUom', summary: true },
  { prop: 'requiredDate', label: '需求日期', width: 110, type: 'date' },
  { prop: 'customerDate', label: '要求交期', width: 110, type: 'date' },
  { prop: 'promisedDate', label: '承诺交期', width: 110, type: 'date' },
  { prop: 'availableQty', label: '可用库存', width: 90, type: 'qty' },
  { prop: 'wipQty', label: '在制', width: 80, type: 'qty' },
  { prop: 'inTransitQty', label: '在途', width: 80, type: 'qty' },
  { prop: 'priority', label: '优先级', width: 70, align: 'center' },
  { prop: 'demandStatus', label: '状态', width: 80, type: 'status', statusMap: DEMAND_STATUS },
  { prop: 'remark', label: '说明', minWidth: 120, hidden: true }
]
const asRow = (r: unknown) => r as DemandRow
function openSource(r: DemandRow) {
  if (r.demandType === 'SALES_ORDER' && r.sourceId) router.push(`/sales/order/${r.sourceId}`)
  else if (r.demandType === 'FORECAST' && r.sourceId) router.push(`/sales/forecast/${r.sourceId}`)
}
const exportParams = () => {
  const { demandTypes, dates, ...rest } = query
  return { ...rest, demandTypes: joinList(demandTypes), dateFrom: dates?.[0], dateTo: dates?.[1] }
}

// ---------- 手工需求 ----------
const dlg = ref(false)
const editId = ref<string>()
const formRef = ref<FormInstance>()
const form = ref<Partial<DemandSave>>({})
const saving = ref(false)
const rules: FormRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  qty: [{ required: true, message: '请填写需求数量', trigger: 'blur' }],
  requiredDate: [{ required: true, message: '请选择需求日期', trigger: 'change' }],
  remark: [{ required: true, message: '请填写需求说明', trigger: 'blur' }]
}
function openManual(r?: DemandRow) {
  editId.value = r?.id
  form.value = r ? { materialId: r.materialId, qty: r.qty, requiredDate: r.requiredDate, priority: r.priority, customerId: r.customerId, remark: r.remark }
    : { priority: 5, requiredDate: today() }
  dlg.value = true
}
async function saveManual() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const d = form.value as DemandSave
    if (editId.value) await demandApi.update(editId.value, d)
    else await demandApi.create(d)
    ElMessage.success('保存成功')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}
const rowActions = (r: DemandRow): RowAction[] => [
  { label: '修改', permission: 'pmc:demand:create', visible: r.demandType === 'MANUAL' && r.demandStatus === 'OPEN', handler: () => openManual(r) },
  { label: '关闭', permission: 'pmc:demand:create', danger: true, visible: r.demandType === 'MANUAL' && r.demandStatus === 'OPEN',
    confirm: '确定关闭该手工需求吗？', handler: async () => {
      await demandApi.close(r.id)
      ElMessage.success('已关闭')
      load()
    } }
]
</script>

<template>
  <ErpPage description="需求池：销售订单、净预测由系统自动维护（每天 01:30 对账），是 MPS / MRP 的输入；计划员可补充手工需求">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pmc.demand" :actions-width="110" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pmc:demand:create'" type="primary" icon="Plus" @click="openManual()">新增手工需求</el-button>
          <el-button v-perm="'pmc:mrp:run'" @click="router.push('/pmc/mrp')">运行 MRP</el-button>
        </template>
        <template #toolbar-right>
          <ExportButton url="/pmc/demands/export" :params="exportParams" filename="需求池" permission="pmc:demand:query" />
        </template>
        <template #col-sourceNo="{ row }">
          <el-link v-if="asRow(row).sourceId && asRow(row).demandType !== 'MANUAL'" type="primary" underline="never" @click="openSource(asRow(row))">
            {{ asRow(row).sourceNo }}{{ asRow(row).sourceLineNo ? ` 行 ${asRow(row).sourceLineNo}` : '' }}
          </el-link>
          <span v-else>{{ asRow(row).demandType === 'MANUAL' ? asRow(row).createdByName ?? '手工' : '-' }}</span>
        </template>
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dlg" :title="editId ? '修改手工需求' : '新增手工需求'" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="物料" prop="materialId"><MaterialSelect v-model="form.materialId" /></el-form-item>
        <el-form-item label="需求数量" prop="qty"><QtyInput v-model="form.qty" /></el-form-item>
        <el-form-item label="需求日期" prop="requiredDate"><el-date-picker v-model="form.requiredDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="1" :max="9" controls-position="right" /></el-form-item>
        <el-form-item label="客户"><CustomerSelect v-model="form.customerId" /></el-form-item>
        <el-form-item label="说明" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" placeholder="如：展会样机、备库" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveManual">保存</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
