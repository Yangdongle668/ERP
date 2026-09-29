<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { SearchField, TableColumn } from '@/components'
import { useListPage } from '@/composables/useListPage'
import { changeApi, DOC_STATUS, joinList, optionsOf, type ChangeRow } from '../api/sales'

defineOptions({ name: 'SalChangeList' })

/** 销售订单变更单列表（需求 04-04 3.1，T1）；新建变更从订单详情发起 */
const router = useRouter()

type Query = { docNo?: string; orderNo?: string; customerId?: string; changeReason?: string; statuses?: string[]; dates?: [string, string] }
const { query, list, total, loading, load, search, reset } = useListPage<Query, ChangeRow>({
  api: (q) => {
    const { statuses, dates, ...rest } = q
    return changeApi.page({ ...rest, statuses: joinList(statuses), dateFrom: dates?.[0], dateTo: dates?.[1] } as never)
  },
  refreshOnActivated: true
})

const fields: SearchField[] = [
  { prop: 'docNo', label: '变更单号', upper: true },
  { prop: 'orderNo', label: '订单号', upper: true },
  { prop: 'customerId', label: '客户', type: 'slot' },
  { prop: 'changeReason', label: '变更原因', type: 'dict', dictType: 'sal_change_reason' },
  { prop: 'statuses', label: '状态', type: 'select', options: optionsOf(DOC_STATUS, ['IN_PROGRESS', 'COMPLETED', 'CLOSED']), multiple: true },
  { prop: 'dates', label: '变更日期', type: 'daterange' }
]

const columns: TableColumn<ChangeRow>[] = [
  { prop: 'docNo', label: '变更单号', width: 170, type: 'link', onClick: (r) => router.push(`/sales/order-change/${r.id}`) },
  { prop: 'orderNo', label: '订单号', width: 160, type: 'link', onClick: (r) => router.push(`/sales/order/${r.orderId}`) },
  { prop: 'customerName', label: '客户', minWidth: 150 },
  { prop: 'versionTo', label: '版本', width: 100, formatter: (r) => `V${r.versionFrom} → V${r.versionTo}` },
  { prop: 'changeReason', label: '变更原因', width: 120, type: 'dict', dictType: 'sal_change_reason' },
  { prop: 'amountDiff', label: '金额变化', width: 130, type: 'amount', currencyProp: 'currency' },
  { prop: 'amountChangeBase', label: '金额变化(本位币)', width: 140, type: 'amount' },
  { prop: 'status', label: '状态', width: 90, type: 'status', statusMap: DOC_STATUS },
  { prop: 'ownerName', label: '经办人', width: 90 },
  { prop: 'docDate', label: '变更日期', width: 110, type: 'date' }
]
</script>

<template>
  <ErpPage description="已审核订单的数量、价格、交期、条款变更；审批通过后订单升版并通知出货、生产与 PMC">
    <ErpPanel>
      <template #filter>
        <ErpSearchForm v-model="query" :fields="fields" :loading="loading" @search="search" @reset="reset">
          <template #field-customerId><CustomerSelect v-model="query.customerId" /></template>
        </ErpSearchForm>
      </template>
      <ErpTable :columns="columns" :data="list" :loading="loading" storage-key="sal.order-change" empty-text="在销售订单详情点击“变更”发起" @refresh="load" />
      <ErpPagination v-model:page-no="query.pageNo" v-model:page-size="query.pageSize" :total="total" @change="load" />
    </ErpPanel>
  </ErpPage>
</template>
