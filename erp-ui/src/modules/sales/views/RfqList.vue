<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { joinList, optionsOf, RFQ_STATUS, rfqApi, type RfqRow } from '../api/sales'

defineOptions({ name: 'SalRfqList' })

/** 客户 RFQ 列表（需求 04-02 3.1，T1） */
const router = useRouter()

type Query = { docNo?: string; customerId?: string; ownerId?: string; engineerId?: string; statuses?: string[]; reply?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, RfqRow>({
  api: (q) => {
    const { statuses, reply, ...rest } = q
    return rfqApi.page({ ...rest, statuses: joinList(statuses), replyFrom: reply?.[0], replyTo: reply?.[1] } as never)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(RFQ_STATUS), multiple: true },
  { prop: 'ownerId', label: '业务员', type: 'user' },
  { prop: 'engineerId', label: '工程师', type: 'user' },
  { prop: 'reply', label: '回复截止', type: 'daterange' }
]

const FEAS: Record<string, string> = { ALL_OK: '全部可行', HAS_NG: '有不可行', PENDING: '待评估' }
const columns: TableColumn<RfqRow>[] = [
  { prop: 'docNo', label: '单号', width: 160, type: 'link', onClick: (r) => router.push(`/sales/rfq/${r.id}`) },
  { prop: 'customerName', label: '客户', minWidth: 160 },
  { prop: 'lineCount', label: '需求行', width: 80, align: 'right' },
  { prop: 'replyDueDate', label: '回复截止', width: 120, slot: true },
  { prop: 'engineerName', label: '工程师', width: 90 },
  { prop: 'costEngineerName', label: '成本核算', width: 90 },
  { prop: 'feasibility', label: '可行性', width: 100, slot: true },
  { prop: 'rfqStatus', label: '状态', width: 90, type: 'status', statusMap: RFQ_STATUS },
  { prop: 'ownerName', label: '业务员', width: 90 },
  { prop: 'docDate', label: '单据日期', width: 110, type: 'date' }
]
const asRow = (r: unknown) => r as RfqRow
</script>

<template>
  <ErpPage description="登记客户询价需求，工程评估可行性、成本核算后生成报价单">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="sal.rfq" @refresh="load">
        <template #toolbar>
          <el-button v-perm="'sales:rfq:create'" type="primary" icon="Plus" @click="router.push('/sales/rfq/new')">新建 RFQ</el-button>
        </template>
        <template #col-replyDueDate="{ row }">
          <span :class="{ 'text-danger': asRow(row).dueLevel === 'OVERDUE', 'text-warning': asRow(row).dueLevel === 'SOON' }">{{ asRow(row).replyDueDate }}</span>
        </template>
        <template #col-feasibility="{ row }">
          <span :class="{ 'text-danger': asRow(row).feasibility === 'HAS_NG', 'text-muted': asRow(row).feasibility === 'PENDING' }">{{ FEAS[asRow(row).feasibility] ?? '-' }}</span>
        </template>
        <template #empty>
          <el-button v-perm="'sales:rfq:create'" icon="Plus" @click="router.push('/sales/rfq/new')">新建 RFQ</el-button>
        </template>
      </ErpTable>
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
