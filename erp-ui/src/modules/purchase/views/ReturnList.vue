<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { useUserStore } from '@/stores/user'
import { joinList } from '../api/common'
import {
  HANDLING_OPTIONS, OUT_STATUS, RETURN_STATUS, RETURN_STATUS_OPTIONS, returnApi,
  type DefectCandidate, type ReturnQuery, type ReturnRow
} from '../api/receipt'

defineOptions({ name: 'PurReturnList' })

/** 采购退货列表（需求 07-08 3.1，T1） */
const router = useRouter()
const me = useUserStore()

type Query = Omit<ReturnQuery, 'pageNo' | 'pageSize' | 'statuses'> & { statuses?: string[]; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ReturnRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return returnApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as ReturnQuery)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'returnReason', label: '退货原因', type: 'dict', dictType: 'pur_return_reason' },
  { prop: 'handling', label: '处理方式', type: 'select', options: HANDLING_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: RETURN_STATUS_OPTIONS, multiple: true },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]

const columns = computed<TableColumn<ReturnRow>[]>(() => [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/purchase/return/${r.id}`) },
  { prop: 'supplierName', label: '供应商', width: 130 },
  { prop: 'returnReason', label: '原因', width: 120, type: 'dict', dictType: 'pur_return_reason' },
  { prop: 'handling', label: '处理方式', width: 80, type: 'enum', options: HANDLING_OPTIONS },
  { prop: 'warehouseName', label: '出库仓', width: 110 },
  { prop: 'materialSummary', label: '物料摘要', minWidth: 200 },
  ...(me.hasPermission('pur:price:view') ? [{ prop: 'totalAmount', label: '金额', width: 120, type: 'amount', currencyProp: 'currency' } as TableColumn<ReturnRow>] : []),
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: RETURN_STATUS },
  { prop: 'outStatus', label: '出库', width: 80, type: 'status', statusMap: OUT_STATUS },
  { prop: 'docDate', label: '日期', width: 110, type: 'date' }
])

// ---------- 从不良品生成（3.3） ----------
interface Cand extends DefectCandidate {
  genQty?: string
}
const defectVisible = ref(false)
const defectSupplier = ref<string>()
const candidates = ref<Cand[]>([])
const picked = ref<Cand[]>([])
const loadingDefects = ref(false)
const generating = ref(false)
async function loadDefects() {
  loadingDefects.value = true
  try {
    candidates.value = (await returnApi.defectCandidates(defectSupplier.value)).map((c) => ({ ...c, genQty: c.returnableQty }))
  } finally {
    loadingDefects.value = false
  }
}
function openDefects() {
  defectVisible.value = true
  picked.value = []
  loadDefects()
}
async function generate() {
  if (!picked.value.length) return ElMessage.warning('请勾选要退货的批次')
  generating.value = true
  try {
    const ids = await returnApi.fromDefects(picked.value.map((c) => ({ receiptLineId: c.receiptLineId, warehouseId: c.warehouseId, qty: c.genQty })))
    defectVisible.value = false
    ElMessage.success(`已生成 ${ids.length} 张草稿退货单`)
    if (ids.length === 1) router.push(`/purchase/return/${ids[0]}`)
    else load()
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <ErpPage description="退回供应商的不良或多收物料：审核后生成退货出库单，仓库确认后回写到货、订单与对账">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="pur.return" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'pur:return:create'" type="primary" icon="Plus" @click="router.push('/purchase/return/new')">新建退货</el-button>
          <el-button v-perm="'pur:return:create'" @click="openDefects">从不良品生成</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="defectVisible" title="从不良品生成退货单" width="1200px" :close-on-click-modal="false" append-to-body>
      <div class="filter">
        <SupplierSelect v-model="defectSupplier" class="w240" />
        <el-button type="primary" icon="Search" @click="loadDefects">查询</el-button>
        <span class="form-tip">不良品仓中有库存、且能追溯到采购到货（IQC 判退）的批次；按供应商生成草稿退货单，原因“检验不合格”，处理方式默认换货</span>
      </div>
      <el-table v-loading="loadingDefects" :data="candidates" max-height="460" @selection-change="(rows: Cand[]) => (picked = rows)">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="supplierName" label="供应商" width="120" />
        <el-table-column label="物料" min-width="180"><template #default="{ row }">{{ row.materialCode }} {{ row.materialName }}</template></el-table-column>
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column prop="warehouseName" label="不良品仓" width="110" />
        <el-table-column prop="ngQty" label="仓库数量" width="100" align="right" />
        <el-table-column prop="rejectedQty" label="判退数量" width="100" align="right" />
        <el-table-column label="退货数量" width="130"><template #default="{ row }"><QtyInput v-model="row.genQty" :uom="row.baseUom" /></template></el-table-column>
        <el-table-column prop="receiptNo" label="来源到货单" width="150" />
        <el-table-column prop="inspectionNo" label="检验单" width="140" />
        <template #empty><ErpEmpty compact description="没有可退的不良品" /></template>
      </el-table>
      <template #footer>
        <el-button @click="defectVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" :disabled="!picked.length" @click="generate">生成退货单</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.filter { display: flex; align-items: center; gap: var(--erp-space-2); margin-bottom: var(--erp-space-3); }
.filter .form-tip { margin: 0; }
</style>
