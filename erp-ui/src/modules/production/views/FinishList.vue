<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { FINISH_STATUS, finishApi, joinList, optionsOf, type FinishRow } from '../api/production'

defineOptions({ name: 'MfgFinishList' })

/** 完工入库记录（需求 09-05，T1）：在生产订单详情申请入库，仓库确认后回写；需 FQC 的产品检验后计入合格入库 */
const route = useRoute()
const router = useRouter()

type Query = { prodOrderNo?: string; prodOrderId?: string; materialId?: string; statuses?: string[]; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, FinishRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return finishApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  defaultQuery: () => ({ prodOrderId: typeof route.query.prodOrderId === 'string' ? route.query.prodOrderId : undefined }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'prodOrderNo', label: '生产订单', upper: true },
  { prop: 'materialId', label: '产品', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(FINISH_STATUS), multiple: true },
  { prop: 'dates', label: '申请日期', type: 'daterange' }
]
const columns: TableColumn<FinishRow>[] = [
  { prop: 'docNo', label: '申请单号', width: 150 },
  { prop: 'prodOrderNo', label: '生产订单', width: 150, type: 'link', onClick: (r) => router.push(`/production/prod-order/${r.prodOrderId}`) },
  { prop: 'materialCode', label: '产品编码', width: 120 },
  { prop: 'materialName', label: '产品名称', minWidth: 140 },
  { prop: 'qty', label: '申请数量', width: 90, type: 'qty', summary: true },
  { prop: 'batchNo', label: '批次', width: 130 },
  { prop: 'fqcRequired', label: 'FQC', width: 60, type: 'bool' },
  { prop: 'warehouseName', label: '入库仓', width: 110 },
  { prop: 'stockInNos', label: '入库单', width: 150 },
  { prop: 'stockedQty', label: '已入库', width: 90, type: 'qty', summary: true },
  { prop: 'qualifiedQty', label: '合格', width: 80, type: 'qty', summary: true },
  { prop: 'rejectedQty', label: '判退', width: 80, type: 'qty' },
  { prop: 'finishStatus', label: '状态', width: 100, type: 'status', statusMap: FINISH_STATUS },
  { prop: 'ownerName', label: '申请人', width: 90 },
  { prop: 'docDate', label: '申请日期', width: 110, type: 'date' },
  { prop: 'remark', label: '备注', minWidth: 120, hidden: true }
]
const rowActions = (r: FinishRow): RowAction[] => [
  { label: '取消', permission: 'mfg:finish:cancel', danger: true, visible: r.finishStatus === 'SUBMITTED',
    confirm: '取消后作废未确认的入库单，可重新申请，确定吗？', handler: async () => {
      await finishApi.cancel(r.id)
      ElMessage.success('已取消')
      load()
    } }
]
const asRow = (r: unknown) => r as FinishRow
</script>

<template>
  <ErpPage description="完工入库申请：在生产订单详情中发起，仓库确认入库后回写订单；需 FQC 的产品检验合格后计入合格入库">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="mfg.finish" :actions-width="80" @refresh="load">
        <template #actions="{ row }"><RowActions :actions="rowActions(asRow(row))" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
