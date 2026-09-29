<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { RowAction, SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { DOC_STATUS, NCR_SOURCE, SEVERITY, joinList, ncrApi, optionsOf, recordOptions, type NcrRow } from '../api/quality'

defineOptions({ name: 'QcNcrList' })

/** NCR 列表（需求 10-03 3.1，T1） */
const router = useRouter()
type Query = { docNo?: string; source?: string; materialId?: string; supplierId?: string; customerId?: string; responsibility?: string; severity?: string; statuses?: string[]; date?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, NcrRow>({
  api: (q) => ncrApi.page({ ...q, statuses: joinList(q.statuses), dateFrom: q.date?.[0], dateTo: q.date?.[1], date: undefined } as never),
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'source', label: '来源', type: 'select', options: recordOptions(NCR_SOURCE) },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'supplierId', label: '供应商', type: 'slot' },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'responsibility', label: '责任', type: 'dict', dictType: 'qc_ncr_responsibility' },
  { prop: 'severity', label: '严重度', type: 'select', options: optionsOf(SEVERITY) },
  { prop: 'statuses', label: '状态', type: 'select', options: [{ value: 'OPEN', label: '未关闭' }, ...optionsOf(DOC_STATUS)], multiple: true },
  { prop: 'date', label: '日期', type: 'daterange' }
]
const columns: TableColumn<NcrRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/quality/ncr/${r.id}`) },
  { prop: 'source', label: '来源', width: 90, formatter: (r) => NCR_SOURCE[r.source] ?? r.source },
  { prop: 'sourceNo', label: '来源单号', width: 150 },
  { prop: 'materialCode', label: '物料编码', width: 120 },
  { prop: 'materialName', label: '名称', minWidth: 130 },
  { prop: 'batchNo', label: '批次', width: 110 },
  { prop: 'ncrQty', label: '数量', width: 90, type: 'qty' },
  { prop: 'severity', label: '严重度', width: 70, type: 'status', statusMap: SEVERITY },
  { prop: 'responsibility', label: '责任', width: 80, type: 'dict', dictType: 'qc_ncr_responsibility' },
  { prop: 'dispositionSummary', label: '处置', minWidth: 160 },
  { prop: 'partner', label: '供应商 / 客户', width: 130, formatter: (r) => r.supplierName ?? r.customerName ?? '-' },
  { prop: 'status', label: '状态', width: 80, type: 'status', statusMap: DOC_STATUS },
  { prop: 'ownerName', label: '创建人', width: 80 },
  { prop: 'docDate', label: '日期', width: 100, type: 'date' }
]
const rowActions = (r: NcrRow): RowAction[] => [
  { label: '编辑', permission: 'qc:ncr:update', visible: r.status === 'DRAFT', handler: () => router.push(`/quality/ncr/${r.id}/edit`) },
  { label: '查看', handler: () => router.push(`/quality/ncr/${r.id}`) }
]
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
          <template #field-supplierId><SupplierSelect v-model="query.supplierId" /></template>
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="qc.ncr" :actions-width="100" @refresh="load">
        <template #toolbar><el-button v-perm="'qc:ncr:create'" type="primary" @click="router.push('/quality/ncr/new/edit')">新建 NCR</el-button></template>
        <template #actions="{ row }"><RowActions :actions="rowActions(row as NcrRow)" /></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
