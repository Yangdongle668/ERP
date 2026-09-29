<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { ISSUE_TYPE_OPTIONS, issueApi, joinList, labelOf, MATERIAL_DOC_STATUS, optionsOf, searchOrders, type IssueRow, type ProdOrderRow } from '../api/production'

defineOptions({ name: 'MfgIssueList' })

/** 领料单列表（需求 09-03 3.1，T1）：正常领料、超领、倒冲；审核后生成仓库出库单，仓库确认后回写已领 */
const route = useRoute()
const router = useRouter()

type Query = { docNo?: string; prodOrderNo?: string; issueType?: string; statuses?: string[]; warehouseId?: string; materialId?: string; dates?: [string, string] }
const { query, list, total, loading, selection, load, search, reset, onSelectionChange } = useListPage<Query, IssueRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return issueApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ prodOrderNo: typeof route.query.prodOrderNo === 'string' ? route.query.prodOrderNo : undefined }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'prodOrderNo', label: '生产订单', upper: true },
  { prop: 'issueType', label: '类型', type: 'select', options: ISSUE_TYPE_OPTIONS },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(MATERIAL_DOC_STATUS), multiple: true },
  { prop: 'warehouseId', label: '仓库', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'dates', label: '单据日期', type: 'daterange' }
]
const columns: TableColumn<IssueRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/production/issue/${r.id}`) },
  { prop: 'issueType', label: '类型', width: 80, formatter: (r) => labelOf(ISSUE_TYPE_OPTIONS, r.issueType) },
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'productCode', label: '产品编码', width: 120 },
  { prop: 'productName', label: '产品名称', minWidth: 140 },
  { prop: 'warehouseName', label: '发料仓', width: 110 },
  { prop: 'lineCount', label: '行数', width: 60, align: 'right' },
  { prop: 'overReason', label: '超领原因', width: 100, type: 'dict', dictType: 'mfg_over_issue_reason' },
  { prop: 'stockOutNos', label: '出库单', width: 150 },
  { prop: 'status', label: '状态', width: 100, type: 'status', statusMap: MATERIAL_DOC_STATUS },
  { prop: 'ownerName', label: '领料人', width: 90 },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]
const printIds = () => selection.value.map((r) => r.id)

// ---------- 按套数批量领料 ----------
const kitVisible = ref(false)
const kitOrders = ref<string[]>([])
const kitQty = ref<string>()
const kitSaving = ref(false)
const orderLabel = (o: ProdOrderRow) => `${o.docNo} ${o.materialCode} ${o.materialName}`
async function saveKit(submit: boolean) {
  if (!kitOrders.value.length) return ElMessage.warning('请选择生产订单')
  kitSaving.value = true
  try {
    const r = await issueApi.byKit(kitOrders.value, kitQty.value || undefined, submit)
    if (r.warnings.length) ElNotification({ type: 'warning', title: `已生成 ${r.ids.length} 张领料单`, message: r.warnings.join('；'), duration: 8000 })
    else ElMessage.success(`已生成 ${r.ids.length} 张领料单`)
    kitVisible.value = false
    load()
  } finally {
    kitSaving.value = false
  }
}
</script>

<template>
  <ErpPage description="按生产订单领料：审核后生成仓库出库单，仓库确认实发后回写已领数量">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-warehouseId><WarehouseSelect v-model="query.warehouseId" :only-mine="false" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" selection storage-key="mfg.issue" @selection-change="onSelectionChange" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'mfg:issue:create'" type="primary" icon="Plus" @click="router.push('/production/issue/new')">新建领料单</el-button>
          <el-button v-perm="'mfg:issue:create'" @click="kitVisible = true">按套数批量领料</el-button>
          <PrintButton biz-type="MFG_ISSUE" :ids="printIds" permission="mfg:issue:print" label="批量打印" />
        </template>
        <template #empty>
          <el-button v-perm="'mfg:issue:create'" icon="Plus" @click="router.push('/production/issue/new')">新建领料单</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>

    <el-dialog v-model="kitVisible" title="按套数批量领料" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="生产订单" required>
          <RemoteSelect v-model="kitOrders" multiple :search="(k: string) => searchOrders(k)" :label="orderLabel" placeholder="生产订单号，可多选" />
        </el-form-item>
        <el-form-item label="套数"><QtyInput v-model="kitQty" placeholder="为空表示领全部未领" /></el-form-item>
      </el-form>
      <p class="hint">每张订单按“套数 × 单位用量 × (1 + 损耗)”计算，不超过未领数量；按发料仓拆分为多张领料单。</p>
      <template #footer>
        <el-button @click="kitVisible = false">取消</el-button>
        <el-button :loading="kitSaving" @click="saveKit(false)">生成草稿</el-button>
        <el-button v-perm="'mfg:issue:submit'" type="primary" :loading="kitSaving" @click="saveKit(true)">生成并提交</el-button>
      </template>
    </el-dialog>
  </ErpPage>
</template>

<style scoped>
.hint { color: var(--erp-color-text-secondary); }
</style>
