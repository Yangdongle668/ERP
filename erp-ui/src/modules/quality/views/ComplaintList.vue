<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { COMPLAINT_STATUS, HANDLING, SEVERITY, complaintApi, joinList, labelOf, optionsOf, type ComplaintRow } from '../api/quality'

defineOptions({ name: 'QcComplaintList' })

/** 客诉列表（需求 10-05 3.1，T1）：逾期未回复标红；业务员只看自己客户的客诉 */
const router = useRouter()
type Query = { docNo?: string; customerId?: string; materialId?: string; complaintType?: string; severity?: string; statuses?: string[]; qeId?: string; received?: string[] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ComplaintRow>({
  api: (q) => complaintApi.page({ ...q, statuses: joinList(q.statuses), receivedFrom: q.received?.[0], receivedTo: q.received?.[1], received: undefined } as never),
  defaultQuery: () => ({ statuses: ['OPEN'] }),
  refreshOnActivated: true
})
const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'materialId', label: '物料', type: 'slot' },
  { prop: 'complaintType', label: '类型', type: 'dict', dictType: 'qc_complaint_type' },
  { prop: 'severity', label: '严重度', type: 'select', options: optionsOf(SEVERITY) },
  { prop: 'statuses', label: '状态', type: 'select', options: [{ value: 'OPEN', label: '未结案' }, ...optionsOf(COMPLAINT_STATUS)], multiple: true },
  { prop: 'qeId', label: '负责 QE', type: 'user' },
  { prop: 'received', label: '收到日期', type: 'daterange' }
]
const columns: TableColumn<ComplaintRow>[] = [
  { prop: 'docNo', label: '单号', width: 150, type: 'link', onClick: (r) => router.push(`/quality/complaint/${r.id}`) },
  { prop: 'customerName', label: '客户', width: 130 },
  { prop: 'material', label: '物料', minWidth: 160, formatter: (r) => (r.materialCode ? `${r.materialCode} ${r.materialName ?? ''}` : '-') },
  { prop: 'complaintType', label: '类型', width: 70, type: 'dict', dictType: 'qc_complaint_type' },
  { prop: 'severity', label: '严重度', width: 70, type: 'status', statusMap: SEVERITY },
  { prop: 'complaintQty', label: '数量', width: 80, type: 'qty' },
  { prop: 'receivedAt', label: '收到时间', width: 140, type: 'datetime' },
  { prop: 'replyDueDate', label: '回复期限', width: 100, slot: true },
  { prop: 'qeName', label: '负责 QE', width: 80 },
  { prop: 'handling', label: '处理方式', width: 90, formatter: (r) => labelOf(HANDLING, r.handling) },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: COMPLAINT_STATUS },
  { prop: 'salesOwnerName', label: '业务员', width: 80 }
]
const asRow = (r: unknown) => r as ComplaintRow
</script>

<template>
  <ErpPage>
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
          <template #field-materialId><MaterialSelect v-model="query.materialId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="qc.complaint" @refresh="load">
        <template #toolbar><el-button v-perm="'qc:complaint:create'" type="primary" @click="router.push('/quality/complaint/new/edit')">登记客诉</el-button></template>
        <template #col-replyDueDate="{ row }"><span :class="{ danger: asRow(row).replyOverdue }">{{ asRow(row).replyDueDate }}</span></template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>

<style scoped>
.danger { color: var(--erp-color-error); }
</style>
