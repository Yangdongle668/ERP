<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { formatQty } from '@/utils/format'
import { defectApi, DISPOSITION_STATUS, joinList, num, optionsOf, type DefectRow } from '../api/production'

defineOptions({ name: 'MfgDefectList' })

/** 不良记录（需求 09-06 3.1～3.3，T1）：报工登记的不良逐条处置——返修合格回到工序合格，报废计入订单报废，或生成 NCR 交品质 */
const router = useRouter()
const ncrAvailable = ref(false)
onMounted(async () => (ncrAvailable.value = await defectApi.ncrAvailable().catch(() => false)))

type Query = { prodOrderNo?: string; materialId?: string; operationSeq?: number; defectCode?: string; dispositions?: string[]; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, DefectRow>({
  api: (q) => {
    const { dispositions, dates, ...rest } = q
    return defectApi.page({ ...rest, dispositions: joinList(dispositions), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ dispositions: ['PENDING'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'prodOrderNo', label: '生产订单', upper: true },
  { prop: 'materialId', label: '产品', type: 'slot' },
  { prop: 'operationSeq', label: '工序号', type: 'number' },
  { prop: 'defectCode', label: '不良代码', type: 'dict', dictType: 'mfg_defect_code' },
  { prop: 'dispositions', label: '处置', type: 'select', options: optionsOf(DISPOSITION_STATUS, ['DRAFT']), multiple: true },
  { prop: 'dates', label: '报工日期', type: 'daterange' }
]
const columns: TableColumn<DefectRow>[] = [
  { prop: 'reportNo', label: '报工单', width: 150, type: 'link', onClick: (r) => router.push(`/production/report/${r.reportId}`) },
  { prop: 'reportDate', label: '日期', width: 100, type: 'date' },
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'materialCode', label: '产品编码', width: 120 },
  { prop: 'materialName', label: '产品名称', minWidth: 130 },
  { prop: 'operationSeq', label: '工序号', width: 70 },
  { prop: 'operation', label: '工序', width: 100 },
  { prop: 'defectCode', label: '不良代码', width: 100, type: 'dict', dictType: 'mfg_defect_code' },
  { prop: 'qty', label: '不良数', width: 80, type: 'qty' },
  { prop: 'position', label: '位置', width: 90 },
  { prop: 'description', label: '描述', minWidth: 140 },
  { prop: 'repairedQty', label: '返修合格', width: 90, type: 'qty' },
  { prop: 'scrappedQty', label: '报废', width: 70, type: 'qty' },
  { prop: 'pendingQty', label: '待处理', width: 80, type: 'qty' },
  { prop: 'ncrNo', label: 'NCR', width: 120 },
  { prop: 'disposition', label: '处置', width: 80, type: 'status', statusMap: DISPOSITION_STATUS },
  { prop: 'handledByName', label: '处置人', width: 90, hidden: true },
  { prop: 'handledAt', label: '处置时间', width: 140, type: 'datetime', hidden: true }
]

// ---------- 处置 ----------
const dlg = ref<{ visible: boolean; mode: 'repair' | 'scrap'; row?: DefectRow; qty?: string; scrapReason?: string }>({ visible: false, mode: 'repair' })
const saving = ref(false)
function open(mode: 'repair' | 'scrap', row: DefectRow) {
  dlg.value = { visible: true, mode, row, qty: row.pendingQty, scrapReason: undefined }
}
async function save() {
  const x = dlg.value
  if (!(num(x.qty) > 0)) return ElMessage.warning('请填写数量')
  if (num(x.qty) > num(x.row?.pendingQty)) return ElMessage.warning('处置数量超过待处理数量')
  if (x.mode === 'scrap' && !x.scrapReason) return ElMessage.warning('请选择报废原因')
  saving.value = true
  try {
    if (x.mode === 'repair') await defectApi.repair(x.row!.id, x.qty!)
    else await defectApi.scrap(x.row!.id, x.qty!, x.scrapReason)
    ElMessage.success(x.mode === 'repair' ? '已登记返修合格' : '已报废')
    dlg.value.visible = false
    load()
  } finally {
    saving.value = false
  }
}
const rowActions = (r: DefectRow): RowAction[] => [
  { label: '返修合格', permission: 'mfg:defect:update', visible: r.disposition === 'PENDING' && num(r.pendingQty) > 0, handler: () => open('repair', r) },
  { label: '报废', permission: 'mfg:defect:update', visible: r.disposition === 'PENDING' && num(r.pendingQty) > 0, handler: () => open('scrap', r) },
  { label: '生成 NCR', permission: 'mfg:defect:to-ncr', visible: ncrAvailable.value && !r.ncrNo && r.disposition === 'PENDING', handler: async () => {
    const no = await defectApi.toNcr(r.id)
    ElMessage.success(`已生成 NCR ${no}`)
    load()
  } }
]
const asRow = (r: unknown) => r as DefectRow
</script>

<template>
  <ErpPage description="报工登记的不良：返修合格计入工序合格，报废计入订单报废；品质模块启用后可生成 NCR">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="mfg.defect" :actions-width="170" @refresh="load">
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="dlg.visible" :title="dlg.mode === 'repair' ? '返修合格' : '不良报废'" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="不良">{{ dlg.row?.prodOrderNo }} 工序 {{ dlg.row?.operationSeq }}，<DictTag type="mfg_defect_code" :value="dlg.row?.defectCode" /></el-form-item>
        <el-form-item label="待处理">{{ formatQty(dlg.row?.pendingQty) }}</el-form-item>
        <el-form-item :label="dlg.mode === 'repair' ? '返修合格' : '报废数量'" required><QtyInput v-model="dlg.qty" /></el-form-item>
        <el-form-item v-if="dlg.mode === 'scrap'" label="报废原因" required><DictSelect v-model="dlg.scrapReason" type="mfg_scrap_reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>
